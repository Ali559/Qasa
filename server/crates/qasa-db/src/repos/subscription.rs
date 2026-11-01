use chrono::{Duration, Utc};
use qasa_core::new_id;
use sqlx::PgPool;
use uuid::Uuid;

#[derive(Debug, Clone, sqlx::FromRow, serde::Serialize)]
pub struct SubscriptionRow {
    pub id: Uuid,
    pub account_id: Uuid,
    pub status: String,
    pub plan_id: String,
    pub amount_iqd: i32,
    pub auto_renew: bool,
    pub started_at: chrono::DateTime<Utc>,
    pub expires_at: chrono::DateTime<Utc>,
    pub cancelled_at: Option<chrono::DateTime<Utc>>,
    pub cancellation_reason: Option<String>,
}

#[derive(Debug, Clone, sqlx::FromRow, serde::Serialize)]
pub struct CancellationFeedbackRow {
    pub id: Uuid,
    pub account_id: Uuid,
    pub plan_id: String,
    pub amount_iqd: i32,
    pub cancellation_reason: Option<String>,
    pub cancelled_at: Option<chrono::DateTime<Utc>>,
    pub expires_at: chrono::DateTime<Utc>,
}

pub fn plan_days(plan_id: &str) -> i64 {
    plan_days_fallback(plan_id)
}

pub fn plan_amount(plan_id: &str) -> i32 {
    plan_amount_fallback(plan_id)
}

pub fn plan_days_fallback(plan_id: &str) -> i64 {
    match plan_id {
        "premium_yearly" => 365,
        _ => 30,
    }
}

pub fn plan_amount_fallback(plan_id: &str) -> i32 {
    match plan_id {
        "premium_yearly" => 100_000,
        _ => 10_000,
    }
}

pub fn normalize_plan_id(plan_id: &str) -> &'static str {
    match plan_id {
        "premium_yearly" | "yearly" => "premium_yearly",
        _ => "premium_monthly",
    }
}

pub async fn has_active_subscription(pool: &PgPool, account_id: Uuid) -> anyhow::Result<bool> {
    let active: bool = sqlx::query_scalar(
        r#"
        SELECT EXISTS(
            SELECT 1 FROM subscriptions
            WHERE account_id = $1
              AND status IN ('active', 'cancelled')
              AND expires_at > now()
        )
        "#,
    )
    .bind(account_id)
    .fetch_one(pool)
    .await?;
    Ok(active)
}

pub async fn latest_expiry(
    pool: &PgPool,
    account_id: Uuid,
) -> anyhow::Result<Option<chrono::DateTime<chrono::Utc>>> {
    let expiry: Option<chrono::DateTime<chrono::Utc>> = sqlx::query_scalar(
        r#"
        SELECT expires_at
        FROM subscriptions
        WHERE account_id = $1
          AND status IN ('active', 'cancelled')
          AND expires_at > now()
        ORDER BY expires_at DESC
        LIMIT 1
        "#,
    )
    .bind(account_id)
    .fetch_optional(pool)
    .await?;
    Ok(expiry)
}

pub async fn get_current(pool: &PgPool, account_id: Uuid) -> anyhow::Result<Option<SubscriptionRow>> {
    let row = sqlx::query_as::<_, SubscriptionRow>(
        r#"
        SELECT id, account_id, status, plan_id, amount_iqd, auto_renew,
               started_at, expires_at, cancelled_at, cancellation_reason
        FROM subscriptions
        WHERE account_id = $1
          AND status IN ('active', 'cancelled')
          AND expires_at > now()
        ORDER BY expires_at DESC
        LIMIT 1
        "#,
    )
    .bind(account_id)
    .fetch_optional(pool)
    .await?;
    Ok(row)
}

pub async fn activate_subscription(
    pool: &PgPool,
    account_id: Uuid,
    amount_iqd: i32,
    plan_id: &str,
    auto_renew: bool,
) -> anyhow::Result<SubscriptionRow> {
    let plan_id = normalize_plan_id(plan_id);
    let days = crate::repos::plan::plan_days(pool, plan_id).await?;
    let id = new_id();
    let started_at = Utc::now();
    let expires_at = started_at + Duration::days(days);

    sqlx::query(
        r#"
        UPDATE subscriptions SET status = 'expired'
        WHERE account_id = $1 AND status IN ('active', 'cancelled')
        "#,
    )
    .bind(account_id)
    .execute(pool)
    .await?;

    let row = sqlx::query_as::<_, SubscriptionRow>(
        r#"
        INSERT INTO subscriptions (
            id, account_id, status, plan_id, amount_iqd, auto_renew,
            started_at, expires_at
        )
        VALUES ($1, $2, 'active', $3, $4, $5, $6, $7)
        RETURNING id, account_id, status, plan_id, amount_iqd, auto_renew,
                  started_at, expires_at, cancelled_at, cancellation_reason
        "#,
    )
    .bind(id)
    .bind(account_id)
    .bind(plan_id)
    .bind(amount_iqd)
    .bind(auto_renew)
    .bind(started_at)
    .bind(expires_at)
    .fetch_one(pool)
    .await?;

    crate::repos::account::set_account_tier(pool, account_id, "premium").await?;

    Ok(row)
}

pub async fn set_auto_renew(
    pool: &PgPool,
    account_id: Uuid,
    subscription_id: Uuid,
    auto_renew: bool,
) -> anyhow::Result<Option<SubscriptionRow>> {
    let row = sqlx::query_as::<_, SubscriptionRow>(
        r#"
        UPDATE subscriptions
        SET auto_renew = $3
        WHERE id = $1
          AND account_id = $2
          AND status = 'active'
          AND expires_at > now()
        RETURNING id, account_id, status, plan_id, amount_iqd, auto_renew,
                  started_at, expires_at, cancelled_at, cancellation_reason
        "#,
    )
    .bind(subscription_id)
    .bind(account_id)
    .bind(auto_renew)
    .fetch_optional(pool)
    .await?;
    Ok(row)
}

pub async fn cancel_subscription(
    pool: &PgPool,
    account_id: Uuid,
    subscription_id: Uuid,
    reason: Option<&str>,
) -> anyhow::Result<Option<SubscriptionRow>> {
    let reason = reason.map(str::trim).filter(|s| !s.is_empty());
    let row = sqlx::query_as::<_, SubscriptionRow>(
        r#"
        UPDATE subscriptions
        SET status = 'cancelled',
            auto_renew = false,
            cancelled_at = now(),
            cancellation_reason = $3
        WHERE id = $1
          AND account_id = $2
          AND status = 'active'
          AND expires_at > now()
        RETURNING id, account_id, status, plan_id, amount_iqd, auto_renew,
                  started_at, expires_at, cancelled_at, cancellation_reason
        "#,
    )
    .bind(subscription_id)
    .bind(account_id)
    .bind(reason)
    .fetch_optional(pool)
    .await?;
    Ok(row)
}

pub async fn list_cancellations(pool: &PgPool, limit: i64) -> anyhow::Result<Vec<CancellationFeedbackRow>> {
    let rows = sqlx::query_as::<_, CancellationFeedbackRow>(
        r#"
        SELECT id, account_id, plan_id, amount_iqd, cancellation_reason,
               cancelled_at, expires_at
        FROM subscriptions
        WHERE status = 'cancelled'
          AND cancelled_at IS NOT NULL
        ORDER BY cancelled_at DESC NULLS LAST
        LIMIT $1
        "#,
    )
    .bind(limit)
    .fetch_all(pool)
    .await?;
    Ok(rows)
}
