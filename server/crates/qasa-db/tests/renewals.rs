//! Integration tests for automatic renewals: saving a payment method at checkout, which
//! subscriptions the worker charges, and when the customer is told it failed.
//!
//! Run with `./scripts/test-integration.sh`.

mod support;

use chrono::{Duration, Utc};
use qasa_db::repos::settlement::{settle_payment, settle_payment_with_token, SettleOutcome, SettleStatus};
use qasa_db::repos::{account, payment, payment_method, renewal, subscription, worker};
use sqlx::PgPool;
use uuid::Uuid;

use support::{cleanup, test_pool};

const RECURRING: &[&str] = &["fib"];

struct Fixture {
    pool: PgPool,
    account_id: Uuid,
}

impl Fixture {
    async fn new() -> Self {
        let pool = test_pool().await;
        let account = account::upsert_account(&pool, Uuid::now_v7()).await.unwrap();
        Self {
            pool,
            account_id: account.id,
        }
    }

    /// Pay a monthly checkout through FIB, the gateway returning `token`.
    async fn checkout(&self, auto_renew: bool, token: Option<&str>) -> subscription::SubscriptionRow {
        let order = format!("FIB-ORD-{}", Uuid::now_v7());
        payment::create_payment(
            &self.pool, self.account_id, "fib", &order, 5_000, "fib://pay", "premium_monthly",
            auto_renew, None,
        )
        .await
        .unwrap()
        .unwrap();
        match settle_payment_with_token(&self.pool, "fib", &order, SettleStatus::Success, Some(5_000), token)
            .await
            .unwrap()
        {
            SettleOutcome::Settled { subscription, .. } => subscription,
            other => panic!("checkout did not settle: {other:?}"),
        }
    }

    async fn set_expiry(&self, sub: Uuid, from_now: Duration) {
        sqlx::query("UPDATE subscriptions SET expires_at = $2 WHERE id = $1")
            .bind(sub)
            .bind(Utc::now() + from_now)
            .execute(&self.pool)
            .await
            .unwrap();
    }

    async fn due_ids(&self) -> Vec<Uuid> {
        renewal::due(&self.pool, RECURRING, 24, 3, 6, 500)
            .await
            .unwrap()
            .into_iter()
            .filter(|r| r.account_id == self.account_id)
            .map(|r| r.subscription_id)
            .collect()
    }

    async fn purge(&self) {
        let acct = self.account_id;
        cleanup(
            &self.pool,
            &[
                format!("UPDATE payments SET subscription_id = NULL, renews_subscription_id = NULL WHERE account_id = '{acct}'"),
                format!("DELETE FROM payment_methods WHERE account_id = '{acct}'"),
                format!("DELETE FROM subscriptions WHERE account_id = '{acct}'"),
                format!("DELETE FROM payments WHERE account_id = '{acct}'"),
                format!("DELETE FROM accounts WHERE id = '{acct}'"),
            ],
        )
        .await;
    }
}

/// Leaving auto-renew on at checkout is the consent to be charged again; turning it off
/// must not leave a chargeable method behind.
#[tokio::test]
#[ignore = "needs a disposable Postgres; run ./scripts/test-integration.sh"]
async fn checkout_saves_a_method_only_with_auto_renew_on() {
    let f = Fixture::new().await;

    f.checkout(false, Some("tok-off")).await;
    assert!(payment_method::live_for_account(&f.pool, f.account_id).await.unwrap().is_none());

    f.checkout(true, Some("tok-1")).await;
    f.checkout(true, Some("tok-2")).await;
    let live = payment_method::live_for_account(&f.pool, f.account_id).await.unwrap().unwrap();
    assert_eq!(live.token, "tok-2", "a newer consented checkout replaces the method");
    assert_eq!(live.gateway, "fib");

    assert!(payment_method::revoke_for_account(&f.pool, f.account_id).await.unwrap());
    assert!(!payment_method::revoke_for_account(&f.pool, f.account_id).await.unwrap());
    f.purge().await;
}

