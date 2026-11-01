use axum::extract::State;
use axum::Json;
use qasa_core::CoreError;
use qasa_vpn::{allocate_ip, ConnectRequest, WireGuardConfig};
use serde_json::json;
use uuid::Uuid;

use crate::auth::LicenseAuth;
use crate::entitlements::{require_active_premium, PremiumFeature};
use crate::error::ApiResult;
use crate::state::AppState;

pub async fn connect(
    State(state): State<AppState>,
    LicenseAuth(claims): LicenseAuth,
    Json(body): Json<ConnectRequest>,
) -> ApiResult<Json<serde_json::Value>> {
    let account_id = claims.account_id()?;
    let device_id = claims.device_id()?;

    let account = qasa_db::repos::account::get_account(&state.pool, account_id)
        .await?
        .ok_or(CoreError::NotFound)?;

    require_active_premium(&state, account_id, &account.tier, PremiumFeature::Vpn).await?;

    let usage = qasa_db::repos::vpn_usage::get_or_create(&state.pool, account_id).await?;
    let plan_id = qasa_db::repos::subscription::get_current(&state.pool, account_id)
        .await?
        .map(|s| s.plan_id)
        .ok_or(crate::error::ApiError::SubscriptionRequired)?;
    let quota_gb = qasa_db::repos::plan::vpn_quota_gb(&state.pool, &plan_id).await?;
    let quota_bytes = qasa_db::repos::vpn_usage::gb_to_bytes(quota_gb);
    let throttle_mbps = 5i32;

    if usage.bytes_used >= quota_bytes {
        let _ = qasa_db::repos::vpn_usage::set_throttled(&state.pool, account_id, true).await;
    } else if usage.bytes_used * 100 / quota_bytes.max(1) >= 80 && !usage.warned_80 {
        let _ = qasa_db::repos::vpn_usage::set_warned_80(&state.pool, account_id).await;
    }

    let throttled = usage.bytes_used >= quota_bytes || usage.throttled;

    qasa_db::repos::device::get_device_for_account(&state.pool, account_id, device_id)
        .await?
        .ok_or(CoreError::Forbidden)?;

    if let Some(existing) =
        qasa_db::repos::vpn::active_session_for_device(&state.pool, account_id, device_id).await?
    {
        if let Some(node) = qasa_db::repos::vpn::get_node(&state.pool, existing.node_id).await? {
            let config = WireGuardConfig::new(
                existing.id,
                &node.public_ip,
                node.endpoint_port,
                &node.wg_public_key,
                &existing.client_public_key,
                &existing.assigned_ip,
            );
            return Ok(Json(json!({
                "config": config,
                "reused": true,
                "usage": {
                    "bytes_used": usage.bytes_used,
                    "quota_bytes": quota_bytes,
                    "quota_gb": quota_gb,
                    "throttled": throttled,
                    "throttle_mbps": if throttled { Some(throttle_mbps) } else { None::<i32> },
                }
            })));
        }
    }

    let nodes = qasa_db::repos::vpn::list_eligible_nodes(&state.pool).await?;
    let selected = nodes
        .iter()
        .filter(|n| n.is_eligible())
        .min_by(|a, b| {
            a.utilization()
                .partial_cmp(&b.utilization())
                .unwrap_or(std::cmp::Ordering::Equal)
        })
        .ok_or_else(|| CoreError::BadRequest("no VPN nodes available".into()))?;

    let used_ips = qasa_db::repos::vpn::used_ips_on_node(&state.pool, selected.id).await?;
    let assigned_ip = allocate_ip(&selected.ip_pool_cidr, &used_ips)
        .map_err(|e| CoreError::BadRequest(e.to_string()))?;

    let client_public_key = body
        .client_public_key
        .unwrap_or_else(|| format!("dev-client-key-{}", Uuid::now_v7().simple()));

    let session = qasa_db::repos::vpn::create_session(
        &state.pool,
        account_id,
        device_id,
        selected.id,
        &client_public_key,
        assigned_ip,
    )
    .await?;

    let config = WireGuardConfig::new(
        session.id,
        &selected.public_ip,
        selected.endpoint_port,
        &selected.wg_public_key,
        &client_public_key,
        &assigned_ip.to_string(),
    );

    Ok(Json(json!({
        "config": config,
        "reused": false,
        "usage": {
            "bytes_used": usage.bytes_used,
            "quota_bytes": quota_bytes,
            "quota_gb": quota_gb,
            "throttled": throttled,
            "throttle_mbps": if throttled { Some(throttle_mbps) } else { None::<i32> },
        }
    })))
}

/// Disconnect is allowed without premium so expired users can tear down leftover sessions.
pub async fn disconnect(
    State(state): State<AppState>,
    LicenseAuth(claims): LicenseAuth,
) -> ApiResult<Json<serde_json::Value>> {
    let account_id = claims.account_id()?;
    let device_id = claims.device_id()?;

    let session =
        qasa_db::repos::vpn::active_session_for_device(&state.pool, account_id, device_id)
            .await?
            .ok_or(CoreError::NotFound)?;

    qasa_db::repos::vpn::disconnect_session(&state.pool, session.id).await?;

    Ok(Json(json!({ "disconnected": true, "session_id": session.id })))
}

pub async fn status(
    State(state): State<AppState>,
    LicenseAuth(claims): LicenseAuth,
) -> ApiResult<Json<serde_json::Value>> {
    let account_id = claims.account_id()?;
    let device_id = claims.device_id()?;

    let account = qasa_db::repos::account::get_account(&state.pool, account_id)
        .await?
        .ok_or(CoreError::NotFound)?;

    // No live subscription → kill any leftover session and refuse connected status.
    if require_active_premium(&state, account_id, &account.tier, PremiumFeature::Vpn)
        .await
        .is_err()
    {
        if let Some(session) =
            qasa_db::repos::vpn::active_session_for_device(&state.pool, account_id, device_id)
                .await?
        {
            let _ = qasa_db::repos::vpn::disconnect_session(&state.pool, session.id).await;
        }
        return Err(crate::error::ApiError::SubscriptionRequired);
    }

    let session =
        qasa_db::repos::vpn::active_session_for_device(&state.pool, account_id, device_id).await?;

    match session {
        Some(s) => {
            let node = qasa_db::repos::vpn::get_node(&state.pool, s.node_id)
                .await?
                .ok_or(CoreError::NotFound)?;
            Ok(Json(json!({
                "connected": true,
                "session_id": s.id,
                "node": node.name,
                "city": node.city,
                "assigned_ip": s.assigned_ip,
                "connected_at": s.connected_at,
            })))
        }
        None => Ok(Json(json!({ "connected": false }))),
    }
}
