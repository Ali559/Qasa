use std::net::Ipv4Addr;
use std::str::FromStr;

use chrono::{DateTime, Utc};
use qasa_core::new_id;
use sqlx::PgPool;
use uuid::Uuid;

#[derive(Debug, Clone, sqlx::FromRow)]
pub struct VpnNodeRow {
    pub id: Uuid,
    pub name: String,
    pub city: String,
    pub public_ip: String,
    pub wg_public_key: String,
    pub endpoint_port: i32,
    pub max_peers: i32,
    pub active_peers: i32,
    pub status: String,
    pub agent_token_hash: String,
    pub last_heartbeat: Option<DateTime<Utc>>,
    pub cpu_percent: i32,
    pub memory_percent: i32,
    pub memory_label: Option<String>,
    pub bandwidth_label: Option<String>,
    pub service_name: String,
    pub ip_pool_cidr: String,
}

impl VpnNodeRow {
    pub fn utilization(&self) -> f64 {
        if self.max_peers <= 0 {
            return 1.0;
        }
        self.active_peers as f64 / self.max_peers as f64
    }

    pub fn is_eligible(&self) -> bool {
        self.status == "online" && self.utilization() < 0.8
    }
}

#[derive(Debug, Clone, sqlx::FromRow)]
pub struct VpnSessionRow {
    pub id: Uuid,
    pub account_id: Uuid,
    pub device_id: Uuid,
    pub node_id: Uuid,
    pub client_public_key: String,
    pub assigned_ip: String,
    pub connected_at: DateTime<Utc>,
    pub disconnected_at: Option<DateTime<Utc>>,
    pub bytes_tx: i64,
    pub bytes_rx: i64,
}

const NODE_SELECT: &str = r#"
    SELECT id, name, city, host(public_ip) AS public_ip, wg_public_key, endpoint_port,
           max_peers, active_peers, status, agent_token_hash, last_heartbeat,
           cpu_percent, memory_percent, memory_label, bandwidth_label, service_name,
           ip_pool_cidr::text AS ip_pool_cidr
    FROM vpn_nodes
"#;

pub async fn list_eligible_nodes(pool: &PgPool) -> anyhow::Result<Vec<VpnNodeRow>> {
    let query = format!("{NODE_SELECT} WHERE status = 'online' ORDER BY active_peers ASC");
    let rows = sqlx::query_as::<_, VpnNodeRow>(&query).fetch_all(pool).await?;
    Ok(rows)
}

pub async fn list_all_nodes(pool: &PgPool) -> anyhow::Result<Vec<VpnNodeRow>> {
    let query = format!("{NODE_SELECT} ORDER BY name ASC");
    let rows = sqlx::query_as::<_, VpnNodeRow>(&query).fetch_all(pool).await?;
    Ok(rows)
}

pub async fn get_node(pool: &PgPool, node_id: Uuid) -> anyhow::Result<Option<VpnNodeRow>> {
    let query = format!("{NODE_SELECT} WHERE id = $1");
    let row = sqlx::query_as::<_, VpnNodeRow>(&query)
        .bind(node_id)
        .fetch_optional(pool)
        .await?;
    Ok(row)
}

pub async fn used_ips_on_node(pool: &PgPool, node_id: Uuid) -> anyhow::Result<Vec<Ipv4Addr>> {
    let ips: Vec<String> = sqlx::query_scalar(
        r#"
        SELECT host(assigned_ip)::text
        FROM vpn_sessions
        WHERE node_id = $1 AND disconnected_at IS NULL
        "#,
    )
    .bind(node_id)
    .fetch_all(pool)
    .await?;

    Ok(ips
        .iter()
        .filter_map(|s| Ipv4Addr::from_str(s).ok())
        .collect())
}

pub async fn active_session_for_device(
    pool: &PgPool,
    account_id: Uuid,
    device_id: Uuid,
) -> anyhow::Result<Option<VpnSessionRow>> {
    let row = sqlx::query_as::<_, VpnSessionRow>(
        r#"
        SELECT id, account_id, device_id, node_id, client_public_key,
               host(assigned_ip) AS assigned_ip, connected_at, disconnected_at, bytes_tx, bytes_rx
        FROM vpn_sessions
        WHERE account_id = $1 AND device_id = $2 AND disconnected_at IS NULL
        ORDER BY connected_at DESC
        LIMIT 1
        "#,
    )
    .bind(account_id)
    .bind(device_id)
    .fetch_optional(pool)
    .await?;
    Ok(row)
}

