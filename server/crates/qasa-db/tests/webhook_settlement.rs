//! End-to-end test for the payment webhook settlement path, against a real Postgres.
//!
//! The bug this guards against: the API unconditionally registered stub gateways whose
//! `verify_webhook` performed no signature check at all, so an unauthenticated
//! `{"order_id": "...", "status": "success"}` granted a full-price subscription.
//!
//! Verified against a running server: with the pre-fix behaviour the request below
//! returned HTTP 200 and created a 100,000 IQD subscription.
//!
//! Run with `./scripts/test-integration.sh`.

mod support;

use qasa_db::repos::settlement::{settle_payment, SettleOutcome, SettleStatus};
use qasa_db::repos::{account, payment, subscription};
use sqlx::PgPool;
use uuid::Uuid;

use support::{cleanup, test_pool};

/// The gateway gate lives in the API (a non-live gateway never reaches settlement); the
/// rest is the shared `settle_payment` both the API and the retry job call.
async fn settle(
    pool: &PgPool,
    gateway_settles: bool,
    gateway_order_id: &str,
    reported_amount: Option<i32>,
) -> Result<(), String> {
    if !gateway_settles {
        return Err("gateway not live".into());
    }
    match settle_payment(pool, "fib", gateway_order_id, SettleStatus::Success, reported_amount)
        .await
        .map_err(|e| e.to_string())?
    {
        SettleOutcome::Settled { .. } => Ok(()),
        SettleOutcome::Unchanged => Err("already settled".into()),
        SettleOutcome::NotFound => Err("not found".into()),
        SettleOutcome::AmountMismatch { reported, expected } => Err(format!(
            "amount mismatch: reported {reported}, charged {expected}"
        )),
        SettleOutcome::Failed { .. } => Err("failed".into()),
        SettleOutcome::AccountDeleted { .. } => Err("account deleted".into()),
    }
}

struct Fixture {
    pool: PgPool,
    account_id: Uuid,
    order_id: String,
}

impl Fixture {
    /// Undoes everything the fixture and the assertions created, child rows first.
    async fn purge(&self) {
        let sqls: Vec<String> = vec![
            format!(
                "UPDATE payments SET subscription_id = NULL WHERE account_id = '{}'",
                self.account_id
            ),
            format!("DELETE FROM subscriptions WHERE account_id = '{}'", self.account_id),
            format!("DELETE FROM payments WHERE account_id = '{}'", self.account_id),
            format!(
                "DELETE FROM webhook_events WHERE gateway_order_id = '{}'",
                self.order_id
            ),
            format!("DELETE FROM accounts WHERE id = '{}'", self.account_id),
        ];
        cleanup(&self.pool, &sqls).await;
    }
}

async fn fixture(amount_iqd: i32) -> Fixture {
    let pool = test_pool().await;
    let account = account::upsert_account(&pool, Uuid::now_v7())
        .await
        .unwrap();
    let order_id = format!("FIB-ORD-{}", Uuid::now_v7());
    payment::create_payment(
        &pool,
        account.id,
        "fib",
        &order_id,
        amount_iqd,
        "fib://pay",
        "premium_monthly",
        true,
        None,
    )
    .await
    .unwrap()
    .expect("new payment");
    Fixture {
        pool,
        account_id: account.id,
        order_id,
    }
}

async fn is_premium(pool: &PgPool, account_id: Uuid) -> bool {
    subscription::get_current(pool, account_id)
        .await
        .unwrap()
        .is_some()
}

async fn payment_status(pool: &PgPool, order_id: &str) -> String {
    payment::find_by_gateway_order_id(pool, order_id)
        .await
        .unwrap()
        .expect("payment row")
        .status
}

/// The headline regression. In prod no gateway settles, so the request must not produce
/// an entitlement no matter how well-formed the body is.
#[tokio::test]
#[ignore = "needs a disposable Postgres; run ./scripts/test-integration.sh"]
async fn prod_gateway_cannot_settle_an_unsigned_webhook() {
    let f = fixture(10_000).await;

    let result = settle(&f.pool, false, &f.order_id, Some(10_000)).await;

    assert_eq!(
        result.as_ref().err().map(String::as_str),
        Some("gateway not live")
    );
    assert!(
        !is_premium(&f.pool, f.account_id).await,
        "attacker must not have received premium"
    );
    assert_eq!(payment_status(&f.pool, &f.order_id).await, "pending");

    f.purge().await;
}

