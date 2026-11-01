//! Request limits, enforced through the shared Postgres counter
//! (`qasa_db::repos::rate_limit`), so they hold across API replicas and restarts.

use std::time::Duration;

use crate::error::{ApiError, ApiResult};
use crate::state::AppState;

#[derive(Debug, Clone, Copy)]
pub struct Limit {
    /// Key namespace, so different limits never share a counter.
    pub name: &'static str,
    pub max: u32,
    pub window: Duration,
    /// Shown to the client in the 429 body.
    pub message: &'static str,
}

/// k-anonymity prefix lookups, per account.
pub const BREACH_PREFIX: Limit = Limit {
    name: "breach_prefix",
    max: 60,
    window: Duration::from_secs(60 * 60),
    message: "Maximum 60 prefix queries per hour.",
};

/// Admin login attempts per email: generous for a person, useless for guessing.
pub const ADMIN_LOGIN_EMAIL: Limit = Limit {
    name: "admin_login_email",
    max: 10,
    window: Duration::from_secs(15 * 60),
    message: "Too many sign-in attempts. Try again in 15 minutes.",
};

/// Per client address; higher because a team may share one office or Tailscale exit.
pub const ADMIN_LOGIN_IP: Limit = Limit {
    name: "admin_login_ip",
    max: 30,
    window: Duration::from_secs(15 * 60),
    message: "Too many sign-in attempts. Try again in 15 minutes.",
};

/// Device registration per account id. The apps register on every launch, so this is
/// sized for many launches, not for one.
pub const REGISTER_ACCOUNT: Limit = Limit {
    name: "register_account",
    max: 30,
    window: Duration::from_secs(10 * 60),
    message: "Too many registration attempts. Try again shortly.",
};

/// Device registration per client address. Deliberately high: mobile carriers put many
/// subscribers behind one CGNAT address, so this only stops floods.
pub const REGISTER_IP: Limit = Limit {
    name: "register_ip",
    max: 600,
    window: Duration::from_secs(10 * 60),
    message: "Too many registration attempts. Try again shortly.",
};

/// Pairing probes per client address (pairing is rare; CGNAT still applies).
pub const LINK_CHECK_IP: Limit = Limit {
    name: "link_check_ip",
    max: 120,
    window: Duration::from_secs(10 * 60),
    message: "Too many pairing attempts. Try again shortly.",
};

/// Count a request against `limit` for `key`; 429 when over.
pub async fn check(state: &AppState, limit: Limit, key: &str) -> ApiResult<()> {
    let allowed = qasa_db::repos::rate_limit::hit(
        &state.pool,
        &format!("{}:{key}", limit.name),
        limit.max,
        limit.window,
    )
    .await?;
    if allowed {
        Ok(())
    } else {
        Err(ApiError::RateLimited(limit.message))
    }
}
