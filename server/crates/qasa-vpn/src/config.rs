use serde::{Deserialize, Serialize};

/// WireGuard configuration returned to the client after a successful connect.
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct WireGuardConfig {
    pub session_id: String,
    pub endpoint: String,
    pub server_public_key: String,
    pub client_public_key: String,
    pub client_ip: String,
    pub dns: Vec<String>,
    pub allowed_ips: Vec<String>,
    pub persistent_keepalive: u16,
}

impl WireGuardConfig {
    pub fn new(
        session_id: impl Into<String>,
        server_ip: &str,
        endpoint_port: i32,
        server_public_key: &str,
        client_public_key: &str,
        client_ip: &str,
    ) -> Self {
        Self {
            session_id: session_id.into(),
            endpoint: format!("{server_ip}:{endpoint_port}"),
            server_public_key: server_public_key.to_string(),
            client_public_key: client_public_key.to_string(),
            client_ip: client_ip.to_string(),
            dns: vec!["1.1.1.1".into(), "8.8.8.8".into()],
            allowed_ips: vec!["0.0.0.0/0".into(), "::/0".into()],
            persistent_keepalive: 25,
        }
    }
}

#[derive(Debug, Clone, Deserialize)]
pub struct ConnectRequest {
    /// The device's WireGuard public key (base64, 32 bytes). The device generates the
    /// keypair and keeps the private key; the server never sees it. Required in prod.
    pub client_public_key: Option<String>,
    /// A node from `GET /v1/vpn/nodes`; the least-loaded online node when omitted.
    #[serde(default)]
    pub node_id: Option<uuid::Uuid>,
}

/// Speed a peer is limited to once its account has used the month's VPN quota.
pub const THROTTLE_MBPS: u32 = 5;

/// Whether `key` is a WireGuard public key: base64 of exactly 32 bytes.
pub fn is_wg_key(key: &str) -> bool {
    use base64::Engine;
    let key = key.trim();
    key.len() == 44
        && base64::engine::general_purpose::STANDARD
            .decode(key)
            .is_ok_and(|b| b.len() == 32)
}

#[derive(Debug, Clone, Deserialize, Serialize)]
pub struct AgentHeartbeat {
    pub cpu_percent: i32,
    pub memory_percent: i32,
    pub memory_label: Option<String>,
    pub bandwidth_label: Option<String>,
    /// Ignored by the API, which counts live sessions itself; kept so older agents
    /// still parse.
    pub active_peers: Option<i32>,
    /// What the node's WireGuard interface reports per peer. Older agents send none.
    #[serde(default)]
    pub peers: Vec<PeerStat>,
}

/// One peer as `wg show <iface> dump` reports it.
#[derive(Debug, Clone, PartialEq, Eq, Deserialize, Serialize)]
pub struct PeerStat {
    pub public_key: String,
    /// Unix seconds of the latest handshake; 0 means never.
    pub last_handshake: u64,
    /// Cumulative bytes since the peer was added to the interface.
    pub rx_bytes: u64,
    pub tx_bytes: u64,
}

/// A peer the node should have, from `GET /v1/internal/vpn/nodes/{id}/peers`.
#[derive(Debug, Clone, PartialEq, Eq, Deserialize, Serialize)]
pub struct DesiredPeer {
    pub public_key: String,
    /// The single address the peer may use, e.g. `10.64.0.2/32`.
    pub allowed_ip: String,
    /// Set when the account is over its monthly quota.
    pub throttle_mbps: Option<u32>,
}

#[derive(Debug, Clone, Deserialize, Serialize)]
pub struct DesiredPeers {
    pub peers: Vec<DesiredPeer>,
}

pub fn hash_agent_token(token: &str) -> String {
    use sha2::{Digest, Sha256};
    hex::encode(Sha256::digest(token.as_bytes()))
}

pub fn verify_agent_token(token: &str, stored_hash: &str) -> bool {
    let computed = hash_agent_token(token);
    constant_time_eq(computed.as_bytes(), stored_hash.as_bytes())
}

fn constant_time_eq(a: &[u8], b: &[u8]) -> bool {
    if a.len() != b.len() {
        return false;
    }
    a.iter()
        .zip(b.iter())
        .fold(0u8, |acc, (x, y)| acc | (x ^ y))
        == 0
}
