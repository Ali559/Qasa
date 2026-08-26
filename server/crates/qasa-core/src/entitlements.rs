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
    if has_active_subscription {
        return AccountTier::Premium;
    }
    AccountTier::parse(stored_tier)
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