/// A wrong amount must not unlock the plan, even from a live gateway.
#[tokio::test]
#[ignore = "needs a disposable Postgres; run ./scripts/test-integration.sh"]
async fn amount_mismatch_does_not_activate() {
    let f = fixture(100_000).await;

    let result = settle(&f.pool, true, &f.order_id, Some(1)).await;

    assert!(result.unwrap_err().contains("amount mismatch"));
    assert!(!is_premium(&f.pool, f.account_id).await);
    assert_eq!(payment_status(&f.pool, &f.order_id).await, "pending");

    f.purge().await;
}

/// The happy path still works, so the fix is not just "reject everything".
#[tokio::test]
#[ignore = "needs a disposable Postgres; run ./scripts/test-integration.sh"]
async fn dev_gateway_settles_a_correct_webhook() {
    let f = fixture(10_000).await;

    settle(&f.pool, true, &f.order_id, Some(10_000))
        .await
        .expect("should settle");

    assert!(is_premium(&f.pool, f.account_id).await);
    assert_eq!(payment_status(&f.pool, &f.order_id).await, "success");

    f.purge().await;
}

#[tokio::test]
#[ignore = "needs a disposable Postgres; run ./scripts/test-integration.sh"]
async fn replaying_a_settled_webhook_is_a_no_op() {
    let f = fixture(10_000).await;

    settle(&f.pool, true, &f.order_id, Some(10_000)).await.unwrap();
    // The gateway retries; it must not create a second subscription.
    let second = settle(&f.pool, true, &f.order_id, Some(10_000)).await;

    assert_eq!(
        second.as_ref().err().map(String::as_str),
        Some("already settled")
    );
    assert_eq!(payment_status(&f.pool, &f.order_id).await, "success");

    let subs: i64 = sqlx::query_scalar("SELECT count(*) FROM subscriptions WHERE account_id = $1")
        .bind(f.account_id)
        .fetch_one(&f.pool)
        .await
        .unwrap();
    assert_eq!(subs, 1, "replay must not grant a second subscription");

    f.purge().await;
}

#[tokio::test]
#[ignore = "needs a disposable Postgres; run ./scripts/test-integration.sh"]
async fn unknown_order_id_is_rejected() {
    let f = fixture(10_000).await;

    let result = settle(&f.pool, true, "FIB-ORD-does-not-exist", Some(1)).await;
    assert_eq!(result.as_ref().err().map(String::as_str), Some("not found"));
    assert!(!is_premium(&f.pool, f.account_id).await);

    f.purge().await;
}

/// A gateway that reports no amount at all is the one case the check cannot help with.
/// Pinning the behaviour down so it is a conscious decision rather than an accident.
#[tokio::test]
#[ignore = "needs a disposable Postgres; run ./scripts/test-integration.sh"]
async fn absent_amount_is_treated_as_unverified_but_allowed_in_dev() {
    let f = fixture(10_000).await;

    settle(&f.pool, true, &f.order_id, None).await.unwrap();
    assert!(is_premium(&f.pool, f.account_id).await);

    f.purge().await;
}

async fn subscription_count(pool: &PgPool, account_id: Uuid) -> i64 {
    sqlx::query_scalar("SELECT count(*) FROM subscriptions WHERE account_id = $1")
        .bind(account_id)
        .fetch_one(pool)
        .await
        .unwrap()
}

/// Two deliveries of the same webhook at once used to both pass the "already paid?"
/// check and grant two subscriptions. The row lock serialises them.
#[tokio::test]
#[ignore = "needs a disposable Postgres; run ./scripts/test-integration.sh"]
async fn concurrent_duplicate_webhooks_grant_one_subscription() {
    let f = fixture(10_000).await;

    let attempts = (0..8).map(|_| {
        let pool = f.pool.clone();
        let order = f.order_id.clone();
        tokio::spawn(async move {
            settle_payment(&pool, "fib", &order, SettleStatus::Success, Some(10_000))
                .await
                .unwrap()
        })
    });
    let mut settled = 0;
    for handle in attempts {
        if matches!(handle.await.unwrap(), SettleOutcome::Settled { .. }) {
            settled += 1;
        }
    }

    assert_eq!(settled, 1, "exactly one delivery may settle");
    assert_eq!(subscription_count(&f.pool, f.account_id).await, 1);

    f.purge().await;
}

