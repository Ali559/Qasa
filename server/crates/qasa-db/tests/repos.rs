//! Integration tests for the repository functions that exist to correct security bugs,
//! and which therefore must be exercised against a real Postgres.
//!
//! These are `#[ignore]`d by default so that a plain `cargo test` never reports them as
//! having passed when they did not run. Run them with:
//!
//! ```sh
//! ./scripts/test-integration.sh
//! ```
//!
//! See `support` for the disposable-database guard that prevents these from being pointed
//! at a development or production database.

mod support;

use qasa_db::repos::{account, device, vpn};
use sqlx::PgPool;
use std::net::Ipv4Addr;
use std::sync::Arc;
use tokio::sync::Mutex;
use uuid::Uuid;

use support::{cleanup, test_pool};

/// `reconcile_active_peers` is a whole-table operation, so tests that assert on its
/// global effects must not run concurrently with each other.
fn reconcile_lock() -> &'static Arc<Mutex<()>> {
    static LOCK: std::sync::OnceLock<Arc<Mutex<()>>> = std::sync::OnceLock::new();
    LOCK.get_or_init(|| Arc::new(Mutex::new(())))
}

struct Account {
    id: Uuid,
    device_id: Uuid,
}

/// Create an account + device pair to hang sessions and revocations off.
///
/// Identifiers are unique per test so cleanup can target exactly this test's rows and
/// never remove another test's fixtures.
async fn account_with_device(pool: &PgPool) -> Account {
    let account = account::upsert_account(pool, Uuid::now_v7())
        .await
        .expect("upsert_account");
    let dev = device::insert_device(
        pool,
        account.id,
        &format!("fp-{}", Uuid::now_v7()),
        "Test Device",
        "ios",
        None,
    )
    .await
    .expect("insert_device");
    Account {
        id: account.id,
        device_id: dev.id,
    }
}

async fn node(pool: &PgPool) -> Uuid {
    let id = Uuid::now_v7();
    sqlx::query(
        r#"
        INSERT INTO vpn_nodes (id, name, city, public_ip, wg_public_key, agent_token_hash, ip_pool_cidr)
        VALUES ($1, $2, 'Erbil', '203.0.113.99', 'k', 'h', $3::cidr)
        "#,
    )
    .bind(id)
    .bind(format!("Test Node {id}"))
    // Unique pool per node so parallel tests cannot collide on an assigned address.
    .bind(format!("10.64.{}.0/24", id.as_fields().0 >> 8 & 0xff))
    .execute(pool)
    .await
    .expect("insert node");
    id
}

async fn active_peers_of(pool: &PgPool, node_id: Uuid) -> i32 {
    sqlx::query_scalar("SELECT active_peers FROM vpn_nodes WHERE id = $1")
        .bind(node_id)
        .fetch_one(pool)
        .await
        .expect("read active_peers")
}

async fn connect_session(pool: &PgPool, node_id: Uuid) -> (Uuid, Account) {
    let acct = account_with_device(pool).await;
    // A node allows one live peer per address and per key, so every session needs its own.
    static NEXT_HOST: std::sync::atomic::AtomicU8 = std::sync::atomic::AtomicU8::new(2);
    let host = NEXT_HOST.fetch_add(1, std::sync::atomic::Ordering::Relaxed);
    let session = vpn::create_session(
        pool,
        acct.id,
        acct.device_id,
        node_id,
        &format!("pk-{}", Uuid::now_v7()),
        Ipv4Addr::new(10, 64, 99, host),
    )
    .await
    .expect("create_session");
    (session.id, acct)
}

/// Deletes everything a node-based test created, child rows first.
async fn purge_node(pool: &PgPool, node_id: Uuid, accounts: &[&Account]) {
    let mut sqls = vec![
        format!("DELETE FROM vpn_sessions WHERE node_id = '{node_id}'"),
        format!("DELETE FROM vpn_nodes WHERE id = '{node_id}'"),
    ];
    sqls.extend(accounts.iter().map(|a| purge_account_sql(a)));
    cleanup(pool, &sqls).await;
}

/// Child-first delete statements for one account and its device.
fn purge_account_sql(acct: &Account) -> String {
    format!(
        "DELETE FROM license_revocations WHERE account_id = '{id}'; \
         DELETE FROM vpn_sessions WHERE account_id = '{id}'; \
         DELETE FROM devices WHERE account_id = '{id}'; \
         DELETE FROM accounts WHERE id = '{id}';",
        id = acct.id,
    )
}

