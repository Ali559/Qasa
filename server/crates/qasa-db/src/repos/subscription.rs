use chrono::{DateTime, Duration, Utc};
use qasa_core::new_id;
use sqlx::{PgConnection, PgPool};
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

pub fn plan_days_fallback(plan_id: &str) -> i64 {
    match plan_id {
        "premium_yearly" => 365,
        _ => 30,
    }
}

/// Resolve the short aliases older clients send. Any other id is returned unchanged and
/// must exist in `plans`; it is no longer silently turned into `premium_monthly`, which
/// made every plan created in the dashboard unpurchasable.
pub fn normalize_plan_id(plan_id: &str) -> &str {
    match plan_id.trim() {
        "yearly" => "premium_yearly",
        "monthly" => "premium_monthly",
        other => other,
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

/// Grant `plan_id` to the account inside the caller's transaction.
///
/// Time stacks: a renewal or plan change paid before the current subscription ends
/// starts counting from that end, so paying early never forfeits days already paid for.
/// The account row is locked first so two settlements for one account cannot both read
/// the same "current end" and grant overlapping periods.
pub async fn activate_subscription(
    conn: &mut PgConnection,
    account_id: Uuid,
    amount_iqd: i32,
    plan_id: &str,
    auto_renew: bool,
) -> anyhow::Result<SubscriptionRow> {
    let plan_id = normalize_plan_id(plan_id);

    // A payment can outlive its plan being edited, so fall back rather than refusing to
    // honour an order that was valid when it was placed.
    let days: i64 = sqlx::query_scalar::<_, i64>("SELECT days::bigint FROM plans WHERE id = $1")
        .bind(plan_id)
        .fetch_optional(&mut *conn)
        .await?
        .unwrap_or_else(|| plan_days_fallback(plan_id));

    insert_stacked(conn, account_id, amount_iqd, plan_id, auto_renew, days, None).await
}

/// Who granted a staff-granted subscription, and why.
#[derive(Debug, Clone, Copy)]
pub struct GrantInfo<'a> {
    pub admin_id: Uuid,
    pub reason: &'a str,
}

/// Lock the account, stack `days` onto whatever is still live, and make the new row the
/// account's only live subscription.
async fn insert_stacked(
    conn: &mut PgConnection,
    account_id: Uuid,
    amount_iqd: i32,
    plan_id: &str,
    auto_renew: bool,
    days: i64,
    grant: Option<GrantInfo<'_>>,
) -> anyhow::Result<SubscriptionRow> {
    sqlx::query("SELECT id FROM accounts WHERE id = $1 FOR UPDATE")
        .bind(account_id)
        .execute(&mut *conn)
        .await?;

    let current_end: Option<DateTime<Utc>> = sqlx::query_scalar(
        r#"
        SELECT max(expires_at)
        FROM subscriptions
        WHERE account_id = $1
          AND status IN ('active', 'cancelled')
          AND expires_at > now()
        "#,
    )
    .bind(account_id)
    .fetch_one(&mut *conn)
    .await?;

    let id = new_id();
    let started_at = Utc::now();
    let expires_at = current_end.unwrap_or(started_at).max(started_at) + Duration::days(days);

    sqlx::query(
        r#"
        UPDATE subscriptions SET status = 'expired'
        WHERE account_id = $1 AND status IN ('active', 'cancelled')
        "#,
    )
    .bind(account_id)
    .execute(&mut *conn)
    .await?;

    let row = sqlx::query_as::<_, SubscriptionRow>(
        r#"
        INSERT INTO subscriptions (
            id, account_id, status, plan_id, amount_iqd, auto_renew,
            started_at, expires_at, granted_by, grant_days, grant_reason
        )
        VALUES ($1, $2, 'active', $3, $4, $5, $6, $7, $8, $9, $10)
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
    .bind(grant.map(|g| g.admin_id))
    .bind(grant.map(|_| days as i32))
    .bind(grant.map(|g| g.reason))
    .fetch_one(&mut *conn)
    .await?;

    sqlx::query("UPDATE accounts SET tier = 'premium', updated_at = now() WHERE id = $1")
        .bind(account_id)
        .execute(&mut *conn)
        .await?;

    Ok(row)
}

/// Give an account Premium for `days` without a payment, stacked like a renewal, and
/// record it in `admin_audit_log` in the same transaction.
///
/// The row carries `amount_iqd = 0` and `auto_renew = false`, so revenue figures and
/// renewal reminders treat it as what it is.
pub async fn grant_premium(
    pool: &PgPool,
    account_id: Uuid,
    plan_id: &str,
    days: i64,
    grant: GrantInfo<'_>,
) -> anyhow::Result<SubscriptionRow> {
    let mut tx = pool.begin().await?;
    let row = insert_stacked(&mut tx, account_id, 0, plan_id, false, days, Some(grant)).await?;
    crate::repos::admin::audit(
        &mut tx,
        grant.admin_id,
        "grant_premium",
        "account",
        &account_id.to_string(),
        serde_json::json!({
            "subscription_id": row.id,
            "plan_id": plan_id,
            "days": days,
            "reason": grant.reason,
            "expires_at": row.expires_at,
        }),
    )
    .await?;
    tx.commit().await?;
    Ok(row)
}

#[derive(Debug)]
pub enum RevokeGrantOutcome {
    /// The granted days were taken off; the subscription may still be live if the grant
    /// was stacked on paid time.
    Revoked(SubscriptionRow),
    NotFound,
    AlreadyRevoked,
    /// A later subscription replaced this row (stacking folds remaining time into the
    /// newest row), so the granted days can no longer be separated from paid ones.
    Superseded,
}

/// Take a grant's days back off. Allowed once, and only while the grant is still the
/// account's current subscription.
pub async fn revoke_grant(
    pool: &PgPool,
    admin_id: Uuid,
    subscription_id: Uuid,
) -> anyhow::Result<RevokeGrantOutcome> {
    let mut tx = pool.begin().await?;

    #[derive(sqlx::FromRow)]
    struct GrantRow {
        account_id: Uuid,
        status: String,
        expires_at: DateTime<Utc>,
        grant_days: Option<i32>,
        grant_revoked_at: Option<DateTime<Utc>>,
    }
    let Some(grant) = sqlx::query_as::<_, GrantRow>(
        r#"
        SELECT account_id, status, expires_at, grant_days, grant_revoked_at
        FROM subscriptions
        WHERE id = $1 AND granted_by IS NOT NULL
        FOR UPDATE
        "#,
    )
    .bind(subscription_id)
    .fetch_optional(&mut *tx)
    .await?
    else {
        return Ok(RevokeGrantOutcome::NotFound);
    };

    if grant.grant_revoked_at.is_some() {
        return Ok(RevokeGrantOutcome::AlreadyRevoked);
    }
    if !matches!(grant.status.as_str(), "active" | "cancelled") || grant.expires_at <= Utc::now() {
        return Ok(RevokeGrantOutcome::Superseded);
    }

    let days = i64::from(grant.grant_days.unwrap_or(0));
    let row = sqlx::query_as::<_, SubscriptionRow>(
        r#"
        UPDATE subscriptions
        SET expires_at = expires_at - make_interval(days => $2::int),
            status = CASE
              WHEN expires_at - make_interval(days => $2::int) <= now() THEN 'expired'
              ELSE status
            END,
            grant_revoked_at = now()
        WHERE id = $1
        RETURNING id, account_id, status, plan_id, amount_iqd, auto_renew,
                  started_at, expires_at, cancelled_at, cancellation_reason
        "#,
    )
    .bind(subscription_id)
    .bind(days as i32)
    .fetch_one(&mut *tx)
    .await?;

    crate::repos::admin::audit(
        &mut tx,
        admin_id,
        "revoke_grant",
        "account",
        &grant.account_id.to_string(),
        serde_json::json!({
            "subscription_id": subscription_id,
            "days_removed": days,
            "expires_at": row.expires_at,
            "status": row.status,
        }),
    )
    .await?;
    tx.commit().await?;
    Ok(RevokeGrantOutcome::Revoked(row))
}

#[derive(Debug, Clone, sqlx::FromRow, serde::Serialize)]
pub struct GrantListItem {
    pub subscription_id: Uuid,
    pub account_id: Uuid,
    pub plan_id: String,
    pub status: String,
    pub expires_at: DateTime<Utc>,
    pub grant_days: Option<i32>,
    pub grant_reason: Option<String>,
    pub granted_by_email: Option<String>,
    pub granted_at: DateTime<Utc>,
    pub grant_revoked_at: Option<DateTime<Utc>>,
}

pub async fn list_grants(pool: &PgPool, limit: i64) -> anyhow::Result<Vec<GrantListItem>> {
    let rows = sqlx::query_as::<_, GrantListItem>(
        r#"
        SELECT s.id AS subscription_id, s.account_id, s.plan_id, s.status, s.expires_at,
               s.grant_days, s.grant_reason, a.email AS granted_by_email,
               s.created_at AS granted_at, s.grant_revoked_at
        FROM subscriptions s
        LEFT JOIN admin_users a ON a.id = s.granted_by
        WHERE s.granted_by IS NOT NULL
        ORDER BY s.created_at DESC
        LIMIT $1
        "#,
    )
    .bind(limit)
    .fetch_all(pool)
    .await?;
    Ok(rows)
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
        SET auto_renew = $3,
            -- Turning it back on after renewals gave up starts a fresh round, and a
            -- fresh "renewal failed" notice if that round fails too.
            renewal_attempts = CASE WHEN $3 AND NOT auto_renew THEN 0 ELSE renewal_attempts END,
            renew_failed_notified_at = CASE WHEN $3 AND NOT auto_renew THEN NULL ELSE renew_failed_notified_at END
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
