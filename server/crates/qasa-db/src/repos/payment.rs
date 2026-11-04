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
    /// `checkout` (the user paid in the app) or `renewal` (the worker charged a saved
    /// payment method).
    pub kind: String,
    /// For a renewal, the subscription it renews.
    pub renews_subscription_id: Option<Uuid>,
    pub created_at: DateTime<Utc>,
    pub completed_at: Option<DateTime<Utc>>,
}

pub(crate) const PAYMENT_COLS: &str = r#"
        id, account_id, subscription_id, gateway, gateway_order_id,
        amount_iqd, status, deep_link, plan_id, auto_renew,
        kind, renews_subscription_id, created_at, completed_at
"#;

/// Insert a pending payment.
///
/// Returns `None` when `idempotency_key` is already taken, so the caller can return the
/// payment that owns it instead of failing on the unique constraint. Before this the
/// retry a client is supposed to be able to make safely came back as a 500.
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
) -> anyhow::Result<Option<PaymentRow>> {
    let id = new_id();
    let plan_id = crate::repos::subscription::normalize_plan_id(plan_id);
    let row = sqlx::query_as::<_, PaymentRow>(&format!(
        r#"
        INSERT INTO payments (
            id, account_id, gateway, gateway_order_id, amount_iqd, status,
            deep_link, plan_id, auto_renew, idempotency_key
        )
        VALUES ($1, $2, $3, $4, $5, 'pending', $6, $7, $8, $9)
        ON CONFLICT (idempotency_key) DO NOTHING
        RETURNING {PAYMENT_COLS}
        "#
    ))
    .bind(id)
    .bind(account_id)
    .bind(gateway)
    .bind(gateway_order_id)
    .bind(amount_iqd)
    .bind(deep_link)
    .bind(plan_id)
    .bind(auto_renew)
    .bind(idempotency_key)
    .fetch_optional(pool)
    .await?;
    Ok(row)
}

pub async fn find_by_idempotency_key(
    pool: &PgPool,
    idempotency_key: &str,
) -> anyhow::Result<Option<PaymentRow>> {
    let row = sqlx::query_as::<_, PaymentRow>(&format!(
        "SELECT {PAYMENT_COLS} FROM payments WHERE idempotency_key = $1"
    ))
    .bind(idempotency_key)
    .fetch_optional(pool)
    .await?;
    Ok(row)
}

pub async fn find_by_id(pool: &PgPool, payment_id: Uuid) -> anyhow::Result<Option<PaymentRow>> {
    let row = sqlx::query_as::<_, PaymentRow>(&format!(
        "SELECT {PAYMENT_COLS} FROM payments WHERE id = $1"
    ))
    .bind(payment_id)
    .fetch_optional(pool)
    .await?;
    Ok(row)
}

pub async fn find_by_gateway_order_id(
    pool: &PgPool,
    gateway_order_id: &str,
) -> anyhow::Result<Option<PaymentRow>> {
    let row = sqlx::query_as::<_, PaymentRow>(&format!(
        "SELECT {PAYMENT_COLS} FROM payments WHERE gateway_order_id = $1"
    ))
    .bind(gateway_order_id)
    .fetch_optional(pool)
    .await?;
    Ok(row)
}

/// Insert a pending renewal charge for `subscription_id`, before the gateway is called.
///
/// The row exists first so a crash between charging and recording can still be traced,
/// and its id is the reference the gateway uses to refuse a duplicate charge. Returns
/// `None` when this attempt already has a row (`idempotency_key`) or another renewal of
/// the subscription is still pending.
pub async fn create_renewal_payment(
    pool: &PgPool,
    account_id: Uuid,
    subscription_id: Uuid,
    gateway: &str,
    amount_iqd: i32,
    plan_id: &str,
    attempt: i32,
) -> anyhow::Result<Option<PaymentRow>> {
    let id = new_id();
    let key = format!("renewal:{subscription_id}:{attempt}");
    let row = sqlx::query_as::<_, PaymentRow>(&format!(
        r#"
        INSERT INTO payments (
            id, account_id, gateway, amount_iqd, status, plan_id, auto_renew,
            idempotency_key, kind, renews_subscription_id
        )
        VALUES ($1, $2, $3, $4, 'pending', $5, true, $6, 'renewal', $7)
        ON CONFLICT DO NOTHING
        RETURNING {PAYMENT_COLS}
        "#
    ))
    .bind(id)
    .bind(account_id)
    .bind(gateway)
    .bind(amount_iqd)
    .bind(plan_id)
    .bind(key)
    .bind(subscription_id)
    .fetch_optional(pool)
    .await?;
    Ok(row)
}

