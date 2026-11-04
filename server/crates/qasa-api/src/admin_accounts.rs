//! Staff actions on a single user account.

use axum::extract::{Path, State};
use axum::http::StatusCode;
use axum::Json;
use qasa_core::CoreError;
use serde::Deserialize;
use serde_json::json;
use uuid::Uuid;

use crate::auth::{AdminAuth, AdminWrite};
use crate::entitlements::resolve_entitlements;
use crate::error::ApiResult;
use crate::state::AppState;

/// Everything support needs about one account on one page.
pub async fn detail(
    State(state): State<AppState>,
    AdminAuth(_claims): AdminAuth,
    Path(account_id): Path<Uuid>,
) -> ApiResult<Json<serde_json::Value>> {
    use qasa_db::repos::{admin, admin_accounts, payment, vpn_usage};

    let account = qasa_db::repos::account::get_account(&state.pool, account_id)
        .await?
        .ok_or(CoreError::NotFound)?;
    let entitlements = resolve_entitlements(&state, account_id, &account.tier).await?;
    let devices = admin_accounts::devices(&state.pool, account_id).await?;
    let subscriptions = admin_accounts::subscriptions(&state.pool, account_id, 20).await?;
    let payments = payment::list_for_account(&state.pool, account_id, None, 20).await?;
    let monitors = admin_accounts::monitor_count(&state.pool, account_id).await?;
    // The gateway and when it was saved; the token itself never leaves the server.
    let payment_method = qasa_db::repos::payment_method::live_for_account(&state.pool, account_id)
        .await?
        .map(|m| json!({ "gateway": m.gateway, "created_at": m.created_at }));
    let usage = vpn_usage::get_or_create(&state.pool, account_id).await?;
    let audit = admin::list_audit(&state.pool, Some(&account_id.to_string()), None, 20).await?;

    let primary = devices.iter().find(|d| d.revoked_at.is_none()).map(|d| d.id);
    let devices: Vec<_> = devices
        .into_iter()
        .map(|d| {
            json!({
                "id": d.id,
                "device_name": d.device_name,
                "platform": d.platform,
                "location": d.location,
                "created_at": d.created_at,
                "last_seen_at": d.last_seen_at,
                "revoked_at": d.revoked_at,
                "notifications_enabled": d.notifications_enabled,
                "is_primary": Some(d.id) == primary,
            })
        })
        .collect();
    let payments: Vec<_> = payments
        .into_iter()
        .map(|p| {
            json!({
                "payment_id": p.id,
                "status": p.status,
                "gateway": p.gateway,
                "gateway_order_id": p.gateway_order_id,
                "amount_iqd": p.amount_iqd,
                "plan_id": p.plan_id,
                "kind": p.kind,
                "created_at": p.created_at,
                "completed_at": p.completed_at,
            })
        })
        .collect();

    Ok(Json(json!({
        "account": {
            "id": account.id,
            "public_id": account.public_id,
            "created_at": account.created_at,
            "blocked_at": account.blocked_at,
            "blocked_reason": account.blocked_reason,
            "lapsed": qasa_core::is_lapsed(&account.tier, entitlements.tier == qasa_core::AccountTier::Premium),
        },
        "entitlements": entitlements,
        "devices": devices,
        "subscriptions": subscriptions,
        "payments": payments,
        "payment_method": payment_method,
        "breach_monitors": monitors,
        "vpn_usage": usage,
        "audit": audit,
    })))
}

#[derive(Debug, Deserialize)]
pub struct BlockRequest {
    pub reason: String,
}

pub async fn block(
    State(state): State<AppState>,
    AdminWrite(claims): AdminWrite,
    Path(account_id): Path<Uuid>,
    Json(body): Json<BlockRequest>,
) -> ApiResult<Json<serde_json::Value>> {
    let reason = body.reason.trim();
    if reason.is_empty() {
        return Err(CoreError::BadRequest("reason is required".into()).into());
    }
    qasa_db::repos::account::get_account(&state.pool, account_id)
        .await?
        .ok_or(CoreError::NotFound)?;
    let changed =
        qasa_db::repos::admin_accounts::block(&state.pool, claims.admin_id()?, account_id, reason)
            .await?;
    Ok(Json(json!({ "blocked": true, "changed": changed })))
}

pub async fn unblock(
    State(state): State<AppState>,
    AdminWrite(claims): AdminWrite,
    Path(account_id): Path<Uuid>,
) -> ApiResult<Json<serde_json::Value>> {
    qasa_db::repos::account::get_account(&state.pool, account_id)
        .await?
        .ok_or(CoreError::NotFound)?;
    let changed =
        qasa_db::repos::admin_accounts::unblock(&state.pool, claims.admin_id()?, account_id)
            .await?;
    Ok(Json(json!({ "blocked": false, "changed": changed })))
}

/// Unlink one of the user's devices (lost phone, support request). Same teardown as a
/// user unlink: tokens, push token and VPN session end.
pub async fn unlink_device(
    State(state): State<AppState>,
    AdminWrite(claims): AdminWrite,
    Path((account_id, device_id)): Path<(Uuid, Uuid)>,
) -> ApiResult<StatusCode> {
    if !qasa_db::repos::device::revoke_device(&state.pool, account_id, device_id).await? {
        return Err(CoreError::NotFound.into());
    }
    qasa_db::repos::admin::audit_now(
        &state.pool,
        claims.admin_id()?,
        "unlink_device",
        "account",
        &account_id.to_string(),
        json!({ "device_id": device_id }),
    )
    .await?;
    Ok(StatusCode::NO_CONTENT)
}
