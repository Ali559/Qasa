//! Integration tests for the VPN tunnel plumbing: race-safe sessions, node sync,
//! handshake-based expiry, usage metering and offline nodes.
//!
//! Run with `./scripts/test-integration.sh`.

mod support;

use std::net::Ipv4Addr;

use qasa_db::repos::vpn::{agent::Stat, NewNode, SessionConflict};
use qasa_db::repos::vpn_usage::UsageEvent;
use qasa_db::repos::{account, device, subscription, vpn, vpn_usage, worker};
use sqlx::PgPool;
use uuid::Uuid;

use support::{cleanup, test_pool};

/// The expiry and offline jobs touch every row, so tests that run them must not overlap.
async fn serial() -> tokio::sync::MutexGuard<'static, ()> {
    static LOCK: std::sync::OnceLock<tokio::sync::Mutex<()>> = std::sync::OnceLock::new();
    LOCK.get_or_init(|| tokio::sync::Mutex::new(())).lock().await
}

fn key(n: u8) -> String {
    use base64::Engine;
    let mut b = [0u8; 32];
    b[0] = n;
    b[1..17].copy_from_slice(Uuid::now_v7().as_bytes());
    base64::engine::general_purpose::STANDARD.encode(b)
}

async fn online_node(pool: &PgPool) -> Uuid {
    let id = Uuid::now_v7();
    let octet = id.as_bytes()[15];
    let node = vpn::create_node(
        pool,
        &NewNode {
            name: "t", city: "Erbil", public_ip: "203.0.113.90", wg_public_key: "k",
            endpoint_port: 51820, max_peers: 100,
            ip_pool_cidr: &format!("10.201.{octet}.0/24"), service_name: "wg",
        },
        "h",
    )
    .await
    .unwrap();
    sqlx::query("UPDATE vpn_nodes SET status = 'online', last_heartbeat = now() WHERE id = $1")
        .bind(node.id)
        .execute(pool)
        .await
        .unwrap();
    node.id
}

async fn device_on(pool: &PgPool) -> (Uuid, Uuid) {
    let a = account::upsert_account(pool, Uuid::now_v7()).await.unwrap().id;
    let d = device::insert_device(pool, a, &format!("fp-{}", Uuid::now_v7()), "P", "ios", None)
        .await
        .unwrap()
        .id;
    (a, d)
}

async fn purge(pool: &PgPool, accounts: &[Uuid], nodes: &[Uuid]) {
    let mut sql = Vec::new();
    for n in nodes {
        sql.push(format!("DELETE FROM vpn_sessions WHERE node_id = '{n}'"));
        sql.push(format!("DELETE FROM vpn_nodes WHERE id = '{n}'"));
    }
    for a in accounts {
        sql.extend([
            format!("DELETE FROM notification_receipts WHERE account_id = '{a}'"),
            format!("DELETE FROM vpn_usage WHERE account_id = '{a}'"),
            format!("DELETE FROM subscriptions WHERE account_id = '{a}'"),
            format!("DELETE FROM devices WHERE account_id = '{a}'"),
            format!("DELETE FROM accounts WHERE id = '{a}'"),
        ]);
    }
    cleanup(pool, &sql).await;
}

#[tokio::test]
#[ignore = "needs a disposable Postgres; run ./scripts/test-integration.sh"]
async fn concurrent_allocation_conflicts_are_reported_not_duplicated() {
    let pool = test_pool().await;
    let node = online_node(&pool).await;
    let (a1, d1) = device_on(&pool).await;
    let (a2, d2) = device_on(&pool).await;
    let ip = Ipv4Addr::new(10, 201, 0, 2);
    let k1 = key(1);

    vpn::try_create_session(&pool, a1, d1, node, &k1, ip).await.unwrap().unwrap();
    assert_eq!(
        vpn::try_create_session(&pool, a2, d2, node, &key(2), ip).await.unwrap().unwrap_err(),
        SessionConflict::IpTaken
    );
    assert_eq!(
        vpn::try_create_session(&pool, a2, d2, node, &k1, Ipv4Addr::new(10, 201, 0, 3)).await.unwrap().unwrap_err(),
        SessionConflict::KeyTaken
    );
    assert_eq!(
        vpn::try_create_session(&pool, a1, d1, node, &key(3), Ipv4Addr::new(10, 201, 0, 4)).await.unwrap().unwrap_err(),
        SessionConflict::DeviceHasSession
    );
    let peers: i32 = sqlx::query_scalar("SELECT active_peers FROM vpn_nodes WHERE id = $1").bind(node).fetch_one(&pool).await.unwrap();
    assert_eq!(peers, 1, "a refused insert must not bump the peer count");

    purge(&pool, &[a1, a2], &[node]).await;
}

