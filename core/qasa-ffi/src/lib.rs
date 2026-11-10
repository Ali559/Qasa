//! What the phones call (UniFFI → Swift and Kotlin). The phones keep their own Bonjour /
//! NSD discovery; this crate owns the socket once an address is known, and the whole
//! session from there (plan §6.5).
//!
//! All calls block: run them off the main thread.

use std::net::{SocketAddr, TcpListener, TcpStream, ToSocketAddrs};
use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::{Arc, Mutex};
use std::time::{Duration, Instant};

use qasa_sync::{Adoption, Intro, MergePlan, Role, Snapshot, SyncError, SyncStore, VaultMeta, WireCollection, WireItem};

uniffi::setup_scaffolding!();

const CODE_LIFETIME: Duration = Duration::from_secs(120);
const MAX_ATTEMPTS: u32 = 3;

// MARK: records

#[derive(uniffi::Record, Clone)]
pub struct FfiVaultMeta {
    pub salt: Vec<u8>,
    pub kdf_version: u32,
    pub verifier: Vec<u8>,
}

#[derive(uniffi::Record, Clone)]
pub struct FfiIntro {
    pub device_name: String,
    /// `ios` or `android`.
    pub platform: String,
    pub account_id: Option<String>,
    /// `None` on a device without a vault yet.
    pub vault: Option<FfiVaultMeta>,
}

/// One item as stored: vault-key ciphertext, payload in the canonical (iOS) JSON shape.
#[derive(uniffi::Record, Clone)]
pub struct FfiItem {
    pub id: String,
    pub kind: String,
    pub envelope: Vec<u8>,
    pub payload: Vec<u8>,
    pub schema_version: i64,
    pub created_at_ms: i64,
    pub updated_at_ms: i64,
    pub last_used_at_ms: Option<i64>,
    pub collection_id: Option<String>,
    pub deleted_at_ms: Option<i64>,
}

#[derive(uniffi::Record, Clone)]
pub struct FfiCollection {
    pub id: String,
    /// Vault-key AES-GCM ciphertext of the UTF-8 name (the apps' item format). Empty for a
    /// tombstone. The apps store it as is and decrypt it after unlock.
    pub name_enc: Vec<u8>,
    pub created_at_ms: i64,
    pub updated_at_ms: i64,
    pub deleted_at_ms: Option<i64>,
}

#[derive(uniffi::Record)]
pub struct FfiOutcome {
    pub peer_name: String,
    pub peer_platform: String,
    /// This device took the other's vault and account.
    pub adopted: bool,
    pub received: u32,
    pub sent: u32,
}

/// Names avoid `message` (Kotlin's `Throwable.message`) and `Protocol` (reserved in Swift).
#[derive(Debug, thiserror::Error, uniffi::Error)]
pub enum FfiSyncError {
    #[error("wrong code")]
    WrongCode { attempts_left: u32 },
    #[error("different vaults")]
    DifferentVault,
    #[error("no vault on either device")]
    NoVault,
    #[error("incompatible version")]
    Version,
    #[error("the code expired")]
    Expired,
    #[error("stopped")]
    Stopped,
    #[error("connection: {detail}")]
    Io { detail: String },
    #[error("storage: {detail}")]
    Store { detail: String },
    /// Not `Protocol`: Swift can't have a type member with that name.
    #[error("unexpected data")]
    Unexpected,
}

impl FfiSyncError {
    fn from_sync(e: SyncError, attempts_left: u32) -> Self {
        match e {
            SyncError::WrongCode => FfiSyncError::WrongCode { attempts_left },
            SyncError::DifferentVault => FfiSyncError::DifferentVault,
            SyncError::NoVault => FfiSyncError::NoVault,
            SyncError::Version => FfiSyncError::Version,
            SyncError::Io(detail) => FfiSyncError::Io { detail },
            SyncError::Store(detail) => FfiSyncError::Store { detail },
            SyncError::Refused(_) | SyncError::Protocol => FfiSyncError::Unexpected,
        }
    }
}

// MARK: the platform's vault

