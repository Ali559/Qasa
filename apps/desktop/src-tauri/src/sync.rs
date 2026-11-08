//! Wi‑Fi vault sync on the desktop (plan §6): mDNS discovery, the host and join sides,
//! and the bridge between `qasa-sync` and this computer's vault and account.
//!
//! - Host: shows a 6-digit code, listens on a random TCP port and advertises
//!   `_qasa-sync._tcp` under the computer's name (never the code). The code works for one
//!   successful session, 3 attempts and 2 minutes, whichever ends first.
//! - Join: browses for hosts, connects to the one the user picks, and runs the session
//!   with the code the user types.
//! Progress goes to the UI as `sync-event`.

use std::net::{SocketAddr, TcpListener, TcpStream};
use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::{Arc, Mutex};
use std::time::{Duration, Instant};

use mdns_sd::{ServiceDaemon, ServiceEvent, ServiceInfo};
use qasa_sync::{Adoption, Intro, MergePlan, Outcome, Role, Snapshot, SyncError, SyncStore, PROTOCOL_VERSION};
use serde::Serialize;
use tauri::{AppHandle, Emitter, Manager, State};

use crate::account::commands::AccountState;
use crate::vault::commands::VaultState;

const MDNS_TYPE: &str = "_qasa-sync._tcp.local.";
const CODE_LIFETIME: Duration = Duration::from_secs(120);
const MAX_ATTEMPTS: u32 = 3;

fn platform() -> &'static str {
    match std::env::consts::OS {
        "macos" => "macos",
        "windows" => "windows",
        _ => "linux",
    }
}

/// The vault and account, locked per call so a slow network never holds them.
struct DesktopStore {
    app: AppHandle,
}

fn store_err(e: impl std::fmt::Display) -> SyncError {
    SyncError::Store(e.to_string())
}

impl SyncStore for DesktopStore {
    fn intro(&self) -> Result<Intro, SyncError> {
        let vault = self.app.state::<VaultState>();
        let meta = vault.0.lock().map_err(store_err)?.sync_meta().map_err(store_err)?;
        let account_id = self.app.state::<AccountState>().0.blocking_lock().account_id();
        Ok(Intro {
            protocol: PROTOCOL_VERSION,
            device_name: whoami::devicename(),
            platform: platform().into(),
            account_id,
            vault: meta,
        })
    }

    fn snapshot(&self) -> Result<Snapshot, SyncError> {
        self.app.state::<VaultState>().0.lock().map_err(store_err)?.sync_snapshot().map_err(store_err)
    }

    fn adopt(&mut self, adoption: &Adoption) -> Result<(), SyncError> {
        self.app.state::<VaultState>().0.lock().map_err(store_err)?.sync_adopt(&adoption.vault).map_err(store_err)?;
        if let Some(account_id) = &adoption.account_id {
            self.app.state::<AccountState>().0.blocking_lock().adopt_account(account_id);
        }
        Ok(())
    }

    fn apply(&mut self, plan: &MergePlan) -> Result<u32, SyncError> {
        self.app.state::<VaultState>().0.lock().map_err(store_err)?.sync_apply(plan).map_err(store_err)
    }

    fn now_ms(&self) -> i64 {
        crate::vault::store::now_ms()
    }
}

#[derive(Serialize, Clone)]
#[serde(rename_all = "camelCase", tag = "stage")]
pub enum SyncEvent {
    /// Hosting: waiting for the other device.
    Waiting { code: String, expires_in: u64 },
    /// A device connected; checking the code.
    Connecting,
    Done {
        peer_name: String,
        peer_platform: String,
        adopted: bool,
        received: u32,
        sent: u32,
    },
    /// `wrongCode`, `differentVault`, `noVault`, `version`, `expired`, `io`, `other`.
    Failed { reason: String, attempts_left: u32 },
    Stopped,
}

fn reason(e: &SyncError) -> &'static str {
    match e {
        SyncError::WrongCode => "wrongCode",
        SyncError::DifferentVault => "differentVault",
        SyncError::NoVault => "noVault",
        SyncError::Version => "version",
        SyncError::Io(_) => "io",
        _ => "other",
    }
}

