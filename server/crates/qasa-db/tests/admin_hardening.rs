//! Integration tests for the admin surface: sessions and roles, account blocking, VPN
//! node management, search, and the MRR figure.
//!
//! Run with `./scripts/test-integration.sh`.

mod support;

use chrono::{Duration, Utc};
use qasa_db::repos::settlement::{settle_payment, SettleStatus};
use qasa_db::repos::vpn::{NewNode, NodePatch, RetireOutcome};
use qasa_db::repos::{account, admin, admin_accounts, device, payment, subscription, vpn};
use sqlx::PgPool;
use std::net::Ipv4Addr;
use uuid::Uuid;

use support::{cleanup, test_pool};

async fn new_admin(pool: &PgPool) -> Uuid {
    admin::create_admin(pool, &format!("staff-{}@test", Uuid::now_v7()), "x")
        .await
        .unwrap()
        .id
}

async fn new_account_with_device(pool: &PgPool) -> (Uuid, Uuid) {
    let a = account::upsert_account(pool, Uuid::now_v7()).await.unwrap().id;
    let d = device::insert_device(pool, a, &format!("fp-{}", Uuid::now_v7()), "P", "ios", None)
        .await
        .unwrap()
        .id;
    (a, d)
}

async fn purge(pool: &PgPool, accounts: &[Uuid], admins: &[Uuid], nodes: &[Uuid]) {
    let mut sql = Vec::new();
    for a in accounts {
        sql.extend([
            format!("DELETE FROM vpn_sessions WHERE account_id = '{a}'"),
            format!("UPDATE payments SET subscription_id = NULL WHERE account_id = '{a}'"),
            format!("DELETE FROM subscriptions WHERE account_id = '{a}'"),
            format!(
                "DELETE FROM webhook_events WHERE gateway_order_id IN \
                 (SELECT gateway_order_id FROM payments WHERE account_id = '{a}')"
            ),
            format!("DELETE FROM payments WHERE account_id = '{a}'"),
            format!("DELETE FROM devices WHERE account_id = '{a}'"),
            format!("DELETE FROM accounts WHERE id = '{a}'"),
        ]);
    }
    for n in nodes {
        sql.extend([
            format!("DELETE FROM vpn_sessions WHERE node_id = '{n}'"),
            format!("DELETE FROM vpn_nodes WHERE id = '{n}'"),
        ]);
    }
    for a in admins {
        sql.extend([
            format!("DELETE FROM admin_audit_log WHERE admin_id = '{a}'"),
            format!("DELETE FROM admin_users WHERE id = '{a}'"),
        ]);
    }
    cleanup(pool, &sql).await;
}

#[tokio::test]
#[ignore = "needs a disposable Postgres; run ./scripts/test-integration.sh"]
async fn admin_session_follows_the_database() {
    let pool = test_pool().await;
    let id = new_admin(&pool).await;

    assert_eq!(admin::live_role(&pool, id, "jti-1").await.unwrap().as_deref(), Some("admin"));

    // A demotion applies to tokens already issued.
    sqlx::query("UPDATE admin_users SET role = 'viewer' WHERE id = $1").bind(id).execute(&pool).await.unwrap();
    assert_eq!(admin::live_role(&pool, id, "jti-1").await.unwrap().as_deref(), Some("viewer"));

    // Logout ends only that token.
    admin::revoke_token(&pool, "jti-1", id, Utc::now() + Duration::hours(8)).await.unwrap();
    assert_eq!(admin::live_role(&pool, id, "jti-1").await.unwrap(), None);
    assert!(admin::live_role(&pool, id, "jti-2").await.unwrap().is_some());

    // A disabled admin can neither use a token nor sign in.
    sqlx::query("UPDATE admin_users SET disabled_at = now() WHERE id = $1").bind(id).execute(&pool).await.unwrap();
    assert_eq!(admin::live_role(&pool, id, "jti-2").await.unwrap(), None);
    let email: String = sqlx::query_scalar("SELECT email FROM admin_users WHERE id = $1").bind(id).fetch_one(&pool).await.unwrap();
    assert!(admin::find_by_email(&pool, &email).await.unwrap().is_none());

    // Unknown roles are rejected by the schema.
    let bad = sqlx::query("UPDATE admin_users SET role = 'root' WHERE id = $1").bind(id).execute(&pool).await;
    assert!(bad.is_err());

    cleanup(&pool, &[format!("DELETE FROM admin_users WHERE id = '{id}'")]).await;
}

