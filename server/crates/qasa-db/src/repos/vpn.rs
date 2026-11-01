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
    /// Latest WireGuard handshake the node reported for this peer.
    pub last_handshake_at: Option<DateTime<Utc>>,
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
               host(assigned_ip) AS assigned_ip, connected_at, disconnected_at, bytes_tx, bytes_rx, last_handshake_at
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

/// Why a new session could not be created. The unique indexes on live sessions make
/// these the losing side of a race rather than silent duplicates.
#[derive(Debug, PartialEq, Eq)]
pub enum SessionConflict {
    /// Another live peer on this node already has the address; pick another.
    IpTaken,
    /// Another live peer on this node already uses this public key.
    KeyTaken,
    /// This device already has a live session (a concurrent connect won).
    DeviceHasSession,
}

pub async fn create_session(
    pool: &PgPool,
    account_id: Uuid,
    device_id: Uuid,
    node_id: Uuid,
    client_public_key: &str,
    assigned_ip: Ipv4Addr,
) -> anyhow::Result<VpnSessionRow> {
    match try_create_session(pool, account_id, device_id, node_id, client_public_key, assigned_ip)
        .await?
    {
        Ok(row) => Ok(row),
        Err(conflict) => anyhow::bail!("session conflict: {conflict:?}"),
    }
}

pub async fn try_create_session(
    pool: &PgPool,
    account_id: Uuid,
    device_id: Uuid,
    node_id: Uuid,
    client_public_key: &str,
    assigned_ip: Ipv4Addr,
) -> anyhow::Result<Result<VpnSessionRow, SessionConflict>> {
    let id = new_id();
    let ip_str = assigned_ip.to_string();
    let mut tx = pool.begin().await?;

    let inserted = sqlx::query_as::<_, VpnSessionRow>(
        r#"
        INSERT INTO vpn_sessions (id, account_id, device_id, node_id, client_public_key, assigned_ip)
        VALUES ($1, $2, $3, $4, $5, $6::inet)
        RETURNING id, account_id, device_id, node_id, client_public_key,
                  host(assigned_ip) AS assigned_ip, connected_at, disconnected_at, bytes_tx, bytes_rx, last_handshake_at
        "#,
    )
    .bind(id)
    .bind(account_id)
    .bind(device_id)
    .bind(node_id)
    .bind(client_public_key)
    .bind(ip_str)
    .fetch_one(&mut *tx)
    .await;

    let row = match inserted {
        Ok(row) => row,
        Err(sqlx::Error::Database(db)) if db.is_unique_violation() => {
            let conflict = match db.constraint() {
                Some("uniq_vpn_sessions_live_ip") => SessionConflict::IpTaken,
                Some("uniq_vpn_sessions_live_key") => SessionConflict::KeyTaken,
                Some("uniq_vpn_sessions_live_device") => SessionConflict::DeviceHasSession,
                _ => return Err(sqlx::Error::Database(db).into()),
            };
            return Ok(Err(conflict));
        }
        Err(e) => return Err(e.into()),
    };

    sqlx::query("UPDATE vpn_nodes SET active_peers = active_peers + 1 WHERE id = $1")
        .bind(node_id)
        .execute(&mut *tx)
        .await?;

    tx.commit().await?;
    Ok(Ok(row))
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
            -- A heartbeat reports health; it must not undo a drain or retirement
            -- that staff set.
            status = CASE WHEN status IN ('draining', 'retired') THEN status ELSE $7 END,
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

    let total_nodes: i64 =
        sqlx::query_scalar("SELECT COUNT(*)::bigint FROM vpn_nodes WHERE status <> 'retired'")
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

// ------------------------------------------------------------- staff node management

pub struct NewNode<'a> {
    pub name: &'a str,
    pub city: &'a str,
    pub public_ip: &'a str,
    pub wg_public_key: &'a str,
    pub endpoint_port: i32,
    pub max_peers: i32,
    pub ip_pool_cidr: &'a str,
    pub service_name: &'a str,
}