fn outcome_event(o: Outcome) -> SyncEvent {
    SyncEvent::Done { peer_name: o.peer_name, peer_platform: o.peer_platform, adopted: o.adopted, received: o.received, sent: o.sent }
}

/// One host or browse at a time.
#[derive(Default)]
pub struct SyncState {
    stop: Mutex<Option<Arc<AtomicBool>>>,
}

fn replace_stop(state: &SyncState) -> Arc<AtomicBool> {
    let flag = Arc::new(AtomicBool::new(false));
    if let Some(old) = state.stop.lock().unwrap().replace(flag.clone()) {
        old.store(true, Ordering::SeqCst);
    }
    flag
}

fn instance_name() -> String {
    // The computer's name, never the code.
    let name: String = whoami::devicename().chars().filter(|c| c.is_alphanumeric() || " -_'".contains(*c)).take(40).collect();
    if name.trim().is_empty() { "Qasa".into() } else { name }
}

/// Starts hosting. Returns the code to show; progress arrives as `sync-event`.
#[tauri::command]
pub fn sync_host_start(app: AppHandle, state: State<'_, SyncState>) -> Result<String, String> {
    let stop = replace_stop(&state);
    let code = qasa_sync::generate_code();
    let listener = TcpListener::bind("0.0.0.0:0").map_err(|e| e.to_string())?;
    listener.set_nonblocking(true).map_err(|e| e.to_string())?;
    let port = listener.local_addr().map_err(|e| e.to_string())?.port();

    let daemon = ServiceDaemon::new().map_err(|e| e.to_string())?;
    let host = format!("qasa-{}.local.", uuid::Uuid::new_v4().simple());
    let info = ServiceInfo::new(MDNS_TYPE, &instance_name(), &host, "", port, &[("v", PROTOCOL_VERSION.to_string().as_str()), ("p", platform())][..])
        .map_err(|e| e.to_string())?
        .enable_addr_auto();
    let fullname = info.get_fullname().to_string();
    daemon.register(info).map_err(|e| e.to_string())?;

    let host_code = code.clone();
    std::thread::spawn(move || {
        let started = Instant::now();
        let mut attempts = 0;
        let _ = app.emit("sync-event", SyncEvent::Waiting { code: host_code.clone(), expires_in: CODE_LIFETIME.as_secs() });
        let end = loop {
            if stop.load(Ordering::SeqCst) {
                break SyncEvent::Stopped;
            }
            if started.elapsed() > CODE_LIFETIME {
                break SyncEvent::Failed { reason: "expired".into(), attempts_left: 0 };
            }
            match listener.accept() {
                Ok((stream, _)) => {
                    attempts += 1;
                    let _ = app.emit("sync-event", SyncEvent::Connecting);
                    let _ = stream.set_nonblocking(false);
                    let _ = stream.set_read_timeout(Some(Duration::from_secs(60)));
                    let mut store = DesktopStore { app: app.clone() };
                    match qasa_sync::run(stream, Role::Host, &host_code, &mut store) {
                        Ok(o) => break outcome_event(o),
                        Err(e) => {
                            let left = MAX_ATTEMPTS.saturating_sub(attempts);
                            let _ = app.emit("sync-event", SyncEvent::Failed { reason: reason(&e).into(), attempts_left: left });
                            // A refused vault decision won't change with retries.
                            if left == 0 || matches!(e, SyncError::DifferentVault | SyncError::NoVault | SyncError::Version) {
                                break SyncEvent::Stopped;
                            }
                        }
                    }
                }
                Err(e) if e.kind() == std::io::ErrorKind::WouldBlock => std::thread::sleep(Duration::from_millis(150)),
                Err(_) => break SyncEvent::Failed { reason: "io".into(), attempts_left: 0 },
            }
        };
        let _ = daemon.unregister(&fullname);
        let _ = daemon.shutdown();
        let _ = app.emit("sync-event", end);
    });
    Ok(code)
}

#[derive(Serialize, Clone)]
#[serde(rename_all = "camelCase")]
pub struct Peer {
    pub name: String,
    pub platform: String,
    pub address: String,
}

