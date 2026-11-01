use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "lowercase")]
pub enum AccountTier {
    Free,
    Premium,
}

impl AccountTier {
    pub fn as_str(self) -> &'static str {
        match self {
            Self::Free => "free",
            Self::Premium => "premium",
        }
    }

    /// Parses the *claimed* tier in a license token. Stored account tiers go through
    /// [`effective_tier`] instead, which never trusts a stored `premium`.
    pub fn parse(value: &str) -> Self {
        match value {
            "premium" => Self::Premium,
            _ => Self::Free,
        }
    }
}

pub fn device_limit_for_tier(tier: AccountTier) -> u32 {
    match tier {
        AccountTier::Free => 1,
        AccountTier::Premium => 5,
    }
}

/// The tier an account is entitled to right now.
///
/// Premium is granted only by a live subscription, never by a stale `accounts.tier`.
/// An account whose subscription ran out falls back to Free (1 device, no VPN or breach
/// monitoring). It used to become `Expired` with a device limit of 0, which made
/// register and license refresh return 403: a user who stopped paying was locked out
/// of their own vault on every device.
pub fn effective_tier(_stored_tier: &str, has_active_subscription: bool) -> AccountTier {
    if has_active_subscription {
        AccountTier::Premium
    } else {
        AccountTier::Free
    }
}

/// Whether the account once had Premium and no longer does. The worker marks such
/// accounts `accounts.tier = 'expired'`; the label is kept for the admin dashboard, but
/// it carries Free entitlements.
pub fn is_lapsed(stored_tier: &str, has_active_subscription: bool) -> bool {
    !has_active_subscription && matches!(stored_tier, "premium" | "expired")
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn premium_requires_active_subscription() {
        assert_eq!(effective_tier("premium", true), AccountTier::Premium);
        assert_eq!(effective_tier("free", true), AccountTier::Premium);
        assert_eq!(effective_tier("free", false), AccountTier::Free);
    }

    /// The regression: a lapsed account used to get 0 devices and be locked out.
    #[test]
    fn lapsed_premium_falls_back_to_free() {
        for stored in ["premium", "expired"] {
            let tier = effective_tier(stored, false);
            assert_eq!(tier, AccountTier::Free);
            assert_eq!(Entitlements::for_tier(tier).device_limit, 1);
            assert!(is_lapsed(stored, false));
            assert!(!is_lapsed(stored, true));
        }
        assert!(!is_lapsed("free", false));
    }

    #[test]
    fn entitlements_follow_tier() {
        let premium = Entitlements::for_tier(AccountTier::Premium);
        assert!(premium.breach && premium.vpn);
        assert_eq!(premium.device_limit, 5);
        assert_eq!(premium.breach_monitor_limit, 10);

        let free = Entitlements::for_tier(AccountTier::Free);
        assert!(!free.breach && !free.vpn);
        assert_eq!(free.device_limit, 1);
        assert_eq!(free.breach_monitor_limit, 0);
    }
}

#[derive(Debug, Clone, Serialize)]
pub struct Entitlements {
    pub tier: AccountTier,
    pub device_limit: u32,
    /// How many email hashes the account may monitor for breaches. The apps read it
    /// from the license instead of hardcoding 10.
    pub breach_monitor_limit: u32,
    pub breach: bool,
    pub vpn: bool,
}

impl Entitlements {
    pub fn for_tier(tier: AccountTier) -> Self {
        let device_limit = device_limit_for_tier(tier);
        let premium = tier == AccountTier::Premium;
        Self {
            tier,
            device_limit,
            breach_monitor_limit: if premium { 10 } else { 0 },
            breach: premium,
            vpn: premium,
        }
    }
}
