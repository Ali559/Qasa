//! Bridge crypto (plan §8.2.1): P-256 ECDH pairing with a 6-digit matching code, then
//! AES-256-GCM for every request and reply, bound to the client id as AAD.

use aes_gcm::aead::{Aead, KeyInit, Payload};
use aes_gcm::{Aes256Gcm, Nonce};
use base64::engine::general_purpose::STANDARD as B64;
use base64::Engine;
use hkdf::Hkdf;
use p256::ecdh::EphemeralSecret;
use p256::elliptic_curve::sec1::ToEncodedPoint;
use p256::PublicKey;
use rand::rngs::OsRng;
use rand::RngCore;
use sha2::Sha256;

pub const KEY_LEN: usize = 32;
const NONCE_LEN: usize = 12;
const INFO: &[u8] = b"qasa-bridge-v1";

/// What both sides derive from one pairing.
#[derive(Debug, PartialEq, Eq)]
pub struct Pairing {
    pub key: [u8; KEY_LEN],
    /// Six digits, shown on both screens.
    pub code: String,
}

/// The desktop side of a pairing: a fresh key pair, its raw public key (65 bytes,
/// uncompressed SEC1) and the derived pairing. `None` if the extension's key is invalid.
pub fn pair_with(ext_pub_raw: &[u8]) -> Option<(Vec<u8>, Pairing)> {
    let ext_pub = PublicKey::from_sec1_bytes(ext_pub_raw).ok()?;
    let secret = EphemeralSecret::random(&mut OsRng);
    let desk_pub = secret.public_key().to_encoded_point(false).as_bytes().to_vec();
    let shared = secret.diffie_hellman(&ext_pub);
    let pairing = derive(shared.raw_secret_bytes(), ext_pub_raw, &desk_pub);
    Some((desk_pub, pairing))
}

/// `HKDF-SHA256(ikm = shared, salt = extPub ‖ deskPub, info = "qasa-bridge-v1")`, 36 bytes:
/// the AES key, then a big-endian u32 whose value mod 1 000 000 is the code.
pub fn derive(shared: &[u8], ext_pub: &[u8], desk_pub: &[u8]) -> Pairing {
    let salt = [ext_pub, desk_pub].concat();
    let mut okm = [0u8; KEY_LEN + 4];
    Hkdf::<Sha256>::new(Some(&salt), shared).expand(INFO, &mut okm).expect("36 bytes is a valid HKDF length");
    let mut key = [0u8; KEY_LEN];
    key.copy_from_slice(&okm[..KEY_LEN]);
    let n = u32::from_be_bytes(okm[KEY_LEN..].try_into().unwrap());
    Pairing { key, code: format!("{:06}", n % 1_000_000) }
}

/// Encrypts a reply: base64 nonce and base64 ciphertext with its tag.
pub fn seal(key: &[u8; KEY_LEN], aad: &[u8], plaintext: &[u8]) -> (String, String) {
    let mut nonce = [0u8; NONCE_LEN];
    OsRng.fill_bytes(&mut nonce);
    let cipher = Aes256Gcm::new(key.into());
    let ct = cipher.encrypt(Nonce::from_slice(&nonce), Payload { msg: plaintext, aad }).expect("AES-GCM encryption cannot fail");
    (B64.encode(nonce), B64.encode(ct))
}

/// Decrypts a request. `None` for bad base64, a wrong nonce length or a failed tag.
pub fn open(key: &[u8; KEY_LEN], aad: &[u8], nonce_b64: &str, ct_b64: &str) -> Option<Vec<u8>> {
    let nonce = B64.decode(nonce_b64).ok()?;
    if nonce.len() != NONCE_LEN {
        return None;
    }
    let ct = B64.decode(ct_b64).ok()?;
    Aes256Gcm::new(key.into()).decrypt(Nonce::from_slice(&nonce), Payload { msg: &ct, aad }).ok()
}

#[cfg(test)]
mod tests {
    use super::*;
    use p256::SecretKey;

