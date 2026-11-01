use axum::extract::{Path, State};
use axum::http::HeaderMap;
use axum::Json;
use qasa_vpn::{verify_agent_token, AgentHeartbeat};
use uuid::Uuid;

use crate::error::{ApiError, ApiResult};
use crate::state::AppState;
use qasa_core::CoreError;

pub async fn heartbeat(
    State(state): State<AppState>,
    Path(node_id): Path<Uuid>,
    headers: HeaderMap,
    Json(body): Json<AgentHeartbeat>,
) -> ApiResult<Json<serde_json::Value>> {
    let token = headers
        .get("x-qasa-agent-token")
        .and_then(|v| v.to_str().ok())
        .ok_or_else(|| ApiError::Core(CoreError::Forbidden))?;

    let node = qasa_db::repos::vpn::get_node(&state.pool, node_id)
        .await?
        .ok_or(CoreError::NotFound)?;

    if !verify_agent_token(token, &node.agent_token_hash) {
        return Err(ApiError::Core(CoreError::Forbidden));
    }

    qasa_db::repos::vpn::update_heartbeat(
        &state.pool,
        node_id,
        body.cpu_percent,
        body.memory_percent,
        body.memory_label.as_deref(),
        body.bandwidth_label.as_deref(),
        body.active_peers,
    )
    .await?;

    Ok(Json(serde_json::json!({ "ok": true })))
}