#[tokio::test]
#[ignore = "needs a disposable Postgres; run ./scripts/test-integration.sh"]
async fn peer_stats_record_handshakes_and_byte_deltas() {
    let pool = test_pool().await;
    let node = online_node(&pool).await;
    let (a, d) = device_on(&pool).await;
    let k = key(4);
    vpn::try_create_session(&pool, a, d, node, &k, Ipv4Addr::new(10, 201, 1, 2)).await.unwrap().unwrap();
    let now = chrono::Utc::now().timestamp() as u64;

    let report = |hs: u64, rx: u64, tx: u64| vec![Stat { public_key: &k, last_handshake_unix: hs, rx_bytes: rx, tx_bytes: tx }];
    let unknown = key(5);

    let moved = vpn::record_peer_stats(&pool, node, &report(now - 30, 1000, 500)).await.unwrap();
    assert_eq!(moved, vec![(a, 1500)]);
    let moved = vpn::record_peer_stats(&pool, node, &report(now, 1600, 900)).await.unwrap();
    assert_eq!(moved, vec![(a, 1000)], "only what moved since the last report");
    // The peer was re-added on the node, so counters restarted.
    let moved = vpn::record_peer_stats(&pool, node, &report(now, 200, 100)).await.unwrap();
    assert_eq!(moved, vec![(a, 300)]);
    // A handshake time never goes backwards; peers without a session are ignored.
    vpn::record_peer_stats(&pool, node, &[Stat { public_key: &k, last_handshake_unix: 0, rx_bytes: 200, tx_bytes: 100 }, Stat { public_key: &unknown, last_handshake_unix: now, rx_bytes: 9, tx_bytes: 9 }])
        .await
        .unwrap();
    let s = vpn::active_session_for_device(&pool, a, d).await.unwrap().unwrap();
    assert_eq!(s.last_handshake_at.map(|t| t.timestamp() as u64), Some(now));

    let desired = vpn::desired_peers(&pool, node).await.unwrap();
    assert_eq!(desired.len(), 1);
    assert_eq!(desired[0].allowed_ip, "10.201.1.2/32");
    assert!(!desired[0].throttled);

    purge(&pool, &[a], &[node]).await;
}

#[tokio::test]
#[ignore = "needs a disposable Postgres; run ./scripts/test-integration.sh"]
async fn metering_warns_at_80_and_throttles_at_100_once() {
    let pool = test_pool().await;
    let staff = qasa_db::repos::admin::create_admin(&pool, &format!("v-{}@t", Uuid::now_v7()), "x").await.unwrap().id;
    let (a, _) = device_on(&pool).await;
    subscription::grant_premium(&pool, a, "premium_monthly", 30, subscription::GrantInfo { admin_id: staff, reason: "t" })
        .await
        .unwrap();
    let quota = vpn_usage::gb_to_bytes(qasa_db::repos::plan::vpn_quota_gb(&pool, "premium_monthly").await.unwrap());

    assert!(vpn_usage::meter(&pool, a, quota / 2).await.unwrap().is_empty());
    assert_eq!(vpn_usage::meter(&pool, a, quota * 3 / 10).await.unwrap(), [UsageEvent::Warned80]);
    assert!(vpn_usage::meter(&pool, a, 1).await.unwrap().is_empty(), "80% notice is sent once");
    assert_eq!(vpn_usage::meter(&pool, a, quota).await.unwrap(), [UsageEvent::Throttled]);
    assert!(vpn_usage::meter(&pool, a, 1).await.unwrap().is_empty(), "throttle notice is sent once");
    assert!(vpn_usage::get_or_create(&pool, a).await.unwrap().throttled);

    purge(&pool, &[a], &[]).await;
    cleanup(&pool, &[format!("DELETE FROM admin_audit_log WHERE admin_id = '{staff}'"), format!("DELETE FROM admin_users WHERE id = '{staff}'")]).await;
}

