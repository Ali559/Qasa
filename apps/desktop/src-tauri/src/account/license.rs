//! Ed25519 license tokens. Mirrors iOS `LicenseTokenVerifier`: the header must say EdDSA
//! (no `alg: none` downgrade), the signature must check out against the verifying key,
//! and the token must not be expired. Anything else is treated as no license at all.

use base64::engine::general_purpose::{STANDARD, URL_SAFE_NO_PAD};
use base64::Engine;
use ed25519_dalek::{Signature, Verifier, VerifyingKey};
use serde::Serialize;
use serde_json::Value;

#[derive(Clone, Debug, PartialEq, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct License {
    pub account_id: String,
    pub device_id: String,
    pub tier: String,
    pub device_limit: u32,
    /// Seconds since the epoch.
    pub expires_at: i64,
    pub entitlements: Vec<String>,
}

impl License {
    pub fn is_premium(&self, now: i64) -> bool {
        self.tier.eq_ignore_ascii_case("premium") && self.expires_at > now
    }
}

fn b64url(input: &str) -> Option<Vec<u8>> {
    URL_SAFE_NO_PAD.decode(input.trim_end_matches('=')).ok()
}

/// Decodes the base64 public key from `/.well-known/qasa-license-key`.
pub fn decode_key(b64: &str) -> Option<[u8; 32]> {
    STANDARD.decode(b64).ok()?.try_into().ok()
}

/// Verifies and decodes `token`. `None` on any problem (fail closed).
pub fn verify(token: &str, key: &[u8; 32], now: i64) -> Option<License> {
    let parts: Vec<&str> = token.split('.').collect();
    if parts.len() != 3 {
        return None;
    }
    let header: Value = serde_json::from_slice(&b64url(parts[0])?).ok()?;
    if header.get("alg")?.as_str()? != "EdDSA" {
        return None;
    }
    let signature: [u8; 64] = b64url(parts[2])?.try_into().ok()?;
    let key = VerifyingKey::from_bytes(key).ok()?;
    let signing_input = format!("{}.{}", parts[0], parts[1]);
    key.verify(signing_input.as_bytes(), &Signature::from_bytes(&signature)).ok()?;

    let claims: Value = serde_json::from_slice(&b64url(parts[1])?).ok()?;
    let exp = claims.get("exp")?.as_f64()? as i64;
    if exp <= now {
        return None;
    }
    Some(License {
        account_id: claims.get("sub")?.as_str()?.to_string(),
        device_id: claims.get("did")?.as_str()?.to_string(),
        tier: claims.get("tier")?.as_str()?.to_string(),
        device_limit: claims.get("device_limit").and_then(Value::as_u64).unwrap_or(1).max(1) as u32,
        expires_at: exp,
        entitlements: claims
            .get("entitlements")
            .and_then(Value::as_array)
            .map(|a| a.iter().filter_map(|v| v.as_str().map(str::to_string)).collect())
            .unwrap_or_default(),
    })
}

#[cfg(test)]
pub mod tests {
    use super::*;
    use ed25519_dalek::{Signer, SigningKey};
    use serde_json::json;

    pub fn signing_key() -> SigningKey {
        SigningKey::from_bytes(&[7u8; 32])
    }

    pub fn token(claims: Value, alg: &str) -> String {
        let header = URL_SAFE_NO_PAD.encode(json!({ "alg": alg, "typ": "JWT" }).to_string());
        let body = URL_SAFE_NO_PAD.encode(claims.to_string());
        let sig = signing_key().sign(format!("{header}.{body}").as_bytes());
        format!("{header}.{body}.{}", URL_SAFE_NO_PAD.encode(sig.to_bytes()))
    }

    pub fn claims(tier: &str, exp: i64) -> Value {
        json!({ "sub": "acct", "did": "dev", "tier": tier, "exp": exp, "device_limit": 5, "entitlements": ["vpn"] })
    }

    #[test]
    fn verifies_a_good_token() {
        let key = signing_key().verifying_key().to_bytes();
        let license = verify(&token(claims("premium", 2_000), "EdDSA"), &key, 1_000).unwrap();
        assert!(license.is_premium(1_000));
        assert_eq!((license.device_limit, license.entitlements.len()), (5, 1));
    }

    #[test]
    fn rejects_forged_expired_and_downgraded_tokens() {
        let key = signing_key().verifying_key().to_bytes();
        let good = token(claims("premium", 2_000), "EdDSA");
        // A hand-edited payload with the old signature.
        let parts: Vec<&str> = good.split('.').collect();
        let forged_body = URL_SAFE_NO_PAD.encode(claims("premium", 9_999).to_string());
        assert!(verify(&format!("{}.{}.{}", parts[0], forged_body, parts[2]), &key, 1_000).is_none());
        assert!(verify(&good, &key, 3_000).is_none(), "expired");
        assert!(verify(&token(claims("premium", 2_000), "none"), &key, 1_000).is_none(), "alg none");
        assert!(verify(&good, &[9u8; 32], 1_000).is_none(), "wrong key");
        assert!(verify("a.b", &key, 1_000).is_none());
    }
}
