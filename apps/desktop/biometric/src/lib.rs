//! Seals a 32-byte vault secret behind the computer's biometrics, so Qasa can unlock with
//! Touch ID or Windows Hello instead of the main password (plan §11). Linux has none.
//!
//! The secret is encrypted with AES-256-GCM under a wrapping key that only the platform
//! can produce, and only after the user authenticates:
//! - **macOS:** ECDH between a Secure Enclave key created with `.biometryCurrentSet` and an
//!   ephemeral software key (whose public half is stored). The Enclave key never leaves the
//!   chip; changing the enrolled fingerprints destroys it, so the seal fails closed.
//! - **Windows:** a Windows Hello key signs a stored random challenge; the deterministic
//!   signature is the key material. Removing or resetting Hello breaks it.
//!
//! Unlike Android's old design (design.md §12.7), the sealed value is never the master
//! password itself.

use aes_gcm::aead::{Aead, KeyInit};
use aes_gcm::{Aes256Gcm, Nonce};
use hkdf::Hkdf;
use rand::RngCore;
use serde::{Deserialize, Serialize};
use sha2::Sha256;
use zeroize::Zeroize;

#[cfg(target_os = "macos")]
mod macos;
#[cfg(target_os = "macos")]
use macos as platform;

#[cfg(windows)]
mod windows_hello;
#[cfg(windows)]
use windows_hello as platform;

#[cfg(not(any(target_os = "macos", windows)))]
mod platform {
    use super::{Error, Kind};
    pub fn kind() -> Kind {
        Kind::None
    }
    pub fn create() -> Result<(Vec<u8>, Vec<u8>, [u8; 32]), Error> {
        Err(Error::Unavailable)
    }
    pub fn derive(_key_ref: &[u8], _peer: &[u8], _reason: &str) -> Result<[u8; 32], Error> {
        Err(Error::Unavailable)
    }
}

/// What this computer offers.
#[derive(Serialize, Clone, Copy, Debug, PartialEq, Eq)]
#[serde(rename_all = "camelCase")]
pub enum Kind {
    None,
    TouchId,
    WindowsHello,
}

#[derive(Debug, thiserror::Error, PartialEq, Eq)]
pub enum Error {
    /// No biometric hardware, nothing enrolled, or locked out.
    #[error("biometric unlock is not available")]
    Unavailable,
    /// The user dismissed the prompt.
    #[error("cancelled")]
    Cancelled,
    /// The platform key is gone or no longer valid (fingerprints changed, Hello reset).
    /// The seal must be thrown away and made again after a password unlock.
    #[error("the biometric key is no longer valid")]
    Invalidated,
    #[error("biometric unlock failed: {0}")]
    Failed(String),
}

/// A sealed secret, safe to store on disk: useless without this computer's platform key
/// and the user's biometrics.
#[derive(Serialize, Deserialize, Clone, Debug, PartialEq, Eq)]
pub struct Sealed {
    /// `macos-se` or `windows-hello`; a seal from another platform is refused.
    pub platform: String,
    pub version: u32,
    /// macOS: the Secure Enclave key blob. Windows: the Hello credential name.
    pub key_ref: Vec<u8>,
    /// macOS: the ephemeral public key (x9.63). Windows: the random challenge.
    pub peer: Vec<u8>,
    /// `nonce (12) ‖ ciphertext ‖ tag (16)` of the secret.
    pub ciphertext: Vec<u8>,
}

const VERSION: u32 = 1;

fn platform_name() -> &'static str {
    if cfg!(target_os = "macos") {
        "macos-se"
    } else if cfg!(windows) {
        "windows-hello"
    } else {
        "none"
    }
}

/// HKDF-SHA256 from the platform's key material to the AES wrapping key.
fn wrapping_key(material: &[u8]) -> [u8; 32] {
    let mut out = [0u8; 32];
    Hkdf::<Sha256>::new(Some(b"qasa-biometric"), material)
        .expand(b"qasa-biometric-unlock-v1", &mut out)
        .expect("32 bytes is a valid HKDF length");
    out
}

pub fn kind() -> Kind {
    platform::kind()
}

/// Seals `secret`. On macOS this needs no Touch ID; on Windows, creating the Hello key
/// asks for Hello once.
pub fn seal(secret: &[u8; 32]) -> Result<Sealed, Error> {
    let (key_ref, peer, mut material) = platform::create()?;
    let mut key = wrapping_key(&material);
    material.zeroize();
    let mut nonce = [0u8; 12];
    rand::rngs::OsRng.fill_bytes(&mut nonce);
    let sealed = Aes256Gcm::new((&key).into())
        .encrypt(&Nonce::from(nonce), secret.as_slice())
        .map_err(|_| Error::Failed("encrypt".into()))?;
    key.zeroize();
    let mut ciphertext = nonce.to_vec();
    ciphertext.extend_from_slice(&sealed);
    Ok(Sealed { platform: platform_name().into(), version: VERSION, key_ref, peer, ciphertext })
}

/// Shows the biometric prompt with `reason` and returns the secret.
pub fn unseal(sealed: &Sealed, reason: &str) -> Result<[u8; 32], Error> {
    if sealed.platform != platform_name() || sealed.version != VERSION || sealed.ciphertext.len() < 12 + 16 {
        return Err(Error::Invalidated);
    }
    let mut material = platform::derive(&sealed.key_ref, &sealed.peer, reason)?;
    let mut key = wrapping_key(&material);
    material.zeroize();
    let (nonce, body) = sealed.ciphertext.split_at(12);
    let nonce: [u8; 12] = nonce.try_into().expect("split at 12");
    let plain = Aes256Gcm::new((&key).into()).decrypt(&Nonce::from(nonce), body);
    key.zeroize();
    let mut plain = plain.map_err(|_| Error::Invalidated)?;
    let out: [u8; 32] = plain.as_slice().try_into().map_err(|_| Error::Invalidated)?;
    plain.zeroize();
    Ok(out)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn foreign_or_damaged_seals_are_refused() {
        let s = Sealed { platform: "elsewhere".into(), version: 1, key_ref: vec![], peer: vec![], ciphertext: vec![0; 40] };
        assert_eq!(unseal(&s, "x"), Err(Error::Invalidated));
        let s = Sealed { platform: platform_name().into(), version: 99, ..s };
        assert_eq!(unseal(&s, "x"), Err(Error::Invalidated));
    }

    #[test]
    fn sealed_round_trips_as_json() {
        let s = Sealed { platform: "macos-se".into(), version: 1, key_ref: vec![1, 2], peer: vec![3], ciphertext: vec![4; 30] };
        let json = serde_json::to_string(&s).unwrap();
        assert_eq!(serde_json::from_str::<Sealed>(&json).unwrap(), s);
    }

    /// Creates a real Secure Enclave key (no prompt), then asks for Touch ID once.
    /// `cargo test touch_id -- --ignored --nocapture`, with a finger ready.
    #[cfg(target_os = "macos")]
    #[test]
    #[ignore]
    fn touch_id_round_trip() {
        assert_eq!(kind(), Kind::TouchId, "no Touch ID on this Mac");
        let secret = [42u8; 32];
        let sealed = seal(&secret).expect("seal");
        println!("sealed with a {}-byte Enclave key blob; touch the sensor…", sealed.key_ref.len());
        assert_eq!(unseal(&sealed, "Qasa test: unlock with Touch ID").expect("unseal"), secret);
    }
}
