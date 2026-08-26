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
    /// Client WireGuard public key (base64). Generated on-device if omitted.
    pub client_public_key: Option<String>,
}

#[derive(Debug, Clone, Deserialize, Serialize)]
pub struct AgentHeartbeat {
    pub cpu_percent: i32,
    pub memory_percent: i32,
    pub memory_label: Option<String>,
    pub bandwidth_label: Option<String>,
    pub active_peers: Option<i32>,
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
