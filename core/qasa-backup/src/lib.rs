//! Encrypted vault backup files (`.qasabackup`), shared by the desktop and, through
//! UniFFI, the phones.
//!
//! A backup is everything a new device needs to rebuild the vault and keep the account:
//! the vault's key material (salt, KDF version, verifier), the account id, and every item
//! and folder exactly as stored (still vault-key ciphertext). It is the same content the
//! Wi‑Fi sync sends to a new device, written to a file instead of a socket.
//!
//! The whole content is sealed a second time with a backup key derived from the master
//! password, so the file reveals nothing but its header (when it was made, on which device)
//! and can be kept anywhere: iCloud Drive, Google Drive, a USB stick. Opening it needs the
//! master password the vault had when the backup was made.
//!
//! ```text
//! "QASA-BACKUP\n"        magic, 12 bytes
//! u32 big-endian         header length
//! header                 JSON, in the clear (see [`BackupInfo`])
//! sealed body            AES-256-GCM: nonce (12) ‖ ciphertext ‖ tag (16)
//!                        key  = HKDF-SHA256(ikm: vault data key, info: "qasa-backup-v1")
//!                        AAD  = everything before the body
//!                        body = JSON: account id, vault meta, items, folders,
//!                               restore key
//! ```
//!
//! **Restore key.** A random 32-byte secret the device puts in each backup, after sending
//! the server its SHA-256 ([`restore_key_hash`]). A new device that restores the backup
//! presents the key when it registers; if the account has no free slot, the server lets it
//! replace (unlink) the device that made the backup, and only that one. Getting the key
//! takes the file and the main password, which already open the whole vault.
//!
//! The backup key comes from the data key (`qasa-enc`), not the root key, so an unlocked
//! app can write a backup on a schedule without asking for the password again.
//! Restoring derives the data key from the typed password and the salt in the header.

use aes_gcm::aead::{Aead, KeyInit, Payload};
use aes_gcm::{Aes256Gcm, Nonce};
use hkdf::Hkdf;
use qasa_sync::{merge, normalize, Adoption, MergePlan, Snapshot, SyncError, SyncStore, VaultMeta, WireCollection, WireItem};
use rand::RngCore;
use serde::{Deserialize, Serialize};
use sha2::Sha256;
use zeroize::Zeroizing;

/// The format this build writes. A newer file is refused rather than guessed at.
pub const FORMAT_VERSION: u32 = 1;
pub const MAGIC: &[u8; 12] = b"QASA-BACKUP\n";
/// File extension, without the dot.
pub const EXTENSION: &str = "qasabackup";
/// MIME type the apps register and pick by.
pub const MIME_TYPE: &str = "application/vnd.qasa.backup";

const BACKUP_INFO: &[u8] = b"qasa-backup-v1";
const NONCE_LEN: usize = 12;
const TAG_LEN: usize = 16;
const MAX_HEADER_LEN: usize = 64 * 1024;

const DAY_MS: i64 = 24 * 60 * 60 * 1000;

mod b64 {
    use base64::engine::general_purpose::STANDARD;
    use base64::Engine;
    use serde::{Deserialize, Deserializer, Serializer};

    pub fn serialize<S: Serializer>(bytes: &[u8], s: S) -> Result<S::Ok, S::Error> {
        s.serialize_str(&STANDARD.encode(bytes))
    }

    pub fn deserialize<'de, D: Deserializer<'de>>(d: D) -> Result<Vec<u8>, D::Error> {
        let s = String::deserialize(d)?;
        STANDARD.decode(s).map_err(serde::de::Error::custom)
    }
}

#[derive(Debug, thiserror::Error, PartialEq, Eq)]
pub enum BackupError {
    /// Not a Qasa backup, or cut short.
    #[error("not a Qasa backup")]
    NotABackup,
    /// Written by a newer Qasa (format or KDF version this build doesn't know).
    #[error("made by a newer version of Qasa")]
    NewerVersion,
    /// The password doesn't open it (or the file was changed).
    #[error("wrong password")]
    WrongPassword,
    /// This device has a different vault; restoring would mix two vaults.
    #[error("this device has a different vault")]
    DifferentVault,
    /// Nothing to back up: this device has no vault.
    #[error("no vault on this device")]
    NoVault,
    #[error("storage: {0}")]
    Store(String),
}