/// Implemented in Swift / Kotlin over SwiftData / Room.
#[uniffi::export(with_foreign)]
pub trait SyncStoreCallback: Send + Sync {
    fn intro(&self) -> FfiIntro;
    /// Every item and collection, tombstones included, payloads canonical.
    fn snapshot(&self) -> Vec<FfiItem>;
    fn collections(&self) -> Vec<FfiCollection>;
    /// A new device: keep this vault's key material and account. False on failure.
    fn adopt(&self, account_id: Option<String>, vault: FfiVaultMeta) -> bool;
    /// Write these rows (they won the merge). Returns how many were written, or -1.
    fn apply(&self, items: Vec<FfiItem>, collections: Vec<FfiCollection>) -> i64;
    fn now_ms(&self) -> i64;
}

struct Bridge(Arc<dyn SyncStoreCallback>);

fn meta_in(m: FfiVaultMeta) -> VaultMeta {
    VaultMeta { salt: m.salt, kdf_version: m.kdf_version, verifier: m.verifier }
}

fn item_in(i: FfiItem) -> WireItem {
    WireItem {
        id: i.id,
        kind: i.kind,
        envelope: i.envelope,
        payload: i.payload,
        schema_version: i.schema_version,
        created_at: i.created_at_ms,
        updated_at: i.updated_at_ms,
        last_used_at: i.last_used_at_ms,
        collection_id: i.collection_id,
        deleted_at: i.deleted_at_ms,
    }
}

fn item_out(i: &WireItem) -> FfiItem {
    FfiItem {
        id: i.id.clone(),
        kind: i.kind.clone(),
        envelope: i.envelope.clone(),
        payload: i.payload.clone(),
        schema_version: i.schema_version,
        created_at_ms: i.created_at,
        updated_at_ms: i.updated_at,
        last_used_at_ms: i.last_used_at,
        collection_id: i.collection_id.clone(),
        deleted_at_ms: i.deleted_at,
    }
}

fn collection_in(c: FfiCollection) -> WireCollection {
    WireCollection { id: c.id, name_enc: c.name_enc, created_at: c.created_at_ms, updated_at: c.updated_at_ms, deleted_at: c.deleted_at_ms }
}

fn collection_out(c: &WireCollection) -> FfiCollection {
    FfiCollection { id: c.id.clone(), name_enc: c.name_enc.clone(), created_at_ms: c.created_at, updated_at_ms: c.updated_at, deleted_at_ms: c.deleted_at }
}

impl SyncStore for Bridge {
    fn intro(&self) -> Result<Intro, SyncError> {
        let i = self.0.intro();
        Ok(Intro {
            protocol: qasa_sync::PROTOCOL_VERSION,
            device_name: i.device_name,
            platform: i.platform,
            account_id: i.account_id,
            vault: i.vault.map(meta_in),
        })
    }
    fn snapshot(&self) -> Result<Snapshot, SyncError> {
        Ok(Snapshot {
            items: self.0.snapshot().into_iter().map(item_in).collect(),
            collections: self.0.collections().into_iter().map(collection_in).collect(),
        })
    }
    fn adopt(&mut self, a: &Adoption) -> Result<(), SyncError> {
        let v = &a.vault;
        let meta = FfiVaultMeta { salt: v.salt.clone(), kdf_version: v.kdf_version, verifier: v.verifier.clone() };
        if self.0.adopt(a.account_id.clone(), meta) { Ok(()) } else { Err(SyncError::Store("adopt failed".into())) }
    }
    fn apply(&mut self, plan: &MergePlan) -> Result<u32, SyncError> {
        let n = self.0.apply(plan.items.iter().map(item_out).collect(), plan.collections.iter().map(collection_out).collect());
        if n < 0 { Err(SyncError::Store("apply failed".into())) } else { Ok(n as u32) }
    }
    fn now_ms(&self) -> i64 {
        self.0.now_ms()
    }
}

fn outcome(o: qasa_sync::Outcome) -> FfiOutcome {
    FfiOutcome { peer_name: o.peer_name, peer_platform: o.peer_platform, adopted: o.adopted, received: o.received, sent: o.sent }
}

