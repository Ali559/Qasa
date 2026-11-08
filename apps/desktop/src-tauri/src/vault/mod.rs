//! The vault service: one SQLite file plus the unlocked keys. Mirrors iOS `VaultSession`
//! (create, unlock, lock, change password) and `VaultRepository` (save, delete, read).
//! Keys never leave this process; the UI gets envelopes for lists and one payload at a
//! time for detail.

pub mod commands;
pub mod error;
pub mod store;
pub mod tools;

use std::collections::HashMap;
use std::path::Path;
use std::time::Instant;

use qasa_vault::payload::{Envelope, ItemKind, VaultPayload};
use qasa_vault::{decode_kdf_version, derive_root_key, encode_kdf_version, generate_salt, VaultKeys, KDF_VERSION};
use serde::Serialize;

use error::{CommandError, CommandResult};
use store::{now_ms, CollectionRow, ItemRow, Store, META_KDF_VERSION, META_SALT, META_VERIFIER};

/// Minimum master password length, counted in characters (iOS `password.count >= 8`).
pub const MIN_PASSWORD_CHARS: usize = 8;

#[derive(Serialize, Clone, Debug, PartialEq, Eq)]
#[serde(rename_all = "camelCase")]
pub struct VaultStatus {
    pub exists: bool,
    pub unlocked: bool,
    /// Set when the vault needs a newer Qasa, so the UI can say so instead of
    /// "wrong password".
    pub unsupported_kdf_version: Option<u32>,
}

/// One list row: the decrypted envelope plus record metadata. iOS `VaultListRow`, with
/// the payload variant added so rows can show their subtype icon without a second call.
#[derive(Serialize, Clone, Debug, PartialEq, Eq)]
#[serde(rename_all = "camelCase")]
pub struct ItemSummary {
    pub id: String,
    pub kind: ItemKind,
    pub variant: String,
    pub display_name: String,
    pub subtitle: String,
    pub created_at: i64,
    pub updated_at: i64,
    pub last_used_at: Option<i64>,
    pub collection_id: Option<String>,
    /// Whether the item has something to copy from the row (iOS `primaryCopy != nil`).
    pub has_primary_copy: bool,
    /// Logins with a 2FA code.
    pub has_totp: bool,
}

#[derive(Serialize, Clone, Debug, PartialEq, Eq)]
#[serde(rename_all = "camelCase")]
pub struct Collection {
    pub id: String,
    pub name: String,
    pub created_at: i64,
    pub item_count: usize,
}

pub struct Vault {
    store: Store,
    keys: Option<VaultKeys>,
    last_activity: Instant,
}

fn new_id() -> String {
    // Swift's `UUID().uuidString` is uppercase; match it so ids look the same everywhere.
    uuid::Uuid::new_v4().to_string().to_uppercase()
}

fn has_primary_copy(p: &VaultPayload) -> bool {
    use VaultPayload::*;
    match p {
        Login(f) => !f.password.is_empty(),
        SecureNote(_) => true,
        Card(f) => !f.number.is_empty(),
        BankAccount(f) => !f.account_number.is_empty(),
        Name(f) => !(f.first_name.is_empty() && f.last_name.is_empty()),
        PersonalEmail(f) => !f.email.is_empty(),
        Phone(f) => !f.number.is_empty(),
        Address(f) => !f.address.is_empty(),
        Company(f) => !f.company_name.is_empty(),
        Website(f) => !f.website_url.is_empty(),
        IdCard(f) | Passport(f) | DriverLicense(f) => !f.number.is_empty(),
    }
}

impl Vault {
    pub fn open(path: &Path) -> CommandResult<Self> {
        Ok(Self::with_store(Store::open(path)?))
    }

    fn with_store(store: Store) -> Self {
        Self { store, keys: None, last_activity: Instant::now() }
    }

    // MARK: session