#[tokio::test]
#[ignore = "needs a disposable Postgres; run ./scripts/test-integration.sh"]
async fn blocking_an_account_cuts_its_tokens_and_vpn_and_is_audited() {
    let pool = test_pool().await;
    let staff = new_admin(&pool).await;
    let (acct, dev) = new_account_with_device(&pool).await;
    let node = vpn::create_node(
        &pool,
        &NewNode { name: "n", city: "c", public_ip: "203.0.113.50", wg_public_key: "k", endpoint_port: 51820, max_peers: 10, ip_pool_cidr: "10.99.1.0/24", service_name: "wg" },
        "h",
    )
    .await
    .unwrap();
    vpn::create_session(&pool, acct, dev, node.id, "pk", Ipv4Addr::new(10, 99, 1, 2)).await.unwrap();
    let iat = (Utc::now().timestamp() - 5) as u64;

    assert!(admin_accounts::block(&pool, staff, acct, "chargeback").await.unwrap());
    assert!(!admin_accounts::block(&pool, staff, acct, "again").await.unwrap(), "already blocked");
    assert!(!device::is_license_live(&pool, acct, dev, "j", iat).await.unwrap());
    assert!(vpn::active_session_for_device(&pool, acct, dev).await.unwrap().is_none());
    assert_eq!(account::get_account(&pool, acct).await.unwrap().unwrap().blocked_reason.as_deref(), Some("chargeback"));

    assert!(admin_accounts::unblock(&pool, staff, acct).await.unwrap());
    assert!(device::is_license_live(&pool, acct, dev, "j", iat).await.unwrap(), "unblock restores tokens");

    let actions: Vec<String> = admin::list_audit(&pool, Some(&acct.to_string()), None, 10)
        .await
        .unwrap()
        .into_iter()
        .map(|r| r.action)
        .collect();
    assert_eq!(actions, ["unblock_account", "block_account"]);

    purge(&pool, &[acct], &[staff], &[node.id]).await;
}

#[tokio::test]
#[ignore = "needs a disposable Postgres; run ./scripts/test-integration.sh"]
async fn node_lifecycle_drain_heartbeat_retire() {
    let pool = test_pool().await;
    let (acct, dev) = new_account_with_device(&pool).await;
    let node = vpn::create_node(
        &pool,
        &NewNode { name: "Erbil-9", city: "Erbil", public_ip: "203.0.113.51", wg_public_key: "k", endpoint_port: 51820, max_peers: 10, ip_pool_cidr: "10.99.2.0/24", service_name: "wg" },
        "h",
    )
    .await
    .unwrap();
    assert_eq!(node.status, "draining", "new nodes take no peers until enabled");

    // A healthy heartbeat must not undo the drain.
    vpn::update_heartbeat(&pool, node.id, 10, 10, None, None, None).await.unwrap();
    assert_eq!(vpn::get_node(&pool, node.id).await.unwrap().unwrap().status, "draining");

    let online = vpn::update_node(&pool, node.id, &NodePatch { status: Some("online"), max_peers: Some(20), ..Default::default() })
        .await
        .unwrap()
        .unwrap();
    assert_eq!((online.status.as_str(), online.max_peers), ("online", 20));

    let session = vpn::create_session(&pool, acct, dev, node.id, "pk", Ipv4Addr::new(10, 99, 2, 2)).await.unwrap();
    assert_eq!(vpn::retire_node(&pool, node.id).await.unwrap(), RetireOutcome::HasActiveSessions(1));
    vpn::disconnect_session(&pool, session.id).await.unwrap();
    assert_eq!(vpn::retire_node(&pool, node.id).await.unwrap(), RetireOutcome::Retired);

    vpn::update_heartbeat(&pool, node.id, 10, 10, None, None, None).await.unwrap();
    assert_eq!(vpn::get_node(&pool, node.id).await.unwrap().unwrap().status, "retired");
    assert!(vpn::update_node(&pool, node.id, &NodePatch { status: Some("online"), ..Default::default() }).await.unwrap().is_none());
    assert!(!vpn::set_agent_token_hash(&pool, node.id, "x").await.unwrap());
    assert!(vpn::list_eligible_nodes(&pool).await.unwrap().iter().all(|n| n.id != node.id));

    purge(&pool, &[acct], &[], &[node.id]).await;
}

