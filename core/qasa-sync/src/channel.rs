//! The authenticated channel. Host (Bonjour listener) is role A, the joiner role B.
//!
//! ```text
//! A → B   hello  { protocol, salt(16), spake2 msg A }        (clear)
//! B → A   reply  { spake2 msg B }                            (clear)
//!         both:  K = SPAKE2(Argon2id(code, salt)); keys via HKDF(K)
//! A → B   confirm_a = HMAC(K_confirm, "A" ‖ transcript)
//! B → A   confirm_b = HMAC(K_confirm, "B" ‖ transcript)       (checked in constant time)
//!         then AES-256-GCM frames, nonce = direction counter
//! ```
//! Frames are a 4-byte big-endian length then the bytes. A wrong code fails the confirm
//! step and both sides close: an eavesdropper or a guessing peer gets one online guess per
//! connection and never sees ciphertext it could test offline.

use std::io::{Read, Write};

use aes_gcm::aead::{Aead, KeyInit};
use aes_gcm::{Aes256Gcm, Nonce};
use argon2::{Algorithm, Argon2, Params, Version};
use hkdf::Hkdf;
use hmac::{Hmac, Mac};
use rand::{Rng, RngCore};
use serde::{Deserialize, Serialize};
use sha2::{Digest, Sha256};
use spake2::{Ed25519Group, Identity, Password, Spake2};
use subtle::ConstantTimeEq;
use zeroize::Zeroize;

use crate::PROTOCOL_VERSION;

/// Largest frame accepted (a whole vault snapshot).
const MAX_FRAME: usize = 64 * 1024 * 1024;
const ID_A: &[u8] = b"qasa-sync-host";
const ID_B: &[u8] = b"qasa-sync-join";

#[derive(Debug, thiserror::Error, PartialEq, Eq)]
pub enum SyncError {
    /// The codes differ (or someone in the middle): the confirm step failed.
    #[error("the pairing code did not match")]
    WrongCode,
    #[error("the other device runs an incompatible version of Qasa")]
    Version,
    #[error("connection error: {0}")]
    Io(String),
    #[error("the other device sent something unexpected")]
    Protocol,
    /// Both devices have a vault, and they are different vaults.
    #[error("these devices hold different vaults")]
    DifferentVault,
    /// Neither device has a vault to share.
    #[error("neither device has a vault")]
    NoVault,
    #[error("the other device refused: {0}")]
    Refused(String),
    #[error("local storage error: {0}")]
    Store(String),
}

impl From<std::io::Error> for SyncError {
    fn from(e: std::io::Error) -> Self {
        SyncError::Io(e.to_string())
    }
}

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum Role {
    /// Shows the code and listens.
    Host,
    /// Finds the host and types the code.
    Join,
}

/// A 6-digit code, uniformly random.
pub fn generate_code() -> String {
    format!("{:06}", rand::rngs::OsRng.gen_range(0..1_000_000u32))
}

/// Argon2id over the code (19 MiB, 2 passes: OWASP's baseline), salted per session, so a
/// recorded handshake can't be tested against all codes with a shared table.
fn stretch(code: &str, salt: &[u8]) -> Result<[u8; 32], SyncError> {
    let params = Params::new(19 * 1024, 2, 1, Some(32)).map_err(|_| SyncError::Protocol)?;
    let mut out = [0u8; 32];
    Argon2::new(Algorithm::Argon2id, Version::V0x13, params)
        .hash_password_into(code.trim().as_bytes(), salt, &mut out)
        .map_err(|_| SyncError::Protocol)?;
    Ok(out)
}

#[derive(Serialize, Deserialize)]
struct Hello {
    protocol: u32,
    salt: Vec<u8>,
    spake: Vec<u8>,
}

#[derive(Serialize, Deserialize)]
struct Reply {
    protocol: u32,
    spake: Vec<u8>,
}

fn write_frame<S: Write>(stream: &mut S, bytes: &[u8]) -> Result<(), SyncError> {
    if bytes.len() > MAX_FRAME {
        return Err(SyncError::Protocol);
    }
    stream.write_all(&(bytes.len() as u32).to_be_bytes())?;
    stream.write_all(bytes)?;
    stream.flush()?;
    Ok(())
}

