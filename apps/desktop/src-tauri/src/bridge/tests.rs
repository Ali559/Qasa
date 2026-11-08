//! The bridge protocol end to end, with a fake app and a Rust stand-in for the extension.

use std::sync::Mutex;

use base64::engine::general_purpose::STANDARD as B64;
use base64::Engine;
use p256::ecdh::EphemeralSecret;
use p256::elliptic_curve::sec1::ToEncodedPoint;
use p256::PublicKey;
use qasa_vault::payload::{LoginFields, VaultPayload};
use rand::rngs::OsRng;
use rand::RngCore;
use serde_json::{json, Value};

use super::clients::MemoryClients;
use super::*;

const TOTP: &str = "otpauth://totp/GitHub:me?secret=JBSWY3DPEHPK3PXPJBSWY3DPEHPK3PXP&issuer=GitHub";

#[derive(Default)]
struct Seen {
    pairs: Vec<Option<PairPrompt>>,
    saves: Vec<SavePrompt>,
    copies: Vec<(String, u64)>,
    focused: usize,
}

struct FakeHost {
    bridge: BridgeState,
    vault: Mutex<Vault>,
    seen: Mutex<Seen>,
}

impl Host for FakeHost {
    fn bridge(&self) -> &BridgeState {
        &self.bridge
    }
    fn vault(&self) -> &Mutex<Vault> {
        &self.vault
    }
    fn device_name(&self) -> String {
        "Test Mac".into()
    }
    fn focus(&self) {
        self.seen.lock().unwrap().focused += 1;
    }
    fn copy(&self, value: String, ms: u64) {
        self.seen.lock().unwrap().copies.push((value, ms));
    }
    fn show_pair(&self, p: Option<PairPrompt>) {
        self.seen.lock().unwrap().pairs.push(p);
    }
    fn show_save(&self, p: SavePrompt) {
        self.seen.lock().unwrap().saves.push(p);
    }
}

fn login(name: &str, website: &str, user: &str, pw: &str, totp: Option<&str>) -> VaultPayload {
    VaultPayload::Login(LoginFields {
        item_name: name.into(),
        username: user.into(),
        password: pw.into(),
        website: website.into(),
        totp: totp.map(String::from),
        ..Default::default()
    })
}

/// An unlocked vault with GitHub (with 2FA), a Google login and a note.
fn host() -> (FakeHost, String, String) {
    let mut vault = Vault::in_memory();
    vault.create("password1", false).unwrap();
    let gh = vault.save(None, &login("GitHub", "https://github.com", "octo", "gh-pass", Some(TOTP))).unwrap().id;
    let google = vault.save(None, &login("Google", "accounts.google.com", "me@gmail.com", "g-pass", None)).unwrap().id;
    vault
        .save(None, &VaultPayload::SecureNote(qasa_vault::payload::SecureNoteFields { title: "n".into(), ..Default::default() }))
        .unwrap();
    let host = FakeHost {
        bridge: BridgeState(Mutex::new(Bridge::new(Box::new(MemoryClients::default())))),
        vault: Mutex::new(vault),
        seen: Mutex::new(Seen::default()),
    };
    (host, gh, google)
}

/// The extension's side: pairs, then encrypts requests like the service worker.
struct Ext {
    client_id: String,
    key: [u8; 32],
}

impl Ext {
    fn pair(host: &FakeHost) -> Ext {
        let secret = EphemeralSecret::random(&mut OsRng);
        let ext_pub = secret.public_key().to_encoded_point(false).as_bytes().to_vec();
        let (pending, rx) = host.bridge.0.lock().unwrap().begin_pair("Chrome", &B64.encode(&ext_pub), host).unwrap();
        assert_eq!(pending["type"], "pairPending");
        let desk_pub = B64.decode(pending["pub"].as_str().unwrap()).unwrap();
        let shared = secret.diffie_hellman(&PublicKey::from_sec1_bytes(&desk_pub).unwrap());
        let mine = crypto::derive(shared.raw_secret_bytes(), &ext_pub, &desk_pub);
        // The desktop shows the same code the extension computed.
        let shown = host.seen.lock().unwrap().pairs.last().cloned().flatten().unwrap();
        assert_eq!(shown, PairPrompt { client: "Chrome".into(), code: mine.code.clone() });
        host.bridge.0.lock().unwrap().decide_pair(true, host);
        let PairOutcome::Paired(client_id) = rx.recv().unwrap() else { panic!("not paired") };
        Ext { client_id, key: mine.key }
    }