/// Starts looking for hosts; the list arrives as `sync-peers` until stopped.
#[tauri::command]
pub fn sync_browse_start(app: AppHandle, state: State<'_, SyncState>) -> Result<(), String> {
    let stop = replace_stop(&state);
    let daemon = ServiceDaemon::new().map_err(|e| e.to_string())?;
    let receiver = daemon.browse(MDNS_TYPE).map_err(|e| e.to_string())?;
    std::thread::spawn(move || {
        let mut peers: Vec<(String, Peer)> = Vec::new();
        while !stop.load(Ordering::SeqCst) {
            match receiver.recv_timeout(Duration::from_millis(300)) {
                Ok(ServiceEvent::ServiceResolved(info)) => {
                    // Prefer IPv4 for a LAN address.
                    let mut addrs: Vec<_> = info.get_addresses().iter().cloned().collect();
                    addrs.sort_by_key(|a| !a.is_ipv4());
                    let Some(ip) = addrs.first() else { continue };
                    let name = info.get_fullname().trim_end_matches(MDNS_TYPE).trim_end_matches('.').to_string();
                    let platform = info.get_property_val_str("p").unwrap_or("").to_string();
                    let address = SocketAddr::new(*ip, info.get_port()).to_string();
                    peers.retain(|(full, _)| full != info.get_fullname());
                    peers.push((info.get_fullname().to_string(), Peer { name, platform, address }));
                    let _ = app.emit("sync-peers", peers.iter().map(|(_, p)| p.clone()).collect::<Vec<_>>());
                }
                Ok(ServiceEvent::ServiceRemoved(_, fullname)) => {
                    peers.retain(|(full, _)| *full != fullname);
                    let _ = app.emit("sync-peers", peers.iter().map(|(_, p)| p.clone()).collect::<Vec<_>>());
                }
                _ => {}
            }
        }
        let _ = daemon.shutdown();
    });
    Ok(())
}

#[tauri::command]
pub fn sync_stop(state: State<'_, SyncState>) {
    if let Some(flag) = state.stop.lock().unwrap().take() {
        flag.store(true, Ordering::SeqCst);
    }
}

/// Connects to a host (an address from `sync-peers`, or typed) and runs the session.
#[tauri::command]
pub async fn sync_join(app: AppHandle, address: String, code: String) -> Result<SyncEvent, String> {
    let addr: SocketAddr = address.parse().map_err(|_| "invalid address".to_string())?;
    let result = tauri::async_runtime::spawn_blocking(move || {
        let stream = TcpStream::connect_timeout(&addr, Duration::from_secs(10)).map_err(|e| SyncError::Io(e.to_string()))?;
        stream.set_read_timeout(Some(Duration::from_secs(60))).ok();
        let mut store = DesktopStore { app };
        qasa_sync::run(stream, Role::Join, &code, &mut store)
    })
    .await
    .map_err(|e| e.to_string())?;
    Ok(match result {
        Ok(o) => outcome_event(o),
        Err(e) => SyncEvent::Failed { reason: reason(&e).into(), attempts_left: 0 },
    })
}

#[cfg(test)]
mod tests {
    use super::*;

    /// Real multicast on this machine's network: advertise, then find it.
    /// `cargo test mdns_round_trip -- --ignored`
    #[test]
    #[ignore]
    fn mdns_round_trip() {
        let daemon = ServiceDaemon::new().unwrap();
        let info = ServiceInfo::new(MDNS_TYPE, "Qasa mDNS test", "qasa-test.local.", "", 45_123, &[("p", "macos")][..])
            .unwrap()
            .enable_addr_auto();
        daemon.register(info).unwrap();
        let browser = ServiceDaemon::new().unwrap();
        let rx = browser.browse(MDNS_TYPE).unwrap();
        let deadline = Instant::now() + Duration::from_secs(10);
        let mut found = None;
        while Instant::now() < deadline && found.is_none() {
            if let Ok(ServiceEvent::ServiceResolved(i)) = rx.recv_timeout(Duration::from_millis(300)) {
                if i.get_fullname().starts_with("Qasa mDNS test") {
                    found = Some((i.get_port(), i.get_property_val_str("p").map(str::to_string), i.get_addresses().len()));
                }
            }
        }
        let _ = daemon.shutdown();
        let _ = browser.shutdown();
        let (port, platform, addrs) = found.expect("service not found over mDNS");
        assert_eq!((port, platform.as_deref()), (45_123, Some("macos")));
        assert!(addrs > 0);
    }
}
