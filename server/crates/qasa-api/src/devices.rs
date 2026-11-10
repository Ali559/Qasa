use std::net::SocketAddr;

use axum::extract::{ConnectInfo, Path, State};
use axum::http::{HeaderMap, StatusCode};
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
    /// The restore key from a backup this device restored (base64, 32 bytes). With no free
    /// slot, it names the device that made the backup, which this one may replace.
    #[serde(default)]
    pub restore_key: Option<String>,
    /// The user tapped "Unlink it and continue on this device": replace the device the
    /// restore key names. Without it the 403 only reports that device.
    #[serde(default)]
    pub replace_device: bool,
}

/// A base64 32-byte value from a request, or a 400.
fn decode_key32(b64: &str, field: &str) -> ApiResult<Vec<u8>> {
    use base64::Engine;
    let bytes = base64::engine::general_purpose::STANDARD
        .decode(b64.trim())
        .map_err(|_| CoreError::BadRequest(format!("{field} must be base64")))?;
    if bytes.len() != 32 {
        return Err(CoreError::BadRequest(format!("{field} must be 32 bytes")).into());
    }
    Ok(bytes)
}

/// SHA-256 of a restore key, as the device stored it (`qasa_backup::restore_key_hash`).
fn restore_key_hash(key: &[u8]) -> Vec<u8> {
    use sha2::{Digest, Sha256};
    Sha256::digest(key).to_vec()
}

/// What a restore key can do for a device that found no free slot.
enum RestoreKeyCheck {
    /// No key sent.
    None,
    /// No linked device has this key (it was replaced by a newer one, the device was
    /// unlinked, or the backup's device never reached the server).
    Unknown,
    /// It names a device, but unlinking it still leaves no free slot (more devices than a
    /// lapsed plan allows).
    NotEnough,
    Replaceable(qasa_db::models::DeviceRow),
}

impl RestoreKeyCheck {
    fn status(&self) -> Option<&'static str> {
        match self {
            Self::None => None,
            Self::Unknown => Some("unknown"),
            Self::NotEnough => Some("not_enough"),
            Self::Replaceable(_) => Some("replaceable"),
        }
    }
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
    ConnectInfo(peer): ConnectInfo<SocketAddr>,
    headers: HeaderMap,
    Json(body): Json<RegisterDeviceRequest>,
) -> ApiResult<(StatusCode, Json<serde_json::Value>)> {
    let ip = crate::middleware::resolve_client_ip(&state.config, &headers, peer.ip());
    crate::limits::check(&state, crate::limits::REGISTER_IP, &ip.to_string()).await?;
    crate::limits::check(&state, crate::limits::REGISTER_ACCOUNT, &body.account_id.to_string())
        .await?;

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
    // The devices that held this id were wiped when the user deleted the account; the
    // app has to start a new one rather than bring the old id back.
    if account.deleted_at.is_some() {
        return Ok((
            StatusCode::GONE,
            Json(json!({
                "error": "account_deleted",
                "message": "This account was deleted. Create a new vault to continue.",
            })),
        ));
    }
    if account.blocked_at.is_some() {
        return Ok((
            StatusCode::FORBIDDEN,
            Json(json!({
                "error": "account_blocked",
                "message": "This account has been suspended. Contact support.",
            })),
        ));
    }
    let entitlements = resolve_entitlements(&state, account.id, &account.tier).await?;

    let existing = qasa_db::repos::device::find_by_fingerprint(
        &state.pool,
        account.id,
        &body.device_fingerprint,
    )
    .await?;

    let mut replaced: Option<qasa_db::models::DeviceRow> = None;
    let device = match existing {
        Some(linked) if linked.revoked_at.is_none() => {
            // Still linked, but the account may hold more devices than its tier now
            // allows (Premium lapsed to Free). Only devices within the limit get a license.
            if !qasa_db::repos::device::is_within_limit(
                &state.pool,
                account.id,
                linked.id,
                entitlements.device_limit,
            )
            .await?
            {
                return device_limit_response(&state, account.id, &entitlements).await;
            }
            qasa_db::repos::device::touch_device(&state.pool, linked.id, location).await?
        }
        // A new device, or one that was unlinked earlier: both take a free slot.
        unlinked_or_new => {
            let active_count =
                qasa_db::repos::device::count_active(&state.pool, account.id).await?;
            if active_count >= entitlements.device_limit as i64 {
                let check = match body.restore_key.as_deref() {
                    None => RestoreKeyCheck::None,
                    Some(b64) => {
                        let hash = restore_key_hash(&decode_key32(b64, "restore_key")?);
                        match qasa_db::repos::device::find_by_restore_key(&state.pool, account.id, &hash).await? {
                            None => RestoreKeyCheck::Unknown,
                            Some(_) if active_count - 1 >= entitlements.device_limit as i64 => RestoreKeyCheck::NotEnough,
                            Some(old) => RestoreKeyCheck::Replaceable(old),
                        }
                    }
                };
                match check {
                    RestoreKeyCheck::Replaceable(old) if body.replace_device => {
                        // Tell every device first, so the old one still gets the push.
                        let notice = qasa_db::repos::push_copy::device_replaced(&old.device_name, &body.device_name);
                        if let Err(err) = state.push.notify_account(&state.pool, account.id, &notice, &format!("device_replaced:{}", old.id)).await {
                            tracing::warn!(?err, "device replaced notice failed");
                        }
                        // Unlinking clears its restore keys: each key replaces once.
                        qasa_db::repos::device::revoke_device(&state.pool, account.id, old.id).await?;
                        replaced = Some(old);
                    }
                    check => return device_limit_response_with(&state, account.id, &entitlements, &check).await,
                }
            }

            match unlinked_or_new {
                Some(unlinked) => {
                    qasa_db::repos::device::reactivate_device(
                        &state.pool,
                        unlinked.id,
                        &body.device_name,
                        &body.platform,
                        location,
                    )
                    .await?
                }
                None => {
                    qasa_db::repos::device::insert_device(
                        &state.pool,
                        account.id,
                        &body.device_fingerprint,
                        &body.device_name,
                        &body.platform,
                        location,
                    )
                    .await?
                }
            }
        }
    };

    let (token, jti) = state
        .license_issuer
        .issue(account.id, device.id, &entitlements)?;
    qasa_db::repos::device::set_license_jti(&state.pool, device.id, &jti).await?;

    let devices_used = qasa_db::repos::device::count_active(&state.pool, account.id).await?;

    Ok((
        StatusCode::CREATED,
        Json(json!({
            "device_id": device.id,
            "license_token": token,
            "devices_used": devices_used,
            "device_limit": entitlements.device_limit,
            "breach_monitor_limit": entitlements.breach_monitor_limit,
            "tier": entitlements.tier.as_str(),
            "replaced_device": replaced.map(|d| json!({ "id": d.id, "device_name": d.device_name })),
        })),
    ))
}

