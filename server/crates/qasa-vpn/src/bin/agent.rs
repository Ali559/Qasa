//! Qasa VPN node agent. Runs next to WireGuard on each node and, every interval:
//!
//! 1. reads host load and, with `--wg-interface`, every peer's handshake time and byte
//!    counters from `wg show <iface> dump`;
//! 2. reports them to the API (`POST …/heartbeat`), which expires dead sessions and
//!    meters each account's data;
//! 3. fetches the peers the node should have (`GET …/peers`) and adds or removes peers
//!    on the interface to match.
//!
//! Without `--apply` it only logs the changes it would make, so it is safe to point at
//! a live interface first.

use std::process::Command;
use std::time::Duration;

use anyhow::Context;
use clap::Parser;
use qasa_vpn::wg::{parse_dump, plan, wg_set_args, WgOp, WgPeer};
use qasa_vpn::{AgentHeartbeat, DesiredPeers};

#[derive(Parser)]
#[command(name = "qasa-vpn-agent", about = "Qasa VPN node agent")]
struct Cli {
    /// VPN node UUID registered in vpn_nodes table.
    #[arg(long, env = "QASA_VPN_NODE_ID")]
    node_id: uuid::Uuid,

    /// Agent authentication token (shown once when the node is created or rotated).
    #[arg(long, env = "QASA_VPN_AGENT_TOKEN", default_value = "dev-agent-token")]
    agent_token: String,

    /// API base URL, e.g. http://localhost:8080
    #[arg(long, env = "QASA_API_URL", default_value = "http://localhost:8080")]
    api_url: String,

    /// Heartbeat interval in seconds.
    #[arg(long, default_value_t = 30)]
    interval_secs: u64,

    /// WireGuard interface to report on and keep in sync, e.g. `wg0`. Without it the
    /// agent only reports host health.
    #[arg(long, env = "QASA_VPN_WG_INTERFACE")]
    wg_interface: Option<String>,

    /// Actually run `wg set`. Off by default: changes are logged, not made.
    #[arg(long, env = "QASA_VPN_APPLY")]
    apply: bool,

    /// The `wg` binary.
    #[arg(long, default_value = "wg")]
    wg_bin: String,
}

#[tokio::main]
async fn main() -> anyhow::Result<()> {
    let cli = Cli::parse();
    let client = reqwest::Client::builder()
        .timeout(Duration::from_secs(15))
        .build()?;
    let base = format!(
        "{}/v1/internal/vpn/nodes/{}",
        cli.api_url.trim_end_matches('/'),
        cli.node_id
    );
    println!(
        "qasa-vpn-agent node={} interface={} mode={}",
        cli.node_id,
        cli.wg_interface.as_deref().unwrap_or("(none)"),
        if cli.apply { "apply" } else { "dry-run" }
    );

    let mut host = HostStats::default();
    let mut warned_throttle = false;
    loop {
        if let Err(err) = tick(&cli, &client, &base, &mut host, &mut warned_throttle).await {
            eprintln!("agent tick failed: {err:#}");
        }
        tokio::time::sleep(Duration::from_secs(cli.interval_secs)).await;
    }
}

async fn tick(
    cli: &Cli,
    client: &reqwest::Client,
    base: &str,
    host: &mut HostStats,
    warned_throttle: &mut bool,
) -> anyhow::Result<()> {
    let current: Vec<WgPeer> = match &cli.wg_interface {
        Some(iface) => parse_dump(&run(&cli.wg_bin, &["show", iface, "dump"])?),
        None => Vec::new(),
    };

    let (cpu, mem, mem_label) = host.sample();
    let bandwidth = cli.wg_interface.as_deref().and_then(|i| host.bandwidth(i));
    let heartbeat = AgentHeartbeat {
        cpu_percent: cpu,
        memory_percent: mem,
        memory_label: mem_label,
        bandwidth_label: bandwidth,
        active_peers: None,
        peers: current.iter().map(|p| p.stat.clone()).collect(),
    };
    let res = client
        .post(format!("{base}/heartbeat"))
        .header("x-qasa-agent-token", &cli.agent_token)
        .json(&heartbeat)
        .send()
        .await
        .context("heartbeat")?;
    anyhow::ensure!(res.status().is_success(), "heartbeat: HTTP {}", res.status());

    let Some(iface) = &cli.wg_interface else {
        return Ok(());
    };
    let res = client
        .get(format!("{base}/peers"))
        .header("x-qasa-agent-token", &cli.agent_token)
        .send()
        .await
        .context("peers")?;
    anyhow::ensure!(res.status().is_success(), "peers: HTTP {}", res.status());
    let desired: DesiredPeers = res.json().await?;

    // Never hand `wg` something that is not a key; skip it and say so.
    let (valid, invalid): (Vec<_>, Vec<_>) = desired
        .peers
        .into_iter()
        .partition(|p| qasa_vpn::is_wg_key(&p.public_key));
    for p in &invalid {
        eprintln!("skipping peer with invalid key {:?}", p.public_key);
    }
    if !*warned_throttle && valid.iter().any(|p| p.throttle_mbps.is_some()) {
        eprintln!("throttled peers present: per-peer rate limiting is not implemented on the node yet");
        *warned_throttle = true;
    }

    for op in plan(&current, &valid) {
        let args = wg_set_args(iface, &op);
        let what = match &op {
            WgOp::Set { public_key, allowed_ip } => format!("set {public_key} {allowed_ip}"),
            WgOp::Remove { public_key } => format!("remove {public_key}"),
        };
        if cli.apply {
            let args: Vec<&str> = args.iter().map(String::as_str).collect();
            match run(&cli.wg_bin, &args) {
                Ok(_) => println!("applied: {what}"),
                Err(err) => eprintln!("wg set failed ({what}): {err:#}"),
            }
        } else {
            println!("dry-run: would {what}");
        }
    }
    Ok(())
}

