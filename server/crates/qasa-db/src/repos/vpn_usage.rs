use chrono::{DateTime, Utc};
use sqlx::PgPool;
use uuid::Uuid;

#[derive(Debug, Clone, sqlx::FromRow, serde::Serialize)]
pub struct VpnUsageRow {
    pub account_id: Uuid,
    pub period_start: DateTime<Utc>,
    pub bytes_used: i64,
    pub throttled: bool,
    pub warned_80: bool,
    pub updated_at: DateTime<Utc>,
}

pub async fn get_or_create(pool: &PgPool, account_id: Uuid) -> anyhow::Result<VpnUsageRow> {
    // Reset period if month rolled over.
    sqlx::query(
        r#"
        INSERT INTO vpn_usage (account_id, period_start, bytes_used)
        VALUES ($1, date_trunc('month', now()), 0)
        ON CONFLICT (account_id) DO UPDATE SET
          period_start = CASE
            WHEN vpn_usage.period_start < date_trunc('month', now())
              THEN date_trunc('month', now())
            ELSE vpn_usage.period_start
          END,
          bytes_used = CASE
            WHEN vpn_usage.period_start < date_trunc('month', now()) THEN 0
            ELSE vpn_usage.bytes_used
          END,
          throttled = CASE
            WHEN vpn_usage.period_start < date_trunc('month', now()) THEN false
            ELSE vpn_usage.throttled
          END,
          warned_80 = CASE
            WHEN vpn_usage.period_start < date_trunc('month', now()) THEN false
            ELSE vpn_usage.warned_80
          END,
          updated_at = now()
        "#,
    )
    .bind(account_id)
    .execute(pool)
    .await?;

    let row = sqlx::query_as::<_, VpnUsageRow>(
        r#"
        SELECT account_id, period_start, bytes_used, throttled, warned_80, updated_at
        FROM vpn_usage WHERE account_id = $1
        "#,
    )
    .bind(account_id)
    .fetch_one(pool)
    .await?;
    Ok(row)
}

pub async fn add_bytes(pool: &PgPool, account_id: Uuid, delta: i64) -> anyhow::Result<VpnUsageRow> {
    let _ = get_or_create(pool, account_id).await?;
    let row = sqlx::query_as::<_, VpnUsageRow>(
        r#"
        UPDATE vpn_usage
        SET bytes_used = bytes_used + $2, updated_at = now()
        WHERE account_id = $1
        RETURNING account_id, period_start, bytes_used, throttled, warned_80, updated_at
        "#,
    )
    .bind(account_id)
    .bind(delta)
    .fetch_one(pool)
    .await?;
    Ok(row)
}

pub async fn set_throttled(pool: &PgPool, account_id: Uuid, throttled: bool) -> anyhow::Result<()> {
    sqlx::query(
        r#"
        UPDATE vpn_usage SET throttled = $2, updated_at = now() WHERE account_id = $1
        "#,
    )
    .bind(account_id)
    .bind(throttled)
    .execute(pool)
    .await?;
    Ok(())
}

pub async fn set_warned_80(pool: &PgPool, account_id: Uuid) -> anyhow::Result<()> {
    sqlx::query(
        r#"
        UPDATE vpn_usage SET warned_80 = true, updated_at = now() WHERE account_id = $1
        "#,
    )
    .bind(account_id)
    .execute(pool)
    .await?;
    Ok(())
}

pub fn gb_to_bytes(gb: i32) -> i64 {
    (gb as i64) * 1024 * 1024 * 1024
}

pub async fn analytics_totals(pool: &PgPool) -> anyhow::Result<(i64, i64)> {
    let bytes: i64 = sqlx::query_scalar("SELECT COALESCE(SUM(bytes_used), 0)::bigint FROM vpn_usage")
        .fetch_one(pool)
        .await?;
    let throttled: i64 =
        sqlx::query_scalar("SELECT COUNT(*)::bigint FROM vpn_usage WHERE throttled = true")
            .fetch_one(pool)
            .await?;
    Ok((bytes, throttled))
}
