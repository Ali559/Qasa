use chrono::{DateTime, Utc};
use qasa_core::new_id;
use sqlx::PgPool;
use uuid::Uuid;

#[derive(Debug, Clone, sqlx::FromRow, serde::Serialize)]
pub struct NotificationRow {
    pub id: Uuid,
    pub title_en: String,
    pub title_ckb: Option<String>,
    pub title_ar: Option<String>,
    pub body_en: String,
    pub body_ckb: Option<String>,
    pub body_ar: Option<String>,
    pub audience: String,
    pub created_at: DateTime<Utc>,
}

#[derive(Debug, Clone, sqlx::FromRow, serde::Serialize)]
pub struct NotificationListItem {
    pub id: Uuid,
    pub title_en: String,
    pub title_ckb: Option<String>,
    pub title_ar: Option<String>,
    pub body_en: String,
    pub body_ckb: Option<String>,
    pub body_ar: Option<String>,
    pub created_at: DateTime<Utc>,
    pub read_at: Option<DateTime<Utc>>,
}

pub async fn create(
    pool: &PgPool,
    title_en: &str,
    title_ckb: Option<&str>,
    title_ar: Option<&str>,
    body_en: &str,
    body_ckb: Option<&str>,
    body_ar: Option<&str>,
    audience: &str,
    created_by: Option<Uuid>,
) -> anyhow::Result<NotificationRow> {
    create_with_meta(
        pool,
        title_en,
        title_ckb,
        title_ar,
        body_en,
        body_ckb,
        body_ar,
        audience,
        created_by,
        "generic",
        None,
    )
    .await?
    .ok_or_else(|| anyhow::anyhow!("notification create failed"))
}

pub async fn create_with_meta(
    pool: &PgPool,
    title_en: &str,
    title_ckb: Option<&str>,
    title_ar: Option<&str>,
    body_en: &str,
    body_ckb: Option<&str>,
    body_ar: Option<&str>,
    audience: &str,
    created_by: Option<Uuid>,
    kind: &str,
    dedupe_key: Option<&str>,
) -> anyhow::Result<Option<NotificationRow>> {
    let id = new_id();
    let row = sqlx::query_as::<_, NotificationRow>(
        r#"
        INSERT INTO notifications (
          id, title_en, title_ckb, title_ar, body_en, body_ckb, body_ar,
          audience, created_by, kind, dedupe_key
        )
        SELECT $1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11
        WHERE $11::text IS NULL
           OR NOT EXISTS (SELECT 1 FROM notifications n WHERE n.dedupe_key = $11)
        RETURNING id, title_en, title_ckb, title_ar, body_en, body_ckb, body_ar, audience, created_at
        "#,
    )
    .bind(id)
    .bind(title_en)
    .bind(title_ckb)
    .bind(title_ar)
    .bind(body_en)
    .bind(body_ckb)
    .bind(body_ar)
    .bind(audience)
    .bind(created_by)
    .bind(kind)
    .bind(dedupe_key)
    .fetch_optional(pool)
    .await?;
    Ok(row)
}

/// Create a multilingual notification for a single account (+ optional push stub).
pub async fn notify_account(
    pool: &PgPool,
    account_id: Uuid,
    copy: &crate::repos::push_copy::LocalizedNotice,
    dedupe_key: &str,
) -> anyhow::Result<Option<NotificationRow>> {
    let Some(n) = create_with_meta(
        pool,
        &copy.title_en,
        Some(&copy.title_ckb),
        Some(&copy.title_ar),
        &copy.body_en,
        Some(&copy.body_ckb),
        Some(&copy.body_ar),
        "targeted",
        None,
        &copy.kind,
        Some(dedupe_key),
    )
    .await?
    else {
        return Ok(None);
    };

    sqlx::query(
        r#"
        INSERT INTO notification_receipts (notification_id, account_id)
        VALUES ($1, $2)
        ON CONFLICT DO NOTHING
        "#,
    )
    .bind(n.id)
    .bind(account_id)
    .execute(pool)
    .await?;

    Ok(Some(n))
}

