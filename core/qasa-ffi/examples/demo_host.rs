//! A stand-in "other device" for testing phones by hand: a real encrypted vault (main
//! password `password1`) with a login, a card and a note, advertised over mDNS as
//! "Qasa Demo Host" and served through the same `SyncHost` the phones use.
//!
//!     cargo run -p qasa-ffi --example demo_host
//!
//! On a phone without a vault: Create vault → "I already have Qasa on another device",
//! pick "Qasa Demo Host", type the printed code, then unlock with `password1`.

use std::collections::HashMap;
use std::sync::{Arc, Mutex};

use mdns_sd::{ServiceDaemon, ServiceInfo};
use qasa_ffi::*;
use qasa_vault::payload::*;

struct Demo {
    meta: FfiVaultMeta,
    items: Mutex<HashMap<String, FfiItem>>,
    collections: Mutex<Vec<FfiCollection>>,
}

impl SyncStoreCallback for Demo {
    fn intro(&self) -> FfiIntro {
        FfiIntro {
            device_name: "Qasa Demo Host".into(),
            platform: "macos".into(),
            account_id: Some("0f1e2d3c-4b5a-4987-8a6b-5c4d3e2f1a0b".into()),
            vault: Some(self.meta.clone()),
        }
    }
    fn snapshot(&self) -> Vec<FfiItem> {
        self.items.lock().unwrap().values().cloned().collect()
    }
    fn collections(&self) -> Vec<FfiCollection> {
        self.collections.lock().unwrap().clone()
    }
    fn adopt(&self, _: Option<String>, _: FfiVaultMeta) -> bool {
        false
    }
    fn apply(&self, items: Vec<FfiItem>, collections: Vec<FfiCollection>) -> i64 {
        let n = (items.len() + collections.len()) as i64;
        for i in items {
            println!("  received item {} (deleted: {})", i.id, i.deleted_at_ms.is_some());
            self.items.lock().unwrap().insert(i.id.clone(), i);
        }
        self.collections.lock().unwrap().extend(collections);
        n
    }
    fn now_ms(&self) -> i64 {
        std::time::SystemTime::now().duration_since(std::time::UNIX_EPOCH).unwrap().as_millis() as i64
    }
}

fn main() {
    let salt = qasa_vault::generate_salt();
    let root = qasa_vault::derive_root_key("password1", &salt).unwrap();
    let keys = root.subkeys();
    let now = std::time::SystemTime::now().duration_since(std::time::UNIX_EPOCH).unwrap().as_millis() as i64;
    let folder = uuid::Uuid::new_v4().to_string().to_uppercase();

    let payloads = vec![
        VaultPayload::Login(LoginFields {
            item_name: "GitHub".into(),
            email: "demo@qasa-app.com".into(),
            password: "Correct-Horse-91".into(),
            website: "https://github.com".into(),
            collection_id: Some(folder.clone()),
            ..Default::default()
        }),
        VaultPayload::Card(CardFields { item_name: "FIB Card".into(), number: "4111111111111111".into(), expiry_date: "08/29".into(), ..Default::default() }),
        VaultPayload::SecureNote(SecureNoteFields { title: "Home Wi‑Fi".into(), content: "Network: Ahmed-Home".into(), ..Default::default() }),
    ];
    let mut items = HashMap::new();
    for p in payloads {
        let id = uuid::Uuid::new_v4().to_string().to_uppercase();
        let envelope = serde_json_bytes(&p.envelope());
        items.insert(
            id.clone(),
            FfiItem {
                id,
                kind: p.kind().as_str().into(),
                envelope: qasa_vault::encrypt(&envelope, keys.enc()),
                payload: qasa_vault::encrypt(&p.to_stored_json(), keys.enc()),
                schema_version: 1,
                created_at_ms: now,
                updated_at_ms: now,
                last_used_at_ms: None,
                collection_id: p.collection_id(),
                deleted_at_ms: None,
            },
        );
    }
    let store = Arc::new(Demo {
        meta: FfiVaultMeta { salt: salt.to_vec(), kdf_version: 1, verifier: root.verifier().to_vec() },
        items: Mutex::new(items),
        collections: Mutex::new(vec![FfiCollection { id: folder, name: "Work".into(), created_at_ms: now, updated_at_ms: now, deleted_at_ms: None }]),
    });

    let host = SyncHost::new().unwrap();
    let daemon = ServiceDaemon::new().unwrap();
    let info = ServiceInfo::new("_qasa-sync._tcp.local.", "Qasa Demo Host", "qasa-demo-host.local.", "", host.port(), &[("p", "macos"), ("v", "1")][..])
        .unwrap()
        .enable_addr_auto();
    daemon.register(info).unwrap();
    println!("Advertising \"Qasa Demo Host\" on port {}. Code: {}", host.port(), host.code());
    loop {
        match host.serve(store.clone()) {
            Ok(o) => {
                println!("SYNCED with {} ({}): adopted={} received={} sent={}", o.peer_name, o.peer_platform, o.adopted, o.received, o.sent);
                break;
            }
            Err(FfiSyncError::WrongCode { attempts_left }) if attempts_left > 0 => println!("wrong code, {attempts_left} left"),
            Err(e) => {
                println!("FAILED: {e:?}");
                break;
            }
        }
    }
    let _ = daemon.shutdown();
}

fn serde_json_bytes(e: &Envelope) -> Vec<u8> {
    // The envelope's JSON, as the apps write it.
    format!(
        "{{\"displayName\":{},\"subtitle\":{}{}}}",
        json_str(&e.display_name),
        json_str(&e.subtitle),
        e.collection_id.as_ref().map(|c| format!(",\"collectionId\":{}", json_str(c))).unwrap_or_default()
    )
    .into_bytes()
}

fn json_str(s: &str) -> String {
    format!("\"{}\"", s.replace('\\', "\\\\").replace('"', "\\\""))
}
