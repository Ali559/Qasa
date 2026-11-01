use chrono::{DateTime, Utc};
use qasa_core::new_id;
use sqlx::PgPool;
use uuid::Uuid;

#[derive(Debug, Clone, sqlx::FromRow)]
pub struct PaymentRow {
    pub id: Uuid,
    pub account_id: Uuid,
    pub subscription_id: Option<Uuid>,
    pub gateway: String,
    pub gateway_order_id: Option<String>,
    pub amount_iqd: i32,
    pub status: String,
    pub deep_link: Option<String>,
    pub plan_id: String,
    pub auto_renew: bool,
    pub created_at: DateTime<Utc>,
    pub completed_at: Option<DateTime<Utc>>,
}

pub async fn create_payment(
    pool: &PgPool,
    account_id: Uuid,
    gateway: &str,
    gateway_order_id: &str,
    amount_iqd: i32,
    deep_link: &str,
    plan_id: &str,
    auto_renew: bool,
    idempotency_key: Option<&str>,
) -> anyhow::Result<PaymentRow> {
    let id = new_id();
    let plan_id = crate::repos::subscription::normalize_plan_id(plan_id);
    let row = sqlx::query_as::<_, PaymentRow>(
        r#"
        INSERT INTO payments (
            id, account_id, gateway, gateway_order_id, amount_iqd, status,
            deep_link, plan_id, auto_renew, idempotency_key
        )
        VALUES ($1, $2, $3, $4, $5, 'pending', $6, $7, $8, $9)
        RETURNING id, account_id, subscription_id, gateway, gateway_order_id,
                  amount_iqd, status, deep_link, plan_id, auto_renew,
                  created_at, completed_at
        "#,
    )
    .bind(id)
    .bind(account_id)
    .bind(gateway)
    .bind(gateway_order_id)
    .bind(amount_iqd)
    .bind(deep_link)
    .bind(plan_id)
    .bind(auto_renew)
    .bind(idempotency_key)
    .fetch_one(pool)
    .await?;
    Ok(row)
}

pub async fn find_by_id(pool: &PgPool, payment_id: Uuid) -> anyhow::Result<Option<PaymentRow>> {
    let row = sqlx::query_as::<_, PaymentRow>(
        r#"
        SELECT id, account_id, subscription_id, gateway, gateway_order_id,
               amount_iqd, status, deep_link, plan_id, auto_renew,
               created_at, completed_at
        FROM payments WHERE id = $1
        "#,
    )
    .bind(payment_id)
    .fetch_optional(pool)
    .await?;
    Ok(row)
}

pub async fn find_by_gateway_order_id(
    pool: &PgPool,
    gateway_order_id: &str,
) -> anyhow::Result<Option<PaymentRow>> {
    let row = sqlx::query_as::<_, PaymentRow>(
        r#"
        SELECT id, account_id, subscription_id, gateway, gateway_order_id,
               amount_iqd, status, deep_link, plan_id, auto_renew,
               created_at, completed_at
        FROM payments WHERE gateway_order_id = $1
        "#,
    )
    .bind(gateway_order_id)
    .fetch_optional(pool)
    .await?;
    Ok(row)
}

pub async fn mark_status(
    pool: &PgPool,
    payment_id: Uuid,
    status: &str,
    subscription_id: Option<Uuid>,
) -> anyhow::Result<PaymentRow> {
    let completed_at = if status == "success" || status == "failed" {
        Some(Utc::now())
    } else {
        None
    };

    let row = sqlx::query_as::<_, PaymentRow>(
        r#"
        UPDATE payments
        SET status = $2,
            subscription_id = COALESCE($3, subscription_id),
            completed_at = COALESCE($4, completed_at)
        WHERE id = $1
        RETURNING id, account_id, subscription_id, gateway, gateway_order_id,
                  amount_iqd, status, deep_link, plan_id, auto_renew,
                  created_at, completed_at
        "#,
    )
    .bind(payment_id)
    .bind(status)
    .bind(subscription_id)
    .bind(completed_at)
    .fetch_one(pool)
    .await?;
    Ok(row)
}