pub async fn create_session(
    pool: &PgPool,
    account_id: Uuid,
    device_id: Uuid,
    node_id: Uuid,
    client_public_key: &str,
    assigned_ip: Ipv4Addr,
) -> anyhow::Result<VpnSessionRow> {
    let id = new_id();
    let ip_str = assigned_ip.to_string();
    let mut tx = pool.begin().await?;

    let row = sqlx::query_as::<_, VpnSessionRow>(
        r#"
        INSERT INTO vpn_sessions (id, account_id, device_id, node_id, client_public_key, assigned_ip)
        VALUES ($1, $2, $3, $4, $5, $6::inet)
        RETURNING id, account_id, device_id, node_id, client_public_key,
                  host(assigned_ip) AS assigned_ip, connected_at, disconnected_at, bytes_tx, bytes_rx
        "#,
    )
    .bind(id)
    .bind(account_id)
    .bind(device_id)
    .bind(node_id)
    .bind(client_public_key)
    .bind(ip_str)
    .fetch_one(&mut *tx)
    .await?;

    sqlx::query("UPDATE vpn_nodes SET active_peers = active_peers + 1 WHERE id = $1")
        .bind(node_id)
        .execute(&mut *tx)
        .await?;

    tx.commit().await?;
    Ok(row)
}

pub async fn disconnect_session(pool: &PgPool, session_id: Uuid) -> anyhow::Result<bool> {
    let mut tx = pool.begin().await?;

    let session: Option<(Uuid,)> = sqlx::query_as(
        r#"
        UPDATE vpn_sessions
        SET disconnected_at = now()
        WHERE id = $1 AND disconnected_at IS NULL
        RETURNING node_id
        "#,
    )
    .bind(session_id)
    .fetch_optional(&mut *tx)
    .await?;

    let Some((node_id,)) = session else {
        tx.commit().await?;
        return Ok(false);
    };

    sqlx::query(
        r#"
        UPDATE vpn_nodes
        SET active_peers = GREATEST(active_peers - 1, 0)
        WHERE id = $1
        "#,
    )
    .bind(node_id)
    .execute(&mut *tx)
    .await?;

    tx.commit().await?;
    Ok(true)
}

pub async fn update_heartbeat(
    pool: &PgPool,
    node_id: Uuid,
    cpu_percent: i32,
    memory_percent: i32,
    memory_label: Option<&str>,
    bandwidth_label: Option<&str>,
    active_peers: Option<i32>,
) -> anyhow::Result<()> {
    let status = if cpu_percent >= 90 || memory_percent >= 90 {
        "warn"
    } else {
        "online"
    };

    sqlx::query(
        r#"
        UPDATE vpn_nodes
        SET cpu_percent = $2,
            memory_percent = $3,
            memory_label = COALESCE($4, memory_label),
            bandwidth_label = COALESCE($5, bandwidth_label),
            active_peers = COALESCE($6, active_peers),
            status = $7,
            last_heartbeat = now()
        WHERE id = $1
        "#,
    )
    .bind(node_id)
    .bind(cpu_percent)
    .bind(memory_percent)
    .bind(memory_label)
    .bind(bandwidth_label)
    .bind(active_peers)
    .bind(status)
    .execute(pool)
    .await?;
    Ok(())
}