async fn purge_account(pool: &PgPool, acct: &Account) {
    cleanup(pool, &[purge_account_sql(acct)]).await;
}

// ---------------------------------------------------------------- active_peers

/// The reconciler is the only thing that repairs a missed decrement, so it has to be
/// correct for every case the increment path can produce.
#[tokio::test]
#[ignore = "needs a disposable Postgres; run ./scripts/test-integration.sh"]
async fn reconcile_repairs_an_inflated_counter() {
    let pool = test_pool().await;
    let _guard = reconcile_lock().lock().await;
    let node_id = node(&pool).await;

    // Simulate the real drift: sessions exist but the counter was never decremented.
    let mut accts = Vec::new();
    for _ in 0..2 {
        let (_, a) = connect_session(&pool, node_id).await;
        accts.push(a);
    }
    sqlx::query("UPDATE vpn_nodes SET active_peers = 17 WHERE id = $1")
        .bind(node_id)
        .execute(&pool)
        .await
        .unwrap();

    let fixed = vpn::reconcile_active_peers(&pool).await.unwrap();
    assert_eq!(active_peers_of(&pool, node_id).await, 2);
    assert!(fixed >= 1, "expected at least the drifted node to be reported");

    purge_node(&pool, node_id, &accts.iter().collect::<Vec<_>>()).await;
}

#[tokio::test]
#[ignore = "needs a disposable Postgres; run ./scripts/test-integration.sh"]
async fn reconcile_zeroes_a_node_with_no_live_sessions() {
    let pool = test_pool().await;
    let _guard = reconcile_lock().lock().await;
    let node_id = node(&pool).await;

    let (session_id, acct) = connect_session(&pool, node_id).await;
    // Counter is wrong in the other direction: sessions closed but count stuck high.
    assert!(vpn::disconnect_session(&pool, session_id).await.unwrap());
    sqlx::query("UPDATE vpn_nodes SET active_peers = 5 WHERE id = $1")
        .bind(node_id)
        .execute(&pool)
        .await
        .unwrap();

    vpn::reconcile_active_peers(&pool).await.unwrap();
    assert_eq!(active_peers_of(&pool, node_id).await, 0);

    purge_node(&pool, node_id, &[&acct]).await;
}

/// A crash that leaves the session row open is the drift this job exists for.
#[tokio::test]
#[ignore = "needs a disposable Postgres; run ./scripts/test-integration.sh"]
async fn reconcile_counts_sessions_that_were_never_disconnected() {
    let pool = test_pool().await;
    let _guard = reconcile_lock().lock().await;
    let node_id = node(&pool).await;

    let (a, acct_a) = connect_session(&pool, node_id).await;
    let (_, acct_b) = connect_session(&pool, node_id).await;
    let _ = vpn::disconnect_session(&pool, a).await;

    vpn::reconcile_active_peers(&pool).await.unwrap();
    assert_eq!(active_peers_of(&pool, node_id).await, 1);

    purge_node(&pool, node_id, &[&acct_a, &acct_b]).await;
}

#[tokio::test]
#[ignore = "needs a disposable Postgres; run ./scripts/test-integration.sh"]
async fn reconcile_is_idempotent() {
    let pool = test_pool().await;
    let _guard = reconcile_lock().lock().await;
    let node_id = node(&pool).await;
    let (_, acct) = connect_session(&pool, node_id).await;

    vpn::reconcile_active_peers(&pool).await.unwrap();
    // A second pass has nothing left to correct.
    assert_eq!(vpn::reconcile_active_peers(&pool).await.unwrap(), 0);
    assert_eq!(active_peers_of(&pool, node_id).await, 1);

    purge_node(&pool, node_id, &[&acct]).await;
}

#[tokio::test]
#[ignore = "needs a disposable Postgres; run ./scripts/test-integration.sh"]
async fn reconcile_never_produces_a_negative_count() {
    let pool = test_pool().await;
    let _guard = reconcile_lock().lock().await;
    let node_id = node(&pool).await;
    sqlx::query("UPDATE vpn_nodes SET active_peers = -5 WHERE id = $1")
        .bind(node_id)
        .execute(&pool)
        .await
        .unwrap();

    vpn::reconcile_active_peers(&pool).await.unwrap();
    assert_eq!(active_peers_of(&pool, node_id).await, 0);

    purge_node(&pool, node_id, &[]).await;
}

