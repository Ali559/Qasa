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
          throttle_notified = CASE
            WHEN vpn_usage.period_start < date_trunc('month', now()) THEN false
            ELSE vpn_usage.throttle_notified
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

/// What crossing a usage threshold should tell the user.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum UsageEvent {
    /// Crossed 80% of the month's quota (sent once per period).
    Warned80,
    /// Used the whole quota: the account is now throttled (sent once per period).
    Throttled,
}

/// Add bytes a node reported for this account, and apply the quota: throttle at 100%,
/// and return the notices to send. Quota comes from the account's current plan (100 GB
/// when it has none). Runs under a row lock so concurrent node reports cannot both send
/// the same notice.
pub async fn meter(pool: &PgPool, account_id: Uuid, delta: i64) -> anyhow::Result<Vec<UsageEvent>> {
    get_or_create(pool, account_id).await?;
    let mut tx = pool.begin().await?;
    let (used, warned, throttled, notified): (i64, bool, bool, bool) = sqlx::query_as(
        r#"
        UPDATE vpn_usage SET bytes_used = bytes_used + $2, updated_at = now()
        WHERE account_id = $1
        RETURNING bytes_used, warned_80, throttled, throttle_notified
        "#,
    )
    .bind(account_id)
    .bind(delta)
    .fetch_one(&mut *tx)
    .await?;

    let quota_gb: i32 = sqlx::query_scalar(
        r#"
        SELECT COALESCE((
            SELECT p.vpn_quota_gb FROM subscriptions s
            JOIN plans p ON p.id = s.plan_id
            WHERE s.account_id = $1 AND s.status IN ('active', 'cancelled') AND s.expires_at > now()
            ORDER BY s.expires_at DESC LIMIT 1
        ), 100)
        "#,
    )
    .bind(account_id)
    .fetch_one(&mut *tx)
    .await?;
    let quota = gb_to_bytes(quota_gb).max(1);

    let mut events = Vec::new();
    if used >= quota && !throttled {
        sqlx::query("UPDATE vpn_usage SET throttled = true WHERE account_id = $1")
            .bind(account_id)
            .execute(&mut *tx)
            .await?;
    }
    if used >= quota && !notified {
        sqlx::query("UPDATE vpn_usage SET throttle_notified = true, warned_80 = true WHERE account_id = $1")
            .bind(account_id)
            .execute(&mut *tx)
            .await?;
        events.push(UsageEvent::Throttled);
    } else if used * 100 >= quota * 80 && !warned {
        sqlx::query("UPDATE vpn_usage SET warned_80 = true WHERE account_id = $1")
            .bind(account_id)
            .execute(&mut *tx)
            .await?;
        events.push(UsageEvent::Warned80);
    }
    tx.commit().await?;
    Ok(events)
}
