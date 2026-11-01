use axum::extract::State;
use axum::http::StatusCode;
use axum::Json;
use serde_json::json;

use crate::auth::LicenseAuth;
use crate::entitlements::resolve_entitlements;
use crate::devices::device_limit_response;
use crate::error::ApiResult;
use crate::state::AppState;

pub async fn public_key(State(state): State<AppState>) -> Json<serde_json::Value> {
    Json(json!({
        "algorithm": "EdDSA",
        "public_key": state.license_issuer.public_key_b64(),
    }))
}

pub async fn refresh(
    State(state): State<AppState>,
    LicenseAuth(claims): LicenseAuth,
) -> ApiResult<(StatusCode, Json<serde_json::Value>)> {
    let account_id = claims.account_id()?;
    let device_id = claims.device_id()?;

    let account = qasa_db::repos::account::get_account(&state.pool, account_id)
        .await?
        .ok_or(qasa_core::CoreError::NotFound)?;

    let device = qasa_db::repos::device::get_device_for_account(&state.pool, account_id, device_id)
        .await?
        .ok_or(qasa_core::CoreError::Forbidden)?;

    let entitlements = resolve_entitlements(&state, account_id, &account.tier).await?;
    // Same rule as register: over the limit (e.g. Premium lapsed with 3 devices linked),
    // only the oldest devices keep a license.
    if !qasa_db::repos::device::is_within_limit(
        &state.pool,
        account_id,
        device.id,
        entitlements.device_limit,
    )
    .await?
    {
        return device_limit_response(&state, account_id, &entitlements).await;
    }

    let (token, jti) = state
        .license_issuer
        .issue(account_id, device.id, &entitlements)?;
    qasa_db::repos::device::set_license_jti(&state.pool, device.id, &jti).await?;

    Ok((
        StatusCode::OK,
        Json(json!({
            "license_token": token,
            "tier": entitlements.tier.as_str(),
            "device_limit": entitlements.device_limit,
            "breach_monitor_limit": entitlements.breach_monitor_limit,
        })),
    ))
}
