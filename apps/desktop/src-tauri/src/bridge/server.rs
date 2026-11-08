//! The local socket nm-host connects to (plan §8.2.1): a Unix socket in the app data
//! folder, or a per-user named pipe on Windows. One thread per connection; frames are
//! handled one at a time, in order.

use std::io::{self, Read, Write};
use std::path::{Path, PathBuf};
use std::sync::atomic::{AtomicUsize, Ordering};
use std::sync::{mpsc, Arc};

use serde_json::{json, Value};

use super::{Host, PairOutcome, PAIR_TIMEOUT};

const MAX_FRAME: usize = 1024 * 1024;
/// Browsers open one connection per extension port; more than this is not a browser.
const MAX_CONNECTIONS: usize = 16;

/// Where the desktop listens: `QASA_BRIDGE_SOCKET`, else `<app data>/bridge.sock`, or the
/// per-user pipe on Windows. nm-host computes the same place.
pub fn socket_path(app_data_dir: &Path) -> PathBuf {
    if let Some(p) = std::env::var_os("QASA_BRIDGE_SOCKET") {
        return PathBuf::from(p);
    }
    if cfg!(windows) {
        let user = std::env::var("USERNAME").unwrap_or_default();
        PathBuf::from(format!(r"\\.\pipe\qasa-bridge-{user}"))
    } else {
        app_data_dir.join("bridge.sock")
    }
}

/// Starts listening in the background. Errors (another Qasa already listening, a path
/// that can't be bound) only disable the extension; the app keeps working.
pub fn start<H: Host + 'static>(host: Arc<H>, path: PathBuf) {
    use interprocess::local_socket::{prelude::*, GenericFilePath, ListenerOptions};
    let name = match path.as_os_str().to_fs_name::<GenericFilePath>() {
        Ok(n) => n,
        Err(e) => return eprintln!("browser bridge disabled: {e}"),
    };
    let listener = match ListenerOptions::new().name(name).try_overwrite(true).create_sync() {
        Ok(l) => l,
        Err(e) => return eprintln!("browser bridge disabled: {e}"),
    };
    #[cfg(unix)]
    {
        use std::os::unix::fs::PermissionsExt;
        let _ = std::fs::set_permissions(&path, std::fs::Permissions::from_mode(0o600));
    }
    let open = Arc::new(AtomicUsize::new(0));
    std::thread::spawn(move || {
        for conn in listener.incoming() {
            let Ok(conn) = conn else { continue };
            if open.fetch_add(1, Ordering::SeqCst) >= MAX_CONNECTIONS {
                open.fetch_sub(1, Ordering::SeqCst);
                continue;
            }
            let (host, open) = (Arc::clone(&host), Arc::clone(&open));
            std::thread::spawn(move || {
                serve(conn, host.as_ref());
                open.fetch_sub(1, Ordering::SeqCst);
            });
        }
    });
}

/// Runs one connection until nm-host goes away.
pub fn serve<S: Read + Write>(mut stream: S, host: &dyn Host) {
    while let Ok(Some(frame)) = read_frame(&mut stream) {
        let reply = match serde_json::from_slice::<Value>(&frame) {
            Ok(frame) => handle(&frame, host, &mut stream),
            Err(_) => Some(json!({ "type": "error", "code": "bad" })),
        };
        if let Some(reply) = reply {
            if write_frame(&mut stream, reply.to_string().as_bytes()).is_err() {
                return;
            }
        }
    }
}

fn handle<S: Write>(frame: &Value, host: &dyn Host, stream: &mut S) -> Option<Value> {
    let bridge = host.bridge();
    match frame.get("type").and_then(Value::as_str) {
        // nm-host's first frame names the calling browser; nothing depends on it.
        Some("hello") => None,
        Some("req") => Some(bridge.0.lock().ok()?.handle_request(frame, host)),
        Some("pair") => {
            let client = frame.get("client").and_then(Value::as_str).unwrap_or_default();
            let public = frame.get("pub").and_then(Value::as_str).unwrap_or_default();
            let begun = bridge.0.lock().ok()?.begin_pair(client, public, host);
            let (pending, rx) = match begun {
                Ok(v) => v,
                Err(error) => return Some(error),
            };
            write_frame(stream, pending.to_string().as_bytes()).ok()?;
            Some(match rx.recv_timeout(PAIR_TIMEOUT) {
                Ok(PairOutcome::Paired(id)) => json!({ "type": "paired", "clientId": id }),
                Ok(PairOutcome::Denied) => json!({ "type": "pairDenied" }),
                Err(mpsc::RecvTimeoutError::Timeout | mpsc::RecvTimeoutError::Disconnected) => {
                    if let Ok(mut b) = bridge.0.lock() {
                        b.expire_pair(host);
                    }
                    json!({ "type": "pairExpired" })
                }
            })
        }
        _ => Some(json!({ "type": "error", "code": "bad" })),
    }
}

pub fn read_frame(r: &mut impl Read) -> io::Result<Option<Vec<u8>>> {
    let mut len = [0u8; 4];
    match r.read_exact(&mut len) {
        Ok(()) => {}
        Err(e) if e.kind() == io::ErrorKind::UnexpectedEof => return Ok(None),
        Err(e) => return Err(e),
    }
    let n = u32::from_le_bytes(len) as usize;
    if n > MAX_FRAME {
        return Err(io::Error::new(io::ErrorKind::InvalidData, "frame too large"));
    }
    let mut buf = vec![0u8; n];
    r.read_exact(&mut buf)?;
    Ok(Some(buf))
}

pub fn write_frame(w: &mut impl Write, frame: &[u8]) -> io::Result<()> {
    w.write_all(&(frame.len() as u32).to_le_bytes())?;
    w.write_all(frame)?;
    w.flush()
}