/// The regression: sessions used to be ended 5 minutes after they started, in use or
/// not. Now only a stale handshake (or none, after a grace period) ends one.
#[tokio::test]
#[ignore = "needs a disposable Postgres; run ./scripts/test-integration.sh"]
async fn sessions_expire_on_handshakes_not_age() {
    let _serial = serial().await;
    let pool = test_pool().await;
    let node = online_node(&pool).await;
    let mut accounts = Vec::new();
    let mut sessions = Vec::new();
    // (connected minutes ago, last handshake minutes ago)
    let cases: [(i32, Option<i32>); 4] = [(120, Some(1)), (120, Some(9)), (3, None), (30, None)];
    for (i, (age, hs)) in cases.iter().enumerate() {
        let (a, d) = device_on(&pool).await;
        let s = vpn::try_create_session(&pool, a, d, node, &key(10 + i as u8), Ipv4Addr::new(10, 201, 2, 2 + i as u8)).await.unwrap().unwrap();
        sqlx::query("UPDATE vpn_sessions SET connected_at = now() - make_interval(mins => $2), last_handshake_at = now() - make_interval(mins => $3) WHERE id = $1")
            .bind(s.id).bind(age).bind(*hs)
            .execute(&pool).await.unwrap();
        accounts.push(a);
        sessions.push(s.id);
    }

    worker::disconnect_stale_vpn_sessions(&pool, 5, 10).await.unwrap();

    let mut live = Vec::new();
    for s in &sessions {
        let ended: bool = sqlx::query_scalar("SELECT disconnected_at IS NOT NULL FROM vpn_sessions WHERE id = $1").bind(s).fetch_one(&pool).await.unwrap();
        live.push(!ended);
    }
    assert_eq!(live, [true, false, true, false], "2-hour session with a fresh handshake stays");
    let peers: i32 = sqlx::query_scalar("SELECT active_peers FROM vpn_nodes WHERE id = $1").bind(node).fetch_one(&pool).await.unwrap();
    assert_eq!(peers, 2);

    purge(&pool, &accounts, &[node]).await;
}

#[tokio::test]
#[ignore = "needs a disposable Postgres; run ./scripts/test-integration.sh"]
async fn silent_nodes_go_offline_and_come_back() {
    let _serial = serial().await;
    let pool = test_pool().await;
    let node = online_node(&pool).await;
    sqlx::query("UPDATE vpn_nodes SET last_heartbeat = now() - interval '10 minutes' WHERE id = $1").bind(node).execute(&pool).await.unwrap();

    worker::mark_offline_nodes(&pool, 120).await.unwrap();
    assert_eq!(vpn::get_node(&pool, node).await.unwrap().unwrap().status, "offline");
    assert!(vpn::list_public_nodes(&pool).await.unwrap().iter().all(|n| n.id != node));

    vpn::update_heartbeat(&pool, node, 10, 10, None, None, None).await.unwrap();
    assert_eq!(vpn::get_node(&pool, node).await.unwrap().unwrap().status, "online");
    assert!(vpn::list_public_nodes(&pool).await.unwrap().iter().any(|n| n.id == node));

    purge(&pool, &[], &[node]).await;
}

#[tokio::test]
#[ignore = "needs a disposable Postgres; run ./scripts/test-integration.sh"]
async fn ended_sessions_are_deleted_after_30_days_live_ones_never() {
    let _g = serial().await;
    let pool = test_pool().await;
    let node = online_node(&pool).await;
    let (account, live_dev) = device_on(&pool).await;
    let other_dev = device::insert_device(&pool, account, &format!("fp-{}", Uuid::now_v7()), "P2", "ios", None).await.unwrap().id;
    let octet = node.as_bytes()[15];
    let insert = |dev: Uuid, n: u8, connected_days: i32, ended_days: Option<i32>| {
        let pool = pool.clone();
        async move {
            let id = Uuid::now_v7();
            sqlx::query(
                "INSERT INTO vpn_sessions (id, account_id, device_id, node_id, client_public_key, assigned_ip, connected_at, disconnected_at)
                 VALUES ($1, $2, $3, $4, $5, $6::inet, now() - make_interval(days => $7),
                         CASE WHEN $8::int IS NULL THEN NULL ELSE now() - make_interval(days => $8) END)",
            )
            .bind(id).bind(account).bind(dev).bind(node).bind(key(n))
            .bind(format!("10.201.{octet}.{}", 10 + n)).bind(connected_days).bind(ended_days)
            .execute(&pool).await.unwrap();
            id
        }
    };
    // Connected 60 days ago and still up: kept, however old.
    let live = insert(live_dev, 1, 60, None).await;
    // Ended 40 days ago: deleted. Ended 5 days ago: kept until day 30.
    let old = insert(other_dev, 2, 41, Some(40)).await;
    let recent = insert(other_dev, 3, 6, Some(5)).await;

    let deleted = worker::purge_ended_vpn_sessions(&pool, 30).await.unwrap();
    assert!(deleted >= 1);
    let left: Vec<Uuid> = sqlx::query_scalar("SELECT id FROM vpn_sessions WHERE node_id = $1")
        .bind(node).fetch_all(&pool).await.unwrap();
    assert!(left.contains(&live) && left.contains(&recent));
    assert!(!left.contains(&old));

    purge(&pool, &[account], &[node]).await;
}