    pub fn status(&self) -> CommandResult<VaultStatus> {
        let exists = self.store.vault_exists()?;
        let unsupported = match decode_kdf_version(self.store.meta(META_KDF_VERSION)?.as_deref()) {
            Err(qasa_vault::VaultError::UnsupportedKdfVersion(v)) if exists => Some(v),
            _ => None,
        };
        Ok(VaultStatus { exists, unlocked: self.keys.is_some(), unsupported_kdf_version: unsupported })
    }

    pub fn is_unlocked(&self) -> bool {
        self.keys.is_some()
    }

    pub fn note_activity(&mut self) {
        self.last_activity = Instant::now();
    }

    pub fn idle_seconds(&self) -> u64 {
        self.last_activity.elapsed().as_secs()
    }

    fn keys(&self) -> CommandResult<&VaultKeys> {
        self.keys.as_ref().ok_or(CommandError::Locked)
    }

    /// iOS `VaultSession.createVault`: the password is used exactly as typed.
    pub fn create(&mut self, password: &str) -> CommandResult<()> {
        if self.store.vault_exists()? {
            return Err(CommandError::VaultExists);
        }
        if password.chars().count() < MIN_PASSWORD_CHARS {
            return Err(CommandError::WeakPassword);
        }
        let salt = generate_salt();
        let root = derive_root_key(password, &salt)?;
        self.store.set_meta(META_SALT, &salt)?;
        self.store.set_meta(META_KDF_VERSION, &encode_kdf_version(KDF_VERSION))?;
        self.store.set_meta(META_VERIFIER, &root.verifier())?;
        self.keys = Some(root.subkeys());
        self.note_activity();
        Ok(())
    }

    /// iOS `LoginView.unlock`: try the password as typed, then trimmed (passwords set by
    /// Change Master Password are trimmed).
    pub fn unlock(&mut self, password: &str) -> CommandResult<()> {
        let salt = self.store.meta(META_SALT)?.ok_or(CommandError::NoVault)?;
        let verifier = self.store.meta(META_VERIFIER)?.ok_or(CommandError::NoVault)?;
        decode_kdf_version(self.store.meta(META_KDF_VERSION)?.as_deref())?;

        let trimmed = password.trim();
        for candidate in [password, trimmed] {
            if candidate.is_empty() {
                continue;
            }
            let root = derive_root_key(candidate, &salt)?;
            if root.matches_verifier(&verifier) {
                self.keys = Some(root.subkeys());
                self.note_activity();
                return Ok(());
            }
            if candidate == trimmed {
                break;
            }
        }
        Err(CommandError::WrongPassword)
    }

    /// Re-checks the master password without changing the session: the desktop's gate for
    /// protected notes until Touch ID / Windows Hello arrive (iOS asks for the device owner).
    pub fn verify_password(&self, password: &str) -> CommandResult<bool> {
        self.keys()?;
        let salt = self.store.meta(META_SALT)?.ok_or(CommandError::NoVault)?;
        let verifier = self.store.meta(META_VERIFIER)?.ok_or(CommandError::NoVault)?;
        for candidate in [password, password.trim()] {
            if !candidate.is_empty() && derive_root_key(candidate, &salt)?.matches_verifier(&verifier) {
                return Ok(true);
            }
        }
        Ok(false)
    }

    pub fn lock(&mut self) {
        // Dropping VaultKeys zeroes them.
        self.keys = None;
    }

    // MARK: items

    fn decrypt_payload(&self, row: &ItemRow) -> CommandResult<VaultPayload> {
        let keys = self.keys()?;
        let bytes = if row.schema_version >= 1 && !row.payload.is_empty() {
            qasa_vault::decrypt(&row.payload, keys.enc())?
        } else if let Some(legacy) = &row.legacy_payload {
            legacy.clone()
        } else {
            return Err(CommandError::Corrupt);
        };
        VaultPayload::from_stored_json(&bytes).map_err(|_| CommandError::Corrupt)
    }