    fn frame(&self, body: Value) -> Value {
        let (n, c) = crypto::seal(&self.key, self.client_id.as_bytes(), body.to_string().as_bytes());
        json!({ "type": "req", "clientId": self.client_id, "n": n, "c": c })
    }

    fn body(op: &str, args: Value) -> Value {
        let mut id = [0u8; 8];
        OsRng.fill_bytes(&mut id);
        let mut body = json!({ "id": hex(&id), "ts": now_ms(), "op": op });
        for (k, v) in args.as_object().unwrap() {
            body[k] = v.clone();
        }
        body
    }

    fn open(&self, reply: &Value) -> Value {
        assert_eq!(reply["type"], "res", "{reply}");
        let plain = crypto::open(&self.key, self.client_id.as_bytes(), reply["n"].as_str().unwrap(), reply["c"].as_str().unwrap()).unwrap();
        serde_json::from_slice(&plain).unwrap()
    }

    fn call(&self, host: &FakeHost, op: &str, args: Value) -> Value {
        let body = Ext::body(op, args);
        let reply = host.bridge.0.lock().unwrap().handle_request(&self.frame(body.clone()), host);
        let out = self.open(&reply);
        assert_eq!(out["id"], body["id"]);
        out
    }
}

fn hex(b: &[u8]) -> String {
    b.iter().map(|x| format!("{x:02x}")).collect()
}

#[test]
fn pairs_and_answers_status() {
    let (host, _, _) = host();
    let ext = Ext::pair(&host);
    let status = ext.call(&host, "status", json!({}));
    assert_eq!(status["ok"], true);
    assert_eq!(status["state"], "unlocked");
    assert_eq!(status["device"], "Test Mac");
    assert_eq!(host.bridge.0.lock().unwrap().clients().len(), 1);
    // The prompt was closed once answered, and the window came forward for it.
    assert_eq!(host.seen.lock().unwrap().pairs.last(), Some(&None));
    assert!(host.seen.lock().unwrap().focused >= 1);
}

#[test]
fn denied_and_busy_pairings() {
    let (host, _, _) = host();
    let key = |h: &FakeHost| {
        let s = EphemeralSecret::random(&mut OsRng);
        let p = B64.encode(s.public_key().to_encoded_point(false).as_bytes());
        h.bridge.0.lock().unwrap().begin_pair("Firefox", &p, h)
    };
    let (_, rx) = key(&host).unwrap();
    assert_eq!(key(&host).unwrap_err()["code"], "pairBusy");
    host.bridge.0.lock().unwrap().decide_pair(false, &host);
    assert_eq!(rx.recv().unwrap(), PairOutcome::Denied);
    assert!(host.bridge.0.lock().unwrap().clients().is_empty());
    assert_eq!(host.bridge.0.lock().unwrap().begin_pair("x", "not base64!", &host).unwrap_err()["code"], "bad");
}

#[test]
fn refuses_unknown_tampered_replayed_and_stale_requests() {
    let (host, _, _) = host();
    let ext = Ext::pair(&host);
    let call = |frame: &Value| host.bridge.0.lock().unwrap().handle_request(frame, &host);

    let stranger = Ext { client_id: "nope".into(), key: [1; 32] };
    assert_eq!(call(&stranger.frame(Ext::body("status", json!({}))))["code"], "notPaired");

    let wrong_key = Ext { client_id: ext.client_id.clone(), key: [2; 32] };
    assert_eq!(call(&wrong_key.frame(Ext::body("status", json!({}))))["code"], "bad");

    let frame = ext.frame(Ext::body("status", json!({})));
    assert_eq!(ext.open(&call(&frame))["ok"], true);
    assert_eq!(ext.open(&call(&frame))["error"], "bad", "a replay is refused");

    let mut stale = Ext::body("status", json!({}));
    stale["ts"] = json!(now_ms() - 10 * 60 * 1000);
    assert_eq!(ext.open(&call(&ext.frame(stale)))["error"], "bad");

    // Revoking a browser cuts it off.
    host.bridge.0.lock().unwrap().revoke(&ext.client_id);
    assert_eq!(call(&ext.frame(Ext::body("status", json!({}))))["code"], "notPaired");
}

