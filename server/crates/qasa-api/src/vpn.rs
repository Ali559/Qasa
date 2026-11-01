use axum::extract::State;
use axum::Json;
use qasa_core::CoreError;
use qasa_db::repos::vpn::{SessionConflict, VpnNodeRow, VpnSessionRow};
use qasa_vpn::{allocate_ip, is_wg_key, ConnectRequest, WireGuardConfig, THROTTLE_MBPS};
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
    qasa_db::repos::device::get_device_for_account(&state.pool, account_id, device_id)
        .await?
        .ok_or(CoreError::Forbidden)?;

    let client_public_key = client_key(&state, body.client_public_key.as_deref())?;
    let usage = usage_json(&state, account_id).await?;

    // Same device, same key: hand back the live session. A new key (the app rotated its
    // keypair) replaces the old session, whose peer the node then removes.
    if let Some(existing) =
        qasa_db::repos::vpn::active_session_for_device(&state.pool, account_id, device_id).await?
    {
        let same_node = body.node_id.is_none_or(|n| n == existing.node_id);
        if existing.client_public_key == client_public_key && same_node {
            if let Some(node) = qasa_db::repos::vpn::get_node(&state.pool, existing.node_id).await? {
                return Ok(Json(connect_json(&existing, &node, true, usage)));
            }
        }
        qasa_db::repos::vpn::disconnect_session(&state.pool, existing.id).await?;
    }

    let node = pick_node(&state, body.node_id).await?;

    // Allocation races with other connects to the same node; the unique index on live
    // addresses makes the loser retry with the next free one.
    for _ in 0..5 {
        let used = qasa_db::repos::vpn::used_ips_on_node(&state.pool, node.id).await?;
        let ip = allocate_ip(&node.ip_pool_cidr, &used)
            .map_err(|e| CoreError::BadRequest(e.to_string()))?;
        match qasa_db::repos::vpn::try_create_session(
            &state.pool,
            account_id,
            device_id,
            node.id,
            &client_public_key,
            ip,
        )
        .await?
        {
            Ok(session) => return Ok(Json(connect_json(&session, &node, false, usage))),
            Err(SessionConflict::IpTaken) => continue,
            Err(SessionConflict::KeyTaken) => {
                return Err(CoreError::BadRequest(
                    "client_public_key is already in use on this node".into(),
                )
                .into())
            }
            Err(SessionConflict::DeviceHasSession) => {
                // A concurrent connect from this device won; return its session.
                let session = qasa_db::repos::vpn::active_session_for_device(
                    &state.pool,
                    account_id,
                    device_id,
                )
                .await?
                .ok_or(CoreError::NotFound)?;
                let node = qasa_db::repos::vpn::get_node(&state.pool, session.node_id)
                    .await?
                    .ok_or(CoreError::NotFound)?;
                return Ok(Json(connect_json(&session, &node, true, usage)));
            }
        }
    }
    Err(CoreError::BadRequest("VPN node is busy, try again".into()).into())
}

/// The device's WireGuard public key. Required and validated in prod. In dev a missing
/// key gets a random placeholder so API-only flows still work (the apps have no tunnel
/// yet); it is a well-formed key, so a node agent can still apply it.
fn client_key(state: &AppState, sent: Option<&str>) -> ApiResult<String> {
    match sent.map(str::trim).filter(|k| !k.is_empty()) {
        Some(key) if is_wg_key(key) => Ok(key.to_string()),
        Some(_) => Err(CoreError::BadRequest(
            "client_public_key must be a base64 WireGuard public key".into(),
        )
        .into()),
        None if state.config.env.is_dev() => {
            use base64::Engine;
            use rand::RngCore;
            let mut bytes = [0u8; 32];
            rand::rngs::OsRng.fill_bytes(&mut bytes);
            Ok(base64::engine::general_purpose::STANDARD.encode(bytes))
        }
        None => Err(CoreError::BadRequest("client_public_key is required".into()).into()),
    }
}

/// The requested node if it can take a peer, otherwise the least-loaded eligible one.
async fn pick_node(state: &AppState, requested: Option<Uuid>) -> ApiResult<VpnNodeRow> {
    let nodes = qasa_db::repos::vpn::list_eligible_nodes(&state.pool).await?;
    let eligible = nodes.into_iter().filter(|n| n.is_eligible());
    let node = match requested {
        Some(id) => eligible.into_iter().find(|n| n.id == id).ok_or_else(|| {
            CoreError::BadRequest("that VPN node is not available; pick another".into())
        })?,
        None => eligible
            .min_by(|a, b| {
                a.utilization()
                    .partial_cmp(&b.utilization())
                    .unwrap_or(std::cmp::Ordering::Equal)
            })
            .ok_or_else(|| CoreError::BadRequest("no VPN nodes available".into()))?,
    };
    Ok(node)
}

async fn usage_json(state: &AppState, account_id: Uuid) -> ApiResult<serde_json::Value> {
    let usage = qasa_db::repos::vpn_usage::get_or_create(&state.pool, account_id).await?;
    let plan_id = qasa_db::repos::subscription::get_current(&state.pool, account_id)
        .await?
        .map(|s| s.plan_id)
        .ok_or(crate::error::ApiError::SubscriptionRequired)?;
    let quota_gb = qasa_db::repos::plan::vpn_quota_gb(&state.pool, &plan_id).await?;
    let quota_bytes = qasa_db::repos::vpn_usage::gb_to_bytes(quota_gb);
    let throttled = usage.throttled || usage.bytes_used >= quota_bytes;
    Ok(json!({
        "bytes_used": usage.bytes_used,
        "quota_bytes": quota_bytes,
        "quota_gb": quota_gb,
        "throttled": throttled,
        "throttle_mbps": if throttled { Some(THROTTLE_MBPS) } else { None },
        "period_start": usage.period_start,
    }))
}

fn connect_json(
    session: &VpnSessionRow,
    node: &VpnNodeRow,
    reused: bool,
    usage: serde_json::Value,
) -> serde_json::Value {
    let config = WireGuardConfig::new(
        session.id,
        &node.public_ip,
        node.endpoint_port,
        &node.wg_public_key,
        &session.client_public_key,
        &session.assigned_ip,
    );
    json!({
        "config": config,
        "node": { "id": node.id, "name": node.name, "city": node.city },
        "reused": reused,
        "usage": usage,
    })
}

/// Nodes a device may choose from, for a region picker.
pub async fn list_nodes(
    State(state): State<AppState>,
    LicenseAuth(_claims): LicenseAuth,
) -> ApiResult<Json<serde_json::Value>> {
    let nodes = qasa_db::repos::vpn::list_public_nodes(&state.pool).await?;
    Ok(Json(json!({ "nodes": nodes })))
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
            // `connected` means a session exists; `tunnel_up` means the node has seen a
            // WireGuard handshake recently, i.e. traffic can actually flow.
            let tunnel_up = s
                .last_handshake_at
                .is_some_and(|t| t > chrono::Utc::now() - chrono::Duration::minutes(3));
            Ok(Json(json!({
                "connected": true,
                "session_id": s.id,
                "node": node.name,
                "node_id": node.id,
                "city": node.city,
                "assigned_ip": s.assigned_ip,
                "connected_at": s.connected_at,
                "last_handshake_at": s.last_handshake_at,
                "tunnel_up": tunnel_up,
            })))
        }
        None => Ok(Json(json!({ "connected": false }))),
    }
}
