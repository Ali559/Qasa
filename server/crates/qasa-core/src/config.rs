use ipnet::IpNet;
use std::env;
use std::fmt;
use std::net::IpAddr;

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
    /// Comma-separated CIDRs of reverse proxies whose `X-Forwarded-For` we believe.
    ///
    /// Empty (the default) means we trust no proxy and always use the TCP peer address.
    /// Set this only to the load balancer / CDN that actually fronts the API — trusting it
    /// unconditionally would let any client spoof its source address.
    pub trusted_proxy_cidrs: Vec<String>,
}

impl AppConfig {
    pub fn from_env() -> Self {
        let env = AppEnvironment::from_env();
        let admin_allow_cidrs = parse_cidr_list("QASA_ADMIN_ALLOW_CIDRS", env, true);
        let trusted_proxy_cidrs = parse_cidr_list("QASA_TRUSTED_PROXY_CIDRS", env, false);
        Self {
            env,
            // The dev default is the local Postgres on its standard port (`make db` creates
            // the role and database). In prod there is no default: silently connecting to
            // some localhost database is worse than not starting.
            database_url: env::var("DATABASE_URL").unwrap_or_else(|_| {
                if env.is_prod() {
                    panic!("DATABASE_URL must be set when QASA_ENV=prod");
                }
                "postgres://qasa:qasa@localhost:5432/qasa".into()
            }),
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
            trusted_proxy_cidrs,
        }
    }
}

/// Parse a comma-separated network allow list.
///
/// A typo here used to be silently swallowed, which for `QASA_ADMIN_ALLOW_CIDRS` meant an
/// operator who wrote `10.0.0.0/8` got an admin panel that rejected every request with no
/// hint as to why. In prod an unparseable entry is fatal.
fn parse_cidr_list(key: &str, env: AppEnvironment, allow_alias: bool) -> Vec<String> {
    let Some(raw) = env::var(key).ok() else {
        return Vec::new();
    };
    raw.split(',')
        .map(str::trim)
        .filter(|p| !p.is_empty())
        .map(|entry| {
            let ok = if allow_alias && entry.eq_ignore_ascii_case("tailscale") {
                true
            } else {
                entry.parse::<IpNet>().is_ok() || entry.parse::<IpAddr>().is_ok()
            };
            if !ok && env.is_prod() {
                panic!("{key} contains an invalid entry {entry:?} (expected a CIDR or IP address)");
            }
            if !ok {
                tracing::warn!("{key}: ignoring unparseable entry {entry:?}");
            }
            entry.to_string()
        })
        .collect()
}