impl From<SyncError> for BackupError {
    fn from(e: SyncError) -> Self {
        match e {
            SyncError::NoVault => BackupError::NoVault,
            SyncError::DifferentVault => BackupError::DifferentVault,
            other => BackupError::Store(other.to_string()),
        }
    }
}

/// The header, readable without the password: enough to show "Backup from Ali's iPhone,
/// 10 Nov 2026" and to derive the key once the password is typed.
#[derive(Serialize, Deserialize, Clone, Debug, PartialEq, Eq)]
pub struct BackupInfo {
    pub format: u32,
    /// Unix ms.
    pub created_at: i64,
    pub device_name: String,
    /// `ios`, `android`, `macos`, `windows` or `linux`.
    pub platform: String,
    pub kdf_version: u32,
    #[serde(with = "b64")]
    pub salt: Vec<u8>,
}

#[derive(Serialize, Deserialize)]
struct Body {
    account_id: Option<String>,
    vault: VaultMeta,
    items: Vec<WireItem>,
    collections: Vec<WireCollection>,
    /// Absent in a backup made before the device could reach the server.
    #[serde(default, skip_serializing_if = "Option::is_none", with = "b64_opt")]
    restore_key: Option<Vec<u8>>,
}

mod b64_opt {
    use base64::engine::general_purpose::STANDARD;
    use base64::Engine;
    use serde::{Deserialize, Deserializer, Serializer};

    pub fn serialize<S: Serializer>(bytes: &Option<Vec<u8>>, s: S) -> Result<S::Ok, S::Error> {
        match bytes {
            Some(b) => s.serialize_str(&STANDARD.encode(b)),
            None => s.serialize_none(),
        }
    }

    pub fn deserialize<'de, D: Deserializer<'de>>(d: D) -> Result<Option<Vec<u8>>, D::Error> {
        Option::<String>::deserialize(d)?
            .map(|s| STANDARD.decode(s).map_err(serde::de::Error::custom))
            .transpose()
    }
}

/// Length of a restore key.
pub const RESTORE_KEY_LEN: usize = 32;

/// A fresh random restore key.
pub fn new_restore_key() -> [u8; RESTORE_KEY_LEN] {
    let mut key = [0u8; RESTORE_KEY_LEN];
    rand::rngs::OsRng.fill_bytes(&mut key);
    key
}

/// What the server stores and compares: SHA-256 of the key. The server computes the same
/// hash of the key a restoring device sends.
pub fn restore_key_hash(key: &[u8]) -> [u8; 32] {
    use sha2::Digest;
    Sha256::digest(key).into()
}

/// What a restore did.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct Restored {
    /// This device had no vault and took the backup's vault and account.
    pub adopted: bool,
    /// Items and folders written here.
    pub written: u32,
    /// Live (not deleted) items in the backup.
    pub items_in_backup: u32,
    pub info: BackupInfo,
    /// The backup's restore key, to send when registering this device. `None` for a
    /// backup made without one.
    pub restore_key: Option<Vec<u8>>,
}

fn backup_key(enc_key: &[u8; 32]) -> Zeroizing<[u8; 32]> {
    let mut key = Zeroizing::new([0u8; 32]);
    Hkdf::<Sha256>::new(None, enc_key)
        .expand(BACKUP_INFO, key.as_mut())
        .expect("32 bytes is a valid HKDF length");
    key
}

