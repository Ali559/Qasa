use axum::extract::{Path, State};
use axum::Json;
use qasa_breach::normalize_prefix;
use qasa_core::{AccountTier, CoreError};
use serde_json::json;

use crate::auth::LicenseAuth;
use crate::entitlements::resolve_entitlements;
use crate::error::{ApiError, ApiResult};
use crate::state::AppState;

pub async fn lookup_prefix(
    State(state): State<AppState>,
    LicenseAuth(claims): LicenseAuth,
    Path(prefix): Path<String>,
) -> ApiResult<Json<serde_json::Value>> {
    let account_id = claims.account_id()?;

    state
        .breach_rate_limiter
        .check(&account_id.to_string())
        .map_err(|_| ApiError::RateLimited)?;

    let account = qasa_db::repos::account::get_account(&state.pool, account_id)
        .await?
        .ok_or(CoreError::NotFound)?;

    let entitlements = resolve_entitlements(&state, account_id, &account.tier).await?;

    if entitlements.tier != AccountTier::Premium || !entitlements.breach {
        return Err(ApiError::Core(CoreError::Forbidden));
    }

    let normalized = normalize_prefix(&prefix).map_err(|e| CoreError::BadRequest(e.to_string()))?;

    let matches = qasa_db::repos::breach::lookup_by_prefix(&state.pool, &normalized).await?;

    Ok(Json(json!({
        "prefix": normalized,
        "count": matches.len(),
        "suffixes": matches,
    })))
}
