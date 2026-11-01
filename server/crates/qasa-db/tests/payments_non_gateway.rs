//! Integration tests for the payment work that does not need a live gateway: staff
//! grants, abandoned-checkout expiry, and per-account payment history.
//!
//! Run with `./scripts/test-integration.sh`.

mod support;

use chrono::Duration;
use qasa_db::repos::settlement::{settle_payment, SettleOutcome, SettleStatus};
use qasa_db::repos::subscription::{GrantInfo, RevokeGrantOutcome};
use qasa_db::repos::{account, admin, payment, subscription, worker};
use sqlx::PgPool;
use uuid::Uuid;

use support::{cleanup, test_pool};

struct Fixture {
    pool: PgPool,
    account_id: Uuid,
    admin_id: Uuid,
}

impl Fixture {
    async fn new() -> Self {
        let pool = test_pool().await;
        let account = account::upsert_account(&pool, Uuid::now_v7()).await.unwrap();
        let admin = admin::create_admin(&pool, &format!("staff-{}@test", Uuid::now_v7()), "x")
            .await
            .unwrap();
        Self {
            pool,
            account_id: account.id,
            admin_id: admin.id,
        }
    }

    fn grant(&self) -> GrantInfo<'static> {
        GrantInfo {
            admin_id: self.admin_id,
            reason: "gateway outage",
        }
    }

    async fn pay(&self, amount: i32) -> String {
        let order = format!("FIB-ORD-{}", Uuid::now_v7());
        payment::create_payment(
            &self.pool, self.account_id, "fib", &order, amount, "fib://pay", "premium_monthly",
            true, None,
        )
        .await
        .unwrap()
        .unwrap();
        order
    }

    async fn purge(&self) {
        let (acct, admin) = (self.account_id, self.admin_id);
        cleanup(
            &self.pool,
            &[
                format!("DELETE FROM admin_audit_log WHERE admin_id = '{admin}'"),
                format!("UPDATE payments SET subscription_id = NULL WHERE account_id = '{acct}'"),
                format!("DELETE FROM subscriptions WHERE account_id = '{acct}'"),
                format!(
                    "DELETE FROM webhook_events WHERE gateway_order_id IN \
                     (SELECT gateway_order_id FROM payments WHERE account_id = '{acct}')"
                ),
                format!("DELETE FROM payments WHERE account_id = '{acct}'"),
                format!("DELETE FROM accounts WHERE id = '{acct}'"),
                format!("DELETE FROM admin_users WHERE id = '{admin}'"),
            ],
        )
        .await;
    }
}

async fn audit_actions(pool: &PgPool, admin_id: Uuid) -> Vec<String> {
    sqlx::query_scalar("SELECT action FROM admin_audit_log WHERE admin_id = $1 ORDER BY created_at")
        .bind(admin_id)
        .fetch_all(pool)
        .await
        .unwrap()
}

#[tokio::test]
#[ignore = "needs a disposable Postgres; run ./scripts/test-integration.sh"]
async fn grant_makes_the_account_premium_and_is_audited() {
    let f = Fixture::new().await;
    assert!(!subscription::has_active_subscription(&f.pool, f.account_id).await.unwrap());

    let sub = subscription::grant_premium(&f.pool, f.account_id, "premium_monthly", 14, f.grant())
        .await
        .unwrap();

    assert!(subscription::has_active_subscription(&f.pool, f.account_id).await.unwrap());
    assert_eq!(sub.amount_iqd, 0, "a grant is not revenue");
    assert!(!sub.auto_renew);
    assert_eq!((sub.expires_at - sub.started_at).num_days(), 14);
    assert_eq!(audit_actions(&f.pool, f.admin_id).await, ["grant_premium"]);


    f.purge().await;
}

/// A grant on top of paid time adds to it, and revoking it gives back exactly the paid
/// time, not less.
#[tokio::test]
#[ignore = "needs a disposable Postgres; run ./scripts/test-integration.sh"]
async fn revoking_a_stacked_grant_restores_the_paid_end() {
    let f = Fixture::new().await;
    let order = f.pay(10_000).await;
    settle_payment(&f.pool, "fib", &order, SettleStatus::Success, Some(10_000))
        .await
        .unwrap();
    let paid_end = subscription::get_current(&f.pool, f.account_id)
        .await
        .unwrap()
        .unwrap()
        .expires_at;

    let grant = subscription::grant_premium(&f.pool, f.account_id, "premium_monthly", 7, f.grant())
        .await
        .unwrap();
    assert_eq!(grant.expires_at - paid_end, Duration::days(7));

    let RevokeGrantOutcome::Revoked(after) =
        subscription::revoke_grant(&f.pool, f.admin_id, grant.id).await.unwrap()
    else {
        panic!("expected revoke");
    };
    assert_eq!(after.expires_at, paid_end);
    assert_eq!(after.status, "active", "paid time is still live");

    assert!(matches!(
        subscription::revoke_grant(&f.pool, f.admin_id, grant.id).await.unwrap(),
        RevokeGrantOutcome::AlreadyRevoked
    ));
    assert_eq!(
        audit_actions(&f.pool, f.admin_id).await,
        ["grant_premium", "revoke_grant"]
    );

    f.purge().await;
}