#[test]
fn matches_and_fills_only_on_the_items_own_site() {
    let (host, gh, google) = host();
    let ext = Ext::pair(&host);

    let m = ext.call(&host, "matches", json!({ "url": "https://github.com/login?return=/x" }));
    let items = m["items"].as_array().unwrap();
    assert_eq!(items.len(), 1);
    assert_eq!(items[0], json!({ "id": gh, "title": "GitHub", "username": "octo", "hasTotp": true }));
    assert_eq!(ext.call(&host, "matches", json!({ "url": "https://mail.google.com" }))["items"][0]["id"], google);
    assert!(ext.call(&host, "matches", json!({ "url": "https://github.com.evil.io" }))["items"].as_array().unwrap().is_empty());

    let fill = ext.call(&host, "fill", json!({ "itemId": gh, "url": "https://github.com/session" }));
    assert_eq!((fill["username"].as_str(), fill["password"].as_str()), (Some("octo"), Some("gh-pass")));
    assert_eq!(fill["totp"].as_str().unwrap().len(), 6);

    // Another site's page can't pull GitHub's password, whatever id it sends.
    assert_eq!(ext.call(&host, "fill", json!({ "itemId": gh, "url": "https://gitlab.com" }))["error"], "noMatch");
    assert_eq!(ext.call(&host, "totp", json!({ "itemId": gh, "url": "https://evil.io" }))["error"], "noMatch");
    assert_eq!(ext.call(&host, "fill", json!({ "itemId": gh, "url": "javascript:alert(1)" }))["error"], "bad");
    assert_eq!(ext.call(&host, "fill", json!({ "itemId": gh, "url": "file:///etc/passwd" }))["error"], "bad");

    let t = ext.call(&host, "totp", json!({ "itemId": gh, "url": "https://github.com" }));
    assert_eq!(t["period"], 30);
    assert!(t["remaining"].as_u64().unwrap() <= 30);
}

#[test]
fn copy_search_and_generate() {
    let (host, gh, _) = host();
    let ext = Ext::pair(&host);
    assert_eq!(ext.call(&host, "copy", json!({ "itemId": gh, "field": "password" }))["ok"], true);
    assert_eq!(host.seen.lock().unwrap().copies.last(), Some(&("gh-pass".to_string(), 30_000)));
    ext.call(&host, "copy", json!({ "itemId": gh, "field": "totp" }));
    assert!(host.seen.lock().unwrap().copies.last().unwrap().1 <= 30_000);

    let s = ext.call(&host, "search", json!({ "query": "GIT" }));
    assert_eq!(s["items"].as_array().unwrap().len(), 1);
    assert_eq!(s["items"][0]["website"], "https://github.com");
    assert_eq!(ext.call(&host, "search", json!({ "query": "" }))["items"].as_array().unwrap().len(), 2, "logins only");

    let g = ext.call(&host, "generate", json!({}));
    assert_eq!(g["password"].as_str().unwrap().chars().count(), 16);
}

#[test]
fn locked_vault_answers_locked() {
    let (host, gh, _) = host();
    let ext = Ext::pair(&host);
    host.vault.lock().unwrap().lock();
    assert_eq!(ext.call(&host, "status", json!({}))["state"], "locked");
    assert_eq!(ext.call(&host, "matches", json!({ "url": "https://github.com" }))["error"], "locked");
    assert_eq!(ext.call(&host, "fill", json!({ "itemId": gh, "url": "https://github.com" }))["error"], "locked");
    let save = ext.call(&host, "save", json!({ "url": "https://x.com", "username": "a", "password": "b" }));
    assert_eq!(save["result"], "locked");
    assert!(host.seen.lock().unwrap().saves.is_empty());
    ext.call(&host, "unlock", json!({}));
    assert!(host.seen.lock().unwrap().focused >= 2);
}