// MARK: host and join

/// The device showing a code. Bind first (to learn the port to advertise over Bonjour /
/// NSD — advertise the device name, never the code), then `serve`.
#[derive(uniffi::Object)]
pub struct SyncHost {
    listener: TcpListener,
    code: String,
    stop: AtomicBool,
    attempts_left: Mutex<u32>,
}

#[uniffi::export]
impl SyncHost {
    #[uniffi::constructor]
    pub fn new() -> Result<Arc<Self>, FfiSyncError> {
        let listener = TcpListener::bind("0.0.0.0:0").map_err(|e| FfiSyncError::Io { detail: e.to_string() })?;
        listener.set_nonblocking(true).map_err(|e| FfiSyncError::Io { detail: e.to_string() })?;
        Ok(Arc::new(Self { listener, code: qasa_sync::generate_code(), stop: AtomicBool::new(false), attempts_left: Mutex::new(MAX_ATTEMPTS) }))
    }

    pub fn port(&self) -> u16 {
        self.listener.local_addr().map(|a| a.port()).unwrap_or(0)
    }

    /// The 6-digit code to show.
    pub fn code(&self) -> String {
        self.code.clone()
    }

    /// Waits for the other device and runs one session. Returns after a success, a refusal,
    /// 3 wrong codes, 2 minutes, or `stop()`. Call again after a wrong code to keep waiting.
    pub fn serve(&self, store: Arc<dyn SyncStoreCallback>) -> Result<FfiOutcome, FfiSyncError> {
        let started = Instant::now();
        loop {
            if self.stop.load(Ordering::SeqCst) {
                return Err(FfiSyncError::Stopped);
            }
            if started.elapsed() > CODE_LIFETIME {
                return Err(FfiSyncError::Expired);
            }
            match self.listener.accept() {
                Ok((stream, _)) => {
                    let _ = stream.set_nonblocking(false);
                    let _ = stream.set_read_timeout(Some(Duration::from_secs(60)));
                    let mut bridge = Bridge(store.clone());
                    let result = qasa_sync::run(stream, Role::Host, &self.code, &mut bridge);
                    let mut left = self.attempts_left.lock().map_err(|_| FfiSyncError::Unexpected)?;
                    *left = left.saturating_sub(1);
                    return match result {
                        Ok(o) => Ok(outcome(o)),
                        Err(e) => Err(FfiSyncError::from_sync(e, *left)),
                    };
                }
                Err(e) if e.kind() == std::io::ErrorKind::WouldBlock => std::thread::sleep(Duration::from_millis(150)),
                Err(e) => return Err(FfiSyncError::Io { detail: e.to_string() }),
            }
        }
    }

    pub fn stop(&self) {
        self.stop.store(true, Ordering::SeqCst);
    }
}

/// The device typing the code: connect to the host Bonjour / NSD found and run a session.
#[uniffi::export]
pub fn sync_join(host: String, port: u16, code: String, store: Arc<dyn SyncStoreCallback>) -> Result<FfiOutcome, FfiSyncError> {
    let addr: SocketAddr = (host.trim_start_matches('[').trim_end_matches(']'), port)
        .to_socket_addrs()
        .map_err(|e| FfiSyncError::Io { detail: e.to_string() })?
        .next()
        .ok_or(FfiSyncError::Io { detail: "no address".into() })?;
    let stream = TcpStream::connect_timeout(&addr, Duration::from_secs(10)).map_err(|e| FfiSyncError::Io { detail: e.to_string() })?;
    let _ = stream.set_read_timeout(Some(Duration::from_secs(60)));
    let mut bridge = Bridge(store);
    qasa_sync::run(stream, Role::Join, &code, &mut bridge).map(outcome).map_err(|e| FfiSyncError::from_sync(e, 0))
}

// MARK: backups