/// The 403 a device gets when the account has no free slot for it. The body lists the
/// linked devices so the app can offer to unlink one (or upgrade).
pub(crate) async fn device_limit_response(
    state: &AppState,
    account_id: Uuid,
    entitlements: &qasa_core::Entitlements,
) -> ApiResult<(StatusCode, Json<serde_json::Value>)> {
    device_limit_response_with(state, account_id, entitlements, &RestoreKeyCheck::None).await
}

/// The 403, plus what the restore key sent (if any) can do: `restore_key` is
/// `replaceable` (then `replaceable_device` names the device the user may replace by
/// registering again with `replace_device: true`), `unknown` or `not_enough`.
async fn device_limit_response_with(
    state: &AppState,
    account_id: Uuid,
    entitlements: &qasa_core::Entitlements,
    check: &RestoreKeyCheck,
) -> ApiResult<(StatusCode, Json<serde_json::Value>)> {
    let devices = qasa_db::repos::device::list_active(&state.pool, account_id).await?;
    let replaceable = match check {
        RestoreKeyCheck::Replaceable(old) => devices.iter().position(|d| d.id == old.id),
        _ => None,
    };
    let devices_used = devices.len();
    let public = to_public(devices);
    let replaceable_device = replaceable.map(|i| serde_json::to_value(&public[i]).unwrap_or_default());
    Ok((
        StatusCode::FORBIDDEN,
        Json(json!({
            "error": "device_limit_exceeded",
            "message": format!(
                "{} tier is limited to {} device(s).",
                entitlements.tier.as_str(),
                entitlements.device_limit
            ),
            "devices_used": devices_used,
            "device_limit": entitlements.device_limit,
            "devices": public,
            "restore_key": check.status(),
            "replaceable_device": replaceable_device,
        })),
    ))
}

#[derive(Debug, Deserialize)]
pub struct RestoreKeyRequest {
    /// SHA-256 of the device's new restore key, base64.
    pub key_hash: String,
}

/// `PUT /v1/devices/me/restore-key`: the device switched to a new restore key, which its
/// next backup will carry. Its previous hash stays valid (see the migration).
pub async fn set_restore_key(
    State(state): State<AppState>,
    LicenseAuth(claims): LicenseAuth,
    Json(body): Json<RestoreKeyRequest>,
) -> ApiResult<StatusCode> {
    let hash = decode_key32(&body.key_hash, "key_hash")?;
    if !qasa_db::repos::device::set_restore_key_hash(&state.pool, claims.device_id()?, &hash).await? {
        return Err(ApiError::Core(CoreError::NotFound));
    }
    Ok(StatusCode::NO_CONTENT)
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

/// The calling device unlinks itself (logout, "clear local data", reset). Same teardown
/// as any unlink: tokens, push token and VPN session all end.
pub async fn unlink_self(
    State(state): State<AppState>,
    LicenseAuth(claims): LicenseAuth,
) -> ApiResult<StatusCode> {
    let revoked = qasa_db::repos::device::revoke_device(
        &state.pool,
        claims.account_id()?,
        claims.device_id()?,
    )
    .await?;
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
///
/// An unknown account is a 404. It used to answer `eligible: true`, so a mistyped id
/// "linked" the device into a brand-new empty account instead of the user's vault.
pub async fn link_check(
    State(state): State<AppState>,
    ConnectInfo(peer): ConnectInfo<SocketAddr>,
    headers: HeaderMap,
    Json(body): Json<LinkCheckRequest>,
) -> ApiResult<Json<serde_json::Value>> {
    let ip = crate::middleware::resolve_client_ip(&state.config, &headers, peer.ip());
    crate::limits::check(&state, crate::limits::LINK_CHECK_IP, &ip.to_string()).await?;

    let account = qasa_db::repos::account::get_account(&state.pool, body.account_id)
        .await?
        .ok_or(CoreError::NotFound)?;

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