#[test]
fn saves_new_logins_and_updates_passwords_after_the_user_confirms() {
    let (host, gh, _) = host();
    let ext = Ext::pair(&host);
    let save = |url: &str, user: &str, pw: &str| ext.call(&host, "save", json!({ "url": url, "username": user, "password": pw }))["result"].clone();
    let decide = |accept: bool| {
        let id = host.seen.lock().unwrap().saves.last().unwrap().id.clone();
        let mut vault = host.vault.lock().unwrap();
        host.bridge.0.lock().unwrap().decide_save(&id, accept, &mut vault).unwrap()
    };

    // Already saved: nothing to ask.
    assert_eq!(save("https://github.com/login", "octo", "gh-pass"), "unchanged");

    // A new site: prompt, and nothing is written until the user says yes.
    assert_eq!(save("https://news.example.com/login", "me@example.com", "s3cret"), "prompted");
    let prompt = host.seen.lock().unwrap().saves.last().unwrap().clone();
    assert_eq!((prompt.kind, prompt.website.as_str(), prompt.username.as_str()), ("save", "news.example.com", "me@example.com"));
    assert_eq!(prompt.client, "Chrome");
    // The same submit twice shows one prompt.
    save("https://news.example.com/login", "me@example.com", "s3cret");
    assert_eq!(host.seen.lock().unwrap().saves.len(), 1);
    assert!(decide(true));
    let found = ext.call(&host, "matches", json!({ "url": "https://news.example.com" }));
    let id = found["items"][0]["id"].as_str().unwrap().to_string();
    let filled = ext.call(&host, "fill", json!({ "itemId": id, "url": "https://news.example.com" }));
    assert_eq!((filled["username"].as_str(), filled["password"].as_str()), (Some("me@example.com"), Some("s3cret")));
    let VaultPayload::Login(saved) = host.vault.lock().unwrap().get(&id).unwrap() else { panic!() };
    assert_eq!((saved.email.as_str(), saved.username.as_str(), saved.website.as_str()), ("me@example.com", "", "https://news.example.com"));

    // A new password for a saved login is an update of that login.
    assert_eq!(save("https://github.com/settings", "OCTO", "new-pass"), "prompted");
    let prompt = host.seen.lock().unwrap().saves.last().unwrap().clone();
    assert_eq!((prompt.kind, prompt.item_title.as_deref()), ("update", Some("GitHub")));
    assert!(!decide(false), "Not now writes nothing");
    assert_eq!(ext.call(&host, "fill", json!({ "itemId": gh, "url": "https://github.com" }))["password"], "gh-pass");
    save("https://github.com/settings", "OCTO", "new-pass");
    assert!(decide(true));
    assert_eq!(ext.call(&host, "fill", json!({ "itemId": gh, "url": "https://github.com" }))["password"], "new-pass");

    // A change-password form without a username updates the site's only login.
    assert_eq!(save("https://github.com/settings", "", "newer-pass"), "prompted");
    assert!(decide(true));
    assert_eq!(ext.call(&host, "fill", json!({ "itemId": gh, "url": "https://github.com" }))["password"], "newer-pass");

    assert_eq!(ext.call(&host, "save", json!({ "url": "https://a.com", "username": "a", "password": "" }))["error"], "bad");
}