/// Audiences an admin broadcast can target. `targeted` is reserved for notices written
/// for one account (breach alerts, expiry reminders).
pub const BROADCAST_AUDIENCES: [&str; 3] = ["all", "subscribers", "free"];

/// Give a broadcast an inbox receipt for every account in its audience:
/// `all`, `subscribers` (a live subscription, paid or granted) or `free` (none).
/// Deleted and blocked accounts are skipped. It used to insert a receipt for every
/// account regardless of `audience`, so "subscribers only" notices reached everyone.
pub async fn fanout_receipts(pool: &PgPool, notification_id: Uuid) -> anyhow::Result<i64> {
    let result = sqlx::query(
        r#"
        INSERT INTO notification_receipts (notification_id, account_id)
        SELECT n.id, a.id
        FROM notifications n
        CROSS JOIN LATERAL (
            SELECT acc.id,
                   EXISTS (
                     SELECT 1 FROM subscriptions s
                     WHERE s.account_id = acc.id
                       AND s.status IN ('active', 'cancelled')
                       AND s.expires_at > now()
                   ) AS premium
            FROM accounts acc
            WHERE acc.deleted_at IS NULL AND acc.blocked_at IS NULL
        ) a
        WHERE n.id = $1
          AND (
            n.audience = 'all'
            OR (n.audience = 'subscribers' AND a.premium)
            OR (n.audience = 'free' AND NOT a.premium)
          )
        ON CONFLICT DO NOTHING
        "#,
    )
    .bind(notification_id)
    .execute(pool)
    .await?;
    Ok(result.rows_affected() as i64)
}

/// One page of the account's inbox, newest first; deleted entries are hidden.
pub async fn list_for_account(
    pool: &PgPool,
    account_id: Uuid,
    before: Option<DateTime<Utc>>,
    limit: i64,
) -> anyhow::Result<Vec<NotificationListItem>> {
    let rows = sqlx::query_as::<_, NotificationListItem>(
        r#"
        SELECT n.id, n.title_en, n.title_ckb, n.title_ar,
               n.body_en, n.body_ckb, n.body_ar, n.created_at, r.read_at
        FROM notification_receipts r
        JOIN notifications n ON n.id = r.notification_id
        WHERE r.account_id = $1
          AND r.deleted_at IS NULL
          AND ($2::timestamptz IS NULL OR n.created_at < $2)
        ORDER BY n.created_at DESC
        LIMIT $3
        "#,
    )
    .bind(account_id)
    .bind(before)
    .bind(limit)
    .fetch_all(pool)
    .await?;
    Ok(rows)
}

pub async fn unread_count(pool: &PgPool, account_id: Uuid) -> anyhow::Result<i64> {
    let n = sqlx::query_scalar(
        r#"
        SELECT COUNT(*)::bigint FROM notification_receipts
        WHERE account_id = $1 AND read_at IS NULL AND deleted_at IS NULL
        "#,
    )
    .bind(account_id)
    .fetch_one(pool)
    .await?;
    Ok(n)
}

pub async fn get_for_account(
    pool: &PgPool,
    account_id: Uuid,
    notification_id: Uuid,
) -> anyhow::Result<Option<NotificationListItem>> {
    let row = sqlx::query_as::<_, NotificationListItem>(
        r#"
        SELECT n.id, n.title_en, n.title_ckb, n.title_ar,
               n.body_en, n.body_ckb, n.body_ar, n.created_at, r.read_at
        FROM notification_receipts r
        JOIN notifications n ON n.id = r.notification_id
        WHERE r.account_id = $1 AND n.id = $2 AND r.deleted_at IS NULL
        "#,
    )
    .bind(account_id)
    .bind(notification_id)
    .fetch_optional(pool)
    .await?;
    Ok(row)
}