/// Names avoid `message` and `Protocol`, as for [`FfiSyncError`].
#[derive(Debug, thiserror::Error, uniffi::Error)]
pub enum FfiBackupError {
    #[error("not a Qasa backup")]
    NotABackup,
    #[error("made by a newer version of Qasa")]
    NewerVersion,
    #[error("wrong password")]
    WrongPassword,
    #[error("this device has a different vault")]
    DifferentVault,
    #[error("no vault on this device")]
    NoVault,
    #[error("storage: {detail}")]
    Store { detail: String },
}

impl From<qasa_backup::BackupError> for FfiBackupError {
    fn from(e: qasa_backup::BackupError) -> Self {
        use qasa_backup::BackupError as E;
        match e {
            E::NotABackup => Self::NotABackup,
            E::NewerVersion => Self::NewerVersion,
            E::WrongPassword => Self::WrongPassword,
            E::DifferentVault => Self::DifferentVault,
            E::NoVault => Self::NoVault,
            E::Store(detail) => Self::Store { detail },
        }
    }
}

/// A backup's header, readable without the password.
#[derive(uniffi::Record)]
pub struct FfiBackupInfo {
    pub created_at_ms: i64,
    pub device_name: String,
    pub platform: String,
}

#[derive(uniffi::Record)]
pub struct FfiRestoreOutcome {
    /// This device had no vault and took the backup's vault and account. Unlock with the
    /// password just typed.
    pub adopted: bool,
    pub written: u32,
    pub items_in_backup: u32,
    pub info: FfiBackupInfo,
    /// Send it when registering this device (`restore_key` on register), so it can take
    /// the place of the device that made the backup. `None` for a backup without one.
    pub restore_key: Option<Vec<u8>>,
}

fn info_out(i: qasa_backup::BackupInfo) -> FfiBackupInfo {
    FfiBackupInfo { created_at_ms: i.created_at, device_name: i.device_name, platform: i.platform }
}

/// The whole backup file of this device's vault. `enc_key` is the unlocked vault's 32-byte
/// data key; `restore_key` is this device's restore key once the server confirmed its hash
/// (else `None`). Write the bytes over the device's previous backup.
#[uniffi::export]
pub fn backup_create(enc_key: Vec<u8>, restore_key: Option<Vec<u8>>, store: Arc<dyn SyncStoreCallback>) -> Result<Vec<u8>, FfiBackupError> {
    let key: [u8; 32] = enc_key.as_slice().try_into().map_err(|_| FfiBackupError::Store { detail: "bad key".into() })?;
    let restore: Option<[u8; 32]> = match restore_key {
        Some(k) => Some(k.as_slice().try_into().map_err(|_| FfiBackupError::Store { detail: "bad restore key".into() })?),
        None => None,
    };
    Ok(qasa_backup::create(&Bridge(store), &key, restore.as_ref())?)
}

/// A fresh random 32-byte restore key. Keep it in the Keychain / Keystore.
#[uniffi::export]
pub fn backup_new_restore_key() -> Vec<u8> {
    qasa_backup::new_restore_key().to_vec()
}

/// SHA-256 of a restore key: what `PUT /v1/devices/me/restore-key` sends.
#[uniffi::export]
pub fn backup_restore_key_hash(key: Vec<u8>) -> Vec<u8> {
    qasa_backup::restore_key_hash(&key).to_vec()
}

/// The header of a picked file: when, and from which device. Fails on anything that isn't
/// a backup this build can read.
#[uniffi::export]
pub fn backup_info(file: Vec<u8>) -> Result<FfiBackupInfo, FfiBackupError> {
    Ok(info_out(qasa_backup::info(&file)?))
}

/// Restores a backup with its master password (slow: PBKDF2). A device without a vault
/// takes the backup's vault and account; the same vault merges; a different one refuses.
#[uniffi::export]
pub fn backup_restore(file: Vec<u8>, password: String, store: Arc<dyn SyncStoreCallback>) -> Result<FfiRestoreOutcome, FfiBackupError> {
    let r = qasa_backup::restore(&file, &password, &mut Bridge(store))?;
    Ok(FfiRestoreOutcome { adopted: r.adopted, written: r.written, items_in_backup: r.items_in_backup, info: info_out(r.info), restore_key: r.restore_key })
}