/// The retry job settles through the same function, so a webhook rejected for its
/// amount stays rejected however often it is retried.
#[tokio::test]
#[ignore = "needs a disposable Postgres; run ./scripts/test-integration.sh"]
async fn retrying_an_amount_mismatch_never_settles() {
    let f = fixture(100_000).await;

    for _ in 0..3 {
        let outcome =
            settle_payment(&f.pool, "fib", &f.order_id, SettleStatus::Success, Some(1))
                .await
                .unwrap();
        assert!(matches!(outcome, SettleOutcome::AmountMismatch { .. }));
        assert!(outcome.is_terminal(SettleStatus::Success));
    }
    assert!(!is_premium(&f.pool, f.account_id).await);
    assert_eq!(payment_status(&f.pool, &f.order_id).await, "pending");

    f.purge().await;
}

#[tokio::test]
#[ignore = "needs a disposable Postgres; run ./scripts/test-integration.sh"]
async fn another_gateways_webhook_cannot_settle_the_order() {
    let f = fixture(10_000).await;

    let outcome =
        settle_payment(&f.pool, "fastpay", &f.order_id, SettleStatus::Success, Some(10_000))
            .await
            .unwrap();
    assert!(matches!(outcome, SettleOutcome::NotFound));
    assert!(!is_premium(&f.pool, f.account_id).await);

    f.purge().await;
}

#[tokio::test]
#[ignore = "needs a disposable Postgres; run ./scripts/test-integration.sh"]
async fn failed_then_pending_webhooks() {
    let f = fixture(10_000).await;

    let pending = settle_payment(&f.pool, "fib", &f.order_id, SettleStatus::Pending, None)
        .await
        .unwrap();
    assert!(matches!(pending, SettleOutcome::Unchanged));
    assert!(!pending.is_terminal(SettleStatus::Pending), "pending is retried");

    let failed = settle_payment(&f.pool, "fib", &f.order_id, SettleStatus::Failed, None)
        .await
        .unwrap();
    assert!(matches!(failed, SettleOutcome::Failed { .. }));
    assert_eq!(payment_status(&f.pool, &f.order_id).await, "failed");

    // A repeated failure notice must not trigger a second "renewal failed" push.
    let again = settle_payment(&f.pool, "fib", &f.order_id, SettleStatus::Failed, None)
        .await
        .unwrap();
    assert!(matches!(again, SettleOutcome::Unchanged));

    f.purge().await;
}

/// Paying before the current subscription ends adds the new period after it, instead
/// of restarting from today and throwing the remaining days away.
#[tokio::test]
#[ignore = "needs a disposable Postgres; run ./scripts/test-integration.sh"]
async fn early_renewal_stacks_onto_the_remaining_time() {
    let f = fixture(10_000).await;
    settle(&f.pool, true, &f.order_id, Some(10_000)).await.unwrap();
    let first = subscription::get_current(&f.pool, f.account_id)
        .await
        .unwrap()
        .unwrap();

    let renewal = format!("FIB-ORD-{}", Uuid::now_v7());
    payment::create_payment(
        &f.pool, f.account_id, "fib", &renewal, 10_000, "fib://pay", "premium_monthly", true, None,
    )
    .await
    .unwrap()
    .unwrap();
    settle(&f.pool, true, &renewal, Some(10_000)).await.unwrap();

    let second = subscription::get_current(&f.pool, f.account_id)
        .await
        .unwrap()
        .unwrap();
    let extra = second.expires_at - first.expires_at;
    assert_eq!(extra.num_days(), 30, "renewal must add a full period: {extra:?}");

    cleanup(
        &f.pool,
        &[format!("DELETE FROM webhook_events WHERE gateway_order_id = '{renewal}'")],
    )
    .await;
    f.purge().await;
}

/// A retried intent with the same key must get the original payment back rather than a
/// unique-constraint 500.
#[tokio::test]
#[ignore = "needs a disposable Postgres; run ./scripts/test-integration.sh"]
async fn idempotency_key_conflict_returns_none() {
    let f = fixture(10_000).await;
    let key = format!("idem-{}", Uuid::now_v7());

    let first = payment::create_payment(
        &f.pool, f.account_id, "fib", &format!("FIB-ORD-{}", Uuid::now_v7()), 10_000,
        "fib://pay", "premium_monthly", true, Some(&key),
    )
    .await
    .unwrap()
    .expect("first insert");
    let second = payment::create_payment(
        &f.pool, f.account_id, "fib", &format!("FIB-ORD-{}", Uuid::now_v7()), 10_000,
        "fib://pay", "premium_monthly", true, Some(&key),
    )
    .await
    .unwrap();

    assert!(second.is_none());
    let found = payment::find_by_idempotency_key(&f.pool, &key)
        .await
        .unwrap()
        .unwrap();
    assert_eq!(found.id, first.id);

    f.purge().await;
}