    #[test]
    fn both_sides_derive_the_same_pairing() {
        let ext = EphemeralSecret::random(&mut OsRng);
        let ext_pub = ext.public_key().to_encoded_point(false).as_bytes().to_vec();
        let (desk_pub, desk) = pair_with(&ext_pub).unwrap();
        let shared = ext.diffie_hellman(&PublicKey::from_sec1_bytes(&desk_pub).unwrap());
        let mine = derive(shared.raw_secret_bytes(), &ext_pub, &desk_pub);
        assert_eq!(mine, desk);
        assert_eq!(mine.code.len(), 6);
    }

    #[test]
    fn rejects_a_bad_public_key() {
        assert!(pair_with(&[4u8; 65]).is_none());
        assert!(pair_with(b"short").is_none());
    }

    #[test]
    fn seal_and_open_round_trip_and_bind_the_aad() {
        let key = [7u8; KEY_LEN];
        let (n, c) = seal(&key, b"client-a", b"{\"op\":\"status\"}");
        assert_eq!(open(&key, b"client-a", &n, &c).unwrap(), b"{\"op\":\"status\"}");
        assert!(open(&key, b"client-b", &n, &c).is_none());
        assert!(open(&[8u8; KEY_LEN], b"client-a", &n, &c).is_none());
    }

    /// The vector the extension's WebCrypto code writes (apps/extension/test/vectors).
    #[test]
    fn matches_the_extension_vector() {
        let path = concat!(env!("CARGO_MANIFEST_DIR"), "/../../extension/test/vectors/bridge-pairing.json");
        let Ok(raw) = std::fs::read_to_string(path) else {
            eprintln!("skipped: {path} not found");
            return;
        };
        let v: serde_json::Value = serde_json::from_str(&raw).unwrap();
        let b = |k: &str| B64.decode(v[k].as_str().unwrap_or_else(|| panic!("missing {k}"))).unwrap();
        let desk = SecretKey::from_slice(&pkcs8_or_raw_scalar(&b("deskPriv"))).unwrap();
        let ext_pub = b("extPub");
        let desk_pub = b("deskPub");
        assert_eq!(desk.public_key().to_encoded_point(false).as_bytes(), &desk_pub[..]);
        let shared = p256::ecdh::diffie_hellman(desk.to_nonzero_scalar(), PublicKey::from_sec1_bytes(&ext_pub).unwrap().as_affine());
        let pairing = derive(shared.raw_secret_bytes(), &ext_pub, &desk_pub);
        assert_eq!(B64.encode(pairing.key), v["key"].as_str().unwrap());
        assert_eq!(pairing.code, v["code"].as_str().unwrap());

        let aes = &v["aesGcm"];
        let key: [u8; KEY_LEN] = B64.decode(aes["key"].as_str().unwrap()).unwrap().try_into().unwrap();
        let aad = aes["aad"].as_str().unwrap().as_bytes();
        let plain = open(&key, aad, aes["nonce"].as_str().unwrap(), aes["ciphertext"].as_str().unwrap()).unwrap();
        assert_eq!(String::from_utf8(plain).unwrap(), aes["plaintext"].as_str().unwrap());
    }

    /// WebCrypto exports private keys as PKCS#8 (or JWK `d`); the scalar is the last
    /// 32 bytes of the inner ECPrivateKey's octet string, which for a P-256 PKCS#8 export
    /// sits at a fixed offset. A bare 32-byte scalar passes through.
    fn pkcs8_or_raw_scalar(bytes: &[u8]) -> Vec<u8> {
        if bytes.len() == 32 {
            return bytes.to_vec();
        }
        // ECPrivateKey: 30 .. 02 01 01 04 20 <32-byte scalar>
        let marker = [0x02, 0x01, 0x01, 0x04, 0x20];
        let at = bytes.windows(marker.len()).position(|w| w == marker).expect("PKCS#8 P-256 key");
        bytes[at + marker.len()..at + marker.len() + 32].to_vec()
    }
}