/// Record the order id the gateway gave a renewal charge, so its webhook can find it.
pub async fn set_gateway_order_id(
    pool: &PgPool,
    payment_id: Uuid,
    gateway_order_id: &str,
) -> anyhow::Result<()> {
    sqlx::query("UPDATE payments SET gateway_order_id = $2 WHERE id = $1")
        .bind(payment_id)
        .bind(gateway_order_id)
        .execute(pool)
        .await?;
    Ok(())
}

/// Mark a renewal failed when the gateway refused to place the charge at all, so there is
/// no order id for `settle_payment` to look up.
pub async fn fail_unplaced(pool: &PgPool, payment_id: Uuid) -> anyhow::Result<()> {
    sqlx::query(
        "UPDATE payments SET status = 'failed', completed_at = now() WHERE id = $1 AND status = 'pending'",
    )
    .bind(payment_id)
    .execute(pool)
    .await?;
    Ok(())
}

/// One page of an account's payments, newest first. `before` is the `created_at` of the
/// last row of the previous page.
pub async fn list_for_account(
    pool: &PgPool,
    account_id: Uuid,
    before: Option<DateTime<Utc>>,
    limit: i64,
) -> anyhow::Result<Vec<PaymentRow>> {
    let rows = sqlx::query_as::<_, PaymentRow>(&format!(
        r#"
        SELECT {PAYMENT_COLS}
        FROM payments
        WHERE account_id = $1
          AND ($2::timestamptz IS NULL OR created_at < $2)
        ORDER BY created_at DESC
        LIMIT $3
        "#
    ))
    .bind(account_id)
    .bind(before)
    .bind(limit)
    .fetch_all(pool)
    .await?;
    Ok(rows)
}

#[derive(Debug, Default)]
pub struct PaymentSearch<'a> {
    /// Start of a gateway order id, or a whole account id.
    pub q: Option<&'a str>,
    pub status: Option<&'a str>,
    pub gateway: Option<&'a str>,
    pub before: Option<DateTime<Utc>>,
}

/// One page of all payments, newest first, filtered for the admin payments view.
pub async fn search_for_admin(
    pool: &PgPool,
    search: &PaymentSearch<'_>,
    limit: i64,
) -> anyhow::Result<Vec<PaymentRow>> {
    let q = search.q.map(str::trim).filter(|s| !s.is_empty());
    let rows = sqlx::query_as::<_, PaymentRow>(&format!(
        r#"
        SELECT {PAYMENT_COLS}
        FROM payments
        WHERE ($1::text IS NULL
               OR gateway_order_id LIKE $1 || '%'
               OR account_id::text = lower($1))
          AND ($2::text IS NULL OR status = $2)
          AND ($3::text IS NULL OR gateway = $3)
          AND ($4::timestamptz IS NULL OR created_at < $4)
        ORDER BY created_at DESC
        LIMIT $5
        "#
    ))
    .bind(q)
    .bind(search.status)
    .bind(search.gateway)
    .bind(search.before)
    .bind(limit)
    .fetch_all(pool)
    .await?;
    Ok(rows)
}

#[derive(Debug, Clone, sqlx::FromRow, serde::Serialize)]
pub struct RevenueDay {
    pub day: DateTime<Utc>,
    pub gateway: String,
    pub total_iqd: i64,
    pub tx_count: i64,
}

#[derive(Debug, Clone, sqlx::FromRow, serde::Serialize)]
pub struct CountDay {
    pub day: DateTime<Utc>,
    pub count: i64,
}

