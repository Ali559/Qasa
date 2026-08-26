use uuid::Uuid;

use crate::error::ApiResult;
use crate::state::AppState;

pub async fn resolve_entitlements(
    state: &AppState,
    account_id: Uuid,
    stored_tier: &str,
) -> ApiResult<qasa_core::Entitlements> {
    let has_active =
        qasa_db::repos::subscription::has_active_subscription(&state.pool, account_id).await?;
    let tier = qasa_core::effective_tier(stored_tier, has_active);
    Ok(qasa_core::Entitlements::for_tier(tier))
}