/// Whether an automatic backup is due. `frequency`: `off`, `daily`, `weekly`, `monthly`.
#[uniffi::export]
pub fn backup_is_due(frequency: String, last_backup_ms: Option<i64>, now_ms: i64) -> bool {
    qasa_backup::is_due(qasa_backup::Frequency::parse(&frequency), last_backup_ms, now_ms)
}

/// Whether to show the "back up your vault" reminder.
#[uniffi::export]
pub fn backup_should_remind(last_backup_ms: Option<i64>, item_count: u32, snoozed_until_ms: Option<i64>, now_ms: i64) -> bool {
    qasa_backup::should_remind(last_backup_ms, item_count, snoozed_until_ms, now_ms)
}

/// The fixed file name of this device's backup: `Qasa backup - <device>.qasabackup`.
#[uniffi::export]
pub fn backup_file_name(device_name: String) -> String {
    qasa_backup::file_name(&device_name)
}

/// How long a dismissed reminder stays away, in ms.
#[uniffi::export]
pub fn backup_reminder_snooze_ms() -> i64 {
    qasa_backup::REMINDER_AFTER_MS
}

// MARK: payload shapes

#[derive(Debug, thiserror::Error, uniffi::Error)]
pub enum FfiPayloadError {
    #[error("unreadable payload")]
    Unreadable,
}

/// Any payload JSON (iOS or Android shape) → the canonical (iOS) shape that crosses the
/// network. Android calls this on every payload it sends.
#[uniffi::export]
pub fn payload_to_canonical(json: Vec<u8>) -> Result<Vec<u8>, FfiPayloadError> {
    qasa_vault::payload::VaultPayload::from_stored_json(&json).map(|p| p.to_stored_json()).map_err(|_| FfiPayloadError::Unreadable)
}

/// Any payload JSON → Android's shape. Android calls this on every payload it receives.
#[uniffi::export]
pub fn payload_to_android(json: Vec<u8>) -> Result<Vec<u8>, FfiPayloadError> {
    qasa_vault::payload::VaultPayload::from_stored_json(&json).map(|p| p.to_android_json()).map_err(|_| FfiPayloadError::Unreadable)
}

// MARK: field validation

/// A calendar day. The phones pass their local "today".
#[derive(uniffi::Record, Clone, Copy, Debug, PartialEq, Eq)]
pub struct FfiDate {
    pub year: i32,
    pub month: u32,
    pub day: u32,
}

impl From<FfiDate> for qasa_vault::validate::CivilDate {
    fn from(d: FfiDate) -> Self {
        Self { year: d.year, month: d.month, day: d.day }
    }
}

impl From<qasa_vault::validate::CivilDate> for FfiDate {
    fn from(d: qasa_vault::validate::CivilDate) -> Self {
        Self { year: d.year, month: d.month, day: d.day }
    }
}