/// Node selection orders by `active_peers ASC`, so a permanently inflated counter starves
/// the node of peers. This is the end-to-end consequence the reconciler prevents.
#[tokio::test]
#[ignore = "needs a disposable Postgres; run ./scripts/test-integration.sh"]
async fn inflated_counter_would_starve_a_node() {
    let pool = test_pool().await;
    let _guard = reconcile_lock().lock().await;
    let busy = node(&pool).await;
    let idle = node(&pool).await;
    let (_, acct) = connect_session(&pool, busy).await;
    sqlx::query("UPDATE vpn_nodes SET active_peers = 999 WHERE id = $1")
        .bind(busy)
        .execute(&pool)
        .await
        .unwrap();

    vpn::reconcile_active_peers(&pool).await.unwrap();

    let eligible = vpn::list_eligible_nodes(&pool).await.unwrap();
    let first = eligible
        .iter()
        .find(|n| n.id == busy)
        .expect("busy node eligible");
    assert_eq!(first.active_peers, 1, "busy node should no longer look full");

    purge_node(&pool, busy, &[&acct]).await;
    purge_node(&pool, idle, &[]).await;
}

// ------------------------------------------------------------- license revocation

/// The regression: `license_revocations` used to have no writer, so this check was
/// permanently `false` and revocation did nothing.
#[tokio::test]
#[ignore = "needs a disposable Postgres; run ./scripts/test-integration.sh"]
async fn revoke_license_jti_makes_the_token_rejected() {
    let pool = test_pool().await;
    let acct = account_with_device(&pool).await;
    let jti = format!("jti-{}", Uuid::now_v7());

    assert!(!device::is_jti_revoked(&pool, &jti).await.unwrap());

    assert!(
        device::revoke_license_jti(&pool, &jti, acct.id, Some("chargeback"))
            .await
            .unwrap()
    );

    assert!(
        device::is_jti_revoked(&pool, &jti).await.unwrap(),
        "revoked jti must be rejected by the auth path"
    );

    purge_account(&pool, &acct).await;
}

#[tokio::test]
#[ignore = "needs a disposable Postgres; run ./scripts/test-integration.sh"]
async fn revoke_is_idempotent_and_unrevoke_restores() {
    let pool = test_pool().await;
    let acct = account_with_device(&pool).await;
    let jti = format!("jti-{}", Uuid::now_v7());

    assert!(device::revoke_license_jti(&pool, &jti, acct.id, None).await.unwrap());
    // Second call must not error and must report that nothing new was created.
    assert!(!device::revoke_license_jti(&pool, &jti, acct.id, None).await.unwrap());

    assert!(device::unrevoke_license_jti(&pool, &jti).await.unwrap());
    assert!(!device::is_jti_revoked(&pool, &jti).await.unwrap());
    // Un-revoking something already gone is not an error.
    assert!(!device::unrevoke_license_jti(&pool, &jti).await.unwrap());

    purge_account(&pool, &acct).await;
}

#[tokio::test]
#[ignore = "needs a disposable Postgres; run ./scripts/test-integration.sh"]
async fn revoke_trims_whitespace_and_rejects_blank() {
    let pool = test_pool().await;
    let acct = account_with_device(&pool).await;

    assert!(device::revoke_license_jti(&pool, "   \t ", acct.id, None)
        .await
        .is_err());

    let jti = format!("jti-{}", Uuid::now_v7());
    device::revoke_license_jti(&pool, &format!("  {jti}  "), acct.id, None)
        .await
        .unwrap();
    assert!(device::is_jti_revoked(&pool, &jti).await.unwrap());

    purge_account(&pool, &acct).await;
}

#[tokio::test]
#[ignore = "needs a disposable Postgres; run ./scripts/test-integration.sh"]
async fn revoke_only_affects_the_named_token() {
    let pool = test_pool().await;
    let acct = account_with_device(&pool).await;
    let revoked = format!("jti-{}", Uuid::now_v7());
    let other = format!("jti-{}", Uuid::now_v7());

    device::revoke_license_jti(&pool, &revoked, acct.id, None)
        .await
        .unwrap();

    assert!(device::is_jti_revoked(&pool, &revoked).await.unwrap());
    assert!(
        !device::is_jti_revoked(&pool, &other).await.unwrap(),
        "revoking one device must not lock out the account's other devices"
    );

    purge_account(&pool, &acct).await;
}

