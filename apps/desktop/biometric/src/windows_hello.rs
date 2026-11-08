//! Windows Hello through `KeyCredentialManager`. The Hello key (RSA, PKCS#1 v1.5, so the
//! signature is deterministic) signs a random challenge; the signature is the key
//! material. NOT YET RUN on Windows: written and type-checked from macOS only.

use rand::RngCore;
use windows::core::HSTRING;
use windows::Security::Credentials::{
    KeyCredentialCreationOption, KeyCredentialManager, KeyCredentialStatus,
};
use windows::Security::Cryptography::CryptographicBuffer;

use super::{Error, Kind};

const CREDENTIAL: &str = "QasaDesktopUnlock";

fn failed(e: windows::core::Error) -> Error {
    Error::Failed(e.message().to_string())
}

fn map_status(status: KeyCredentialStatus) -> Result<(), Error> {
    match status {
        KeyCredentialStatus::Success => Ok(()),
        KeyCredentialStatus::UserCanceled => Err(Error::Cancelled),
        KeyCredentialStatus::NotFound => Err(Error::Invalidated),
        KeyCredentialStatus::UserPrefersPassword => Err(Error::Cancelled),
        KeyCredentialStatus::SecurityDeviceLocked => Err(Error::Unavailable),
        _ => Err(Error::Failed(format!("Windows Hello status {}", status.0))),
    }
}

pub fn kind() -> Kind {
    match KeyCredentialManager::IsSupportedAsync().and_then(|op| op.get()) {
        Ok(true) => Kind::WindowsHello,
        _ => Kind::None,
    }
}

fn sign(credential: &windows::Security::Credentials::KeyCredential, challenge: &[u8]) -> Result<[u8; 32], Error> {
    let buffer = CryptographicBuffer::CreateFromByteArray(challenge).map_err(failed)?;
    let result = credential.RequestSignAsync(&buffer).and_then(|op| op.get()).map_err(failed)?;
    map_status(result.Status().map_err(failed)?)?;
    let signature = result.Result().map_err(failed)?;
    let mut bytes = windows::core::Array::<u8>::new();
    CryptographicBuffer::CopyToByteArray(&signature, &mut bytes).map_err(failed)?;
    // Reduce the RSA signature to 32 bytes of key material.
    use sha2::{Digest, Sha256};
    Ok(Sha256::digest(&bytes[..]).into())
}

/// Creates (or replaces) the Hello key — Windows asks for Hello here — and signs a fresh
/// challenge.
pub fn create() -> Result<(Vec<u8>, Vec<u8>, [u8; 32]), Error> {
    let name = HSTRING::from(CREDENTIAL);
    let created = KeyCredentialManager::RequestCreateAsync(&name, KeyCredentialCreationOption::ReplaceExisting)
        .and_then(|op| op.get())
        .map_err(failed)?;
    map_status(created.Status().map_err(failed)?)?;
    let credential = created.Credential().map_err(failed)?;
    let mut challenge = vec![0u8; 32];
    rand::rngs::OsRng.fill_bytes(&mut challenge);
    let material = sign(&credential, &challenge)?;
    Ok((CREDENTIAL.as_bytes().to_vec(), challenge, material))
}

/// Opens the Hello key and signs the stored challenge (Windows shows the Hello prompt;
/// `reason` is not shown, Windows supplies its own text).
pub fn derive(key_ref: &[u8], peer: &[u8], _reason: &str) -> Result<[u8; 32], Error> {
    let name = HSTRING::from(String::from_utf8_lossy(key_ref).as_ref());
    let opened = KeyCredentialManager::OpenAsync(&name).and_then(|op| op.get()).map_err(failed)?;
    map_status(opened.Status().map_err(failed)?)?;
    sign(&opened.Credential().map_err(failed)?, peer)
}
