use thiserror::Error;

#[derive(Debug, Error, PartialEq, Eq)]
pub enum VaultError {
    /// The stored salt is shorter than [`crate::MIN_SALT_LEN`]: the key material is corrupt.
    #[error("vault key material is corrupt")]
    InvalidSalt,
    /// The vault was written by a newer Qasa with a KDF this build doesn't know.
    #[error("vault was created by a newer version of Qasa (KDF version {0})")]
    UnsupportedKdfVersion(u32),
    /// Too short to hold a nonce and a tag.
    #[error("invalid ciphertext")]
    InvalidCiphertext,
    /// Wrong key, or the blob was tampered with.
    #[error("decryption failed")]
    DecryptionFailed,
}
