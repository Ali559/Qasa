//! Integration tests for account deletion and breach-monitor management.
//!
//! Run with `./scripts/test-integration.sh`.

mod support;

use qasa_db::repos::settlement::{settle_payment, SettleOutcome, SettleStatus};
use qasa_db::repos::{account, account_deletion, device, monitored_email, notification, payment, push_copy, subscription};
use sqlx::PgPool;
use uuid::Uuid;

use support::{cleanup, test_pool};

async fn count(pool: &PgPool, sql: &str, account_id: Uuid) -> i64 {
    sqlx::query_scalar(sql).bind(account_id).fetch_one(pool).await.unwrap()
}

async fn purge(pool: &PgPool, account_id: Uuid) {
    cleanup(
        pool,
        &[
            format!("UPDATE payments SET subscription_id = NULL WHERE account_id = '{account_id}'"),
            format!("DELETE FROM subscriptions WHERE account_id = '{account_id}'"),
            format!(
                "DELETE FROM webhook_events WHERE gateway_order_id IN \
                 (SELECT gateway_order_id FROM payments WHERE account_id = '{account_id}')"
            ),
            format!("DELETE FROM payments WHERE account_id = '{account_id}'"),
            format!("DELETE FROM monitored_email_hashes WHERE account_id = '{account_id}'"),
            format!("DELETE FROM notification_receipts WHERE account_id = '{account_id}'"),
            format!("DELETE FROM devices WHERE account_id = '{account_id}'"),
            format!("DELETE FROM accounts WHERE id = '{account_id}'"),
        ],
    )
    .await;
}

/// A premium account with a device, a monitor, a targeted notification and a paid
/// subscription, so deletion has something of every kind to remove.
async fn populated_account(pool: &PgPool) -> (Uuid, Uuid) {
    let account_id = account::upsert_account(pool, Uuid::now_v7()).await.unwrap().id;
    let dev = device::insert_device(pool, account_id, &format!("fp-{}", Uuid::now_v7()), "Phone", "ios", Some("Erbil"))
        .await
        .unwrap();
    device::update_prefs(&pool, dev.id, Some("en"), Some("fcm-token"), Some(true)).await.unwrap();
    monitored_email::upsert_hash(pool, account_id, &"ab".repeat(32), Some("me@…")).await.unwrap();
    notification::notify_account(pool, account_id, &push_copy::SUB_EXPIRING_3D, &format!("t:{account_id}"))
        .await
        .unwrap();
    let order = format!("FIB-ORD-{}", Uuid::now_v7());
    payment::create_payment(pool, account_id, "fib", &order, 10_000, "fib://pay", "premium_monthly", true, None)
        .await
        .unwrap()
        .unwrap();
    settle_payment(pool, "fib", &order, SettleStatus::Success, Some(10_000)).await.unwrap();
    (account_id, dev.id)
}

#[tokio::test]
#[ignore = "needs a disposable Postgres; run ./scripts/test-integration.sh"]
async fn deleting_an_account_removes_personal_data_and_keeps_financial_records() {
    let pool = test_pool().await;
    let (account_id, device_id) = populated_account(&pool).await;
    let pending = format!("FIB-ORD-{}", Uuid::now_v7());
    payment::create_payment(&pool, account_id, "fib", &pending, 10_000, "fib://pay", "premium_monthly", true, None)
        .await
        .unwrap()
        .unwrap();

    let summary = account_deletion::delete_account(&pool, account_id)
        .await
        .unwrap()
        .expect("deleted");
    assert_eq!(summary.devices, 1);
    assert_eq!(summary.monitored_emails, 1);
    assert_eq!(summary.notifications, 1);
    assert_eq!(summary.subscriptions_ended, 1);
    assert_eq!(summary.payments_closed, 1);

    for (sql, what) in [
        ("SELECT count(*) FROM devices WHERE account_id = $1", "devices"),
        ("SELECT count(*) FROM monitored_email_hashes WHERE account_id = $1", "monitors"),
        ("SELECT count(*) FROM notification_receipts WHERE account_id = $1", "receipts"),
    ] {
        assert_eq!(count(&pool, sql, account_id).await, 0, "{what} must be gone");
    }
    assert_eq!(
        count(&pool, "SELECT count(*) FROM payments WHERE account_id = $1", account_id).await,
        2,
        "payments are financial records and stay"
    );
    assert!(!subscription::has_active_subscription(&pool, account_id).await.unwrap());
    assert!(!device::is_license_live(&pool, account_id, device_id, "j", u64::MAX / 2).await.unwrap());

    // The tombstone: invisible to normal lookups, never revived by register.
    assert!(account::get_account(&pool, account_id).await.unwrap().is_none());
    let again = account::upsert_account(&pool, account_id).await.unwrap();
    assert!(again.deleted_at.is_some());
    assert!(account_deletion::delete_account(&pool, account_id).await.unwrap().is_none());

    // A late confirmation for the closed checkout grants nothing.
    let late = settle_payment(&pool, "fib", &pending, SettleStatus::Success, Some(10_000))
        .await
        .unwrap();
    assert!(matches!(late, SettleOutcome::AccountDeleted { .. }), "{late:?}");
    assert!(late.is_terminal(SettleStatus::Success));
    assert!(!subscription::has_active_subscription(&pool, account_id).await.unwrap());

    purge(&pool, account_id).await;
}

#[tokio::test]
#[ignore = "needs a disposable Postgres; run ./scripts/test-integration.sh"]
async fn deleting_one_account_leaves_others_alone() {
    let pool = test_pool().await;
    let (gone, _) = populated_account(&pool).await;
    let (kept, kept_device) = populated_account(&pool).await;

    account_deletion::delete_account(&pool, gone).await.unwrap().unwrap();

    assert!(account::get_account(&pool, kept).await.unwrap().is_some());
    assert!(subscription::has_active_subscription(&pool, kept).await.unwrap());
    assert_eq!(monitored_email::list_for_account(&pool, kept).await.unwrap().len(), 1);
    assert!(device::is_license_live(&pool, kept, kept_device, "j", u64::MAX / 2).await.unwrap());

    purge(&pool, gone).await;
    purge(&pool, kept).await;
}

#[tokio::test]
#[ignore = "needs a disposable Postgres; run ./scripts/test-integration.sh"]
async fn monitor_delete_by_id_is_scoped_to_the_account() {
    let pool = test_pool().await;
    let a = account::upsert_account(&pool, Uuid::now_v7()).await.unwrap().id;
    let b = account::upsert_account(&pool, Uuid::now_v7()).await.unwrap().id;
    let row = monitored_email::upsert_hash(&pool, a, &"cd".repeat(32), None).await.unwrap();

    assert!(!monitored_email::delete_by_id(&pool, b, row.id).await.unwrap(), "not b's monitor");
    assert!(monitored_email::delete_by_id(&pool, a, row.id).await.unwrap());
    assert!(!monitored_email::delete_by_id(&pool, a, row.id).await.unwrap());

    purge(&pool, a).await;
    purge(&pool, b).await;
}
