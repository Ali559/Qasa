//! Staff management of VPN nodes. The dashboard's "Add node" used to be a toast.

use std::net::IpAddr;

use axum::extract::{Path, State};
use axum::http::StatusCode;
use axum::Json;
use qasa_core::CoreError;
use qasa_db::repos::vpn::{NewNode, NodePatch, RetireOutcome, VpnNodeRow};
use serde::Deserialize;
use serde_json::json;
use uuid::Uuid;

use crate::auth::AdminWrite;
use crate::error::ApiResult;
use crate::state::AppState;

#[derive(Debug, Deserialize)]
pub struct CreateNodeRequest {
    pub name: String,
    pub city: String,
    pub public_ip: String,
    pub wg_public_key: String,
    #[serde(default = "default_port")]
    pub endpoint_port: i32,
    #[serde(default = "default_max_peers")]
    pub max_peers: i32,
    pub ip_pool_cidr: String,
    #[serde(default = "default_service")]
    pub service_name: String,
}

fn default_port() -> i32 {
    51820
}
fn default_max_peers() -> i32 {
    1000
}
fn default_service() -> String {
    "wg-main".into()
}

/// Register a node. The response carries the agent token **once**; only its hash is
/// stored. The node starts `draining`: set it `online` after its agent checks in.
pub async fn create(
    State(state): State<AppState>,
    AdminWrite(claims): AdminWrite,
    Json(body): Json<CreateNodeRequest>,
) -> ApiResult<(StatusCode, Json<serde_json::Value>)> {
    let name = required("name", &body.name)?;
    let city = required("city", &body.city)?;
    body.public_ip
        .trim()
        .parse::<IpAddr>()
        .map_err(|_| CoreError::BadRequest("public_ip must be an IP address".into()))?;
    check_wg_key(&body.wg_public_key)?;
    check_port(body.endpoint_port)?;
    check_max_peers(body.max_peers)?;
    let (_, prefix) = qasa_vpn::pool::parse_cidr(body.ip_pool_cidr.trim())
        .map_err(|e| CoreError::BadRequest(e.to_string()))?;
    if !(8..=30).contains(&prefix) {
        return Err(CoreError::BadRequest("ip_pool_cidr must be an IPv4 /8 to /30".into()).into());
    }

    let token = new_agent_token();
    let node = qasa_db::repos::vpn::create_node(
        &state.pool,
        &NewNode {
            name,
            city,
            public_ip: body.public_ip.trim(),
            wg_public_key: body.wg_public_key.trim(),
            endpoint_port: body.endpoint_port,
            max_peers: body.max_peers,
            ip_pool_cidr: body.ip_pool_cidr.trim(),
            service_name: body.service_name.trim(),
        },
        &qasa_vpn::hash_agent_token(&token),
    )
    .await?;
    audit(&state, &claims, "create_vpn_node", node.id, json!({ "name": node.name, "city": node.city, "public_ip": node.public_ip })).await?;

    Ok((
        StatusCode::CREATED,
        Json(json!({ "node": node_json(&node), "agent_token": token })),
    ))
}

#[derive(Debug, Deserialize)]
pub struct UpdateNodeRequest {
    pub name: Option<String>,
    pub city: Option<String>,
    pub max_peers: Option<i32>,
    pub endpoint_port: Option<i32>,
    pub wg_public_key: Option<String>,
    /// `online` or `draining` (no new peers; existing sessions continue).
    pub status: Option<String>,
}

pub async fn update(
    State(state): State<AppState>,
    AdminWrite(claims): AdminWrite,
    Path(node_id): Path<Uuid>,
    Json(body): Json<UpdateNodeRequest>,
) -> ApiResult<Json<serde_json::Value>> {
    let name = body.name.as_deref().map(|v| required("name", v)).transpose()?;
    let city = body.city.as_deref().map(|v| required("city", v)).transpose()?;
    if let Some(key) = &body.wg_public_key {
        check_wg_key(key)?;
    }
    if let Some(port) = body.endpoint_port {
        check_port(port)?;
    }
    if let Some(max) = body.max_peers {
        check_max_peers(max)?;
    }
    if let Some(status) = body.status.as_deref() {
        if !matches!(status, "online" | "draining") {
            return Err(CoreError::BadRequest(
                "status must be online or draining (retire with DELETE)".into(),
            )
            .into());
        }
    }

    let node = qasa_db::repos::vpn::update_node(
        &state.pool,
        node_id,
        &NodePatch {
            name,
            city,
            max_peers: body.max_peers,
            endpoint_port: body.endpoint_port,
            wg_public_key: body.wg_public_key.as_deref().map(str::trim),
            status: body.status.as_deref(),
        },
    )
    .await?
    .ok_or(CoreError::NotFound)?;
    audit(
        &state,
        &claims,
        "update_vpn_node",
        node_id,
        json!({
            "name": body.name, "city": body.city, "max_peers": body.max_peers,
            "endpoint_port": body.endpoint_port, "status": body.status,
            "wg_public_key_changed": body.wg_public_key.is_some(),
        }),
    )
    .await?;
    Ok(Json(json!({ "node": node_json(&node) })))
}

