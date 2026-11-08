//! AES-256-GCM with the iOS `AES.GCM.SealedBox.combined` layout:
//! `nonce (12) ‖ ciphertext ‖ tag (16)`.

use aes_gcm::aead::{Aead, KeyInit};
use aes_gcm::{Aes256Gcm, Nonce};
use rand::RngCore;

use crate::VaultError;

pub const NONCE_LEN: usize = 12;
pub const TAG_LEN: usize = 16;

/// Encrypts with a fresh random nonce.
pub fn encrypt(plaintext: &[u8], key: &[u8; 32]) -> Vec<u8> {
    let mut nonce = [0u8; NONCE_LEN];
    rand::rngs::OsRng.fill_bytes(&mut nonce);
    encrypt_with_nonce(plaintext, key, &nonce)
}

/// Deterministic encryption for test vectors only. Never reuse a nonce with a real key.
pub fn encrypt_with_nonce(plaintext: &[u8], key: &[u8; 32], nonce: &[u8; NONCE_LEN]) -> Vec<u8> {
    let cipher = Aes256Gcm::new(key.into());
    let sealed = cipher
        .encrypt(Nonce::from_slice(nonce), plaintext)
        .expect("AES-GCM encryption of an in-memory buffer cannot fail");
    let mut blob = Vec::with_capacity(NONCE_LEN + sealed.len());
    blob.extend_from_slice(nonce);
    blob.extend_from_slice(&sealed);
    blob
}

pub fn decrypt(blob: &[u8], key: &[u8; 32]) -> Result<Vec<u8>, VaultError> {
    if blob.len() < NONCE_LEN + TAG_LEN {
        return Err(VaultError::InvalidCiphertext);
    }
    let (nonce, sealed) = blob.split_at(NONCE_LEN);
    Aes256Gcm::new(key.into())
        .decrypt(Nonce::from_slice(nonce), sealed)
        .map_err(|_| VaultError::DecryptionFailed)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn round_trip_and_tamper() {
        let key = [3u8; 32];
        let mut blob = encrypt(b"hello", &key);
        assert_eq!(blob.len(), NONCE_LEN + 5 + TAG_LEN);
        assert_eq!(decrypt(&blob, &key).unwrap(), b"hello");
        let last = blob.len() - 1;
        blob[last] ^= 1;
        assert_eq!(decrypt(&blob, &key), Err(VaultError::DecryptionFailed));
        assert_eq!(decrypt(&[0u8; 27], &key), Err(VaultError::InvalidCiphertext));
    }
}