/// Writes a backup of `store`'s vault. `enc_key` is the unlocked vault's data key; the
/// caller must hold the vault unlocked. The result replaces the device's previous backup.
/// `restore_key` is this device's key whose hash the server has confirmed, or `None`.
pub fn create<S: SyncStore + ?Sized>(store: &S, enc_key: &[u8; 32], restore_key: Option<&[u8; RESTORE_KEY_LEN]>) -> Result<Vec<u8>, BackupError> {
    let intro = store.intro()?;
    let vault = intro.vault.ok_or(BackupError::NoVault)?;
    let snapshot = normalize(store.snapshot()?);
    let info = BackupInfo {
        format: FORMAT_VERSION,
        created_at: store.now_ms(),
        device_name: intro.device_name,
        platform: intro.platform,
        kdf_version: vault.kdf_version.max(1),
        salt: vault.salt.clone(),
    };
    let body = Zeroizing::new(
        serde_json::to_vec(&Body {
            account_id: intro.account_id,
            vault,
            items: snapshot.items,
            collections: snapshot.collections,
            restore_key: restore_key.map(|k| k.to_vec()),
        })
            .map_err(|e| BackupError::Store(e.to_string()))?,
    );
    let header = serde_json::to_vec(&info).map_err(|e| BackupError::Store(e.to_string()))?;

    let mut out = Vec::with_capacity(MAGIC.len() + 4 + header.len() + NONCE_LEN + body.len() + TAG_LEN);
    out.extend_from_slice(MAGIC);
    out.extend_from_slice(&(header.len() as u32).to_be_bytes());
    out.extend_from_slice(&header);

    let mut nonce = [0u8; NONCE_LEN];
    rand::rngs::OsRng.fill_bytes(&mut nonce);
    let key = backup_key(enc_key);
    let sealed = Aes256Gcm::new(key.as_ref().into())
        .encrypt(Nonce::from_slice(&nonce), Payload { msg: &body, aad: &out })
        .expect("AES-GCM encryption of an in-memory buffer cannot fail");
    out.extend_from_slice(&nonce);
    out.extend_from_slice(&sealed);
    Ok(out)
}

/// Splits a file into its header, the bytes the body is bound to, and the sealed body.
fn split(file: &[u8]) -> Result<(BackupInfo, &[u8], &[u8]), BackupError> {
    if file.len() < MAGIC.len() + 4 || &file[..MAGIC.len()] != MAGIC {
        return Err(BackupError::NotABackup);
    }
    let len_at = MAGIC.len();
    let header_len = u32::from_be_bytes([file[len_at], file[len_at + 1], file[len_at + 2], file[len_at + 3]]) as usize;
    let header_end = len_at + 4 + header_len;
    if header_len > MAX_HEADER_LEN || file.len() < header_end + NONCE_LEN + TAG_LEN {
        return Err(BackupError::NotABackup);
    }
    // Check the version before the rest of the header: a newer format may change it.
    let value: serde_json::Value = serde_json::from_slice(&file[len_at + 4..header_end]).map_err(|_| BackupError::NotABackup)?;
    match value.get("format").and_then(|f| f.as_u64()) {
        Some(f) if f > FORMAT_VERSION as u64 => return Err(BackupError::NewerVersion),
        Some(_) => {}
        None => return Err(BackupError::NotABackup),
    }
    let info: BackupInfo = serde_json::from_value(value).map_err(|_| BackupError::NotABackup)?;
    Ok((info, &file[..header_end], &file[header_end..]))
}

/// The header, without the password.
pub fn info(file: &[u8]) -> Result<BackupInfo, BackupError> {
    split(file).map(|(info, _, _)| info)
}

struct Opened {
    info: BackupInfo,
    account_id: Option<String>,
    vault: VaultMeta,
    snapshot: Snapshot,
    restore_key: Option<Vec<u8>>,
}

/// Decrypts a backup with the master password. Slow on purpose: PBKDF2 at 310,000
/// iterations, as at unlock.
fn open(file: &[u8], password: &str) -> Result<Opened, BackupError> {
    let (info, aad, sealed) = split(file)?;
    let kdf_bytes = qasa_vault::encode_kdf_version(info.kdf_version);
    qasa_vault::decode_kdf_version(Some(&kdf_bytes)).map_err(|_| BackupError::NewerVersion)?;
    let root = qasa_vault::derive_root_key(password, &info.salt).map_err(|_| BackupError::NotABackup)?;
    let key = backup_key(root.subkeys().enc());

    let (nonce, ct) = sealed.split_at(NONCE_LEN);
    let plain = Zeroizing::new(
        Aes256Gcm::new(key.as_ref().into())
            .decrypt(Nonce::from_slice(nonce), Payload { msg: ct, aad })
            .map_err(|_| BackupError::WrongPassword)?,
    );
    let body: Body = serde_json::from_slice(&plain).map_err(|_| BackupError::NotABackup)?;
    // The body must describe the vault this password opens.
    if body.vault.salt != info.salt || !root.matches_verifier(&body.vault.verifier) {
        return Err(BackupError::NotABackup);
    }
    Ok(Opened {
        info,
        account_id: body.account_id,
        vault: body.vault,
        snapshot: normalize(Snapshot { items: body.items, collections: body.collections }),
        restore_key: body.restore_key.filter(|k| k.len() == RESTORE_KEY_LEN),
    })
}