/// Mark one notification read. Returns `false` when it is not in this inbox. Already
/// read keeps its original `read_at`.
pub async fn mark_read(
    pool: &PgPool,
    account_id: Uuid,
    notification_id: Uuid,
) -> anyhow::Result<bool> {
    let n = sqlx::query(
        r#"
        UPDATE notification_receipts
        SET read_at = COALESCE(read_at, now())
        WHERE account_id = $1 AND notification_id = $2 AND deleted_at IS NULL
        "#,
    )
    .bind(account_id)
    .bind(notification_id)
    .execute(pool)
    .await?
    .rows_affected();
    Ok(n > 0)
}

/// Mark everything in the inbox read. Returns how many were unread.
pub async fn mark_all_read(pool: &PgPool, account_id: Uuid) -> anyhow::Result<u64> {
    let n = sqlx::query(
        r#"
        UPDATE notification_receipts SET read_at = now()
        WHERE account_id = $1 AND read_at IS NULL AND deleted_at IS NULL
        "#,
    )
    .bind(account_id)
    .execute(pool)
    .await?
    .rows_affected();
    Ok(n)
}

/// Remove a notification from this account's inbox only. Returns `false` when it was
/// not there (or already removed).
pub async fn delete_for_account(
    pool: &PgPool,
    account_id: Uuid,
    notification_id: Uuid,
) -> anyhow::Result<bool> {
    let n = sqlx::query(
        r#"
        UPDATE notification_receipts SET deleted_at = now()
        WHERE account_id = $1 AND notification_id = $2 AND deleted_at IS NULL
        "#,
    )
    .bind(account_id)
    .bind(notification_id)
    .execute(pool)
    .await?
    .rows_affected();
    Ok(n > 0)
}

#[derive(Debug, Clone, sqlx::FromRow)]
pub struct PendingBroadcastPush {
    pub notification_id: Uuid,
    pub account_id: Uuid,
    pub title_en: String,
    pub title_ckb: Option<String>,
    pub title_ar: Option<String>,
    pub body_en: String,
    pub body_ckb: Option<String>,
    pub body_ar: Option<String>,
    pub kind: String,
}

/// Broadcast receipts not pushed yet, oldest first. Only broadcasts from the last
/// `max_age_hours`: a push about something days old is noise, the inbox still has it.
/// Read or deleted receipts are skipped; the user has already seen or dismissed it.
pub async fn pending_broadcast_pushes(
    pool: &PgPool,
    max_age_hours: i32,
    limit: i64,
) -> anyhow::Result<Vec<PendingBroadcastPush>> {
    let rows = sqlx::query_as::<_, PendingBroadcastPush>(
        r#"
        SELECT r.notification_id, r.account_id,
               n.title_en, n.title_ckb, n.title_ar, n.body_en, n.body_ckb, n.body_ar, n.kind
        FROM notification_receipts r
        JOIN notifications n ON n.id = r.notification_id
        WHERE r.pushed_at IS NULL
          AND r.read_at IS NULL
          AND r.deleted_at IS NULL
          AND n.audience <> 'targeted'
          AND n.created_at > now() - make_interval(hours => $1)
        ORDER BY n.created_at ASC
        LIMIT $2
        "#,
    )
    .bind(max_age_hours)
    .bind(limit)
    .fetch_all(pool)
    .await?;
    Ok(rows)
}

/// Record that a receipt's push was handled (sent, or no device to send to), so the
/// worker does not try it again.
pub async fn mark_pushed(pool: &PgPool, notification_id: Uuid, account_id: Uuid) -> anyhow::Result<()> {
    sqlx::query(
        "UPDATE notification_receipts SET pushed_at = now() WHERE notification_id = $1 AND account_id = $2",
    )
    .bind(notification_id)
    .bind(account_id)
    .execute(pool)
    .await?;
    Ok(())
}

pub async fn list_admin(pool: &PgPool, limit: i64) -> anyhow::Result<Vec<NotificationRow>> {
    let rows = sqlx::query_as::<_, NotificationRow>(
        r#"
        SELECT id, title_en, title_ckb, title_ar, body_en, body_ckb, body_ar, audience, created_at
        FROM notifications
        ORDER BY created_at DESC
        LIMIT $1
        "#,
    )
    .bind(limit)
    .fetch_all(pool)
    .await?;
    Ok(rows)
}
