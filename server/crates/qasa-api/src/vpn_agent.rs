//! Endpoints the VPN node agent calls, authenticated by the node's agent token.

use axum::extract::{Path, State};
use axum::http::HeaderMap;
use axum::Json;
use qasa_core::CoreError;
use qasa_db::repos::push_copy::{VPN_THROTTLED, VPN_USAGE_80};
use qasa_db::repos::vpn::{agent::Stat, VpnNodeRow};
use qasa_db::repos::vpn_usage::UsageEvent;
use qasa_vpn::{verify_agent_token, AgentHeartbeat, DesiredPeer, DesiredPeers, THROTTLE_MBPS};
use uuid::Uuid;

use crate::error::{ApiError, ApiResult};
use crate::state::AppState;

async fn authorized_node(state: &AppState, node_id: Uuid, headers: &HeaderMap) -> ApiResult<VpnNodeRow> {
    let token = headers
        .get("x-qasa-agent-token")
        .and_then(|v| v.to_str().ok())
        .ok_or(ApiError::Core(CoreError::Forbidden))?;
    let node = qasa_db::repos::vpn::get_node(&state.pool, node_id)
        .await?
        .ok_or(CoreError::NotFound)?;
    if !verify_agent_token(token, &node.agent_token_hash) {
        return Err(ApiError::Core(CoreError::Forbidden));
    }
    Ok(node)
}

/// Node health plus what WireGuard reports per peer. Peer stats feed session handshake
/// times (used to expire dead sessions) and per-account data usage (quota, 80% notice,
/// throttling).
pub async fn heartbeat(
    State(state): State<AppState>,
    Path(node_id): Path<Uuid>,
    headers: HeaderMap,
    Json(body): Json<AgentHeartbeat>,
) -> ApiResult<Json<serde_json::Value>> {
    authorized_node(&state, node_id, &headers).await?;

    // `active_peers` from the agent is ignored: the API counts live sessions itself, and
    // letting a report overwrite that count is how it used to drift.
    qasa_db::repos::vpn::update_heartbeat(
        &state.pool,
        node_id,
        body.cpu_percent,
        body.memory_percent,
        body.memory_label.as_deref(),
        body.bandwidth_label.as_deref(),
        None,
    )
    .await?;

    let stats: Vec<Stat> = body
        .peers
        .iter()
        .map(|p| Stat {
            public_key: &p.public_key,
            last_handshake_unix: p.last_handshake,
            rx_bytes: p.rx_bytes,
            tx_bytes: p.tx_bytes,
        })
        .collect();
    let moved = qasa_db::repos::vpn::record_peer_stats(&state.pool, node_id, &stats).await?;

    for (account_id, bytes) in &moved {
        for event in qasa_db::repos::vpn_usage::meter(&state.pool, *account_id, *bytes).await? {
            let (notice, tag) = match event {
                UsageEvent::Warned80 => (&VPN_USAGE_80, "vpn80"),
                UsageEvent::Throttled => (&VPN_THROTTLED, "vpnthrottle"),
            };
            let period = chrono::Utc::now().format("%Y-%m");
            let dedupe = format!("{tag}:{account_id}:{period}");
            if let Err(err) = state.push.notify_account(&state.pool, *account_id, notice, &dedupe).await {
                tracing::warn!(error = %err, %account_id, "VPN usage notice failed");
            }
        }
    }

    Ok(Json(serde_json::json!({ "ok": true, "metered_accounts": moved.len() })))
}

/// The peers this node should have right now. The agent adds what is missing and
/// removes what is no longer listed (disconnected, expired, unlinked, blocked).
pub async fn peers(
    State(state): State<AppState>,
    Path(node_id): Path<Uuid>,
    headers: HeaderMap,
) -> ApiResult<Json<DesiredPeers>> {
    let node = authorized_node(&state, node_id, &headers).await?;
    // A retired node must drop everything, whatever sessions say.
    if node.status == "retired" {
        return Ok(Json(DesiredPeers { peers: Vec::new() }));
    }
    let peers = qasa_db::repos::vpn::desired_peers(&state.pool, node_id)
        .await?
        .into_iter()
        .map(|p| DesiredPeer {
            public_key: p.public_key,
            allowed_ip: p.allowed_ip,
            throttle_mbps: p.throttled.then_some(THROTTLE_MBPS),
        })
        .collect();
    Ok(Json(DesiredPeers { peers }))
}