/// The socket loop with real framing: pair, then a request, over a Unix socket pair.
#[cfg(unix)]
#[test]
fn serves_pairing_and_requests_over_a_socket() {
    use std::os::unix::net::UnixStream;
    use std::sync::Arc;

    let (host, _, _) = host();
    let host = Arc::new(host);
    let (mut ext_end, desk_end) = UnixStream::pair().unwrap();
    let server_host = Arc::clone(&host);
    let server = std::thread::spawn(move || server::serve(desk_end, server_host.as_ref()));

    let send = |s: &mut UnixStream, v: Value| server::write_frame(s, v.to_string().as_bytes()).unwrap();
    let recv = |s: &mut UnixStream| -> Value { serde_json::from_slice(&server::read_frame(s).unwrap().unwrap()).unwrap() };

    send(&mut ext_end, json!({ "type": "hello", "origin": "chrome-extension://abc/" }));
    let secret = EphemeralSecret::random(&mut OsRng);
    let ext_pub = secret.public_key().to_encoded_point(false).as_bytes().to_vec();
    send(&mut ext_end, json!({ "type": "pair", "client": "Chrome", "pub": B64.encode(&ext_pub) }));
    let pending = recv(&mut ext_end);
    assert_eq!(pending["type"], "pairPending");
    host.bridge.0.lock().unwrap().decide_pair(true, host.as_ref());
    let paired = recv(&mut ext_end);
    assert_eq!(paired["type"], "paired");

    let desk_pub = B64.decode(pending["pub"].as_str().unwrap()).unwrap();
    let shared = secret.diffie_hellman(&PublicKey::from_sec1_bytes(&desk_pub).unwrap());
    let ext = Ext { client_id: paired["clientId"].as_str().unwrap().into(), key: crypto::derive(shared.raw_secret_bytes(), &ext_pub, &desk_pub).key };
    send(&mut ext_end, ext.frame(Ext::body("status", json!({}))));
    assert_eq!(ext.open(&recv(&mut ext_end))["state"], "unlocked");

    send(&mut ext_end, json!("not an object"));
    assert_eq!(recv(&mut ext_end)["code"], "bad");
    drop(ext_end);
    server.join().unwrap();
}

/// Not a test: runs the real bridge on `$QASA_BRIDGE_SOCKET` with a demo vault, and
/// approves every pairing and save by itself, so the extension's browser tests can run
/// against the desktop's own code instead of a stand-in:
///
/// `QASA_BRIDGE_SOCKET=/tmp/q.sock cargo test bridge_demo_server -- --ignored --nocapture`
///
/// It prints `PAIRED <client>` and `SAVED <kind> <site> <username>` as things happen.
#[cfg(unix)]
#[test]
#[ignore]
fn bridge_demo_server() {
    use std::sync::Arc;
    use std::time::Duration;

    let path = std::env::var("QASA_BRIDGE_SOCKET").expect("set QASA_BRIDGE_SOCKET");
    let mut vault = Vault::in_memory();
    vault.create("password1", false).unwrap();
    for (name, site, user, pw, totp) in [
        ("Fixture 127", "http://127.0.0.1", "alice", "alice-pass-127", None),
        ("Fixture localhost", "http://localhost", "bob", "bob-pass-localhost", None),
        ("GitHub", "https://github.com", "octo", "gh-pass", Some(TOTP)),
    ] {
        vault.save(None, &login(name, site, user, pw, totp)).unwrap();
    }
    let host = Arc::new(FakeHost {
        bridge: BridgeState(Mutex::new(Bridge::new(Box::new(MemoryClients::default())))),
        vault: Mutex::new(vault),
        seen: Mutex::new(Seen::default()),
    });
    let _ = std::fs::remove_file(&path);
    server::start(Arc::clone(&host), std::path::PathBuf::from(&path));
    println!("LISTENING {path}");
    loop {
        std::thread::sleep(Duration::from_millis(100));
        let mut bridge = host.bridge.0.lock().unwrap();
        if let Some(client) = bridge.pair.as_ref().map(|p| p.client.clone()) {
            bridge.decide_pair(true, host.as_ref());
            println!("PAIRED {client}");
        }
        let ids: Vec<String> = bridge.saves.keys().cloned().collect();
        for id in ids {
            let save = bridge.saves[&id].2.clone();
            let mut vault = host.vault.lock().unwrap();
            if bridge.decide_save(&id, true, &mut vault).unwrap_or(false) {
                let kind = if save.kind == SaveKind::New { "save" } else { "update" };
                println!("SAVED {kind} {} {}", save.host, save.username);
            }
        }
    }
}