#[tokio::test]
#[ignore = "needs a disposable Postgres; run ./scripts/test-integration.sh"]
async fn revoke_rejects_an_unknown_account() {
    let pool = test_pool().await;
    // account_id is NOT NULL REFERENCES accounts(id); the FK must reject a bogus owner
    // rather than silently creating an orphan revocation.
    let err = device::revoke_license_jti(&pool, "jti-x", Uuid::now_v7(), None)
        .await
        .unwrap_err();
    assert!(
        err.to_string().contains("foreign key") || err.to_string().contains("violates"),
        "expected a foreign-key error, got: {err}"
    );
    // Nothing should have been written.
    assert!(!device::is_jti_revoked(&pool, "jti-x").await.unwrap());
}

#[tokio::test]
#[ignore = "needs a disposable Postgres; run ./scripts/test-integration.sh"]
async fn list_revocations_includes_new_entries() {
    let pool = test_pool().await;
    let acct = account_with_device(&pool).await;
    let jti = format!("jti-list-{}", Uuid::now_v7());
    device::revoke_license_jti(&pool, &jti, acct.id, Some("manual"))
        .await
        .unwrap();

    let rows = device::list_license_revocations(&pool).await.unwrap();
    let row = rows.iter().find(|r| r.jti == jti).expect("row present");
    assert_eq!(row.account_id, acct.id);
    assert_eq!(row.reason.as_deref(), Some("manual"));

    purge_account(&pool, &acct).await;
}

// ------------------------------------------------------------- unlink and re-link

fn now_secs() -> u64 {
    std::time::SystemTime::now()
        .duration_since(std::time::UNIX_EPOCH)
        .unwrap()
        .as_secs()
}

/// The regression: unlinking only set `revoked_at`, which the auth path never read, so
/// the unlinked device kept full API access until its 30-day token expired.
#[tokio::test]
#[ignore = "needs a disposable Postgres; run ./scripts/test-integration.sh"]
async fn unlinked_device_token_is_rejected() {
    let pool = test_pool().await;
    let acct = account_with_device(&pool).await;
    let iat = now_secs() - 5;

    assert!(device::is_license_live(&pool, acct.id, acct.device_id, "jti-a", iat)
        .await
        .unwrap());

    assert!(device::revoke_device(&pool, acct.id, acct.device_id).await.unwrap());

    assert!(
        !device::is_license_live(&pool, acct.id, acct.device_id, "jti-a", iat)
            .await
            .unwrap(),
        "a token of an unlinked device must stop working at once"
    );

    purge_account(&pool, &acct).await;
}

#[tokio::test]
#[ignore = "needs a disposable Postgres; run ./scripts/test-integration.sh"]
async fn token_for_another_account_is_rejected() {
    let pool = test_pool().await;
    let a = account_with_device(&pool).await;
    let b = account_with_device(&pool).await;

    assert!(!device::is_license_live(&pool, b.id, a.device_id, "jti-x", now_secs())
        .await
        .unwrap());

    purge_account(&pool, &a).await;
    purge_account(&pool, &b).await;
}

/// The regression: re-registering an unlinked device inserted a duplicate
/// `(account_id, fingerprint)` row and failed with a 500. It now gets its row back, and
/// tokens from before the unlink stay dead.
#[tokio::test]
#[ignore = "needs a disposable Postgres; run ./scripts/test-integration.sh"]
async fn unlinked_device_can_relink_without_reviving_old_tokens() {
    let pool = test_pool().await;
    let acct = account_with_device(&pool).await;
    let row = sqlx::query_as::<_, (String,)>("SELECT fingerprint FROM devices WHERE id = $1")
        .bind(acct.device_id)
        .fetch_one(&pool)
        .await
        .unwrap();
    let old_iat = now_secs() - 5;

    device::revoke_device(&pool, acct.id, acct.device_id).await.unwrap();

    let found = device::find_by_fingerprint(&pool, acct.id, &row.0)
        .await
        .unwrap()
        .expect("unlinked row is still found");
    assert!(found.revoked_at.is_some());

    // iat has one-second resolution; a new token is issued in a later second.
    tokio::time::sleep(std::time::Duration::from_millis(1100)).await;
    let relinked = device::reactivate_device(&pool, found.id, "Renamed", "android", None)
        .await
        .unwrap();
    assert_eq!(relinked.id, acct.device_id);
    assert!(relinked.revoked_at.is_none());
    assert_eq!(relinked.device_name, "Renamed");
    assert_eq!(device::count_active(&pool, acct.id).await.unwrap(), 1);

    assert!(
        !device::is_license_live(&pool, acct.id, acct.device_id, "jti-old", old_iat)
            .await
            .unwrap(),
        "tokens issued before the unlink must not come back"
    );
    assert!(device::is_license_live(&pool, acct.id, acct.device_id, "jti-new", now_secs())
        .await
        .unwrap());

    purge_account(&pool, &acct).await;
}