pub async fn seed_dev_nodes(pool: &PgPool) -> anyhow::Result<()> {
    let count: i64 = sqlx::query_scalar("SELECT COUNT(*)::bigint FROM vpn_nodes")
        .fetch_one(pool)
        .await?;
    if count > 0 {
        return Ok(());
    }

    let token_hash = {
        use sha2::{Digest, Sha256};
        hex::encode(Sha256::digest(b"dev-agent-token"))
    };
    let nodes = [
        (
            "Node 1",
            "Erbil",
            "203.0.113.10",
            "YAnz6k0Yh3BzX1+ExampleKeyErbil000000000000=",
            "10.64.1.0/24",
        ),
        (
            "Node 2",
            "Sulaymaniyah",
            "203.0.113.11",
            "YAnz6k0Yh3BzX1+ExampleKeySuli0000000000000=",
            "10.64.2.0/24",
        ),
        (
            "Node 3",
            "Baghdad",
            "203.0.113.12",
            "YAnz6k0Yh3BzX1+ExampleKeyBaghdad00000000000=",
            "10.64.3.0/24",
        ),
    ];

    for (name, city, ip, wg_key, cidr) in nodes {
        let id = new_id();
        sqlx::query(
            r#"
            INSERT INTO vpn_nodes (
                id, name, city, public_ip, wg_public_key, agent_token_hash, ip_pool_cidr,
                cpu_percent, memory_percent, memory_label, bandwidth_label, active_peers
            )
            VALUES ($1, $2, $3, $4::inet, $5, $6, $7::cidr, 0, 0, '0% (0GB)', '0 Mbps', 0)
            "#,
        )
        .bind(id)
        .bind(name)
        .bind(city)
        .bind(ip)
        .bind(wg_key)
        .bind(&token_hash)
        .bind(cidr)
        .execute(pool)
        .await?;
    }

    Ok(())
}

/// Recompute `vpn_nodes.active_peers` from `vpn_sessions`, the actual source of truth.
///
/// `active_peers` is a denormalized counter bumped in `create_session` and decremented in
/// `disconnect_session` / `disconnect_stale_vpn_sessions`. Any path that misses a decrement
/// — a crashed agent, a rolled-back transaction, a manual session edit — leaves the count
/// permanently inflated. Because node selection orders by `active_peers ASC`, a drifted
/// node silently stops receiving new peers while looking busy in the admin dashboard.
///
/// This makes the counter self-healing. Run it periodically from the worker.
///
/// Returns the number of nodes whose count was corrected.
pub async fn reconcile_active_peers(pool: &PgPool) -> anyhow::Result<u64> {
    let corrected = sqlx::query(
        r#"
        UPDATE vpn_nodes n
        SET active_peers = COALESCE(live.count, 0)
        FROM (
            SELECT node_id, COUNT(*)::bigint AS count
            FROM vpn_sessions
            WHERE disconnected_at IS NULL
            GROUP BY node_id
        ) live
        WHERE n.id = live.node_id AND n.active_peers <> live.count
        "#,
    )
    .execute(pool)
    .await?
    .rows_affected();

    // Nodes with zero live sessions are not in the subquery, so fix them separately.
    let zeroed = sqlx::query(
        r#"
        UPDATE vpn_nodes n
        SET active_peers = 0
        WHERE n.active_peers <> 0
          AND NOT EXISTS (
              SELECT 1 FROM vpn_sessions s
              WHERE s.node_id = n.id AND s.disconnected_at IS NULL
          )
        "#,
    )
    .execute(pool)
    .await?
    .rows_affected();

    Ok(corrected + zeroed)
}

pub async fn vpn_admin_kpis(pool: &PgPool) -> anyhow::Result<VpnAdminKpis> {
    let total_peers: i64 = sqlx::query_scalar(
        r#"
        SELECT COALESCE(SUM(active_peers), 0)::bigint FROM vpn_nodes
        "#,
    )
    .fetch_one(pool)
    .await?;

    let online_nodes: i64 = sqlx::query_scalar(
        r#"
        SELECT COUNT(*)::bigint FROM vpn_nodes WHERE status = 'online'
        "#,
    )
    .fetch_one(pool)
    .await?;

    let total_nodes: i64 = sqlx::query_scalar("SELECT COUNT(*)::bigint FROM vpn_nodes")
        .fetch_one(pool)
        .await?;

    let uptime_pct = if total_nodes > 0 {
        (online_nodes as f64 / total_nodes as f64) * 100.0
    } else {
        0.0
    };

    Ok(VpnAdminKpis {
        total_peers,
        uptime_pct,
    })
}

#[derive(Debug, Clone)]
pub struct VpnAdminKpis {
    pub total_peers: i64,
    pub uptime_pct: f64,
}