    fn decrypt_envelope(&self, row: &ItemRow, payload: &VaultPayload) -> CommandResult<Envelope> {
        if row.schema_version >= 1 && !row.envelope.is_empty() {
            let bytes = qasa_vault::decrypt(&row.envelope, self.keys()?.enc())?;
            serde_json::from_slice(&bytes).map_err(|_| CommandError::Corrupt)
        } else {
            Ok(payload.envelope())
        }
    }

    fn summary(&self, row: &ItemRow) -> CommandResult<ItemSummary> {
        let payload = self.decrypt_payload(row)?;
        let envelope = self.decrypt_envelope(row, &payload)?;
        Ok(ItemSummary {
            id: row.id.clone(),
            kind: ItemKind::parse(&row.kind),
            variant: payload.variant().to_string(),
            display_name: envelope.display_name,
            subtitle: envelope.subtitle,
            created_at: row.created_at,
            updated_at: row.updated_at,
            last_used_at: row.last_used_at,
            collection_id: envelope.collection_id.or_else(|| row.collection_id.clone()),
            has_primary_copy: has_primary_copy(&payload),
            has_totp: matches!(&payload, VaultPayload::Login(f) if f.totp.as_deref().is_some_and(|t| !t.is_empty())),
        })
    }

    /// Every readable item. Rows that fail to decrypt are skipped and counted, as iOS
    /// `warmCache` skips them, so one bad row never hides the rest.
    pub fn list(&self) -> CommandResult<(Vec<ItemSummary>, usize)> {
        self.keys()?;
        let mut out = Vec::new();
        let mut failures = 0;
        for row in self.store.items()? {
            match self.summary(&row) {
                Ok(s) => out.push(s),
                Err(_) => failures += 1,
            }
        }
        Ok((out, failures))
    }

    pub fn get(&self, id: &str) -> CommandResult<VaultPayload> {
        self.keys()?;
        let row = self.store.item(id)?.ok_or(CommandError::NotFound)?;
        self.decrypt_payload(&row)
    }

    fn encrypt_row(&self, id: String, payload: &VaultPayload, existing: Option<&ItemRow>) -> CommandResult<(ItemRow, Vec<String>)> {
        let keys = self.keys()?;
        let envelope = payload.envelope();
        let envelope_json = serde_json::to_vec(&envelope).map_err(|e| CommandError::Invalid(e.to_string()))?;
        let now = now_ms();
        let hashes = payload.search_token_hashes(&envelope, keys.search());
        let row = ItemRow {
            id,
            kind: payload.kind().as_str().to_string(),
            envelope: qasa_vault::encrypt(&envelope_json, keys.enc()),
            payload: qasa_vault::encrypt(&payload.to_stored_json(), keys.enc()),
            schema_version: 1,
            legacy_payload: None,
            created_at: existing.map_or(now, |e| e.created_at),
            updated_at: now,
            last_used_at: existing.and_then(|e| e.last_used_at),
            collection_id: envelope.collection_id.clone(),
        };
        Ok((row, hashes))
    }

    /// iOS `VaultRepository.save`: new when `id` is None or unknown, else an update.
    pub fn save(&mut self, id: Option<&str>, payload: &VaultPayload) -> CommandResult<ItemSummary> {
        self.keys()?;
        let existing = match id {
            Some(id) => self.store.item(id)?,
            None => None,
        };
        let item_id = existing.as_ref().map(|e| e.id.clone()).unwrap_or_else(new_id);
        let (row, hashes) = self.encrypt_row(item_id, payload, existing.as_ref())?;
        self.store.put_item(&row, &hashes)?;
        self.summary(&row)
    }

    pub fn delete(&mut self, id: &str) -> CommandResult<()> {
        self.keys()?;
        if self.store.delete_item(id)? { Ok(()) } else { Err(CommandError::NotFound) }
    }

    pub fn mark_used(&mut self, id: &str) -> CommandResult<()> {
        self.keys()?;
        self.store.mark_used(id)
    }