/// Retire a node. Refused while it still carries sessions: drain it first.
pub async fn retire(
    State(state): State<AppState>,
    AdminWrite(claims): AdminWrite,
    Path(node_id): Path<Uuid>,
) -> ApiResult<StatusCode> {
    match qasa_db::repos::vpn::retire_node(&state.pool, node_id).await? {
        RetireOutcome::Retired => {
            audit(&state, &claims, "retire_vpn_node", node_id, json!({})).await?;
            Ok(StatusCode::NO_CONTENT)
        }
        RetireOutcome::NotFound => Err(CoreError::NotFound.into()),
        RetireOutcome::HasActiveSessions(n) => Err(CoreError::BadRequest(format!(
            "node still has {n} active session(s); set it to draining and wait"
        ))
        .into()),
    }
}

/// Issue a new agent token; the old one stops working at once.
pub async fn rotate_agent_token(
    State(state): State<AppState>,
    AdminWrite(claims): AdminWrite,
    Path(node_id): Path<Uuid>,
) -> ApiResult<Json<serde_json::Value>> {
    let token = new_agent_token();
    if !qasa_db::repos::vpn::set_agent_token_hash(
        &state.pool,
        node_id,
        &qasa_vpn::hash_agent_token(&token),
    )
    .await?
    {
        return Err(CoreError::NotFound.into());
    }
    audit(&state, &claims, "rotate_vpn_agent_token", node_id, json!({})).await?;
    Ok(Json(json!({ "node_id": node_id, "agent_token": token })))
}

fn node_json(n: &VpnNodeRow) -> serde_json::Value {
    json!({
        "id": n.id,
        "name": n.name,
        "city": n.city,
        "status": n.status,
        "public_ip": n.public_ip,
        "endpoint_port": n.endpoint_port,
        "wg_public_key": n.wg_public_key,
        "max_peers": n.max_peers,
        "active_peers": n.active_peers,
        "ip_pool_cidr": n.ip_pool_cidr,
        "service_name": n.service_name,
        "last_heartbeat": n.last_heartbeat,
    })
}

async fn audit(
    state: &AppState,
    claims: &qasa_core::AdminClaims,
    action: &str,
    node_id: Uuid,
    payload: serde_json::Value,
) -> ApiResult<()> {
    qasa_db::repos::admin::audit_now(
        &state.pool,
        claims.admin_id()?,
        action,
        "vpn_node",
        &node_id.to_string(),
        payload,
    )
    .await?;
    Ok(())
}

/// 256 random bits, hex. Shown once to the operator who installs the agent.
fn new_agent_token() -> String {
    use rand::RngCore;
    let mut bytes = [0u8; 32];
    rand::rngs::OsRng.fill_bytes(&mut bytes);
    bytes.iter().map(|b| format!("{b:02x}")).collect()
}

fn required<'a>(field: &str, value: &'a str) -> ApiResult<&'a str> {
    let v = value.trim();
    if v.is_empty() {
        return Err(CoreError::BadRequest(format!("{field} is required")).into());
    }
    Ok(v)
}

/// A WireGuard public key is 32 bytes, base64: 44 characters ending in `=`.
fn check_wg_key(key: &str) -> ApiResult<()> {
    use base64::Engine;
    let ok = base64::engine::general_purpose::STANDARD
        .decode(key.trim())
        .is_ok_and(|b| b.len() == 32);
    if !ok {
        return Err(CoreError::BadRequest("wg_public_key must be a base64 WireGuard key".into()).into());
    }
    Ok(())
}

fn check_port(port: i32) -> ApiResult<()> {
    if !(1..=65535).contains(&port) {
        return Err(CoreError::BadRequest("endpoint_port must be 1-65535".into()).into());
    }
    Ok(())
}

fn check_max_peers(max: i32) -> ApiResult<()> {
    if !(1..=65_000).contains(&max) {
        return Err(CoreError::BadRequest("max_peers must be 1-65000".into()).into());
    }
    Ok(())
}
