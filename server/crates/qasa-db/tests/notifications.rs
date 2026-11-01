//! Integration tests for the notification inbox and broadcast audiences.
//!
//! Run with `./scripts/test-integration.sh`.

mod support;

use qasa_db::repos::{account, notification, push_copy, subscription};
use sqlx::PgPool;
use uuid::Uuid;

use support::{cleanup, test_pool};

/// An `all` broadcast lands in every account in the database, including those another
/// test in this file just created, so these tests must not overlap.
async fn serial() -> tokio::sync::MutexGuard<'static, ()> {
    static LOCK: std::sync::OnceLock<tokio::sync::Mutex<()>> = std::sync::OnceLock::new();
    LOCK.get_or_init(|| tokio::sync::Mutex::new(())).lock().await
}

async fn admin(pool: &PgPool) -> Uuid {
    qasa_db::repos::admin::create_admin(pool, &format!("n-{}@test", Uuid::now_v7()), "x")
        .await
        .unwrap()
        .id
}

async fn broadcast(pool: &PgPool, audience: &str) -> Uuid {
    let n = notification::create(pool, "Hello", None, Some("مرحبا"), "Body", None, None, audience, None)
        .await
        .unwrap();
    notification::fanout_receipts(pool, n.id).await.unwrap();
    n.id
}

async fn has_receipt(pool: &PgPool, notification_id: Uuid, account_id: Uuid) -> bool {
    sqlx::query_scalar("SELECT EXISTS(SELECT 1 FROM notification_receipts WHERE notification_id = $1 AND account_id = $2)")
        .bind(notification_id)
        .bind(account_id)
        .fetch_one(pool)
        .await
        .unwrap()
}