fn read_frame<S: Read>(stream: &mut S) -> Result<Vec<u8>, SyncError> {
    let mut len = [0u8; 4];
    stream.read_exact(&mut len)?;
    let len = u32::from_be_bytes(len) as usize;
    if len > MAX_FRAME {
        return Err(SyncError::Protocol);
    }
    let mut buf = vec![0u8; len];
    stream.read_exact(&mut buf)?;
    Ok(buf)
}

/// An open, authenticated, encrypted channel.
pub struct Channel<S: Read + Write> {
    stream: S,
    send_key: [u8; 32],
    recv_key: [u8; 32],
    send_counter: u64,
    recv_counter: u64,
}

impl<S: Read + Write> Drop for Channel<S> {
    fn drop(&mut self) {
        self.send_key.zeroize();
        self.recv_key.zeroize();
    }
}

fn nonce(counter: u64) -> [u8; 12] {
    let mut n = [0u8; 12];
    n[4..].copy_from_slice(&counter.to_be_bytes());
    n
}

impl<S: Read + Write> Channel<S> {
    /// Runs the handshake. `code` is what the user sees (host) or types (joiner).
    pub fn open(mut stream: S, role: Role, code: &str) -> Result<Self, SyncError> {
        let (key, transcript) = match role {
            Role::Host => {
                let mut salt = vec![0u8; 16];
                rand::rngs::OsRng.fill_bytes(&mut salt);
                let mut pw = stretch(code, &salt)?;
                let (state, msg_a) =
                    Spake2::<Ed25519Group>::start_a(&Password::new(pw), &Identity::new(ID_A), &Identity::new(ID_B));
                pw.zeroize();
                let hello = Hello { protocol: PROTOCOL_VERSION, salt, spake: msg_a.clone() };
                write_frame(&mut stream, &serde_json::to_vec(&hello).map_err(|_| SyncError::Protocol)?)?;
                let reply: Reply = serde_json::from_slice(&read_frame(&mut stream)?).map_err(|_| SyncError::Protocol)?;
                if reply.protocol != PROTOCOL_VERSION {
                    return Err(SyncError::Version);
                }
                let key = state.finish(&reply.spake).map_err(|_| SyncError::WrongCode)?;
                (key, [msg_a, reply.spake].concat())
            }
            Role::Join => {
                let hello: Hello = serde_json::from_slice(&read_frame(&mut stream)?).map_err(|_| SyncError::Protocol)?;
                if hello.protocol != PROTOCOL_VERSION {
                    // Still answer, so the host can say why.
                    let _ = write_frame(&mut stream, &serde_json::to_vec(&Reply { protocol: PROTOCOL_VERSION, spake: vec![] }).unwrap_or_default());
                    return Err(SyncError::Version);
                }
                let mut pw = stretch(code, &hello.salt)?;
                let (state, msg_b) =
                    Spake2::<Ed25519Group>::start_b(&Password::new(pw), &Identity::new(ID_A), &Identity::new(ID_B));
                pw.zeroize();
                let reply = Reply { protocol: PROTOCOL_VERSION, spake: msg_b.clone() };
                write_frame(&mut stream, &serde_json::to_vec(&reply).map_err(|_| SyncError::Protocol)?)?;
                let key = state.finish(&hello.spake).map_err(|_| SyncError::WrongCode)?;
                (key, [hello.spake, msg_b].concat())
            }
        };

        let hk = Hkdf::<Sha256>::new(Some(&Sha256::digest(&transcript)), &key);
        let mut confirm = [0u8; 32];
        let mut a_to_b = [0u8; 32];
        let mut b_to_a = [0u8; 32];
        hk.expand(b"qasa-sync confirm", &mut confirm).map_err(|_| SyncError::Protocol)?;
        hk.expand(b"qasa-sync a->b", &mut a_to_b).map_err(|_| SyncError::Protocol)?;
        hk.expand(b"qasa-sync b->a", &mut b_to_a).map_err(|_| SyncError::Protocol)?;

        let tag = |who: &[u8]| -> Vec<u8> {
            let mut mac = <Hmac<Sha256> as Mac>::new_from_slice(&confirm).expect("any key length");
            mac.update(who);
            mac.update(&transcript);
            mac.finalize().into_bytes().to_vec()
        };
        let (mine, theirs) = match role {
            Role::Host => (tag(b"A"), tag(b"B")),
            Role::Join => (tag(b"B"), tag(b"A")),
        };
        // The host proves first; the joiner checks before answering, so a joiner with the
        // wrong code never gets a confirm it could use.
        match role {
            Role::Host => {
                write_frame(&mut stream, &mine)?;
                let got = read_frame(&mut stream)?;
                if got.len() != theirs.len() || !bool::from(got.ct_eq(&theirs)) {
                    return Err(SyncError::WrongCode);
                }
            }
            Role::Join => {
                let got = read_frame(&mut stream)?;
                if got.len() != theirs.len() || !bool::from(got.ct_eq(&theirs)) {
                    return Err(SyncError::WrongCode);
                }
                write_frame(&mut stream, &mine)?;
            }
        }
        confirm.zeroize();

        let (send_key, recv_key) = match role {
            Role::Host => (a_to_b, b_to_a),
            Role::Join => (b_to_a, a_to_b),
        };
        Ok(Self { stream, send_key, recv_key, send_counter: 0, recv_counter: 0 })
    }

