//! The browser extension bridge (plan §8.1, §8.2.1): pairing, end-to-end encrypted
//! requests and the ops the extension may run. Nothing here depends on Tauri: the app
//! plugs in through [`Host`], so the whole protocol runs in tests.

pub mod clients;
pub mod commands;
pub mod crypto;
pub mod manifest;
pub mod matcher;
mod ops;
pub mod server;

use std::collections::HashMap;
use std::sync::mpsc;
use std::sync::Mutex;
use std::time::{Duration, Instant};

use base64::engine::general_purpose::STANDARD as B64;
use base64::Engine;
use serde::Serialize;
use serde_json::{json, Value};

use clients::{Client, ClientInfo, Clients, ClientStore};
use crypto::KEY_LEN;
use ops::OpError;

use crate::vault::Vault;

/// How long a pairing waits for the user (plan §8.2.1).
pub const PAIR_TIMEOUT: Duration = Duration::from_secs(120);
/// Requests whose timestamp is further off than this are refused.
const MAX_SKEW_MS: i64 = 2 * 60 * 1000;
/// Request ids are remembered this long, so a recorded request can't be replayed.
const SEEN_WINDOW_MS: i64 = 5 * 60 * 1000;
/// Unanswered save prompts are dropped after this.
const SAVE_LIFETIME: Duration = Duration::from_secs(10 * 60);
const MAX_PENDING_SAVES: usize = 20;
/// A payment confirmation waits this long for Touch ID / Hello or the master password.
const CONFIRM_LIFETIME: Duration = Duration::from_secs(120);
/// An approved confirmation must be used for its fill within this.
const APPROVAL_LIFETIME: Duration = Duration::from_secs(60);

/// What the bridge needs from the running app.
pub trait Host: Send + Sync {
    fn bridge(&self) -> &BridgeState;
    fn vault(&self) -> &Mutex<Vault>;
    fn device_name(&self) -> String;
    /// Brings the window forward (the `unlock` op, and pairing).
    fn focus(&self);
    /// Copies a secret and clears it later if it is still on the clipboard.
    fn copy(&self, value: String, clear_after_ms: u64);
    /// Shows "Allow <browser> to use Qasa?" with the code, or hides it (`None`).
    fn show_pair(&self, prompt: Option<PairPrompt>);
    /// Shows a save or update prompt.
    fn show_save(&self, prompt: SavePrompt);
    /// Starts Touch ID / Windows Hello for `auth` on its own thread, outside every lock,
    /// and reports a confirmation back through [`Bridge::finish_confirm`] (an unlock just
    /// unlocks the vault). False when this computer has no usable seal: the caller then
    /// asks for the master password in the window instead.
    fn start_biometric(&self, auth: Auth) -> bool;
    /// Shows "Confirm to fill <card>" with a master password field, or hides it (`None`).
    fn show_confirm(&self, prompt: Option<ConfirmPrompt>);
}

/// What a Touch ID / Hello prompt started by the bridge is for.
#[derive(Clone, Debug, PartialEq, Eq)]
pub enum Auth {
    /// The extension's "Unlock": unlock the vault.
    Unlock,
    /// A payment fill (plan §1.3): approve the confirmation `key`.
    Confirm(ConfirmPrompt),
}

/// A payment fill waiting for the user. `id` is the confirmation's key.
#[derive(Serialize, Clone, Debug, PartialEq, Eq)]
#[serde(rename_all = "camelCase")]
pub struct ConfirmPrompt {
    pub id: String,
    pub item_title: String,
    pub client: String,
}

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
enum ConfirmState {
    Pending,
    Approved,
    Denied,
}

#[derive(Serialize, Clone, Debug, PartialEq, Eq)]
#[serde(rename_all = "camelCase")]
pub struct PairPrompt {
    pub client: String,
    pub code: String,
}

#[derive(Serialize, Clone, Debug, PartialEq, Eq)]
#[serde(rename_all = "camelCase")]
pub struct SavePrompt {
    pub id: String,
    /// `save` or `update`.
    pub kind: &'static str,
    pub website: String,
    pub username: String,
    /// The saved login being updated.
    pub item_title: Option<String>,
    pub client: String,
}

#[derive(Clone, Debug, PartialEq, Eq)]
pub enum SaveKind {
    New,
    Update { item_id: String, title: String },
}

