//! Touch ID through the Secure Enclave (swift/Sources/QasaBiometric).

use std::ffi::CString;

use p256::ecdh::EphemeralSecret;
use p256::elliptic_curve::sec1::ToEncodedPoint;
use p256::PublicKey;

use super::{Error, Kind};

extern "C" {
    fn qasa_bio_available() -> i32;
    fn qasa_se_create(key_out: *mut u8, key_cap: usize, key_len: *mut usize, pub_out: *mut u8, pub_cap: usize, pub_len: *mut usize) -> i32;
    fn qasa_se_agree(
        key: *const u8,
        key_len: usize,
        peer: *const u8,
        peer_len: usize,
        reason: *const std::ffi::c_char,
        out: *mut u8,
        out_cap: usize,
        out_len: *mut usize,
    ) -> i32;
}

fn status(code: i32) -> Result<(), Error> {
    match code {
        0 => Ok(()),
        1 => Err(Error::Cancelled),
        2 => Err(Error::Invalidated),
        5 => Err(Error::Unavailable),
        4 => Err(Error::Failed("buffer too small".into())),
        _ => Err(Error::Failed(format!("Secure Enclave error {code}"))),
    }
}

pub fn kind() -> Kind {
    // SAFETY: no arguments; the Swift side only queries LocalAuthentication.
    if unsafe { qasa_bio_available() } == 1 {
        Kind::TouchId
    } else {
        Kind::None
    }
}

/// A new Enclave key plus the ECDH secret with a fresh ephemeral key, computed in
/// software from the Enclave key's public half (no prompt).
pub fn create() -> Result<(Vec<u8>, Vec<u8>, [u8; 32]), Error> {
    let mut key = vec![0u8; 4096];
    let mut public = vec![0u8; 256];
    let (mut key_len, mut pub_len) = (0usize, 0usize);
    // SAFETY: buffers are valid for their stated capacities; Swift writes at most that many
    // bytes and reports the length.
    status(unsafe { qasa_se_create(key.as_mut_ptr(), key.len(), &mut key_len, public.as_mut_ptr(), public.len(), &mut pub_len) })?;
    key.truncate(key_len);
    public.truncate(pub_len);

    let enclave_public = PublicKey::from_sec1_bytes(&public).map_err(|_| Error::Failed("bad Enclave public key".into()))?;
    let ephemeral = EphemeralSecret::random(&mut rand::rngs::OsRng);
    let shared = ephemeral.diffie_hellman(&enclave_public);
    let mut material = [0u8; 32];
    material.copy_from_slice(shared.raw_secret_bytes());
    let peer = ephemeral.public_key().to_encoded_point(false).as_bytes().to_vec();
    Ok((key, peer, material))
}

/// The same ECDH secret, computed inside the Enclave after Touch ID.
pub fn derive(key_ref: &[u8], peer: &[u8], reason: &str) -> Result<[u8; 32], Error> {
    let reason = CString::new(reason.replace('\0', "")).expect("no interior NULs");
    let mut out = [0u8; 32];
    let mut out_len = 0usize;
    // SAFETY: all pointers are valid for the given lengths for the duration of the call.
    status(unsafe {
        qasa_se_agree(key_ref.as_ptr(), key_ref.len(), peer.as_ptr(), peer.len(), reason.as_ptr(), out.as_mut_ptr(), out.len(), &mut out_len)
    })?;
    if out_len != 32 {
        return Err(Error::Failed("unexpected shared secret length".into()));
    }
    Ok(out)
}