    pub fn send(&mut self, plaintext: &[u8]) -> Result<(), SyncError> {
        let n = nonce(self.send_counter);
        self.send_counter += 1;
        let sealed = Aes256Gcm::new((&self.send_key).into())
            .encrypt(&Nonce::from(n), plaintext)
            .map_err(|_| SyncError::Protocol)?;
        write_frame(&mut self.stream, &sealed)
    }

    pub fn recv(&mut self) -> Result<Vec<u8>, SyncError> {
        let sealed = read_frame(&mut self.stream)?;
        let n = nonce(self.recv_counter);
        self.recv_counter += 1;
        // A replayed, reordered or altered frame fails here.
        Aes256Gcm::new((&self.recv_key).into()).decrypt(&Nonce::from(n), sealed.as_slice()).map_err(|_| SyncError::Protocol)
    }
}

#[cfg(test)]
pub mod tests {
    use super::*;
    use std::net::{TcpListener, TcpStream};
    use std::thread;

    /// A connected pair of local TCP sockets.
    pub fn socket_pair() -> (TcpStream, TcpStream) {
        let listener = TcpListener::bind("127.0.0.1:0").unwrap();
        let addr = listener.local_addr().unwrap();
        let client = thread::spawn(move || TcpStream::connect(addr).unwrap());
        let (server, _) = listener.accept().unwrap();
        (server, client.join().unwrap())
    }

    #[test]
    fn same_code_opens_a_channel() {
        let (a, b) = socket_pair();
        let host = thread::spawn(move || {
            let mut ch = Channel::open(a, Role::Host, "123456").unwrap();
            ch.send(b"hello from host").unwrap();
            ch.recv().unwrap()
        });
        let mut ch = Channel::open(b, Role::Join, " 123456 ").unwrap();
        assert_eq!(ch.recv().unwrap(), b"hello from host");
        ch.send(b"hi host").unwrap();
        assert_eq!(host.join().unwrap(), b"hi host");
    }

    #[test]
    fn wrong_code_fails_on_both_sides() {
        let (a, b) = socket_pair();
        let host = thread::spawn(move || Channel::open(a, Role::Host, "123456").err());
        let joined = Channel::open(b, Role::Join, "654321").err();
        assert_eq!(joined, Some(SyncError::WrongCode));
        // The joiner hangs up without confirming, so the host sees a closed connection or
        // a wrong code; either way no channel.
        assert!(host.join().unwrap().is_some());
    }

    #[test]
    fn codes_are_six_digits() {
        for _ in 0..50 {
            let c = generate_code();
            assert!(c.len() == 6 && c.chars().all(|ch| ch.is_ascii_digit()));
        }
    }
}
