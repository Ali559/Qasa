use axum::extract::{Path, State};
use axum::http::StatusCode;
use axum::Json;
use chrono::{DateTime, Utc};
use qasa_core::CoreError;
use serde::{Deserialize, Serialize};
use serde_json::json;
use uuid::Uuid;

use crate::auth::LicenseAuth;
use crate::entitlements::resolve_entitlements;
use crate::error::{ApiError, ApiResult};
use crate::state::AppState;

#[derive(Debug, Deserialize)]
pub struct RegisterDeviceRequest {
    pub account_id: Uuid,
    pub device_fingerprint: String,
    pub device_name: String,
    pub platform: String,
    /// Optional human-readable region/city hint from the client.
    #[serde(default)]
    pub location: Option<String>,
}

#[derive(Debug, Serialize)]
pub struct DeviceSummary {
    pub id: Uuid,
    pub device_name: String,
    pub platform: String,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub location: Option<String>,
    pub created_at: DateTime<Utc>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub last_seen_at: Option<DateTime<Utc>>,
    pub is_primary: bool,
}

fn to_public(devices: Vec<qasa_db::models::DeviceRow>) -> Vec<DeviceSummary> {
    let primary_id = devices.first().map(|d| d.id);
    devices
        .into_iter()
        .map(|d| DeviceSummary {
            id: d.id,
            device_name: d.device_name,
            platform: d.platform,
            location: d.location,
            created_at: d.created_at,
            last_seen_at: d.last_seen_at,
            is_primary: primary_id == Some(d.id),
        })
        .collect()
}

pub async fn register(
    State(state): State<AppState>,
    Json(body): Json<RegisterDeviceRequest>,
) -> ApiResult<(StatusCode, Json<serde_json::Value>)> {
    if body.device_name.trim().is_empty() {
        return Err(ApiError::Core(CoreError::BadRequest(
            "device_name is required".into(),
        )));
    }

    let location = body
        .location
        .as_deref()
        .map(str::trim)
        .filter(|s| !s.is_empty());

    let account = qasa_db::repos::account::upsert_account(&state.pool, body.account_id).await?;
    let entitlements = resolve_entitlements(&state, account.id, &account.tier).await?;

    if entitlements.tier == qasa_core::AccountTier::Expired {
        return Err(ApiError::Core(CoreError::Forbidden));
    }

    let device = if let Some(existing) = qasa_db::repos::device::find_by_fingerprint(
        &state.pool,
        account.id,
        &body.device_fingerprint,
    )
    .await?
    {
        qasa_db::repos::device::touch_device(&state.pool, existing.id, location).await?
    } else {
        let active_count = qasa_db::repos::device::count_active(&state.pool, account.id).await?;
        if active_count >= entitlements.device_limit as i64 {
            let devices = qasa_db::repos::device::list_active(&state.pool, account.id).await?;
            let summaries = to_public(devices);

            return Ok((
                StatusCode::FORBIDDEN,
                Json(json!({
                    "error": "device_limit_exceeded",
                    "message": format!(
                        "{} tier is limited to {} device(s).",
                        entitlements.tier.as_str(),
                        entitlements.device_limit
                    ),
                    "devices_used": active_count,
                    "device_limit": entitlements.device_limit,
                    "devices": summaries,
                })),
            ));
        }

        qasa_db::repos::device::insert_device(
            &state.pool,
            account.id,
            &body.device_fingerprint,
            &body.device_name,
            &body.platform,
            location,
        )
        .await?
    };

    let (token, jti) = state
        .license_issuer
        .issue(account.id, device.id, &entitlements)?;
    qasa_db::repos::device::set_license_jti(&state.pool, device.id, &jti).await?;
    let _ = qasa_db::repos::account::set_vault_initialized(&state.pool, account.id).await;

    let devices_used = qasa_db::repos::device::count_active(&state.pool, account.id).await?;

    Ok((
        StatusCode::CREATED,
        Json(json!({
            "device_id": device.id,
            "license_token": token,
            "devices_used": devices_used,
            "device_limit": entitlements.device_limit,
            "tier": entitlements.tier.as_str(),
        })),
    ))
}

pub async fn list(
    State(state): State<AppState>,
    LicenseAuth(claims): LicenseAuth,
) -> ApiResult<Json<serde_json::Value>> {
    let account_id = claims.account_id()?;
    let devices = qasa_db::repos::device::list_active(&state.pool, account_id).await?;
    Ok(Json(json!({ "devices": to_public(devices) })))
}

pub async fn unlink(
    State(state): State<AppState>,
    LicenseAuth(claims): LicenseAuth,
    Path(device_id): Path<Uuid>,
) -> ApiResult<StatusCode> {
    let account_id = claims.account_id()?;
    let revoked =
        qasa_db::repos::device::revoke_device(&state.pool, account_id, device_id).await?;
    if !revoked {
        return Err(ApiError::Core(CoreError::NotFound));
    }
    Ok(StatusCode::NO_CONTENT)
}

/// Unlink every device except the primary (oldest / first linked to the account).
pub async fn unlink_others(
    State(state): State<AppState>,
    LicenseAuth(claims): LicenseAuth,
) -> ApiResult<Json<serde_json::Value>> {
    let account_id = claims.account_id()?;
    let revoked =
        qasa_db::repos::device::revoke_all_except_primary(&state.pool, account_id).await?;
    let devices = qasa_db::repos::device::list_active(&state.pool, account_id).await?;
    Ok(Json(json!({
        "revoked": revoked,
        "devices": to_public(devices),
    })))
}

#[derive(Debug, Deserialize)]
pub struct LinkCheckRequest {
    pub account_id: Uuid,
}

/// Unauthenticated probe used by the joining device before adopting `account_id`.
/// Does not create a device. Account IDs are unguessable UUIDs.
pub async fn link_check(
    State(state): State<AppState>,
    Json(body): Json<LinkCheckRequest>,
) -> ApiResult<Json<serde_json::Value>> {
    let Some(account) = qasa_db::repos::account::get_account(&state.pool, body.account_id).await?
    else {
        return Ok(Json(json!({
            "eligible": true,
            "tier": "free",
            "devices_used": 0,
            "device_limit": 1,
            "error": serde_json::Value::Null,
        })));
    };

    let entitlements = resolve_entitlements(&state, account.id, &account.tier).await?;
    let devices_used = qasa_db::repos::device::count_active(&state.pool, account.id).await?;
    let can_add = devices_used < entitlements.device_limit as i64;
    let error = if can_add {
        serde_json::Value::Null
    } else if entitlements.tier != qasa_core::AccountTier::Premium {
        json!("premium_required")
    } else {
        json!("device_limit_exceeded")
    };

    Ok(Json(json!({
        "eligible": can_add,
        "tier": entitlements.tier.as_str(),
        "devices_used": devices_used,
        "device_limit": entitlements.device_limit,
        "error": error,
    })))
}
