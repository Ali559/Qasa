use std::time::{Duration, SystemTime, UNIX_EPOCH};

use jsonwebtoken::{decode, encode, Algorithm, DecodingKey, EncodingKey, Header, Validation};
use serde::{Deserialize, Serialize};
use uuid::Uuid;

use crate::{new_id, CoreError};

const ADMIN_TTL: Duration = Duration::from_secs(8 * 60 * 60);

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct AdminClaims {
    pub sub: String,
    pub email: String,
    pub role: String,
    pub jti: String,
    pub iat: u64,
    pub exp: u64,
}

#[derive(Clone)]
pub struct AdminTokenIssuer {
    secret: String,
}

impl AdminTokenIssuer {
    pub fn new(secret: impl Into<String>) -> Self {
        Self {
            secret: secret.into(),
        }
    }

    pub fn issue(&self, admin_id: Uuid, email: &str, role: &str) -> Result<String, CoreError> {
        let now = SystemTime::now()
            .duration_since(UNIX_EPOCH)
            .map_err(|e| CoreError::Token(e.to_string()))?;
        let claims = AdminClaims {
            sub: admin_id.to_string(),
            email: email.to_string(),
            role: role.to_string(),
            jti: new_id().to_string(),
            iat: now.as_secs(),
            exp: (now + ADMIN_TTL).as_secs(),
        };

        encode(
            &Header::new(Algorithm::HS256),
            &claims,
            &EncodingKey::from_secret(self.secret.as_bytes()),
        )
        .map_err(|e| CoreError::Token(e.to_string()))
    }

    pub fn verify(&self, token: &str) -> Result<AdminClaims, CoreError> {
        decode::<AdminClaims>(
            token,
            &DecodingKey::from_secret(self.secret.as_bytes()),
            &Validation::new(Algorithm::HS256),
        )
        .map(|data| data.claims)
        .map_err(|e| CoreError::Token(e.to_string()))
    }
}

impl AdminClaims {
    pub fn admin_id(&self) -> Result<Uuid, CoreError> {
        Uuid::parse_str(&self.sub).map_err(|e| CoreError::Token(e.to_string()))
    }
}

pub fn hash_password(password: &str) -> Result<String, CoreError> {
    use argon2::{
        password_hash::{rand_core::OsRng, PasswordHasher, SaltString},
        Argon2,
    };

    let salt = SaltString::generate(&mut OsRng);
    Argon2::default()
        .hash_password(password.as_bytes(), &salt)
        .map(|hash| hash.to_string())
        .map_err(|e| CoreError::Token(e.to_string()))
}

pub fn verify_password(password: &str, hash: &str) -> Result<bool, CoreError> {
    use argon2::{
        password_hash::{PasswordHash, PasswordVerifier},
        Argon2,
    };

    let parsed = PasswordHash::new(hash).map_err(|e| CoreError::Token(e.to_string()))?;
    Ok(Argon2::default()
        .verify_password(password.as_bytes(), &parsed)
        .is_ok())
}