/// A save waiting for the user. The password never leaves the core process.
#[derive(Clone, Debug, PartialEq, Eq)]
pub struct PendingSave {
    pub kind: SaveKind,
    pub host: String,
    pub origin: String,
    pub username: String,
    pub password: String,
}

/// How a pairing ended, sent back to the connection that asked.
#[derive(Debug, PartialEq, Eq)]
pub enum PairOutcome {
    Paired(String),
    Denied,
}

struct PendingPair {
    client: String,
    key: [u8; KEY_LEN],
    started: Instant,
    reply: mpsc::Sender<PairOutcome>,
}

pub struct Bridge {
    clients: Clients,
    seen: HashMap<String, i64>,
    pair: Option<PendingPair>,
    saves: HashMap<String, (Instant, String, PendingSave)>,
    /// Payment confirmations by `<client>:<item>`, with when they last changed.
    confirms: HashMap<String, (Instant, ConfirmState)>,
}

pub struct BridgeState(pub Mutex<Bridge>);

fn now_ms() -> i64 {
    std::time::SystemTime::now().duration_since(std::time::UNIX_EPOCH).map(|d| d.as_millis() as i64).unwrap_or(0)
}

fn clear_error(code: &str) -> Value {
    json!({ "type": "error", "code": code })
}

impl Bridge {
    pub fn new(store: Box<dyn ClientStore>) -> Self {
        Bridge { clients: Clients::new(store), seen: HashMap::new(), pair: None, saves: HashMap::new(), confirms: HashMap::new() }
    }

    pub fn clients(&self) -> Vec<ClientInfo> {
        self.clients.infos()
    }

    pub fn revoke(&mut self, id: &str) -> bool {
        self.clients.remove(id)
    }

    /// Step 2 of pairing: answers with the desktop's public key and shows the code. The
    /// connection then waits on the receiver for the user's decision. Refused while
    /// another pairing is waiting, so a local process can't flood the screen.
    pub fn begin_pair(&mut self, client: &str, ext_pub_b64: &str, host: &dyn Host) -> Result<(Value, mpsc::Receiver<PairOutcome>), Value> {
        if self.pair.as_ref().is_some_and(|p| p.started.elapsed() < PAIR_TIMEOUT) {
            return Err(clear_error("pairBusy"));
        }
        let ext_pub = B64.decode(ext_pub_b64).map_err(|_| clear_error("bad"))?;
        let (desk_pub, pairing) = crypto::pair_with(&ext_pub).ok_or_else(|| clear_error("bad"))?;
        let client: String = client.chars().filter(|c| !c.is_control()).take(40).collect();
        let client = if client.trim().is_empty() { "Browser".to_string() } else { client };
        let (tx, rx) = mpsc::channel();
        self.pair = Some(PendingPair { client: client.clone(), key: pairing.key, started: Instant::now(), reply: tx });
        host.show_pair(Some(PairPrompt { client, code: pairing.code }));
        host.focus();
        Ok((json!({ "type": "pairPending", "pub": B64.encode(desk_pub) }), rx))
    }

    /// The user's answer to the pairing prompt.
    pub fn decide_pair(&mut self, allow: bool, host: &dyn Host) {
        let Some(pair) = self.pair.take() else { return };
        host.show_pair(None);
        if pair.started.elapsed() >= PAIR_TIMEOUT {
            return;
        }
        let outcome = if allow {
            let id = uuid::Uuid::new_v4().to_string();
            self.clients.add(Client::new(id.clone(), pair.client, &pair.key, now_ms()));
            PairOutcome::Paired(id)
        } else {
            PairOutcome::Denied
        };
        let _ = pair.reply.send(outcome);
    }

    /// The prompt expired without an answer.
    pub fn expire_pair(&mut self, host: &dyn Host) {
        if self.pair.as_ref().is_some_and(|p| p.started.elapsed() >= PAIR_TIMEOUT) {
            self.pair = None;
            host.show_pair(None);
        }
    }

    /// A `req` frame: authenticates, decrypts, checks freshness, runs the op and returns
    /// the encrypted `res` frame (or a clear error frame when it can't encrypt one).
    pub fn handle_request(&mut self, frame: &Value, host: &dyn Host) -> Value {
        let mut reply = self.handle_request_inner(frame, host);
        // Clear errors carry the request's nonce, so the extension knows which one failed.
        if reply["type"] == "error" {
            if let Some(n) = frame.get("n").and_then(Value::as_str) {
                reply["n"] = json!(n);
            }
        }
        reply
    }

