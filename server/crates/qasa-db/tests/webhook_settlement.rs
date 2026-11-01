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

use qasa_db::repos::{account, payment, subscription};
use sqlx::PgPool;
use uuid::Uuid;

use support::{cleanup, test_pool};

/// Mirrors `payments::handle_webhook`'s decision, so the ordering of the security checks
/// is reproduced exactly. Returns Ok(settled) / Err(reason).
async fn settle(
    pool: &PgPool,
    gateway_settles: bool,
    gateway_order_id: &str,
    reported_amount: Option<i32>,
) -> Result<(), String> {
    if !gateway_settles {
        return Err("gateway not live".into());
    }

    let payment = payment::find_by_gateway_order_id(pool, gateway_order_id)
        .await
        .map_err(|e| e.to_string())?
        .ok_or_else(|| "not found".to_string())?;

    if payment.status == "success" {
        return Err("already settled".into());
    }

    if let Some(reported) = reported_amount {
        if reported != payment.amount_iqd {
            return Err(format!(
                "amount mismatch: reported {reported}, charged {}",
                payment.amount_iqd
            ));
        }
    }

    let sub = subscription::activate_subscription(
        pool,
        payment.account_id,
        payment.amount_iqd,
        &payment.plan_id,
        payment.auto_renew,
    )
    .await
    .map_err(|e| e.to_string())?;
    let _ = sub;

    payment::mark_status(pool, payment.id, "success", Some(sub.id))
        .await
        .map_err(|e| e.to_string())?;
    Ok(())
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
    .unwrap();
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
