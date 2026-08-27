use std::env;
use std::fmt;

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum AppEnvironment {
    Dev,
    Prod,
}

impl AppEnvironment {
    pub fn from_env() -> Self {
        match env::var("QASA_ENV")
            .unwrap_or_else(|_| "dev".into())
            .to_lowercase()
            .as_str()
        {
            "prod" | "production" => Self::Prod,
            _ => Self::Dev,
        }
    }

    pub fn is_prod(self) -> bool {
        matches!(self, Self::Prod)
    }

    pub fn is_dev(self) -> bool {
        matches!(self, Self::Dev)
    }

    pub fn as_str(self) -> &'static str {
        match self {
            Self::Dev => "dev",
            Self::Prod => "prod",
        }
    }
}

impl fmt::Display for AppEnvironment {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        f.write_str(self.as_str())
    }
}

#[derive(Clone, Debug)]
pub struct AppConfig {
    pub env: AppEnvironment,
    pub database_url: String,
    pub bind_addr: String,
    pub license_private_key_b64: Option<String>,
    pub admin_jwt_secret: String,
    pub admin_seed_email: Option<String>,
    pub admin_seed_password: Option<String>,
    pub admin_cors_origin: Option<String>,
    /// Comma-separated CIDRs (e.g. Tailscale `100.64.0.0/10`). When set in prod,
    /// `/v1/admin/*` (except login optionally) is restricted to these peers.
    pub admin_allow_cidrs: Vec<String>,
}

impl AppConfig {
    pub fn from_env() -> Self {
        let env = AppEnvironment::from_env();
        let admin_allow_cidrs = env::var("QASA_ADMIN_ALLOW_CIDRS")
            .ok()
            .map(|s| {
                s.split(',')
                    .map(|p| p.trim().to_string())
                    .filter(|p| !p.is_empty())
                    .collect()
            })
            .unwrap_or_default();
        Self {
            env,
            database_url: env::var("DATABASE_URL")
                .unwrap_or_else(|_| "postgres://qasa:qasa@localhost:5432/qasa".into()),
            bind_addr: env::var("BIND_ADDR").unwrap_or_else(|_| "0.0.0.0:8080".into()),
            license_private_key_b64: env::var("QASA_LICENSE_PRIVATE_KEY").ok(),
            admin_jwt_secret: env::var("QASA_ADMIN_JWT_SECRET").unwrap_or_else(|_| {
                if env.is_prod() {
                    panic!("QASA_ADMIN_JWT_SECRET must be set when QASA_ENV=prod");
                }
                "dev-admin-secret-change-me".into()
            }),
            admin_seed_email: env::var("QASA_ADMIN_SEED_EMAIL").ok(),
            admin_seed_password: env::var("QASA_ADMIN_SEED_PASSWORD").ok(),
            admin_cors_origin: env::var("QASA_ADMIN_CORS_ORIGIN").ok(),
            admin_allow_cidrs,
        }
    }
}