    fn handle_request_inner(&mut self, frame: &Value, host: &dyn Host) -> Value {
        let Some(client_id) = frame.get("clientId").and_then(Value::as_str) else { return clear_error("bad") };
        let Some(client) = self.clients.get(client_id) else { return clear_error("notPaired") };
        let (Some(key), client_name) = (client.key(), client.name.clone()) else { return clear_error("notPaired") };
        let aad = client_id.as_bytes();
        let (Some(n), Some(c)) = (frame.get("n").and_then(Value::as_str), frame.get("c").and_then(Value::as_str)) else {
            return clear_error("bad");
        };
        let Some(plain) = crypto::open(&key, aad, n, c) else { return clear_error("bad") };
        let Ok(req) = serde_json::from_slice::<Value>(&plain) else { return clear_error("bad") };
        let id = req.get("id").and_then(Value::as_str).unwrap_or_default().to_string();

        let now = now_ms();
        let fresh = req.get("ts").and_then(Value::as_i64).is_some_and(|ts| (now - ts).abs() <= MAX_SKEW_MS);
        self.seen.retain(|_, t| now - *t <= SEEN_WINDOW_MS);
        let reply = if id.is_empty() || id.len() > 64 || !fresh || self.seen.contains_key(&id) {
            Err(OpError::Bad)
        } else {
            self.seen.insert(id.clone(), now);
            self.clients.touch(client_id, now);
            self.run(&req, client_id, &client_name, host)
        };

        let body = match reply {
            Ok(mut v) => {
                v["id"] = json!(id);
                v["ok"] = json!(true);
                v
            }
            Err(e) => json!({ "id": id, "ok": false, "error": e.code() }),
        };
        let (n, c) = crypto::seal(&key, aad, body.to_string().as_bytes());
        json!({ "type": "res", "n": n, "c": c })
    }

    fn run(&mut self, req: &Value, client_id: &str, client: &str, host: &dyn Host) -> Result<Value, OpError> {
        let op = req.get("op").and_then(Value::as_str).ok_or(OpError::Bad)?;
        if op == "unlock" {
            // `prompt`: the in-page menu asks for Touch ID / Hello right away (plan §3.2).
            // The extension then watches `status` until the vault is unlocked.
            let locked = host.vault().lock().map(|v| !v.is_unlocked()).unwrap_or(false);
            let wants_prompt = req.get("prompt").and_then(Value::as_bool).unwrap_or(false);
            if locked && wants_prompt && host.start_biometric(Auth::Unlock) {
                return Ok(json!({ "method": "biometric" }));
            }
            host.focus();
            return Ok(json!({ "method": "window" }));
        }
        if op == "generate" {
            return ops::generate();
        }
        let mut vault = host.vault().lock().map_err(|_| OpError::Bad)?;
        if op == "status" {
            return ops::status(&vault, host);
        }
        if !vault.is_unlocked() {
            let exists = vault.status().map(|s| s.exists).unwrap_or(false);
            if op == "save" && exists {
                return Ok(json!({ "result": "locked" }));
            }
            return Err(if exists { OpError::Locked } else { OpError::NoVault });
        }
        match op {
            "matches" => ops::matches(&vault, req),
            "fill" => ops::fill(&mut vault, req),
            "totp" => ops::totp(&vault, req),
            "copy" => ops::copy(&mut vault, req, host),
            "search" => ops::search(&vault, req),
            "identities" => ops::identities(&vault),
            "fillIdentity" => {
                let key = confirm_key(client_id, req)?;
                let confirmed = self.take_approval(&key)?;
                ops::fill_identity(&mut vault, req, confirmed)
            }
            "confirm" => {
                let key = confirm_key(client_id, req)?;
                let id = req.get("itemId").and_then(Value::as_str).ok_or(OpError::Bad)?;
                let item_title = ops::confirmable(&vault, id)?;
                drop(vault);
                self.start_confirm(key, item_title, client, host);
                Ok(json!({}))
            }
            "save" => {
                let Some(save) = ops::plan_save(&vault, req)? else { return Ok(json!({ "result": "unchanged" })) };
                drop(vault);
                self.queue_save(save, client, host);
                Ok(json!({ "result": "prompted" }))
            }
            _ => Err(OpError::Bad),
        }
    }