    /// Every decryptable payload, and how many rows could not be read. iOS `allPayloads`.
    pub fn all_payloads(&self) -> CommandResult<(Vec<(String, VaultPayload)>, usize)> {
        self.keys()?;
        let mut out = Vec::new();
        let mut failures = 0;
        for row in self.store.items()? {
            match self.decrypt_payload(&row) {
                Ok(p) => out.push((row.id.clone(), p)),
                Err(_) => failures += 1,
            }
        }
        Ok((out, failures))
    }

    /// Saves many new items in one transaction (CSV import).
    pub fn insert_many(&mut self, payloads: &[VaultPayload]) -> CommandResult<usize> {
        self.keys()?;
        let rows = payloads
            .iter()
            .map(|p| self.encrypt_row(new_id(), p, None))
            .collect::<CommandResult<Vec<_>>>()?;
        self.store.put_items(&rows)?;
        Ok(rows.len())
    }

    // MARK: collections

    pub fn collections(&self) -> CommandResult<Vec<Collection>> {
        self.keys()?;
        let (items, _) = self.list()?;
        let mut counts: HashMap<String, usize> = HashMap::new();
        for item in &items {
            if let Some(c) = &item.collection_id {
                *counts.entry(c.clone()).or_default() += 1;
            }
        }
        Ok(self
            .store
            .collections()?
            .into_iter()
            .map(|CollectionRow { id, name, created_at }| {
                let item_count = counts.get(&id).copied().unwrap_or(0);
                Collection { id, name, created_at, item_count }
            })
            .collect())
    }

    pub fn create_collection(&mut self, name: &str) -> CommandResult<Collection> {
        self.keys()?;
        let name = name.trim();
        if name.is_empty() {
            return Err(CommandError::Invalid("empty collection name".into()));
        }
        let id = new_id();
        let created_at = now_ms();
        self.store.put_collection(&id, name, created_at)?;
        Ok(Collection { id, name: name.to_string(), created_at, item_count: 0 })
    }

    pub fn rename_collection(&mut self, id: &str, name: &str) -> CommandResult<()> {
        self.keys()?;
        let name = name.trim();
        if name.is_empty() {
            return Err(CommandError::Invalid("empty collection name".into()));
        }
        let existing = self.store.collections()?.into_iter().find(|c| c.id == id).ok_or(CommandError::NotFound)?;
        self.store.put_collection(id, name, existing.created_at)
    }

    /// Deletes the collection and moves its items out of it, re-encrypting each.
    pub fn delete_collection(&mut self, id: &str) -> CommandResult<()> {
        self.keys()?;
        let (payloads, _) = self.all_payloads()?;
        for (item_id, mut payload) in payloads {
            if payload.collection_id().as_deref() == Some(id) {
                payload.set_collection_id(None);
                self.save(Some(&item_id), &payload)?;
            }
        }
        self.store.delete_collection(id)
    }

    // MARK: master password

    /// iOS `derivePasswordChange` + `rekeyAllItems` + `commitPasswordChange`, as one
    /// transaction: either every item is re-encrypted under the new key or nothing changes.
    pub fn change_password(&mut self, current: &str, new_password: &str) -> CommandResult<()> {
        self.keys()?;
        let salt = self.store.meta(META_SALT)?.ok_or(CommandError::NoVault)?;
        let verifier = self.store.meta(META_VERIFIER)?.ok_or(CommandError::NoVault)?;
        decode_kdf_version(self.store.meta(META_KDF_VERSION)?.as_deref())?;
        if !derive_root_key(current, &salt)?.matches_verifier(&verifier) {
            return Err(CommandError::WrongPassword);
        }
        let trimmed = new_password.trim();
        if trimmed.chars().count() < MIN_PASSWORD_CHARS {
            return Err(CommandError::WeakPassword);
        }

        // Read everything under the old keys first; refuse if anything is unreadable, so a
        // re-key never silently drops an item.
        let rows = self.store.items()?;
        let mut decoded = Vec::with_capacity(rows.len());
        for row in &rows {
            let payload = self.decrypt_payload(row)?;
            decoded.push((row, payload));
        }

        let new_salt = generate_salt();
        let new_root = derive_root_key(trimmed, &new_salt)?;
        let new_keys = new_root.subkeys();
        let old_keys = self.keys.replace(new_keys);
        let rekeyed: CommandResult<Vec<_>> =
            decoded.iter().map(|(row, payload)| self.encrypt_row(row.id.clone(), payload, Some(row))).collect();
        let rekeyed = match rekeyed {
            Ok(r) => r,
            Err(e) => {
                self.keys = old_keys;
                return Err(e);
            }
        };
        if let Err(e) = self.store.put_items(&rekeyed) {
            self.keys = old_keys;
            return Err(e);
        }
        self.store.set_meta(META_SALT, &new_salt)?;
        self.store.set_meta(META_KDF_VERSION, &encode_kdf_version(KDF_VERSION))?;
        self.store.set_meta(META_VERIFIER, &new_root.verifier())?;
        Ok(())
    }