/// Restores a backup into `store`:
/// - a device without a vault takes the backup's vault and account, then every row;
/// - a device with the same vault merges it in (last writer wins, as in Wi‑Fi sync), so
///   nothing newer on the device is overwritten;
/// - a device with a different vault refuses, and nothing changes.
pub fn restore<S: SyncStore + ?Sized>(file: &[u8], password: &str, store: &mut S) -> Result<Restored, BackupError> {
    let opened = open(file, password)?;
    let items_in_backup = opened.snapshot.items.iter().filter(|i| i.deleted_at.is_none()).count() as u32;
    let local_vault = store.intro()?.vault;

    let (adopted, local) = match local_vault {
        None => {
            store.adopt(&Adoption { account_id: opened.account_id.clone(), vault: opened.vault.clone() })?;
            (true, Snapshot::default())
        }
        Some(v) if v == opened.vault => (false, normalize(store.snapshot()?)),
        Some(_) => return Err(BackupError::DifferentVault),
    };
    let plan: MergePlan = merge(&local, &opened.snapshot, store.now_ms());
    let written = store.apply(&plan)?;
    Ok(Restored { adopted, written, items_in_backup, info: opened.info, restore_key: opened.restore_key })
}

/// How often automatic backups run.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum Frequency {
    Off,
    Daily,
    Weekly,
    Monthly,
}

impl Frequency {
    /// `off`, `daily`, `weekly` or `monthly`; anything else is off.
    pub fn parse(s: &str) -> Self {
        match s {
            "daily" => Self::Daily,
            "weekly" => Self::Weekly,
            "monthly" => Self::Monthly,
            _ => Self::Off,
        }
    }

    fn interval_ms(self) -> Option<i64> {
        match self {
            Self::Off => None,
            Self::Daily => Some(DAY_MS),
            Self::Weekly => Some(7 * DAY_MS),
            Self::Monthly => Some(30 * DAY_MS),
        }
    }
}

/// Whether an automatic backup should run now. Never when off; at once when there has
/// never been one. A clock set backwards (last backup "in the future") also counts as due.
pub fn is_due(frequency: Frequency, last_backup_ms: Option<i64>, now_ms: i64) -> bool {
    let Some(interval) = frequency.interval_ms() else { return false };
    match last_backup_ms {
        None => true,
        Some(last) => now_ms < last || now_ms - last >= interval,
    }
}

/// The reminder shows when the last backup (of any kind) is older than this, or there is
/// none, and the vault has items.
pub const REMINDER_AFTER_MS: i64 = 30 * DAY_MS;

/// Whether to remind the user to back up. `snoozed_until_ms` is set when they dismiss it.
pub fn should_remind(last_backup_ms: Option<i64>, item_count: u32, snoozed_until_ms: Option<i64>, now_ms: i64) -> bool {
    if item_count == 0 || snoozed_until_ms.is_some_and(|s| now_ms < s) {
        return false;
    }
    last_backup_ms.map_or(true, |last| now_ms - last >= REMINDER_AFTER_MS)
}

/// The backup's file name. Fixed per device, so a new backup replaces the old one even when
/// several devices write to the same folder: `Qasa backup - Ali's iPhone.qasabackup`.
pub fn file_name(device_name: &str) -> String {
    let cleaned: String = device_name
        .chars()
        .map(|c| if c.is_alphanumeric() || " -_'.()".contains(c) { c } else { ' ' })
        .collect();
    let mut name = cleaned.split_whitespace().collect::<Vec<_>>().join(" ");
    name = name.trim_matches('.').trim().chars().take(40).collect::<String>().trim().to_string();
    if name.is_empty() {
        name = "device".into();
    }
    format!("Qasa backup - {name}.{EXTENSION}")
}

#[cfg(test)]
mod tests;