macro_rules! mirror_unit_enum {
    ($(#[$doc:meta])* $ffi:ident = $core:path { $($v:ident),* $(,)? }) => {
        $(#[$doc])*
        #[derive(uniffi::Enum, Clone, Copy, Debug, PartialEq, Eq, Hash)]
        pub enum $ffi { $($v),* }

        impl From<$core> for $ffi {
            fn from(x: $core) -> Self {
                use $core as C;
                match x { $(C::$v => $ffi::$v),* }
            }
        }
    };
}

mirror_unit_enum! {
    /// The payload field an issue belongs to. `qasa_vault::validate::Field`.
    FfiField = qasa_vault::validate::Field {
        ItemName, Email, Username, Password, Website, Note, Totp, Title, Content, AccountHolder, Number,
        SecurityCode, ExpiryDate, AccountNumber, RoutingNumber, CountryCode, BankName, FirstName, MiddleName,
        LastName, DefaultUsername, BirthDate, BirthPlace, Address, City, ZipCode, Recipient, Building, Floor,
        Apartment, DoorCode, CompanyName, JobTitle, WebsiteUrl, Name, IssueDate, IssuePlace,
    }
}

mirror_unit_enum! {
    /// `qasa_vault::validate::CardBrand`.
    FfiCardBrand = qasa_vault::validate::CardBrand {
        Visa, Mastercard, Amex, Discover, DinersClub, Jcb, UnionPay, Maestro, Mir, Unknown,
    }
}

/// What is wrong with a field. `qasa_vault::validate::Problem`; the apps localize it.
#[derive(uniffi::Enum, Clone, Copy, Debug, PartialEq, Eq, Hash)]
pub enum FfiProblem {
    Required,
    TooLong { max: u32 },
    DigitsOnly,
    LettersDigitsOnly,
    CardNumberLength,
    CardNumberChecksum,
    SecurityCodeLength { min: u32, max: u32 },
    ExpiryFormat,
    ExpiryMonth,
    ExpiryTooFar { max_years: u32 },
    DateInvalid,
    DateInFuture,
    DateTooOld,
    DateBeforeIssue,
    DateTooFar { max_years: u32 },
    EmailInvalid,
    UrlInvalid,
    PhoneLength { min: u32, max: u32 },
    PhoneCountryMismatch,
    IbanInvalid,
    IbanChecksum,
    RoutingInvalid,
    CountryInvalid,
    TotpInvalid,
}

impl From<qasa_vault::validate::Problem> for FfiProblem {
    fn from(p: qasa_vault::validate::Problem) -> Self {
        use qasa_vault::validate::Problem as P;
        match p {
            P::Required => Self::Required,
            P::TooLong { max } => Self::TooLong { max },
            P::DigitsOnly => Self::DigitsOnly,
            P::LettersDigitsOnly => Self::LettersDigitsOnly,
            P::CardNumberLength => Self::CardNumberLength,
            P::CardNumberChecksum => Self::CardNumberChecksum,
            P::SecurityCodeLength { min, max } => Self::SecurityCodeLength { min, max },
            P::ExpiryFormat => Self::ExpiryFormat,
            P::ExpiryMonth => Self::ExpiryMonth,
            P::ExpiryTooFar { max_years } => Self::ExpiryTooFar { max_years },
            P::DateInvalid => Self::DateInvalid,
            P::DateInFuture => Self::DateInFuture,
            P::DateTooOld => Self::DateTooOld,
            P::DateBeforeIssue => Self::DateBeforeIssue,
            P::DateTooFar { max_years } => Self::DateTooFar { max_years },
            P::EmailInvalid => Self::EmailInvalid,
            P::UrlInvalid => Self::UrlInvalid,
            P::PhoneLength { min, max } => Self::PhoneLength { min, max },
            P::PhoneCountryMismatch => Self::PhoneCountryMismatch,
            P::IbanInvalid => Self::IbanInvalid,
            P::IbanChecksum => Self::IbanChecksum,
            P::RoutingInvalid => Self::RoutingInvalid,
            P::CountryInvalid => Self::CountryInvalid,
            P::TotpInvalid => Self::TotpInvalid,
        }
    }
}

#[derive(uniffi::Record, Clone, Debug, PartialEq, Eq)]
pub struct FfiFieldIssue {
    pub field: FfiField,
    pub problem: FfiProblem,
    /// Stable `field:problem` id (`number:cardNumberChecksum`) for logs and the golden
    /// vector tests. The UI switches on `field` / `problem`.
    pub code: String,
}

fn parse_payload(json: &[u8]) -> Result<qasa_vault::payload::VaultPayload, FfiPayloadError> {
    qasa_vault::payload::VaultPayload::from_stored_json(json).map_err(|_| FfiPayloadError::Unreadable)
}

/// Every problem with a payload (iOS or Android JSON), at most one per field, in form
/// order. Empty means it may be saved. Run it on the output of [`normalize_payload`].
#[uniffi::export]
pub fn validate_payload(json: Vec<u8>, today: FfiDate) -> Result<Vec<FfiFieldIssue>, FfiPayloadError> {
    let payload = parse_payload(&json)?;
    Ok(qasa_vault::validate::validate(&payload, today.into())
        .into_iter()
        .map(|i| FfiFieldIssue { field: i.field.into(), problem: i.problem.into(), code: i.code() })
        .collect())
}

/// The canonical stored form of a payload (dates `YYYY-MM-DD`, expiry `MM/YY`, grouped card
/// numbers, ASCII digits…), in the same JSON shape it came in (iOS or Android).
#[uniffi::export]
pub fn normalize_payload(json: Vec<u8>) -> Result<Vec<u8>, FfiPayloadError> {
    let shape = qasa_vault::payload::PayloadShape::detect(&json).ok_or(FfiPayloadError::Unreadable)?;
    Ok(qasa_vault::validate::normalize(&parse_payload(&json)?).to_json(shape))
}

/// The card brand from the leading digits.
#[uniffi::export]
pub fn card_brand(number: String) -> FfiCardBrand {
    qasa_vault::validate::CardBrand::detect(&number).into()
}

/// `(min, max)` security-code digits for a card number's brand, as `[min, max]`.
#[uniffi::export]
pub fn card_security_code_digits(number: String) -> Vec<u32> {
    let (min, max) = qasa_vault::validate::CardBrand::detect(&number).security_code_digits();
    vec![min, max]
}

/// Card number as the user types it: digits only, grouped for the brand.
#[uniffi::export]
pub fn format_card_number(raw: String) -> String {
    qasa_vault::validate::format_card_number(&raw)
}

/// Card expiry as the user types it: `MM/YY`.
#[uniffi::export]
pub fn format_card_expiry(raw: String) -> String {
    qasa_vault::validate::format_card_expiry(&raw)
}

/// Reads a stored date (`YYYY-MM-DD`, or the forms older versions stored) so the UI can
/// show it localized. `None` when it isn't a date.
#[uniffi::export]
pub fn parse_vault_date(raw: String) -> Option<FfiDate> {
    qasa_vault::validate::parse_date(&raw).map(Into::into)
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::collections::HashMap;

    #[derive(Default)]
    struct Mem {
        name: String,
        state: Mutex<(Option<String>, Option<FfiVaultMeta>, HashMap<String, FfiItem>)>,
    }

    impl SyncStoreCallback for Mem {
        fn intro(&self) -> FfiIntro {
            let s = self.state.lock().unwrap();
            FfiIntro { device_name: self.name.clone(), platform: "ios".into(), account_id: s.0.clone(), vault: s.1.clone() }
        }
        fn snapshot(&self) -> Vec<FfiItem> {
            self.state.lock().unwrap().2.values().cloned().collect()
        }
        fn collections(&self) -> Vec<FfiCollection> {
            vec![]
        }
        fn adopt(&self, account_id: Option<String>, vault: FfiVaultMeta) -> bool {
            let mut s = self.state.lock().unwrap();
            s.0 = account_id;
            s.1 = Some(vault);
            true
        }
        fn apply(&self, items: Vec<FfiItem>, _c: Vec<FfiCollection>) -> i64 {
            let mut s = self.state.lock().unwrap();
            let n = items.len() as i64;
            for i in items {
                s.2.insert(i.id.clone(), i);
            }
            n
        }
        fn now_ms(&self) -> i64 {
            1_000
        }
    }

    #[test]
    fn host_and_join_through_the_ffi() {
        let phone = Arc::new(Mem { name: "iPhone".into(), ..Default::default() });
        {
            let mut s = phone.state.lock().unwrap();
            s.0 = Some("acct".into());
            s.1 = Some(FfiVaultMeta { salt: vec![1; 32], kdf_version: 1, verifier: vec![2; 32] });
            s.2.insert(
                "a".into(),
                FfiItem {
                    id: "a".into(),
                    kind: "login".into(),
                    envelope: vec![9],
                    payload: vec![8],
                    schema_version: 1,
                    created_at_ms: 1,
                    updated_at_ms: 2,
                    last_used_at_ms: None,
                    collection_id: None,
                    deleted_at_ms: None,
                },
            );
        }
        let host = SyncHost::new().unwrap();
        let (port, code) = (host.port(), host.code());
        let h = host.clone();
        let p = phone.clone();
        let served = std::thread::spawn(move || h.serve(p));
        let android = Arc::new(Mem { name: "Pixel".into(), ..Default::default() });
        let joined = sync_join("127.0.0.1".into(), port, code, android.clone()).unwrap();
        assert!(joined.adopted);
        assert_eq!(served.join().unwrap().unwrap().sent, 1);
        assert_eq!(android.state.lock().unwrap().0.as_deref(), Some("acct"));
    }

    #[test]
    fn backup_through_the_ffi() {
        use qasa_vault::derive_root_key;
        let root = derive_root_key("pw-12345678", &[4; 32]).unwrap();
        let phone = Arc::new(Mem { name: "iPhone".into(), ..Default::default() });
        phone.state.lock().unwrap().0 = Some("acct".into());
        phone.state.lock().unwrap().1 = Some(FfiVaultMeta { salt: vec![4; 32], kdf_version: 1, verifier: root.verifier().to_vec() });
        let restore_key = backup_new_restore_key();
        let file = backup_create(root.subkeys().enc().to_vec(), Some(restore_key.clone()), phone).unwrap();
        assert_eq!(backup_info(file.clone()).unwrap().device_name, "iPhone");

        let fresh = Arc::new(Mem { name: "Pixel".into(), ..Default::default() });
        assert!(matches!(backup_restore(file.clone(), "nope".into(), fresh.clone()), Err(FfiBackupError::WrongPassword)));
        let r = backup_restore(file, "pw-12345678".into(), fresh.clone()).unwrap();
        assert!(r.adopted);
        assert_eq!(r.restore_key, Some(restore_key.clone()));
        assert_eq!(backup_restore_key_hash(restore_key).len(), 32);
        assert_eq!(fresh.state.lock().unwrap().0.as_deref(), Some("acct"));
        assert_eq!(backup_file_name("iPhone".into()), "Qasa backup - iPhone.qasabackup");
    }

    #[test]
    fn validation_through_the_ffi() {
        let today = FfiDate { year: 2026, month: 11, day: 10 };
        let android = br#"{"type":"card","itemName":"Visa","number":"4111111111111112","expiryDate":"Nov 6, 2027"}"#.to_vec();
        let issues = validate_payload(android.clone(), today).unwrap();
        assert_eq!(issues.len(), 1);
        assert_eq!((issues[0].field, issues[0].problem), (FfiField::Number, FfiProblem::CardNumberChecksum));
        assert_eq!(issues[0].code, "number:cardNumberChecksum");
        let normalized = String::from_utf8(normalize_payload(android).unwrap()).unwrap();
        assert!(normalized.contains("\"type\":\"card\"") && normalized.contains("\"expiryDate\":\"11/27\""), "{normalized}");
        let ios = br#"{"name":{"_0":{"firstName":"Ali","birthDate":"Nov 6, 2000"}}}"#.to_vec();
        assert!(String::from_utf8(normalize_payload(ios).unwrap()).unwrap().starts_with("{\"name\":{\"_0\""));
        assert!(matches!(validate_payload(b"[]".to_vec(), today), Err(FfiPayloadError::Unreadable)));
        assert_eq!(card_brand("3782".into()), FfiCardBrand::Amex);
        assert_eq!(format_card_expiry("1228".into()), "12/28");
        assert_eq!(parse_vault_date("2000-11-06".into()), Some(FfiDate { year: 2000, month: 11, day: 6 }));
    }

    #[test]
    fn payload_conversion() {
        let ios = br#"{"phone":{"_0":{"itemName":"Me","type":"Work Cell","countryCode":"IQ","number":"750"}}}"#.to_vec();
        let android = payload_to_android(ios.clone()).unwrap();
        assert!(String::from_utf8_lossy(&android).contains("\"phoneType\":\"Work Cell\""));
        assert_eq!(payload_to_canonical(android).unwrap(), payload_to_canonical(ios).unwrap());
    }
}