#[tokio::test]
#[ignore = "needs a disposable Postgres; run ./scripts/test-integration.sh"]
async fn account_search_and_payment_filters() {
    let pool = test_pool().await;
    let (a, _) = new_account_with_device(&pool).await;
    let (b, _) = new_account_with_device(&pool).await;

    let prefix = &a.to_string()[..13];
    let found = admin_accounts::search(&pool, Some(prefix), None, 50).await.unwrap();
    assert!(found.iter().any(|r| r.id == a));
    assert!(found.iter().all(|r| r.id.to_string().starts_with(prefix)));
    assert_eq!(found.iter().find(|r| r.id == a).unwrap().active_devices, 1);

    let order = format!("FIB-ORD-{}", Uuid::now_v7());
    payment::create_payment(&pool, b, "fib", &order, 10_000, "fib://pay", "premium_monthly", true, None).await.unwrap().unwrap();
    let by_account = payment::search_for_admin(&pool, &payment::PaymentSearch { q: Some(&b.to_string()), ..Default::default() }, 50).await.unwrap();
    assert_eq!(by_account.len(), 1);
    let by_order = payment::search_for_admin(&pool, &payment::PaymentSearch { q: Some(&order[..20]), status: Some("pending"), gateway: Some("fib"), ..Default::default() }, 50).await.unwrap();
    assert!(by_order.iter().any(|p| p.account_id == b));
    let none = payment::search_for_admin(&pool, &payment::PaymentSearch { q: Some(&b.to_string()), status: Some("success"), ..Default::default() }, 50).await.unwrap();
    assert!(none.is_empty());

    purge(&pool, &[a, b], &[], &[]).await;
}

/// The old figure was cash collected this month, so one yearly payment showed as a
/// month's recurring revenue of 100,000 IQD. Normalised it is 100,000 × 30 / 365.
#[tokio::test]
#[ignore = "needs a disposable Postgres; run ./scripts/test-integration.sh"]
async fn mrr_counts_a_yearly_plan_at_its_monthly_equivalent() {
    let pool = test_pool().await;
    let before = payment::overview_stats(&pool).await.unwrap();
    let (a, _) = new_account_with_device(&pool).await;
    let order = format!("FIB-ORD-{}", Uuid::now_v7());
    payment::create_payment(&pool, a, "fib", &order, 100_000, "fib://pay", "premium_yearly", true, None).await.unwrap().unwrap();
    settle_payment(&pool, "fib", &order, SettleStatus::Success, Some(100_000)).await.unwrap();

    let after = payment::overview_stats(&pool).await.unwrap();
    // Other tests may run in parallel, so compare against this test's own contribution
    // with a tolerance only for rounding.
    let added = after.mrr_iqd - before.mrr_iqd;
    assert!((8_000..=8_500).contains(&added), "yearly plan added {added} to MRR");
    assert!(subscription::has_active_subscription(&pool, a).await.unwrap());

    purge(&pool, &[a], &[], &[]).await;
}