#[tokio::test]
#[ignore = "needs a disposable Postgres; run ./scripts/test-integration.sh"]
async fn unlink_others_rejects_their_tokens_and_keeps_the_primary() {
    let pool = test_pool().await;
    let acct = account_with_device(&pool).await;
    let second = device::insert_device(
        &pool,
        acct.id,
        &format!("fp-{}", Uuid::now_v7()),
        "Second",
        "android",
        None,
    )
    .await
    .unwrap();
    let iat = now_secs() - 5;

    assert_eq!(device::revoke_all_except_primary(&pool, acct.id).await.unwrap(), 1);

    assert!(device::is_license_live(&pool, acct.id, acct.device_id, "p", iat)
        .await
        .unwrap());
    assert!(!device::is_license_live(&pool, acct.id, second.id, "s", iat)
        .await
        .unwrap());

    purge_account(&pool, &acct).await;
}

// ------------------------------------------------------------- lapsed premium

/// When Premium lapses the limit drops to 1 while several devices stay linked. Only the
/// primary (oldest) is within the limit; unlinking it promotes the next one.
#[tokio::test]
#[ignore = "needs a disposable Postgres; run ./scripts/test-integration.sh"]
async fn only_the_primary_is_within_a_free_limit() {
    let pool = test_pool().await;
    let acct = account_with_device(&pool).await;
    let second = device::insert_device(
        &pool,
        acct.id,
        &format!("fp-{}", Uuid::now_v7()),
        "Second",
        "android",
        None,
    )
    .await
    .unwrap();

    assert!(device::is_within_limit(&pool, acct.id, acct.device_id, 1).await.unwrap());
    assert!(!device::is_within_limit(&pool, acct.id, second.id, 1).await.unwrap());
    assert!(device::is_within_limit(&pool, acct.id, second.id, 5).await.unwrap());

    device::revoke_device(&pool, acct.id, acct.device_id).await.unwrap();
    assert!(!device::is_within_limit(&pool, acct.id, acct.device_id, 1).await.unwrap());
    assert!(
        device::is_within_limit(&pool, acct.id, second.id, 1).await.unwrap(),
        "with the primary unlinked, the next device takes the free slot"
    );

    purge_account(&pool, &acct).await;
}

// ------------------------------------------------------------- unlink teardown

/// Unlinking used to leave the push token (the unlinked phone kept getting alerts for
/// the account) and the VPN session running.
#[tokio::test]
#[ignore = "needs a disposable Postgres; run ./scripts/test-integration.sh"]
async fn unlink_drops_push_token_and_ends_vpn_session() {
    let pool = test_pool().await;
    let node_id = node(&pool).await;
    let (session_id, acct) = connect_session(&pool, node_id).await;
    device::update_prefs(&pool, acct.device_id, Some("en"), Some("fcm-token"), Some(true))
        .await
        .unwrap();
    assert_eq!(active_peers_of(&pool, node_id).await, 1);

    device::revoke_device(&pool, acct.id, acct.device_id).await.unwrap();

    let (token, enabled): (Option<String>, bool) = sqlx::query_as(
        "SELECT push_token, notifications_enabled FROM devices WHERE id = $1",
    )
    .bind(acct.device_id)
    .fetch_one(&pool)
    .await
    .unwrap();
    assert_eq!(token, None);
    assert!(!enabled);

    let ended: bool =
        sqlx::query_scalar("SELECT disconnected_at IS NOT NULL FROM vpn_sessions WHERE id = $1")
            .bind(session_id)
            .fetch_one(&pool)
            .await
            .unwrap();
    assert!(ended, "the unlinked device's VPN session must end");
    assert_eq!(active_peers_of(&pool, node_id).await, 0);

    purge_node(&pool, node_id, &[&acct]).await;
}
