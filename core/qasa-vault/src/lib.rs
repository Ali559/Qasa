//! The Qasa vault core shared by the desktop app (and later, through UniFFI, the phones).
//!
//! Everything here must stay byte-for-byte compatible with the iOS app, which is the
//! source of truth (`apps/ios/qasa_ios/Services/Vault/`), and with Android
//! (`apps/android/.../vault/crypto/`). `tests/vectors.json` pins the shared behaviour;
//! the iOS and Android test suites check the same file.

mod aead;
pub mod country;
pub mod csv;
mod error;
pub mod generator;
pub mod health;
mod kdf;
pub mod payload;
mod search;
pub mod totp;

pub use aead::{decrypt, encrypt, NONCE_LEN, TAG_LEN};
pub use error::VaultError;
pub use kdf::{
    decode_kdf_version, derive_root_key, encode_kdf_version, generate_salt, RootKey, VaultKeys,
    KDF_VERSION, MIN_SALT_LEN, PBKDF2_ITERATIONS, ROOT_KEY_LEN, SALT_LEN,
};
pub use search::{hash_token, token_hashes, tokenize};

#[doc(hidden)]
pub use aead::encrypt_with_nonce;