    /// "Clear local data": locks and deletes every item, collection and the key material.
    pub fn wipe(&mut self) -> CommandResult<()> {
        self.lock();
        self.store.wipe()
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use qasa_vault::payload::{LoginFields, SecureNoteFields};

    fn vault() -> Vault {
        Vault::with_store(Store::in_memory())
    }

    fn login(name: &str) -> VaultPayload {
        VaultPayload::Login(LoginFields { item_name: name.into(), password: "pw".into(), ..Default::default() })
    }

    #[test]
    fn create_lock_unlock() {
        let mut v = vault();
        assert_eq!(v.status().unwrap(), VaultStatus { exists: false, unlocked: false, unsupported_kdf_version: None });
        assert!(matches!(v.create("short"), Err(CommandError::WeakPassword)));
        v.create(" pass word ").unwrap();
        assert!(v.status().unwrap().unlocked);
        v.lock();
        assert!(matches!(v.list(), Err(CommandError::Locked)));
        assert!(matches!(v.unlock("wrong one"), Err(CommandError::WrongPassword)));
        // The trimmed form is tried too, but the raw form must work.
        v.unlock(" pass word ").unwrap();
        assert!(matches!(v.create("another one"), Err(CommandError::VaultExists)));
    }

    #[test]
    fn save_list_get_delete() {
        let mut v = vault();
        v.create("password1").unwrap();
        let saved = v.save(None, &login("GitHub")).unwrap();
        assert_eq!((saved.display_name.as_str(), saved.variant.as_str()), ("GitHub", "login"));
        assert!(saved.has_primary_copy);
        let updated = v.save(Some(&saved.id), &login("GitHub 2")).unwrap();
        assert_eq!((updated.id.clone(), updated.created_at), (saved.id.clone(), saved.created_at));
        assert_eq!(v.list().unwrap().0.len(), 1);
        assert_eq!(v.get(&saved.id).unwrap(), login("GitHub 2"));
        v.delete(&saved.id).unwrap();
        assert!(v.list().unwrap().0.is_empty());
        assert!(matches!(v.get(&saved.id), Err(CommandError::NotFound)));
    }

    #[test]
    fn change_password_rekeys_everything() {
        let mut v = vault();
        v.create("password1").unwrap();
        let a = v.save(None, &login("A")).unwrap();
        assert!(matches!(v.change_password("nope", "password2"), Err(CommandError::WrongPassword)));
        assert!(matches!(v.change_password("password1", "  short "), Err(CommandError::WeakPassword)));
        v.change_password("password1", "  password2  ").unwrap();
        v.lock();
        assert!(matches!(v.unlock("password1"), Err(CommandError::WrongPassword)));
        v.unlock("password2").unwrap();
        assert_eq!(v.get(&a.id).unwrap(), login("A"));
    }

    #[test]
    fn collections_count_and_release_items() {
        let mut v = vault();
        v.create("password1").unwrap();
        let c = v.create_collection(" Work ").unwrap();
        assert_eq!(c.name, "Work");
        let note = VaultPayload::SecureNote(SecureNoteFields { title: "n".into(), collection_id: Some(c.id.clone()), ..Default::default() });
        let saved = v.save(None, &note).unwrap();
        assert_eq!(v.collections().unwrap()[0].item_count, 1);
        v.rename_collection(&c.id, "Office").unwrap();
        assert_eq!(v.collections().unwrap()[0].name, "Office");
        v.delete_collection(&c.id).unwrap();
        assert!(v.collections().unwrap().is_empty());
        assert_eq!(v.get(&saved.id).unwrap().collection_id(), None);
    }

    /// The UI sends payloads shaped like `emptyPayload` in `src/vault/present.ts`, with
    /// unset optionals left out. Every variant must deserialize and save.
    #[test]
    fn ui_shaped_payloads_save() {
        let mut v = vault();
        v.create("password1").unwrap();
        let shapes = [
            r#"{"variant":"login","fields":{"itemName":"GitHub","email":"","username":"","password":"p","website":"","note":""}}"#,
            r#"{"variant":"secureNote","fields":{"title":"n","content":"","color":"mint","protectItem":true}}"#,
            r#"{"variant":"card","fields":{"itemName":"c","accountHolder":"","number":"4111","securityCode":"","expiryDate":"","note":"","color":"blue"}}"#,
            r#"{"variant":"bankAccount","fields":{"itemName":"b","accountHolder":"","accountNumber":"","routingNumber":"","countryCode":"IQ","bankName":""}}"#,
            r#"{"variant":"name","fields":{"type":"Mr","firstName":"Ali","lastName":"","middleName":"","defaultUsername":"","birthDate":"","birthPlace":""}}"#,
            r#"{"variant":"personalEmail","fields":{"itemName":"","type":"Work","email":"a@b.c"}}"#,
            r#"{"variant":"phone","fields":{"itemName":"","type":"Work Cell","countryCode":"IQ","number":"750"}}"#,
            r#"{"variant":"address","fields":{"itemName":"Home","address":"","city":"","zipCode":"","countryCode":"IQ","recipient":"","building":"","floor":"","apartment":"","doorCode":""}}"#,
            r#"{"variant":"company","fields":{"companyName":"Qasa","jobTitle":""}}"#,
            r#"{"variant":"website","fields":{"itemName":"w","websiteURL":"https://qasa-app.com"}}"#,
            r#"{"variant":"idCard","fields":{"name":"x","countryCode":"IQ","number":"","issueDate":"","expiryDate":"","issuePlace":""}}"#,
            r#"{"variant":"passport","fields":{"name":"x","countryCode":"IQ","number":"","issueDate":"","expiryDate":"","issuePlace":"Erbil"}}"#,
            r#"{"variant":"driverLicense","fields":{"name":"x","countryCode":"IQ","number":"","issueDate":"","expiryDate":"","issuePlace":""}}"#,
        ];
        for shape in shapes {
            let payload: VaultPayload = serde_json::from_str(shape).unwrap_or_else(|e| panic!("{shape}: {e}"));
            let saved = v.save(None, &payload).unwrap();
            assert_eq!(v.get(&saved.id).unwrap(), payload);
        }
        assert_eq!(v.list().unwrap().0.len(), 13);
    }

    #[test]
    fn verify_password_does_not_unlock() {
        let mut v = vault();
        v.create("password1").unwrap();
        assert!(v.verify_password("password1").unwrap());
        assert!(!v.verify_password("nope").unwrap());
        v.lock();
        assert!(matches!(v.verify_password("password1"), Err(CommandError::Locked)));
    }

    #[test]
    fn wipe_removes_the_vault() {
        let mut v = vault();
        v.create("password1").unwrap();
        v.save(None, &login("A")).unwrap();
        v.wipe().unwrap();
        assert_eq!(v.status().unwrap(), VaultStatus { exists: false, unlocked: false, unsupported_kdf_version: None });
    }
}
