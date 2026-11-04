//! Which subscriptions the worker renews, and when it gives up.
//!
//! A subscription renews by itself only when all of these hold: auto-renew is on, the
//! account has a live saved payment method, and that method's gateway can charge it
//! without the customer approving each payment (`recurring_gateways`). Everything else
//! lapses at its end date like a manual plan.
//!
//! Where no gateway can do that (Wayl cannot), the worker instead sends a payment link
//! a few days before the end, and the customer pays it like a checkout. Without either,
//! the customer gets the 3-day reminder.

use chrono::{DateTime, Utc};
use sqlx::PgPool;
use uuid::Uuid;

#[derive(Debug, Clone, sqlx::FromRow)]
pub struct DueRenewal {
    pub subscription_id: Uuid,
    pub account_id: Uuid,
    pub plan_id: String,
    pub expires_at: DateTime<Utc>,
    pub renewal_attempts: i32,
    pub method_id: Uuid,
    pub gateway: String,
    pub token: String,
}

/// Subscriptions ending within `lead_hours` that are due a renewal attempt: fewer than
/// `max_attempts` so far, none in the last `retry_hours`, and no renewal charge pending
/// or already paid for them.
pub async fn due(
    pool: &PgPool,
    recurring_gateways: &[&str],
    lead_hours: i32,
    max_attempts: i32,
    retry_hours: i32,
    limit: i64,
) -> anyhow::Result<Vec<DueRenewal>> {
    let rows = sqlx::query_as::<_, DueRenewal>(
        r#"
        SELECT s.id AS subscription_id, s.account_id, s.plan_id, s.expires_at,
               s.renewal_attempts, m.id AS method_id, m.gateway, m.token
        FROM subscriptions s
        JOIN payment_methods m ON m.account_id = s.account_id AND m.revoked_at IS NULL
        JOIN accounts a ON a.id = s.account_id
        WHERE s.status = 'active'
          AND s.auto_renew = true
          AND s.expires_at > now()
          AND s.expires_at <= now() + make_interval(hours => $2)
          AND s.renewal_attempts < $3
          AND (s.last_renewal_attempt_at IS NULL
               OR s.last_renewal_attempt_at < now() - make_interval(hours => $4))
          AND m.gateway = ANY($1)
          AND a.deleted_at IS NULL
          AND a.blocked_at IS NULL
          AND NOT EXISTS (
            SELECT 1 FROM payments p
            WHERE p.renews_subscription_id = s.id
              AND p.status IN ('pending', 'success')
          )
        ORDER BY s.expires_at ASC
        LIMIT $5
        "#,
    )
    .bind(recurring_gateways)
    .bind(lead_hours)
    .bind(max_attempts)
    .bind(retry_hours)
    .bind(limit)
    .fetch_all(pool)
    .await?;
    Ok(rows)
}

/// Count one attempt, if no other run has counted it first. Returns the attempt number
/// (1-based), or `None` when another run got there.
pub async fn claim_attempt(
    pool: &PgPool,
    subscription_id: Uuid,
    seen_attempts: i32,
) -> anyhow::Result<Option<i32>> {
    let attempt: Option<i32> = sqlx::query_scalar(
        r#"
        UPDATE subscriptions
        SET renewal_attempts = renewal_attempts + 1,
            last_renewal_attempt_at = now()
        WHERE id = $1 AND renewal_attempts = $2
        RETURNING renewal_attempts
        "#,
    )
    .bind(subscription_id)
    .bind(seen_attempts)
    .fetch_optional(pool)
    .await?;
    Ok(attempt)
}

/// Stop trying to renew: the plan can no longer be bought, so retrying cannot help.
pub async fn give_up(pool: &PgPool, subscription_id: Uuid, max_attempts: i32) -> anyhow::Result<()> {
    sqlx::query(
        "UPDATE subscriptions SET renewal_attempts = GREATEST(renewal_attempts, $2) WHERE id = $1",
    )
    .bind(subscription_id)
    .bind(max_attempts)
    .execute(pool)
    .await?;
    Ok(())
}

/// Whether the account's current subscription will renew by itself, and through which
/// gateway. Used for what the apps show under the auto-renew toggle.
pub async fn recurring_method_gateway(
    pool: &PgPool,
    account_id: Uuid,
    recurring_gateways: &[&str],
) -> anyhow::Result<Option<String>> {
    let gateway: Option<String> = sqlx::query_scalar(
        r#"
        SELECT m.gateway
        FROM payment_methods m
        WHERE m.account_id = $1
          AND m.revoked_at IS NULL
          AND m.gateway = ANY($2)
        "#,
    )
    .bind(account_id)
    .bind(recurring_gateways)
    .fetch_optional(pool)
    .await?;
    Ok(gateway)
}

#[derive(Debug, Clone, sqlx::FromRow)]
pub struct DueLink {
    pub subscription_id: Uuid,
    pub account_id: Uuid,
    pub plan_id: String,
    pub expires_at: DateTime<Utc>,
}

/// Auto-renew subscriptions ending within `lead_hours` that renew by link: no live saved
/// method a recurring gateway could charge, no link sent this period, and no renewal
/// pending or paid.
pub async fn due_links(
    pool: &PgPool,
    recurring_gateways: &[&str],
    lead_hours: i32,
    limit: i64,
) -> anyhow::Result<Vec<DueLink>> {
    let rows = sqlx::query_as::<_, DueLink>(
        r#"
        SELECT s.id AS subscription_id, s.account_id, s.plan_id, s.expires_at
        FROM subscriptions s
        JOIN accounts a ON a.id = s.account_id
        WHERE s.status = 'active'
          AND s.auto_renew = true
          AND s.expires_at > now()
          AND s.expires_at <= now() + make_interval(hours => $2)
          AND s.renewal_link_sent_at IS NULL
          AND a.deleted_at IS NULL
          AND a.blocked_at IS NULL
          AND NOT EXISTS (
            SELECT 1 FROM payment_methods m
            WHERE m.account_id = s.account_id
              AND m.revoked_at IS NULL
              AND m.gateway = ANY($1)
          )
          AND NOT EXISTS (
            SELECT 1 FROM payments p
            WHERE p.renews_subscription_id = s.id
              AND p.status IN ('pending', 'success')
          )
        ORDER BY s.expires_at ASC
        LIMIT $3
        "#,
    )
    .bind(recurring_gateways)
    .bind(lead_hours)
    .bind(limit)
    .fetch_all(pool)
    .await?;
    Ok(rows)
}

/// Mark this period's link as sent, if no other run has. `false` when one did.
pub async fn claim_link(pool: &PgPool, subscription_id: Uuid) -> anyhow::Result<bool> {
    let n = sqlx::query(
        "UPDATE subscriptions SET renewal_link_sent_at = now() WHERE id = $1 AND renewal_link_sent_at IS NULL",
    )
    .bind(subscription_id)
    .execute(pool)
    .await?
    .rows_affected();
    Ok(n == 1)
}

/// The link could not be created; let the next run try again. If it keeps failing, the
/// 3-day reminder goes out instead.
pub async fn release_link(pool: &PgPool, subscription_id: Uuid) -> anyhow::Result<()> {
    sqlx::query("UPDATE subscriptions SET renewal_link_sent_at = NULL WHERE id = $1")
        .bind(subscription_id)
        .execute(pool)
        .await?;
    Ok(())
}
