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
    pub name: String,
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
    WireCollection { id: c.id, name: c.name, created_at: c.created_at_ms, updated_at: c.updated_at_ms, deleted_at: c.deleted_at_ms }
}

fn collection_out(c: &WireCollection) -> FfiCollection {
    FfiCollection { id: c.id.clone(), name: c.name.clone(), created_at_ms: c.created_at, updated_at_ms: c.updated_at, deleted_at_ms: c.deleted_at }
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
    fn payload_conversion() {
        let ios = br#"{"phone":{"_0":{"itemName":"Me","type":"Work Cell","countryCode":"IQ","number":"750"}}}"#.to_vec();
        let android = payload_to_android(ios.clone()).unwrap();
        assert!(String::from_utf8_lossy(&android).contains("\"phoneType\":\"Work Cell\""));
        assert_eq!(payload_to_canonical(android).unwrap(), payload_to_canonical(ios).unwrap());
    }
}