async fn purge(pool: &PgPool, accounts: &[Uuid], notifications: &[Uuid], admins: &[Uuid]) {
    let mut sql = Vec::new();
    for n in notifications {
        sql.push(format!("DELETE FROM notifications WHERE id = '{n}'"));
    }
    for a in accounts {
        sql.extend([
            format!("DELETE FROM notification_receipts WHERE account_id = '{a}'"),
            format!("DELETE FROM subscriptions WHERE account_id = '{a}'"),
            format!("DELETE FROM admin_audit_log WHERE target_id = '{a}'"),
            format!("DELETE FROM accounts WHERE id = '{a}'"),
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

/// The regression: `audience` was stored but ignored, so "subscribers only" notices
/// reached every account.
#[tokio::test]
#[ignore = "needs a disposable Postgres; run ./scripts/test-integration.sh"]
async fn broadcasts_reach_only_their_audience() {
    let _serial = serial().await;
    let pool = test_pool().await;
    let staff = admin(&pool).await;
    let premium = account::upsert_account(&pool, Uuid::now_v7()).await.unwrap().id;
    let free = account::upsert_account(&pool, Uuid::now_v7()).await.unwrap().id;
    let blocked = account::upsert_account(&pool, Uuid::now_v7()).await.unwrap().id;
    subscription::grant_premium(&pool, premium, "premium_monthly", 30, subscription::GrantInfo { admin_id: staff, reason: "t" })
        .await
        .unwrap();
    qasa_db::repos::admin_accounts::block(&pool, staff, blocked, "t").await.unwrap();

    let all = broadcast(&pool, "all").await;
    let subs = broadcast(&pool, "subscribers").await;
    let frees = broadcast(&pool, "free").await;

    assert!(has_receipt(&pool, all, premium).await && has_receipt(&pool, all, free).await);
    assert!(has_receipt(&pool, subs, premium).await);
    assert!(!has_receipt(&pool, subs, free).await, "free users must not get subscriber notices");
    assert!(has_receipt(&pool, frees, free).await);
    assert!(!has_receipt(&pool, frees, premium).await);
    assert!(!has_receipt(&pool, all, blocked).await, "blocked accounts are skipped");

    // The schema refuses an audience the fan-out does not understand.
    let bad = notification::create(&pool, "x", None, None, "x", None, None, "vip", None).await;
    assert!(bad.is_err());

    purge(&pool, &[premium, free, blocked], &[all, subs, frees], &[staff]).await;
}

#[tokio::test]
#[ignore = "needs a disposable Postgres; run ./scripts/test-integration.sh"]
async fn read_read_all_delete_and_unread_count() {
    let _serial = serial().await;
    let pool = test_pool().await;
    let acct = account::upsert_account(&pool, Uuid::now_v7()).await.unwrap().id;
    let other = account::upsert_account(&pool, Uuid::now_v7()).await.unwrap().id;
    let mut ids = Vec::new();
    for i in 0..3 {
        let n = notification::notify_account(&pool, acct, &push_copy::SUB_EXPIRING_3D, &format!("t:{acct}:{i}"))
            .await
            .unwrap()
            .unwrap();
        ids.push(n.id);
    }
    let shared = broadcast(&pool, "all").await;

    assert_eq!(notification::unread_count(&pool, acct).await.unwrap(), 4);

    assert!(notification::mark_read(&pool, acct, ids[0]).await.unwrap());
    assert!(!notification::mark_read(&pool, other, ids[0]).await.unwrap(), "not in other's inbox");
    assert_eq!(notification::unread_count(&pool, acct).await.unwrap(), 3);

    assert!(notification::delete_for_account(&pool, acct, shared).await.unwrap());
    assert!(!notification::delete_for_account(&pool, acct, shared).await.unwrap());
    assert!(notification::get_for_account(&pool, acct, shared).await.unwrap().is_none());
    assert!(
        notification::get_for_account(&pool, other, shared).await.unwrap().is_some(),
        "deleting a broadcast removes it from one inbox only"
    );
    assert_eq!(notification::unread_count(&pool, acct).await.unwrap(), 2);

    assert_eq!(notification::mark_all_read(&pool, acct).await.unwrap(), 2);
    assert_eq!(notification::unread_count(&pool, acct).await.unwrap(), 0);
    assert!(!notification::mark_read(&pool, acct, shared).await.unwrap(), "deleted stays deleted");

    let page1 = notification::list_for_account(&pool, acct, None, 2).await.unwrap();
    assert_eq!(page1.len(), 2);
    let page2 = notification::list_for_account(&pool, acct, Some(page1[1].created_at), 2).await.unwrap();
    assert_eq!(page2.len(), 1, "3 left after deleting the broadcast");
    assert!(page1.iter().chain(&page2).all(|n| n.id != shared));

    purge(&pool, &[acct, other], &[shared], &[]).await;
}

/// Broadcast receipts are pushed once by the worker; targeted ones are pushed when
/// created, and read or deleted receipts are not pushed at all.
#[tokio::test]
#[ignore = "needs a disposable Postgres; run ./scripts/test-integration.sh"]
async fn pending_broadcast_pushes_are_handed_out_once() {
    let _serial = serial().await;
    let pool = test_pool().await;
    let a = account::upsert_account(&pool, Uuid::now_v7()).await.unwrap().id;
    let b = account::upsert_account(&pool, Uuid::now_v7()).await.unwrap().id;
    let targeted = notification::notify_account(&pool, a, &push_copy::SUB_EXPIRING_3D, &format!("t:{a}"))
        .await
        .unwrap()
        .unwrap();
    let n = broadcast(&pool, "all").await;
    notification::mark_read(&pool, b, n).await.unwrap();

    let mine = |rows: &[notification::PendingBroadcastPush], acct: Uuid| {
        rows.iter().filter(|r| r.account_id == acct).map(|r| r.notification_id).collect::<Vec<_>>()
    };
    let pending = notification::pending_broadcast_pushes(&pool, 24, 100_000).await.unwrap();
    assert_eq!(mine(&pending, a), [n], "only the broadcast; targeted notices push on creation");
    assert!(mine(&pending, b).is_empty(), "already read");
    assert!(pending.iter().all(|r| r.notification_id != targeted.id));
    let row = pending.iter().find(|r| r.account_id == a).unwrap();
    assert_eq!(row.title_ar.as_deref(), Some("مرحبا"));
    assert_eq!(row.title_ckb, None, "missing translation: the worker falls back to English");

    notification::mark_pushed(&pool, n, a).await.unwrap();
    let again = notification::pending_broadcast_pushes(&pool, 24, 100_000).await.unwrap();
    assert!(mine(&again, a).is_empty());

    purge(&pool, &[a, b], &[n, targeted.id], &[]).await;
}
