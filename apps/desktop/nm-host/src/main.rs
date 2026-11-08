//! The Qasa native messaging host (plan §8.2.1). The browser starts it for each extension
//! connection. It relays frames between the extension (stdin/stdout, native-endian length
//! prefix) and the running desktop app (a local socket, little-endian length prefix).
//!
//! It is a dumb pipe: it holds no keys and reads no messages. Every request is encrypted
//! end to end between the extension and the desktop, so a process that replaces this
//! binary gains nothing but a pairing prompt the user can refuse.

use std::io::{self, Read, Write};
use std::path::PathBuf;
use std::sync::{Arc, Mutex};

/// Chrome's own limit for host → browser messages; used both ways.
const MAX_FRAME: usize = 1024 * 1024;
const APP_ID: &str = "com.qasa-app.desktop";

#[cfg(unix)]
type Conn = std::os::unix::net::UnixStream;
#[cfg(windows)]
type Conn = std::fs::File;

fn main() {
    // Chrome passes the caller's origin, Firefox the manifest path and the add-on id.
    let origin = std::env::args().skip(1).find(|a| !a.ends_with(".json")).unwrap_or_default();
    let stdout = Arc::new(Mutex::new(io::stdout()));
    let conn: Arc<Mutex<Option<Conn>>> = Arc::new(Mutex::new(None));
    let mut stdin = io::stdin().lock();

    loop {
        let frame = match read_frame(&mut stdin, false) {
            Ok(Some(f)) => f,
            // The browser closed the port: the extension went away.
            Ok(None) | Err(_) => return,
        };
        if !ensure_connected(&conn, &stdout, &origin) {
            write_browser(&stdout, br#"{"type":"error","code":"appNotRunning"}"#);
            continue;
        }
        let mut guard = conn.lock().unwrap();
        let sent = guard.as_mut().map(|c| write_frame(c, &frame, true).is_ok()).unwrap_or(false);
        if !sent {
            *guard = None;
            drop(guard);
            write_browser(&stdout, br#"{"type":"error","code":"appNotRunning"}"#);
        }
    }
}

/// Connects to the desktop if not connected yet, sends the hello frame and starts the
/// thread that relays the desktop's frames back to the browser.
fn ensure_connected(conn: &Arc<Mutex<Option<Conn>>>, stdout: &Arc<Mutex<io::Stdout>>, origin: &str) -> bool {
    let mut guard = conn.lock().unwrap();
    if guard.is_some() {
        return true;
    }
    let Some(mut stream) = connect() else { return false };
    let hello = format!(r#"{{"type":"hello","origin":{}}}"#, json_string(origin));
    if write_frame(&mut stream, hello.as_bytes(), true).is_err() {
        return false;
    }
    let Ok(mut reader) = stream.try_clone() else { return false };
    *guard = Some(stream);
    let (conn, stdout) = (Arc::clone(conn), Arc::clone(stdout));
    std::thread::spawn(move || {
        while let Ok(Some(frame)) = read_frame(&mut reader, true) {
            write_browser(&stdout, &frame);
        }
        // The desktop quit or dropped us; the next request reconnects.
        *conn.lock().unwrap() = None;
    });
    true
}

fn write_browser(stdout: &Arc<Mutex<io::Stdout>>, frame: &[u8]) {
    let mut out = stdout.lock().unwrap();
    if write_frame(&mut *out, frame, false).is_err() {
        // The browser is gone; nothing left to relay to.
        std::process::exit(0);
    }
}

/// Reads one length-prefixed frame. `None` on a clean end of stream.
fn read_frame(r: &mut impl Read, little_endian: bool) -> io::Result<Option<Vec<u8>>> {
    let mut len = [0u8; 4];
    match r.read_exact(&mut len) {
        Ok(()) => {}
        Err(e) if e.kind() == io::ErrorKind::UnexpectedEof => return Ok(None),
        Err(e) => return Err(e),
    }
    let n = if little_endian { u32::from_le_bytes(len) } else { u32::from_ne_bytes(len) } as usize;
    if n > MAX_FRAME {
        return Err(io::Error::new(io::ErrorKind::InvalidData, "frame too large"));
    }
    let mut buf = vec![0u8; n];
    r.read_exact(&mut buf)?;
    Ok(Some(buf))
}

fn write_frame(w: &mut impl Write, frame: &[u8], little_endian: bool) -> io::Result<()> {
    if frame.len() > MAX_FRAME {
        return Err(io::Error::new(io::ErrorKind::InvalidData, "frame too large"));
    }
    let n = frame.len() as u32;
    w.write_all(&if little_endian { n.to_le_bytes() } else { n.to_ne_bytes() })?;
    w.write_all(frame)?;
    w.flush()
}

fn json_string(s: &str) -> String {
    let mut out = String::with_capacity(s.len() + 2);
    out.push('"');
    for c in s.chars() {
        match c {
            '"' => out.push_str("\\\""),
            '\\' => out.push_str("\\\\"),
            c if (c as u32) < 0x20 => out.push_str(&format!("\\u{:04x}", c as u32)),
            c => out.push(c),
        }
    }
    out.push('"');
    out
}

/// The socket the desktop listens on (plan §8.2.1). `QASA_BRIDGE_SOCKET` wins, for tests.
pub fn socket_path() -> Option<PathBuf> {
    if let Some(p) = std::env::var_os("QASA_BRIDGE_SOCKET") {
        return Some(PathBuf::from(p));
    }
    #[cfg(target_os = "macos")]
    {
        let home = std::env::var_os("HOME")?;
        Some(PathBuf::from(home).join("Library/Application Support").join(APP_ID).join("bridge.sock"))
    }
    #[cfg(all(unix, not(target_os = "macos")))]
    {
        let base = std::env::var_os("XDG_DATA_HOME")
            .map(PathBuf::from)
            .filter(|p| p.is_absolute())
            .or_else(|| std::env::var_os("HOME").map(|h| PathBuf::from(h).join(".local/share")))?;
        Some(base.join(APP_ID).join("bridge.sock"))
    }
    #[cfg(windows)]
    {
        let _ = APP_ID;
        let user = std::env::var("USERNAME").unwrap_or_default();
        Some(PathBuf::from(format!(r"\\.\pipe\qasa-bridge-{user}")))
    }
}

#[cfg(unix)]
fn connect() -> Option<Conn> {
    std::os::unix::net::UnixStream::connect(socket_path()?).ok()
}

/// A named pipe opens like a file. Requests and replies alternate, so the synchronous
/// handle shared by the reader thread and the writer never waits on itself for long.
#[cfg(windows)]
fn connect() -> Option<Conn> {
    std::fs::OpenOptions::new().read(true).write(true).open(socket_path()?).ok()
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn frames_round_trip_in_both_byte_orders() {
        for le in [true, false] {
            let mut buf = Vec::new();
            write_frame(&mut buf, br#"{"a":1}"#, le).unwrap();
            let mut r = &buf[..];
            assert_eq!(read_frame(&mut r, le).unwrap().unwrap(), br#"{"a":1}"#);
            assert!(read_frame(&mut r, le).unwrap().is_none());
        }
    }

    #[test]
    fn rejects_oversized_frames() {
        let len = ((MAX_FRAME + 1) as u32).to_le_bytes();
        let mut r = &len[..];
        assert!(read_frame(&mut r, true).is_err());
    }

    #[test]
    fn escapes_the_origin() {
        assert_eq!(json_string("a\"b\\c\n"), r#""a\"b\\c\u000a""#);
    }
}