pub async fn list_for_admin(pool: &PgPool, limit: i64) -> anyhow::Result<Vec<PaymentRow>> {
    let rows = sqlx::query_as::<_, PaymentRow>(
        r#"
        SELECT id, account_id, subscription_id, gateway, gateway_order_id,
               amount_iqd, status, deep_link, plan_id, auto_renew,
               created_at, completed_at
        FROM payments
        ORDER BY created_at DESC
        LIMIT $1
        "#,
    )
    .bind(limit)
    .fetch_all(pool)
    .await?;
    Ok(rows)
}

pub async fn insert_webhook_event(
    pool: &PgPool,
    gateway: &str,
    gateway_order_id: Option<&str>,
    payload: &serde_json::Value,
    signature_valid: bool,
    processed: bool,
    error_message: Option<&str>,
) -> anyhow::Result<Uuid> {
    let id = new_id();
    sqlx::query(
        r#"
        INSERT INTO webhook_events (
            id, gateway, gateway_order_id, payload, signature_valid, processed, error_message
        )
        VALUES ($1, $2, $3, $4, $5, $6, $7)
        "#,
    )
    .bind(id)
    .bind(gateway)
    .bind(gateway_order_id)
    .bind(payload)
    .bind(signature_valid)
    .bind(processed)
    .bind(error_message)
    .execute(pool)
    .await?;
    Ok(id)
}

pub async fn list_webhooks_for_admin(
    pool: &PgPool,
    limit: i64,
) -> anyhow::Result<Vec<WebhookEventRow>> {
    let rows = sqlx::query_as::<_, WebhookEventRow>(
        r#"
        SELECT id, gateway, gateway_order_id, payload, signature_valid, processed,
               error_message, received_at
        FROM webhook_events
        ORDER BY received_at DESC
        LIMIT $1
        "#,
    )
    .bind(limit)
    .fetch_all(pool)
    .await?;
    Ok(rows)
}

#[derive(Debug, Clone, sqlx::FromRow, serde::Serialize)]
pub struct WebhookEventRow {
    pub id: Uuid,
    pub gateway: String,
    pub gateway_order_id: Option<String>,
    pub payload: serde_json::Value,
    pub signature_valid: bool,
    pub processed: bool,
    pub error_message: Option<String>,
    pub received_at: DateTime<Utc>,
}

pub async fn overview_stats(pool: &PgPool) -> anyhow::Result<OverviewStats> {
    let mrr: i64 = sqlx::query_scalar(
        r#"
        SELECT COALESCE(SUM(amount_iqd), 0)::bigint
        FROM payments
        WHERE status = 'success'
          AND completed_at >= date_trunc('month', now())
        "#,
    )
    .fetch_one(pool)
    .await?;

    let paid_subscribers: i64 = sqlx::query_scalar(
        r#"
        SELECT COUNT(DISTINCT account_id)::bigint
        FROM subscriptions
        WHERE status = 'active' AND expires_at > now()
        "#,
    )
    .fetch_one(pool)
    .await?;

    let free_devices: i64 = sqlx::query_scalar(
        r#"
        SELECT COUNT(*)::bigint
        FROM devices d
        JOIN accounts a ON a.id = d.account_id
        WHERE d.revoked_at IS NULL AND a.tier = 'free'
        "#,
    )
    .fetch_one(pool)
    .await?;

    Ok(OverviewStats {
        mrr_iqd: mrr,
        paid_subscribers,
        free_devices,
    })
}

#[derive(Debug, Clone, serde::Serialize)]
pub struct OverviewStats {
    pub mrr_iqd: i64,
    pub paid_subscribers: i64,
    pub free_devices: i64,
}