/// The whole worker path at the repository level: selected once it is close to its end,
/// claimed by exactly one run, charged once, and the paid renewal stacks onto the old end.
#[tokio::test]
#[ignore = "needs a disposable Postgres; run ./scripts/test-integration.sh"]
async fn due_renewal_is_claimed_once_and_stacks_when_paid() {
    let f = Fixture::new().await;
    let sub = f.checkout(true, Some("tok")).await;

    f.set_expiry(sub.id, Duration::days(5)).await;
    assert!(f.due_ids().await.is_empty(), "not due until the lead window");

    f.set_expiry(sub.id, Duration::hours(12)).await;
    assert_eq!(f.due_ids().await, vec![sub.id]);
    let old_end = subscription::get_current(&f.pool, f.account_id).await.unwrap().unwrap().expires_at;

    assert_eq!(renewal::claim_attempt(&f.pool, sub.id, 0).await.unwrap(), Some(1));
    assert_eq!(
        renewal::claim_attempt(&f.pool, sub.id, 0).await.unwrap(),
        None,
        "a second run that saw the same count must not also charge"
    );

    let charge = payment::create_renewal_payment(&f.pool, f.account_id, sub.id, "fib", 5_000, "premium_monthly", 1)
        .await
        .unwrap()
        .expect("renewal row");
    assert_eq!(charge.kind, "renewal");
    assert_eq!(charge.renews_subscription_id, Some(sub.id));
    assert!(
        payment::create_renewal_payment(&f.pool, f.account_id, sub.id, "fib", 5_000, "premium_monthly", 2)
            .await
            .unwrap()
            .is_none(),
        "only one renewal charge may be pending per subscription"
    );
    assert!(f.due_ids().await.is_empty(), "a pending charge is not charged again");

    let order = format!("FIB-ORD-REN-{}", charge.id.simple());
    payment::set_gateway_order_id(&f.pool, charge.id, &order).await.unwrap();
    let renewed = match settle_payment(&f.pool, "fib", &order, SettleStatus::Success, Some(5_000)).await.unwrap() {
        SettleOutcome::Settled { subscription, .. } => subscription,
        other => panic!("renewal did not settle: {other:?}"),
    };
    assert!(renewed.auto_renew, "a renewed period keeps renewing");
    let gained = renewed.expires_at - old_end;
    assert!(
        (gained - Duration::days(30)).num_seconds().abs() < 5,
        "renewal adds a full period after the old end, got {gained}"
    );
    assert!(f.due_ids().await.is_empty());
    f.purge().await;
}

/// A declined charge is retried, but not before the retry gap.
#[tokio::test]
#[ignore = "needs a disposable Postgres; run ./scripts/test-integration.sh"]
async fn declined_renewal_waits_for_the_retry_gap() {
    let f = Fixture::new().await;
    let sub = f.checkout(true, Some("tok")).await;
    f.set_expiry(sub.id, Duration::hours(20)).await;

    renewal::claim_attempt(&f.pool, sub.id, 0).await.unwrap().unwrap();
    let charge = payment::create_renewal_payment(&f.pool, f.account_id, sub.id, "fib", 5_000, "premium_monthly", 1)
        .await
        .unwrap()
        .unwrap();
    let order = format!("FIB-ORD-REN-{}", charge.id.simple());
    payment::set_gateway_order_id(&f.pool, charge.id, &order).await.unwrap();
    assert!(matches!(
        settle_payment(&f.pool, "fib", &order, SettleStatus::Failed, None).await.unwrap(),
        SettleOutcome::Failed { .. }
    ));

    assert!(f.due_ids().await.is_empty(), "retried only after the gap");
    sqlx::query("UPDATE subscriptions SET last_renewal_attempt_at = now() - interval '7 hours' WHERE id = $1")
        .bind(sub.id)
        .execute(&f.pool)
        .await
        .unwrap();
    assert_eq!(f.due_ids().await, vec![sub.id]);
    f.purge().await;
}

/// Who is told what near the end: a subscription that will renew gets no "ending soon"
/// reminder, and "renewal failed" goes out only after the worker really tried.
#[tokio::test]
#[ignore = "needs a disposable Postgres; run ./scripts/test-integration.sh"]
async fn reminders_follow_whether_it_will_really_renew() {
    let f = Fixture::new().await;
    let sub = f.checkout(true, Some("tok")).await;
    f.set_expiry(sub.id, Duration::days(2)).await;

    let warned = |rows: Vec<worker::ExpiringSubRow>| rows.iter().any(|r| r.id == sub.id);

    assert!(!warned(worker::subs_needing_expiry_warning(&f.pool, RECURRING).await.unwrap()));
    assert!(
        warned(worker::subs_needing_expiry_warning(&f.pool, &[]).await.unwrap()),
        "no gateway can charge it, so it lapses and needs the reminder"
    );
    assert!(
        !warned(worker::subs_needing_renew_failed_notice(&f.pool, RECURRING, 3).await.unwrap()),
        "never tried, so nothing failed"
    );

    sqlx::query("UPDATE subscriptions SET renewal_attempts = 1 WHERE id = $1")
        .bind(sub.id)
        .execute(&f.pool)
        .await
        .unwrap();
    assert!(!warned(worker::subs_needing_renew_failed_notice(&f.pool, RECURRING, 3).await.unwrap()));

    payment_method::revoke_for_account(&f.pool, f.account_id).await.unwrap();
    assert!(
        warned(worker::subs_needing_renew_failed_notice(&f.pool, RECURRING, 3).await.unwrap()),
        "tried, and the method is gone: tell them"
    );
    assert!(
        !warned(worker::subs_needing_expiry_warning(&f.pool, RECURRING).await.unwrap()),
        "and only that, not the 'ending soon' reminder as well"
    );

    // Turning auto-renew off and on again starts a fresh round.
    subscription::set_auto_renew(&f.pool, f.account_id, sub.id, false).await.unwrap();
    let back = subscription::set_auto_renew(&f.pool, f.account_id, sub.id, true).await.unwrap().unwrap();
    let attempts: i32 = sqlx::query_scalar("SELECT renewal_attempts FROM subscriptions WHERE id = $1")
        .bind(back.id)
        .fetch_one(&f.pool)
        .await
        .unwrap();
    assert_eq!(attempts, 0);
    f.purge().await;
}
