use std::time::{Duration, SystemTime, UNIX_EPOCH};

use base64::{engine::general_purpose::STANDARD as B64, Engine};
use ed25519_dalek::{SigningKey, VerifyingKey};
use jsonwebtoken::{decode, encode, Algorithm, DecodingKey, EncodingKey, Header, Validation};
use rand::rngs::OsRng;
use serde::{Deserialize, Serialize};
use uuid::Uuid;

use crate::{new_id, AccountTier, CoreError, Entitlements};

const LICENSE_TTL: Duration = Duration::from_secs(30 * 24 * 60 * 60);

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct LicenseClaims {
    pub sub: String,
    pub did: String,
    pub jti: String,
    pub tier: String,
    pub entitlements: Vec<String>,
    pub device_limit: u32,
    pub iat: u64,
    pub exp: u64,
}

#[derive(Clone)]
pub struct LicenseKeyPair {
    signing: SigningKey,
    verifying: VerifyingKey,
}

impl LicenseKeyPair {
    pub fn generate() -> Self {
        let signing = SigningKey::generate(&mut OsRng);
        let verifying = signing.verifying_key();
        Self { signing, verifying }
    }

    pub fn from_private_key_b64(value: &str) -> Result<Self, CoreError> {
        let bytes = B64
            .decode(value)
            .map_err(|e| CoreError::Token(e.to_string()))?;
        let signing = SigningKey::from_bytes(
            bytes
                .as_slice()
                .try_into()
                .map_err(|_| CoreError::Token("invalid private key length".into()))?,
        );
        Ok(Self {
            verifying: signing.verifying_key(),
            signing,
        })
    }

    pub fn private_key_b64(&self) -> String {
        B64.encode(self.signing.to_bytes())
    }

    pub fn public_key_b64(&self) -> String {
        B64.encode(self.verifying.to_bytes())
    }
}

#[derive(Clone)]
pub struct LicenseTokenIssuer {
    keys: LicenseKeyPair,
}

impl LicenseTokenIssuer {
    pub fn new(keys: LicenseKeyPair) -> Self {
        Self { keys }
    }

    pub fn public_key_b64(&self) -> String {
        self.keys.public_key_b64()
    }

    pub fn issue(
        &self,
        account_id: Uuid,
        device_id: Uuid,
        entitlements: &Entitlements,
    ) -> Result<(String, String), CoreError> {
        let now = SystemTime::now()
            .duration_since(UNIX_EPOCH)
            .map_err(|e| CoreError::Token(e.to_string()))?;
        let jti = new_id().to_string();
        let claims = LicenseClaims {
            sub: account_id.to_string(),
            did: device_id.to_string(),
            jti: jti.clone(),
            tier: entitlements.tier.as_str().into(),
            entitlements: entitlement_names(entitlements),
            device_limit: entitlements.device_limit,
            iat: now.as_secs(),
            exp: (now + LICENSE_TTL).as_secs(),
        };

        let token = encode(
            &Header::new(Algorithm::EdDSA),
            &claims,
            &EncodingKey::from_ed_der(&private_key_der(&self.keys.signing)),
        )
        .map_err(|e| CoreError::Token(e.to_string()))?;

        Ok((token, jti))
    }

    pub fn verify(&self, token: &str) -> Result<LicenseClaims, CoreError> {
        let mut validation = Validation::new(Algorithm::EdDSA);
        validation.validate_exp = true;
        // jsonwebtoken passes this key straight to ring; Ed25519 expects 32 raw bytes,
        // not SPKI DER (see public_key_der — useful for PEM export only).
        let key = DecodingKey::from_ed_der(self.keys.verifying.to_bytes().as_ref());
        decode::<LicenseClaims>(token, &key, &validation)
            .map(|data| data.claims)
            .map_err(|e| CoreError::Token(e.to_string()))
    }
}

fn entitlement_names(entitlements: &Entitlements) -> Vec<String> {
    // Always lists multi_device; enforce capacity via `device_limit`, not this name alone.
    let mut names = vec!["multi_device".into()];
    if entitlements.breach {
        names.push("breach".into());
    }
    if entitlements.vpn {
        names.push("vpn".into());
    }
    names
}

fn private_key_der(signing: &SigningKey) -> Vec<u8> {
    // PKCS#8 DER for Ed25519 private key
    let seed = signing.to_bytes();
    let mut der = vec![
        0x30, 0x2e, 0x02, 0x01, 0x00, 0x30, 0x05, 0x06, 0x03, 0x2b, 0x65, 0x70, 0x04, 0x22,
        0x04, 0x20,
    ];
    der.extend_from_slice(&seed);
    der
}

impl LicenseClaims {
    pub fn account_id(&self) -> Result<Uuid, CoreError> {
        Uuid::parse_str(&self.sub).map_err(|e| CoreError::Token(e.to_string()))
    }

    pub fn device_id(&self) -> Result<Uuid, CoreError> {
        Uuid::parse_str(&self.did).map_err(|e| CoreError::Token(e.to_string()))
    }

    pub fn tier(&self) -> AccountTier {
        AccountTier::parse(&self.tier)
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use uuid::Uuid;

    #[test]
    fn license_token_round_trips() {
        let keys = LicenseKeyPair::generate();
        let issuer = LicenseTokenIssuer::new(keys);
        let account_id = Uuid::now_v7();
        let device_id = Uuid::now_v7();
        let entitlements = Entitlements::for_tier(AccountTier::Free);

        let (token, _) = issuer
            .issue(account_id, device_id, &entitlements)
            .expect("issue");
        let claims = issuer.verify(&token).expect("verify");

        assert_eq!(claims.account_id().unwrap(), account_id);
        assert_eq!(claims.device_id().unwrap(), device_id);
        assert_eq!(claims.tier(), AccountTier::Free);
    }
}