    /// Whether a payment fill was approved: consumes an approval, reports a pending or
    /// dismissed one, and forgets stale ones.
    fn take_approval(&mut self, key: &str) -> Result<bool, OpError> {
        self.confirms.retain(|_, (t, state)| match state {
            ConfirmState::Approved => t.elapsed() < APPROVAL_LIFETIME,
            _ => t.elapsed() < CONFIRM_LIFETIME,
        });
        match self.confirms.get(key).map(|(_, s)| *s) {
            None => Ok(false),
            Some(ConfirmState::Pending) => Err(OpError::Pending),
            Some(ConfirmState::Approved) => {
                self.confirms.remove(key);
                Ok(true)
            }
            Some(ConfirmState::Denied) => {
                self.confirms.remove(key);
                Err(OpError::Cancelled)
            }
        }
    }

    /// Asks the user to confirm a payment fill: Touch ID / Hello when sealed, else the
    /// master password in the window. One prompt per item at a time.
    fn start_confirm(&mut self, key: String, item_title: String, client: &str, host: &dyn Host) {
        if self.confirms.get(&key).is_some_and(|(t, s)| *s == ConfirmState::Pending && t.elapsed() < CONFIRM_LIFETIME) {
            return;
        }
        self.confirms.insert(key.clone(), (Instant::now(), ConfirmState::Pending));
        let prompt = ConfirmPrompt { id: key, item_title, client: client.to_string() };
        if !host.start_biometric(Auth::Confirm(prompt.clone())) {
            host.show_confirm(Some(prompt));
            host.focus();
        }
    }

    /// The answer to a payment confirmation (Touch ID / Hello, or the password prompt).
    pub fn finish_confirm(&mut self, key: &str, approved: bool, host: &dyn Host) {
        if let Some(entry) = self.confirms.get_mut(key) {
            if entry.1 == ConfirmState::Pending {
                *entry = (Instant::now(), if approved { ConfirmState::Approved } else { ConfirmState::Denied });
            }
        }
        host.show_confirm(None);
    }

    fn queue_save(&mut self, save: PendingSave, client: &str, host: &dyn Host) {
        self.saves.retain(|_, (t, _, _)| t.elapsed() < SAVE_LIFETIME);
        // The same submit can arrive twice (submit event and button click).
        if self.saves.values().any(|(_, _, s)| s == &save) || self.saves.len() >= MAX_PENDING_SAVES {
            return;
        }
        let id = uuid::Uuid::new_v4().to_string();
        let (kind, item_title) = match &save.kind {
            SaveKind::New => ("save", None),
            SaveKind::Update { title, .. } => ("update", Some(title.clone())),
        };
        host.show_save(SavePrompt {
            id: id.clone(),
            kind,
            website: save.host.clone(),
            username: save.username.clone(),
            item_title,
            client: client.to_string(),
        });
        self.saves.insert(id, (Instant::now(), client.to_string(), save));
    }

    /// The user's answer to a save prompt. Returns `Ok(true)` when something was saved.
    pub fn decide_save(&mut self, id: &str, accept: bool, vault: &mut Vault) -> Result<bool, crate::vault::error::CommandError> {
        use crate::vault::error::CommandError;
        let Some((started, _, save)) = self.saves.remove(id) else { return Err(CommandError::NotFound) };
        if !accept || started.elapsed() >= SAVE_LIFETIME {
            return Ok(false);
        }
        ops::apply_save(vault, &save).map_err(|e| match e {
            OpError::Locked => CommandError::Locked,
            OpError::NotFound => CommandError::NotFound,
            _ => CommandError::Invalid("could not save".into()),
        })?;
        Ok(true)
    }
}

/// A confirmation belongs to one paired browser and one item.
fn confirm_key(client_id: &str, req: &Value) -> Result<String, OpError> {
    let id = req.get("itemId").and_then(Value::as_str).filter(|s| !s.is_empty() && s.len() <= 64).ok_or(OpError::Bad)?;
    Ok(format!("{client_id}:{id}"))
}

#[cfg(test)]
mod tests;
