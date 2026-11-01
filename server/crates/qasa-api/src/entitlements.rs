use qasa_core::{AccountTier, Entitlements};
use uuid::Uuid;

use crate::error::{ApiError, ApiResult};
use crate::state::AppState;

#[derive(Debug, Clone, Copy)]
pub enum PremiumFeature {
    Breach,
    Vpn,
}

pub async fn resolve_entitlements(
    state: &AppState,
    account_id: Uuid,
    stored_tier: &str,
) -> ApiResult<Entitlements> {
    let has_active =
        qasa_db::repos::subscription::has_active_subscription(&state.pool, account_id).await?;
    let tier = qasa_core::effective_tier(stored_tier, has_active);
    Ok(Entitlements::for_tier(tier))
}

/// Hard gate: live active/cancelled-but-unexpired subscription + feature flag.
/// Stale `accounts.tier = premium` alone is never enough.
pub async fn require_active_premium(
    state: &AppState,
    account_id: Uuid,
    stored_tier: &str,
    feature: PremiumFeature,
) -> ApiResult<Entitlements> {
    let has_active =
        qasa_db::repos::subscription::has_active_subscription(&state.pool, account_id).await?;
    if !has_active {
        return Err(ApiError::SubscriptionRequired);
    }

    let entitlements = Entitlements::for_tier(qasa_core::effective_tier(stored_tier, true));
    if entitlements.tier != AccountTier::Premium {
        return Err(ApiError::SubscriptionRequired);
    }

    let allowed = match feature {
        PremiumFeature::Breach => entitlements.breach,
        PremiumFeature::Vpn => entitlements.vpn,
    };
    if !allowed {
        return Err(ApiError::SubscriptionRequired);
    }

    Ok(entitlements)
}