/// Register a node. Starts `draining`, so it takes no peers until staff have seen its
/// agent heartbeat and set it `online`.
pub async fn create_node(
    pool: &PgPool,
    node: &NewNode<'_>,
    agent_token_hash: &str,
) -> anyhow::Result<VpnNodeRow> {
    let id = new_id();
    sqlx::query(
        r#"
        INSERT INTO vpn_nodes (
            id, name, city, public_ip, wg_public_key, endpoint_port, max_peers,
            status, agent_token_hash, ip_pool_cidr, service_name
        )
        VALUES ($1, $2, $3, $4::inet, $5, $6, $7, 'draining', $8, $9::cidr, $10)
        "#,
    )
    .bind(id)
    .bind(node.name)
    .bind(node.city)
    .bind(node.public_ip)
    .bind(node.wg_public_key)
    .bind(node.endpoint_port)
    .bind(node.max_peers)
    .bind(agent_token_hash)
    .bind(node.ip_pool_cidr)
    .bind(node.service_name)
    .execute(pool)
    .await?;
    get_node(pool, id)
        .await?
        .ok_or_else(|| anyhow::anyhow!("node vanished after insert"))
}

#[derive(Debug, Default)]
pub struct NodePatch<'a> {
    pub name: Option<&'a str>,
    pub city: Option<&'a str>,
    pub max_peers: Option<i32>,
    pub endpoint_port: Option<i32>,
    pub wg_public_key: Option<&'a str>,
    /// `online` or `draining`. Retirement goes through [`retire_node`].
    pub status: Option<&'a str>,
}

pub async fn update_node(
    pool: &PgPool,
    node_id: Uuid,
    patch: &NodePatch<'_>,
) -> anyhow::Result<Option<VpnNodeRow>> {
    let updated = sqlx::query(
        r#"
        UPDATE vpn_nodes
        SET name = COALESCE($2, name),
            city = COALESCE($3, city),
            max_peers = COALESCE($4, max_peers),
            endpoint_port = COALESCE($5, endpoint_port),
            wg_public_key = COALESCE($6, wg_public_key),
            status = COALESCE($7, status)
        WHERE id = $1 AND status <> 'retired'
        "#,
    )
    .bind(node_id)
    .bind(patch.name)
    .bind(patch.city)
    .bind(patch.max_peers)
    .bind(patch.endpoint_port)
    .bind(patch.wg_public_key)
    .bind(patch.status)
    .execute(pool)
    .await?
    .rows_affected();
    if updated == 0 {
        return Ok(None);
    }
    get_node(pool, node_id).await
}

#[derive(Debug, PartialEq, Eq)]
pub enum RetireOutcome {
    Retired,
    NotFound,
    /// Still carrying peers; drain it first.
    HasActiveSessions(i64),
}

/// Take a node out of service for good. Kept as a row (sessions reference it) with
/// status `retired`; it never takes peers again.
pub async fn retire_node(pool: &PgPool, node_id: Uuid) -> anyhow::Result<RetireOutcome> {
    let mut tx = pool.begin().await?;
    let exists: Option<String> =
        sqlx::query_scalar("SELECT status FROM vpn_nodes WHERE id = $1 FOR UPDATE")
            .bind(node_id)
            .fetch_optional(&mut *tx)
            .await?;
    if exists.is_none() {
        return Ok(RetireOutcome::NotFound);
    }
    let live: i64 = sqlx::query_scalar(
        "SELECT COUNT(*) FROM vpn_sessions WHERE node_id = $1 AND disconnected_at IS NULL",
    )
    .bind(node_id)
    .fetch_one(&mut *tx)
    .await?;
    if live > 0 {
        return Ok(RetireOutcome::HasActiveSessions(live));
    }
    sqlx::query("UPDATE vpn_nodes SET status = 'retired', active_peers = 0 WHERE id = $1")
        .bind(node_id)
        .execute(&mut *tx)
        .await?;
    tx.commit().await?;
    Ok(RetireOutcome::Retired)
}

/// Replace the node agent's token. The old one stops working immediately.
pub async fn set_agent_token_hash(
    pool: &PgPool,
    node_id: Uuid,
    agent_token_hash: &str,
) -> anyhow::Result<bool> {
    let n = sqlx::query(
        "UPDATE vpn_nodes SET agent_token_hash = $2 WHERE id = $1 AND status <> 'retired'",
    )
    .bind(node_id)
    .bind(agent_token_hash)
    .execute(pool)
    .await?
    .rows_affected();
    Ok(n > 0)
}

// ------------------------------------------------------------- node agent sync

