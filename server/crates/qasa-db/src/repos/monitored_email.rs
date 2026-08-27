use chrono::{DateTime, Utc};
use qasa_core::new_id;
use sqlx::PgPool;
use uuid::Uuid;

#[derive(Debug, Clone, sqlx::FromRow)]
pub struct MonitoredHashRow {
    pub id: Uuid,
    pub account_id: Uuid,
    pub email_hash: String,
    pub label_hint: Option<String>,
    pub created_at: DateTime<Utc>,
    pub last_matched_at: Option<DateTime<Utc>>,
    pub last_alerted_at: Option<DateTime<Utc>>,
}

#[derive(Debug, Clone, sqlx::FromRow)]
pub struct BreachAlertCandidate {
    pub monitor_id: Uuid,
    pub account_id: Uuid,
    pub email_hash: String,
    pub tier: String,
}

pub async fn upsert_hash(
    pool: &PgPool,
    account_id: Uuid,
    email_hash: &str,
    label_hint: Option<&str>,
) -> anyhow::Result<MonitoredHashRow> {
    let id = new_id();
    let row = sqlx::query_as::<_, MonitoredHashRow>(
        r#"
        INSERT INTO monitored_email_hashes (id, account_id, email_hash, label_hint)
        VALUES ($1, $2, $3, $4)
        ON CONFLICT (account_id, email_hash) DO UPDATE
          SET label_hint = COALESCE(EXCLUDED.label_hint, monitored_email_hashes.label_hint)
        RETURNING id, account_id, email_hash, label_hint, created_at, last_matched_at, last_alerted_at
        "#,
    )
    .bind(id)
    .bind(account_id)
    .bind(email_hash.to_lowercase())
    .bind(label_hint)
    .fetch_one(pool)
    .await?;
    Ok(row)
}

pub async fn list_for_account(
    pool: &PgPool,
    account_id: Uuid,
) -> anyhow::Result<Vec<MonitoredHashRow>> {
    let rows = sqlx::query_as::<_, MonitoredHashRow>(
        r#"
        SELECT id, account_id, email_hash, label_hint, created_at, last_matched_at, last_alerted_at
        FROM monitored_email_hashes
        WHERE account_id = $1
        ORDER BY created_at ASC
        "#,
    )
    .bind(account_id)
    .fetch_all(pool)
    .await?;
    Ok(rows)
}

pub async fn count_for_account(pool: &PgPool, account_id: Uuid) -> anyhow::Result<i64> {
    let n: i64 = sqlx::query_scalar(
        r#"
        SELECT COUNT(*)::bigint FROM monitored_email_hashes WHERE account_id = $1
        "#,
    )
    .bind(account_id)
    .fetch_one(pool)
    .await?;
    Ok(n)
}

pub async fn delete_hash(
    pool: &PgPool,
    account_id: Uuid,
    email_hash: &str,
) -> anyhow::Result<bool> {
    let r = sqlx::query(
        r#"
        DELETE FROM monitored_email_hashes
        WHERE account_id = $1 AND email_hash = $2
        "#,
    )
    .bind(account_id)
    .bind(email_hash.to_lowercase())
    .execute(pool)
    .await?;
    Ok(r.rows_affected() > 0)
}

/// Full SHA-256 hex present in breach_hashes (prefix+suffix), not alerted in last 7 days.
pub async fn find_unalerted_matches(pool: &PgPool, limit: i64) -> anyhow::Result<Vec<BreachAlertCandidate>> {
    let rows = sqlx::query_as::<_, BreachAlertCandidate>(
        r#"
        SELECT m.id AS monitor_id, m.account_id, m.email_hash, a.tier
        FROM monitored_email_hashes m
        JOIN accounts a ON a.id = m.account_id
        WHERE EXISTS (
          SELECT 1 FROM breach_hashes b
          WHERE b.prefix = left(m.email_hash, 5)
            AND lower(b.suffix) = lower(substring(m.email_hash from 6))
        )
        AND (
          m.last_alerted_at IS NULL
          OR m.last_alerted_at < now() - interval '7 days'
        )
        ORDER BY m.created_at ASC
        LIMIT $1
        "#,
    )
    .bind(limit)
    .fetch_all(pool)
    .await?;
    Ok(rows)
}

pub async fn mark_alerted(pool: &PgPool, monitor_id: Uuid) -> anyhow::Result<()> {
    sqlx::query(
        r#"
        UPDATE monitored_email_hashes
        SET last_matched_at = now(), last_alerted_at = now()
        WHERE id = $1
        "#,
    )
    .bind(monitor_id)
    .execute(pool)
    .await?;
    Ok(())
}

pub async fn hash_is_breached(pool: &PgPool, email_hash: &str) -> anyhow::Result<bool> {
    let hash = email_hash.to_lowercase();
    if hash.len() != 64 {
        return Ok(false);
    }
    let prefix = &hash[..5];
    let suffix = &hash[5..];
    let exists: bool = sqlx::query_scalar(
        r#"
        SELECT EXISTS(
          SELECT 1 FROM breach_hashes
          WHERE prefix = $1 AND lower(suffix) = $2
        )
        "#,
    )
    .bind(prefix)
    .bind(suffix)
    .fetch_one(pool)
    .await?;
    Ok(exists)
}