fn run(bin: &str, args: &[&str]) -> anyhow::Result<String> {
    let out = Command::new(bin)
        .args(args)
        .output()
        .with_context(|| format!("running {bin} {}", args.join(" ")))?;
    anyhow::ensure!(
        out.status.success(),
        "{bin} {}: {}",
        args.join(" "),
        String::from_utf8_lossy(&out.stderr).trim()
    );
    Ok(String::from_utf8_lossy(&out.stdout).into_owned())
}

/// Host load from `/proc` and `/sys` (Linux). Elsewhere every value reads as 0.
#[derive(Default)]
struct HostStats {
    last_cpu: Option<(u64, u64)>,
    last_net: Option<(u64, std::time::Instant)>,
}

impl HostStats {
    /// CPU % since the previous sample, memory %, and a memory label like "41% (6.5GB)".
    fn sample(&mut self) -> (i32, i32, Option<String>) {
        let cpu = std::fs::read_to_string("/proc/stat")
            .ok()
            .and_then(|s| {
                let f: Vec<u64> = s
                    .lines()
                    .next()?
                    .split_whitespace()
                    .skip(1)
                    .filter_map(|v| v.parse().ok())
                    .collect();
                let idle = f.get(3)? + f.get(4).unwrap_or(&0);
                Some((f.iter().sum::<u64>(), idle))
            })
            .map(|(total, idle)| {
                let pct = match self.last_cpu {
                    Some((t0, i0)) if total > t0 => {
                        100 - ((idle - i0.min(idle)) * 100 / (total - t0)) as i32
                    }
                    _ => 0,
                };
                self.last_cpu = Some((total, idle));
                pct.clamp(0, 100)
            })
            .unwrap_or(0);

        let mem = std::fs::read_to_string("/proc/meminfo").ok().and_then(|s| {
            let kb = |key: &str| -> Option<u64> {
                s.lines()
                    .find(|l| l.starts_with(key))?
                    .split_whitespace()
                    .nth(1)?
                    .parse()
                    .ok()
            };
            let (total, avail) = (kb("MemTotal:")?, kb("MemAvailable:")?);
            let used = total.saturating_sub(avail);
            let pct = (used * 100 / total.max(1)) as i32;
            Some((pct, format!("{pct}% ({:.1}GB)", used as f64 / 1_048_576.0)))
        });
        match mem {
            Some((pct, label)) => (cpu, pct, Some(label)),
            None => (cpu, 0, None),
        }
    }

    /// Throughput of the interface since the previous sample, like "120 Mbps".
    fn bandwidth(&mut self, iface: &str) -> Option<String> {
        let read = |f: &str| -> Option<u64> {
            std::fs::read_to_string(format!("/sys/class/net/{iface}/statistics/{f}"))
                .ok()?
                .trim()
                .parse()
                .ok()
        };
        let bytes = read("rx_bytes")? + read("tx_bytes")?;
        let now = std::time::Instant::now();
        let label = self.last_net.and_then(|(b0, t0)| {
            let secs = now.duration_since(t0).as_secs_f64();
            (secs > 0.0 && bytes >= b0)
                .then(|| format!("{:.0} Mbps", (bytes - b0) as f64 * 8.0 / secs / 1_000_000.0))
        });
        self.last_net = Some((bytes, now));
        label
    }
}