/// The peers a node should have: every live session on it. A throttled account's peers
/// carry the speed limit.
pub async fn desired_peers(pool: &PgPool, node_id: Uuid) -> anyhow::Result<Vec<agent::Peer>> {
    let rows = sqlx::query_as::<_, agent::Peer>(
        r#"
        SELECT s.client_public_key AS public_key,
               host(s.assigned_ip) || '/32' AS allowed_ip,
               COALESCE(u.throttled, false) AS throttled
        FROM vpn_sessions s
        LEFT JOIN vpn_usage u ON u.account_id = s.account_id
        WHERE s.node_id = $1 AND s.disconnected_at IS NULL
        ORDER BY s.connected_at
        "#,
    )
    .bind(node_id)
    .fetch_all(pool)
    .await?;
    Ok(rows)
}

/// Row types for the agent sync, kept apart from the API's wire types.
pub mod agent {
    #[derive(Debug, Clone, sqlx::FromRow)]
    pub struct Peer {
        pub public_key: String,
        pub allowed_ip: String,
        pub throttled: bool,
    }

    /// One peer as the node reported it in a heartbeat.
    #[derive(Debug, Clone)]
    pub struct Stat<'a> {
        pub public_key: &'a str,
        pub last_handshake_unix: u64,
        pub rx_bytes: u64,
        pub tx_bytes: u64,
    }
}

/// Record what a node reported per peer: the latest handshake, and the bytes moved since
/// the last report. Returns `(account_id, new bytes)` per account that moved any, for
/// usage metering. Peers with no live session on this node are ignored (the agent
/// removes them on its next sync).
///
/// WireGuard counters are cumulative from when the peer was added to the interface, so
/// a smaller value than stored means the peer was re-added: the whole value is new.
pub async fn record_peer_stats(
    pool: &PgPool,
    node_id: Uuid,
    stats: &[agent::Stat<'_>],
) -> anyhow::Result<Vec<(Uuid, i64)>> {
    let mut tx = pool.begin().await?;
    let mut moved: std::collections::HashMap<Uuid, i64> = Default::default();
    for st in stats {
        let Some((session_id, account_id, rx, txb)) = sqlx::query_as::<_, (Uuid, Uuid, i64, i64)>(
            r#"
            SELECT id, account_id, bytes_rx, bytes_tx FROM vpn_sessions
            WHERE node_id = $1 AND client_public_key = $2 AND disconnected_at IS NULL
            FOR UPDATE
            "#,
        )
        .bind(node_id)
        .bind(st.public_key)
        .fetch_optional(&mut *tx)
        .await?
        else {
            continue;
        };

        let new_rx = i64::try_from(st.rx_bytes).unwrap_or(i64::MAX);
        let new_tx = i64::try_from(st.tx_bytes).unwrap_or(i64::MAX);
        let delta = |new: i64, old: i64| if new >= old { new - old } else { new };
        let d = delta(new_rx, rx) + delta(new_tx, txb);

        let handshake = (st.last_handshake_unix > 0)
            .then(|| DateTime::from_timestamp(st.last_handshake_unix as i64, 0))
            .flatten();
        sqlx::query(
            r#"
            UPDATE vpn_sessions
            SET bytes_rx = $2, bytes_tx = $3,
                last_handshake_at = GREATEST(last_handshake_at, $4)
            WHERE id = $1
            "#,
        )
        .bind(session_id)
        .bind(new_rx)
        .bind(new_tx)
        .bind(handshake)
        .execute(&mut *tx)
        .await?;

        if d > 0 {
            *moved.entry(account_id).or_default() += d;
        }
    }
    tx.commit().await?;
    Ok(moved.into_iter().collect())
}

#[derive(Debug, Clone, sqlx::FromRow, serde::Serialize)]
pub struct PublicNode {
    pub id: Uuid,
    pub name: String,
    pub city: String,
    /// Live peers as a share of capacity, 0–100.
    pub load_pct: i32,
}

/// Nodes a client may pick: online and not over the 80% connect threshold.
pub async fn list_public_nodes(pool: &PgPool) -> anyhow::Result<Vec<PublicNode>> {
    let rows = sqlx::query_as::<_, PublicNode>(
        r#"
        SELECT id, name, city,
               LEAST(100, (active_peers * 100 / GREATEST(max_peers, 1)))::int AS load_pct
        FROM vpn_nodes
        WHERE status = 'online' AND active_peers * 100 < max_peers * 80
        ORDER BY city, name
        "#,
    )
    .fetch_all(pool)
    .await?;
    Ok(rows)
}
