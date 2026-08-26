use std::time::Duration;

use anyhow::Context;
use clap::Parser;
use qasa_vpn::AgentHeartbeat;

#[derive(Parser)]
#[command(name = "qasa-vpn-agent", about = "Qasa VPN node heartbeat agent")]
struct Cli {
    /// VPN node UUID registered in vpn_nodes table.
    #[arg(long, env = "QASA_VPN_NODE_ID")]
    node_id: uuid::Uuid,

    /// Agent authentication token (must match node agent_token_hash).
    #[arg(long, env = "QASA_VPN_AGENT_TOKEN", default_value = "dev-agent-token")]
    agent_token: String,

    /// API base URL, e.g. http://localhost:8080
    #[arg(long, env = "QASA_API_URL", default_value = "http://localhost:8080")]
    api_url: String,

    /// Heartbeat interval in seconds.
    #[arg(long, default_value_t = 30)]
    interval_secs: u64,
}

#[tokio::main]
async fn main() -> anyhow::Result<()> {
    let cli = Cli::parse();
    let client = reqwest::Client::new();
    let url = format!(
        "{}/v1/internal/vpn/nodes/{}/heartbeat",
        cli.api_url.trim_end_matches('/'),
        cli.node_id
    );

    println!("qasa-vpn-agent sending heartbeats to {url}");

    loop {
        let payload = AgentHeartbeat {
            cpu_percent: 20,
            memory_percent: 40,
            memory_label: Some("40% (12.8GB)".into()),
            bandwidth_label: Some("450 Mbps".into()),
            active_peers: None,
        };

        let resp = client
            .post(&url)
            .header("x-qasa-agent-token", &cli.agent_token)
            .json(&payload)
            .send()
            .await
            .with_context(|| format!("heartbeat POST to {url}"))?;

        if !resp.status().is_success() {
            eprintln!("heartbeat failed: {}", resp.status());
        } else {
            println!("heartbeat ok");
        }

        tokio::time::sleep(Duration::from_secs(cli.interval_secs)).await;
    }
}
