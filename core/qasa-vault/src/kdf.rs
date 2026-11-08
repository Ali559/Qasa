//! Master password → root key → subkeys. Mirrors iOS `MasterKeyDeriver` and the KDF
//! constants in `VaultCrypto`.

use hkdf::Hkdf;
use rand::RngCore;
use sha2::{Digest, Sha256};
use subtle::ConstantTimeEq;
use zeroize::{Zeroize, ZeroizeOnDrop};

use crate::VaultError;

/// The KDF this build writes. Stored next to the salt as a big-endian `Int32`.
pub const KDF_VERSION: u32 = 1;
pub const PBKDF2_ITERATIONS: u32 = 310_000;
pub const SALT_LEN: usize = 32;
/// iOS refuses anything shorter (`VaultCryptoError.invalidSalt`).
pub const MIN_SALT_LEN: usize = 16;
pub const ROOT_KEY_LEN: usize = 32;

const ENC_INFO: &[u8] = b"qasa-enc";
const SEARCH_INFO: &[u8] = b"qasa-search";

/// A fresh random salt for a new vault.
pub fn generate_salt() -> [u8; SALT_LEN] {
    let mut salt = [0u8; SALT_LEN];
    rand::rngs::OsRng.fill_bytes(&mut salt);
    salt
}

/// PBKDF2-HMAC-SHA256 over the password's UTF-8 bytes, exactly as typed (no trimming, no
/// normalization).
pub fn derive_root_key(password: &str, salt: &[u8]) -> Result<RootKey, VaultError> {
    if salt.len() < MIN_SALT_LEN {
        return Err(VaultError::InvalidSalt);
    }
    let mut key = [0u8; ROOT_KEY_LEN];
    pbkdf2::pbkdf2_hmac::<Sha256>(password.as_bytes(), salt, PBKDF2_ITERATIONS, &mut key);
    Ok(RootKey(key))
}

/// Big-endian bytes for the stored KDF version.
pub fn encode_kdf_version(version: u32) -> [u8; 4] {
    // iOS writes `Int32(v).bigEndian`; versions are small positive numbers.
    (version as i32).to_be_bytes()
}

/// The stored KDF version. A missing or malformed value is a legacy vault (version 1),
/// as on iOS. A version newer than this build is refused rather than guessed at.
pub fn decode_kdf_version(stored: Option<&[u8]>) -> Result<u32, VaultError> {
    let version = match stored {
        Some(bytes) if bytes.len() == 4 => {
            i32::from_be_bytes([bytes[0], bytes[1], bytes[2], bytes[3]]).max(1) as u32
        }
        _ => 1,
    };
    if version > KDF_VERSION {
        return Err(VaultError::UnsupportedKdfVersion(version));
    }
    Ok(version)
}

/// The 32-byte root key. Zeroed when dropped.
#[derive(Zeroize, ZeroizeOnDrop)]
pub struct RootKey([u8; ROOT_KEY_LEN]);

impl RootKey {
    pub fn from_bytes(bytes: [u8; ROOT_KEY_LEN]) -> Self {
        Self(bytes)
    }

    pub fn as_bytes(&self) -> &[u8; ROOT_KEY_LEN] {
        &self.0
    }

    /// `SHA-256(root key)`, stored to check the master password.
    pub fn verifier(&self) -> [u8; 32] {
        Sha256::digest(self.0).into()
    }

    /// Constant-time comparison with a stored verifier.
    pub fn matches_verifier(&self, stored: &[u8]) -> bool {
        let ours = self.verifier();
        stored.len() == ours.len() && bool::from(ours.ct_eq(stored))
    }

    /// HKDF-SHA256 with an empty salt, infos `qasa-enc` and `qasa-search`.
    pub fn subkeys(&self) -> VaultKeys {
        let hk = Hkdf::<Sha256>::new(None, &self.0);
        let mut enc = [0u8; 32];
        let mut search = [0u8; 32];
        hk.expand(ENC_INFO, &mut enc).expect("32 bytes is a valid HKDF length");
        hk.expand(SEARCH_INFO, &mut search).expect("32 bytes is a valid HKDF length");
        VaultKeys { enc, search }
    }
}

/// The AES-256-GCM data key and the blind-index key. Zeroed when dropped.
#[derive(Zeroize, ZeroizeOnDrop)]
pub struct VaultKeys {
    enc: [u8; 32],
    search: [u8; 32],
}

impl VaultKeys {
    pub fn enc(&self) -> &[u8; 32] {
        &self.enc
    }

    pub fn search(&self) -> &[u8; 32] {
        &self.search
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn short_salt_is_refused() {
        assert_eq!(derive_root_key("pw", &[0u8; 15]).err(), Some(VaultError::InvalidSalt));
    }

    #[test]
    fn kdf_version_round_trips_and_refuses_newer() {
        assert_eq!(encode_kdf_version(1), [0, 0, 0, 1]);
        assert_eq!(decode_kdf_version(Some(&encode_kdf_version(1))), Ok(1));
        assert_eq!(decode_kdf_version(None), Ok(1));
        assert_eq!(decode_kdf_version(Some(&[1, 2])), Ok(1));
        assert_eq!(
            decode_kdf_version(Some(&encode_kdf_version(2))),
            Err(VaultError::UnsupportedKdfVersion(2))
        );
    }

    #[test]
    fn verifier_compare_rejects_wrong_length() {
        let key = RootKey::from_bytes([7u8; 32]);
        assert!(key.matches_verifier(&key.verifier()));
        assert!(!key.matches_verifier(&key.verifier()[..31]));
        assert!(!key.matches_verifier(&[0u8; 32]));
    }
}
