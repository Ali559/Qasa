//! Browsers the user allowed (plan §8.1), kept in the OS keychain as one JSON entry: the
//! client id, the browser's name, its shared key and when it was paired and last used.

use base64::engine::general_purpose::STANDARD as B64;
use base64::Engine;
use serde::{Deserialize, Serialize};

use super::crypto::KEY_LEN;

const SERVICE: &str = "com.qasa-app.desktop";
const ENTRY: &str = "qasa.bridge.clients";
/// "Last used" is saved at most this often, so filling doesn't write the keychain each time.
const LAST_USED_GRANULARITY_MS: i64 = 60 * 60 * 1000;

#[derive(Serialize, Deserialize, Clone, Debug, PartialEq, Eq)]
#[serde(rename_all = "camelCase")]
pub struct Client {
    pub id: String,
    pub name: String,
    key: String,
    pub paired_at: i64,
    pub last_used_at: Option<i64>,
}

impl Client {
    pub fn new(id: String, name: String, key: &[u8; KEY_LEN], now: i64) -> Self {
        Client { id, name, key: B64.encode(key), paired_at: now, last_used_at: None }
    }

    pub fn key(&self) -> Option<[u8; KEY_LEN]> {
        B64.decode(&self.key).ok()?.try_into().ok()
    }
}

/// What the Settings page shows: no keys.
#[derive(Serialize, Clone, Debug, PartialEq, Eq)]
#[serde(rename_all = "camelCase")]
pub struct ClientInfo {
    pub id: String,
    pub name: String,
    pub paired_at: i64,
    pub last_used_at: Option<i64>,
}

pub trait ClientStore: Send {
    fn load(&self) -> Vec<Client>;
    fn save(&self, clients: &[Client]);
}

pub struct KeyringClients;

impl ClientStore for KeyringClients {
    fn load(&self) -> Vec<Client> {
        keyring::Entry::new(SERVICE, ENTRY)
            .ok()
            .and_then(|e| e.get_password().ok())
            .and_then(|raw| serde_json::from_str(&raw).ok())
            .unwrap_or_default()
    }

    fn save(&self, clients: &[Client]) {
        let Ok(entry) = keyring::Entry::new(SERVICE, ENTRY) else { return };
        let result = if clients.is_empty() {
            match entry.delete_credential() {
                Err(keyring::Error::NoEntry) => Ok(()),
                other => other,
            }
        } else {
            entry.set_password(&serde_json::to_string(clients).unwrap_or_default())
        };
        if let Err(e) = result {
            eprintln!("could not save browser pairings: {e}");
        }
    }
}

#[cfg(test)]
#[derive(Default)]
pub struct MemoryClients(pub std::sync::Mutex<Vec<Client>>);

#[cfg(test)]
impl ClientStore for MemoryClients {
    fn load(&self) -> Vec<Client> {
        self.0.lock().unwrap().clone()
    }
    fn save(&self, clients: &[Client]) {
        *self.0.lock().unwrap() = clients.to_vec();
    }
}

/// The allowed browsers, loaded once and written through on every change.
pub struct Clients {
    store: Box<dyn ClientStore>,
    list: Vec<Client>,
}

impl Clients {
    pub fn new(store: Box<dyn ClientStore>) -> Self {
        let list = store.load();
        Clients { store, list }
    }

    pub fn get(&self, id: &str) -> Option<&Client> {
        self.list.iter().find(|c| c.id == id)
    }

    pub fn add(&mut self, client: Client) {
        self.list.push(client);
        self.store.save(&self.list);
    }

    pub fn remove(&mut self, id: &str) -> bool {
        let before = self.list.len();
        self.list.retain(|c| c.id != id);
        let removed = self.list.len() != before;
        if removed {
            self.store.save(&self.list);
        }
        removed
    }

    pub fn touch(&mut self, id: &str, now: i64) {
        let Some(c) = self.list.iter_mut().find(|c| c.id == id) else { return };
        if c.last_used_at.is_some_and(|t| now - t < LAST_USED_GRANULARITY_MS) {
            return;
        }
        c.last_used_at = Some(now);
        self.store.save(&self.list);
    }

    pub fn infos(&self) -> Vec<ClientInfo> {
        let mut out: Vec<ClientInfo> = self
            .list
            .iter()
            .map(|c| ClientInfo { id: c.id.clone(), name: c.name.clone(), paired_at: c.paired_at, last_used_at: c.last_used_at })
            .collect();
        out.sort_by(|a, b| b.paired_at.cmp(&a.paired_at));
        out
    }
}
