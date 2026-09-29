use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "lowercase")]
pub enum AccountTier {
    Free,
    Premium,
    Expired,
}

impl AccountTier {
    pub fn as_str(self) -> &'static str {
        match self {
            Self::Free => "free",
            Self::Premium => "premium",
            Self::Expired => "expired",
        }
    }

    pub fn parse(value: &str) -> Self {
        match value {
            "premium" => Self::Premium,
            "expired" => Self::Expired,
            _ => Self::Free,
        }
    }
}

pub fn device_limit_for_tier(tier: AccountTier) -> u32 {
    match tier {
        AccountTier::Free => 1,
        AccountTier::Premium => 5,
        AccountTier::Expired => 0,
    }
}

pub fn effective_tier(stored_tier: &str, has_active_subscription: bool) -> AccountTier {
    // Premium is granted only by a live subscription — never by a stale accounts.tier flag.
    if has_active_subscription {
        return AccountTier::Premium;
    }
    match AccountTier::parse(stored_tier) {
        AccountTier::Premium => AccountTier::Expired,
        other => other,
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn premium_requires_active_subscription() {
        assert_eq!(
            effective_tier("premium", true),
            AccountTier::Premium
        );
        assert_eq!(
            effective_tier("premium", false),
            AccountTier::Expired
        );
        assert_eq!(effective_tier("free", false), AccountTier::Free);
        assert_eq!(effective_tier("expired", false), AccountTier::Expired);
    }

    #[test]
    fn entitlements_follow_tier() {
        let premium = Entitlements::for_tier(AccountTier::Premium);
        assert!(premium.breach && premium.vpn);

        let free = Entitlements::for_tier(AccountTier::Free);
        assert!(!free.breach && !free.vpn);

        let expired = Entitlements::for_tier(AccountTier::Expired);
        assert!(!expired.breach && !expired.vpn);
    }
}

#[derive(Debug, Clone, Serialize)]
pub struct Entitlements {
    pub tier: AccountTier,
    pub device_limit: u32,
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
            breach: premium,
            vpn: premium,
        }
    }
}