/// Daily paid revenue per gateway, daily new accounts and daily new paid subscriptions
/// over the last `days` days, for the dashboard charts that used to be mock data.
///
/// Read straight from the tables; at this size the queries are cheap. (A materialized
/// view used to exist for this but was never read, and has been dropped.)
pub async fn timeseries(
    pool: &PgPool,
    days: i32,
) -> anyhow::Result<(Vec<RevenueDay>, Vec<CountDay>, Vec<CountDay>)> {
    let revenue = sqlx::query_as::<_, RevenueDay>(
        r#"
        SELECT date_trunc('day', completed_at) AS day, gateway,
               SUM(amount_iqd)::bigint AS total_iqd, COUNT(*)::bigint AS tx_count
        FROM payments
        WHERE status = 'success'
          AND completed_at >= date_trunc('day', now()) - make_interval(days => $1)
        GROUP BY 1, 2
        ORDER BY 1, 2
        "#,
    )
    .bind(days)
    .fetch_all(pool)
    .await?;

    let signups = sqlx::query_as::<_, CountDay>(
        r#"
        SELECT date_trunc('day', created_at) AS day, COUNT(*)::bigint AS count
        FROM accounts
        WHERE created_at >= date_trunc('day', now()) - make_interval(days => $1)
        GROUP BY 1
        ORDER BY 1
        "#,
    )
    .bind(days)
    .fetch_all(pool)
    .await?;

    let new_paid = sqlx::query_as::<_, CountDay>(
        r#"
        SELECT date_trunc('day', created_at) AS day, COUNT(*)::bigint AS count
        FROM subscriptions
        WHERE granted_by IS NULL
          AND amount_iqd > 0
          AND created_at >= date_trunc('day', now()) - make_interval(days => $1)
        GROUP BY 1
        ORDER BY 1
        "#,
    )
    .bind(days)
    .fetch_all(pool)
    .await?;

    Ok((revenue, signups, new_paid))
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

/// Record a webhook whose signature the gateway verified, together with the outcome it
/// verified. The retry job settles from these columns, never from `payload`.
pub async fn insert_verified_webhook_event(
    pool: &PgPool,
    gateway: &str,
    gateway_order_id: &str,
    payload: &serde_json::Value,
    event_status: &str,
    reported_amount_iqd: Option<i32>,
    payment_token: Option<&str>,
) -> anyhow::Result<Uuid> {
    let id = new_id();
    sqlx::query(
        r#"
        INSERT INTO webhook_events (
            id, gateway, gateway_order_id, payload, signature_valid, processed,
            event_status, reported_amount_iqd, payment_token
        )
        VALUES ($1, $2, $3, $4, true, false, $5, $6, $7)
        "#,
    )
    .bind(id)
    .bind(gateway)
    .bind(gateway_order_id)
    .bind(payload)
    .bind(event_status)
    .bind(reported_amount_iqd)
    .bind(payment_token)
    .execute(pool)
    .await?;
    Ok(id)
}

/// Mark a webhook event as handled so the retry job leaves it alone. `error` records why
/// a terminal event did not settle (amount mismatch, unknown order).
pub async fn finish_webhook_event(
    pool: &PgPool,
    id: Uuid,
    error: Option<&str>,
) -> anyhow::Result<()> {
    sqlx::query(
        r#"
        UPDATE webhook_events
        SET processed = true, error_message = COALESCE($2, error_message)
        WHERE id = $1
        "#,
    )
    .bind(id)
    .bind(error)
    .execute(pool)
    .await?;
    Ok(())
}

/// Newest first; only events for one order when `gateway_order_id` is given.
pub async fn list_webhooks_for_admin(
    pool: &PgPool,
    gateway_order_id: Option<&str>,
    limit: i64,
) -> anyhow::Result<Vec<WebhookEventRow>> {
    let rows = sqlx::query_as::<_, WebhookEventRow>(
        r#"
        SELECT id, gateway, gateway_order_id, payload, signature_valid, processed,
               error_message, received_at
        FROM webhook_events
        WHERE ($1::text IS NULL OR gateway_order_id = $1)
        ORDER BY received_at DESC
        LIMIT $2
        "#,
    )
    .bind(gateway_order_id)
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
    // Monthly recurring revenue: every live paid subscription at its monthly-equivalent
    // price (a yearly plan counts as 1/12 per month). It used to be the cash collected
    // this calendar month, which booked a whole yearly payment into a single month.
    let mrr: i64 = sqlx::query_scalar(
        r#"
        SELECT COALESCE(SUM(
                 s.amount_iqd::numeric * 30
                 / GREATEST(COALESCE(p.days, CASE WHEN s.plan_id = 'premium_yearly' THEN 365 ELSE 30 END), 1)
               ), 0)::bigint
        FROM subscriptions s
        LEFT JOIN plans p ON p.id = s.plan_id
        WHERE s.status IN ('active', 'cancelled')
          AND s.expires_at > now()
          AND s.granted_by IS NULL
        "#,
    )
    .fetch_one(pool)
    .await?;

    let collected_this_month: i64 = sqlx::query_scalar(
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
        WHERE status IN ('active', 'cancelled') AND expires_at > now()
          AND granted_by IS NULL
        "#,
    )
    .fetch_one(pool)
    .await?;

    let free_devices: i64 = sqlx::query_scalar(
        r#"
        SELECT COUNT(*)::bigint
        FROM devices d
        WHERE d.revoked_at IS NULL
          AND NOT EXISTS (
            SELECT 1 FROM subscriptions s
            WHERE s.account_id = d.account_id
              AND s.status IN ('active', 'cancelled')
              AND s.expires_at > now()
          )
        "#,
    )
    .fetch_one(pool)
    .await?;

    Ok(OverviewStats {
        mrr_iqd: mrr,
        collected_this_month_iqd: collected_this_month,
        paid_subscribers,
        free_devices,
    })
}

#[derive(Debug, Clone, serde::Serialize)]
pub struct OverviewStats {
    pub mrr_iqd: i64,
    pub collected_this_month_iqd: i64,
    pub paid_subscribers: i64,
    pub free_devices: i64,
}