#[tokio::test]
#[ignore = "needs a disposable Postgres; run ./scripts/test-integration.sh"]
async fn revoking_a_plain_grant_ends_premium() {
    let f = Fixture::new().await;
    let grant = subscription::grant_premium(&f.pool, f.account_id, "premium_monthly", 10, f.grant())
        .await
        .unwrap();

    let RevokeGrantOutcome::Revoked(after) =
        subscription::revoke_grant(&f.pool, f.admin_id, grant.id).await.unwrap()
    else {
        panic!("expected revoke");
    };
    assert_eq!(after.status, "expired");
    assert!(!subscription::has_active_subscription(&f.pool, f.account_id).await.unwrap());

    f.purge().await;
}

/// Once a later subscription folds the grant's remaining time into a new row, the
/// granted days cannot be told apart from paid ones, so revoke refuses.
#[tokio::test]
#[ignore = "needs a disposable Postgres; run ./scripts/test-integration.sh"]
async fn a_superseded_grant_cannot_be_revoked() {
    let f = Fixture::new().await;
    let grant = subscription::grant_premium(&f.pool, f.account_id, "premium_monthly", 10, f.grant())
        .await
        .unwrap();
    let order = f.pay(10_000).await;
    settle_payment(&f.pool, "fib", &order, SettleStatus::Success, Some(10_000))
        .await
        .unwrap();

    assert!(matches!(
        subscription::revoke_grant(&f.pool, f.admin_id, grant.id).await.unwrap(),
        RevokeGrantOutcome::Superseded
    ));
    assert!(matches!(
        subscription::revoke_grant(&f.pool, f.admin_id, Uuid::now_v7()).await.unwrap(),
        RevokeGrantOutcome::NotFound
    ));

    f.purge().await;
}

#[tokio::test]
#[ignore = "needs a disposable Postgres; run ./scripts/test-integration.sh"]
async fn abandoned_checkouts_expire_but_a_late_confirmation_still_settles() {
    let f = Fixture::new().await;
    let stale = f.pay(10_000).await;
    let fresh = f.pay(10_000).await;
    sqlx::query("UPDATE payments SET created_at = now() - interval '2 hours' WHERE gateway_order_id = $1")
        .bind(&stale)
        .execute(&f.pool)
        .await
        .unwrap();

    worker::expire_abandoned_payments(&f.pool, 60).await.unwrap();

    let status = |order: String| {
        let pool = f.pool.clone();
        async move {
            payment::find_by_gateway_order_id(&pool, &order)
                .await
                .unwrap()
                .unwrap()
                .status
        }
    };
    assert_eq!(status(stale.clone()).await, "expired");
    assert_eq!(status(fresh.clone()).await, "pending", "recent checkouts are left alone");

    let late = settle_payment(&f.pool, "fib", &stale, SettleStatus::Success, Some(10_000))
        .await
        .unwrap();
    assert!(matches!(late, SettleOutcome::Settled { .. }), "the money moved: {late:?}");
    assert_eq!(status(stale).await, "success");

    f.purge().await;
}

#[tokio::test]
#[ignore = "needs a disposable Postgres; run ./scripts/test-integration.sh"]
async fn payment_history_pages_newest_first() {
    let f = Fixture::new().await;
    let mut orders = Vec::new();
    for _ in 0..3 {
        orders.push(f.pay(10_000).await);
        tokio::time::sleep(std::time::Duration::from_millis(5)).await;
    }

    let page1 = payment::list_for_account(&f.pool, f.account_id, None, 2).await.unwrap();
    assert_eq!(page1.len(), 2);
    assert_eq!(page1[0].gateway_order_id.as_deref(), Some(orders[2].as_str()));
    assert_eq!(page1[1].gateway_order_id.as_deref(), Some(orders[1].as_str()));

    let page2 = payment::list_for_account(&f.pool, f.account_id, Some(page1[1].created_at), 2)
        .await
        .unwrap();
    assert_eq!(page2.len(), 1);
    assert_eq!(page2[0].gateway_order_id.as_deref(), Some(orders[0].as_str()));

    let other = payment::list_for_account(&f.pool, Uuid::now_v7(), None, 10).await.unwrap();
    assert!(other.is_empty());

    f.purge().await;
}
