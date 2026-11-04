//! Staff views of, and actions on, user accounts.

use chrono::{DateTime, Utc};
use sqlx::PgPool;
use uuid::Uuid;

#[derive(Debug, Clone, sqlx::FromRow)]
pub struct AccountSearchRow {
    pub id: Uuid,
    pub public_id: String,
    pub tier: String,
    pub created_at: DateTime<Utc>,
    pub blocked_at: Option<DateTime<Utc>>,
    pub active_devices: i64,
    /// End of the live subscription, if there is one.
    pub live_expiry: Option<DateTime<Utc>>,
}

/// One page of live accounts, newest first, in a single query (the old list ran three
/// queries per account). `q` matches the start of the account id or any part of the
/// masked public id.
pub async fn search(
    pool: &PgPool,
    q: Option<&str>,
    before: Option<DateTime<Utc>>,
    limit: i64,
) -> anyhow::Result<Vec<AccountSearchRow>> {
    let q = q.map(str::trim).filter(|s| !s.is_empty()).map(str::to_lowercase);
    let rows = sqlx::query_as::<_, AccountSearchRow>(
        r#"
        SELECT a.id, a.public_id, a.tier, a.created_at, a.blocked_at,
               (SELECT COUNT(*) FROM devices d
                 WHERE d.account_id = a.id AND d.revoked_at IS NULL) AS active_devices,
               (SELECT max(s.expires_at) FROM subscriptions s
                 WHERE s.account_id = a.id
                   AND s.status IN ('active', 'cancelled')
                   AND s.expires_at > now()) AS live_expiry
        FROM accounts a
        WHERE a.deleted_at IS NULL
          AND ($1::text IS NULL
               OR a.id::text LIKE $1 || '%'
               OR lower(a.public_id) LIKE '%' || $1 || '%')
          AND ($2::timestamptz IS NULL OR a.created_at < $2)
        ORDER BY a.created_at DESC
        LIMIT $3
        "#,
    )
    .bind(q)
    .bind(before)
    .bind(limit)
    .fetch_all(pool)
    .await?;
    Ok(rows)
}

#[derive(Debug, Clone, sqlx::FromRow, serde::Serialize)]
pub struct AdminDeviceRow {
    pub id: Uuid,
    pub device_name: String,
    pub platform: String,
    pub location: Option<String>,
    pub created_at: DateTime<Utc>,
    pub last_seen_at: Option<DateTime<Utc>>,
    pub revoked_at: Option<DateTime<Utc>>,
    pub notifications_enabled: bool,
}

/// Every device the account has had, linked ones first (oldest first, so the primary
/// leads), then unlinked ones.
pub async fn devices(pool: &PgPool, account_id: Uuid) -> anyhow::Result<Vec<AdminDeviceRow>> {
    let rows = sqlx::query_as::<_, AdminDeviceRow>(
        r#"
        SELECT id, device_name, platform, location, created_at, last_seen_at, revoked_at,
               notifications_enabled
        FROM devices
        WHERE account_id = $1
        ORDER BY (revoked_at IS NOT NULL), created_at ASC, id ASC
        "#,
    )
    .bind(account_id)
    .fetch_all(pool)
    .await?;
    Ok(rows)
}

#[derive(Debug, Clone, sqlx::FromRow, serde::Serialize)]
pub struct AdminSubscriptionRow {
    pub id: Uuid,
    pub status: String,
    pub plan_id: String,
    pub amount_iqd: i32,
    pub auto_renew: bool,
    pub started_at: DateTime<Utc>,
    pub expires_at: DateTime<Utc>,
    pub cancelled_at: Option<DateTime<Utc>>,
    pub cancellation_reason: Option<String>,
    pub granted_by: Option<Uuid>,
    pub grant_days: Option<i32>,
    pub grant_reason: Option<String>,
    pub grant_revoked_at: Option<DateTime<Utc>>,
    /// Automatic renewal charges tried for this period.
    pub renewal_attempts: i32,
}

pub async fn subscriptions(
    pool: &PgPool,
    account_id: Uuid,
    limit: i64,
) -> anyhow::Result<Vec<AdminSubscriptionRow>> {
    let rows = sqlx::query_as::<_, AdminSubscriptionRow>(
        r#"
        SELECT id, status, plan_id, amount_iqd, auto_renew, started_at, expires_at,
               cancelled_at, cancellation_reason, granted_by, grant_days, grant_reason,
               grant_revoked_at, renewal_attempts
        FROM subscriptions
        WHERE account_id = $1
        ORDER BY created_at DESC
        LIMIT $2
        "#,
    )
    .bind(account_id)
    .bind(limit)
    .fetch_all(pool)
    .await?;
    Ok(rows)
}

pub async fn monitor_count(pool: &PgPool, account_id: Uuid) -> anyhow::Result<i64> {
    crate::repos::monitored_email::count_for_account(pool, account_id).await
}

/// Block an account: its tokens stop working (see `device::is_license_live`), it cannot
/// register devices, and its VPN sessions end now. Returns `false` if it was already
/// blocked or does not exist.
pub async fn block(
    pool: &PgPool,
    admin_id: Uuid,
    account_id: Uuid,
    reason: &str,
) -> anyhow::Result<bool> {
    let mut tx = pool.begin().await?;
    let blocked = sqlx::query(
        r#"
        UPDATE accounts SET blocked_at = now(), blocked_reason = $2, updated_at = now()
        WHERE id = $1 AND deleted_at IS NULL AND blocked_at IS NULL
        "#,
    )
    .bind(account_id)
    .bind(reason)
    .execute(&mut *tx)
    .await?
    .rows_affected()
        > 0;
    if blocked {
        let devices: Vec<Uuid> =
            sqlx::query_scalar("SELECT id FROM devices WHERE account_id = $1")
                .bind(account_id)
                .fetch_all(&mut *tx)
                .await?;
        crate::repos::device::end_vpn_sessions(&mut tx, &devices).await?;
        crate::repos::admin::audit(
            &mut tx,
            admin_id,
            "block_account",
            "account",
            &account_id.to_string(),
            serde_json::json!({ "reason": reason }),
        )
        .await?;
    }
    tx.commit().await?;
    Ok(blocked)
}

/// Lift a block. Tokens issued before the block work again; nothing else changed while
/// it was in place.
pub async fn unblock(pool: &PgPool, admin_id: Uuid, account_id: Uuid) -> anyhow::Result<bool> {
    let mut tx = pool.begin().await?;
    let unblocked = sqlx::query(
        r#"
        UPDATE accounts SET blocked_at = NULL, blocked_reason = NULL, updated_at = now()
        WHERE id = $1 AND deleted_at IS NULL AND blocked_at IS NOT NULL
        "#,
    )
    .bind(account_id)
    .execute(&mut *tx)
    .await?
    .rows_affected()
        > 0;
    if unblocked {
        crate::repos::admin::audit(
            &mut tx,
            admin_id,
            "unblock_account",
            "account",
            &account_id.to_string(),
            serde_json::json!({}),
        )
        .await?;
    }
    tx.commit().await?;
    Ok(unblocked)
}
