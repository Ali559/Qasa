use qasa_core::new_id;
use sqlx::{PgConnection, PgPool};
use uuid::Uuid;

use crate::models::AdminUserRow;

pub async fn find_by_email(pool: &PgPool, email: &str) -> anyhow::Result<Option<AdminUserRow>> {
    let row = sqlx::query_as::<_, AdminUserRow>(
        r#"
        SELECT id, email, password_hash, role
        FROM admin_users
        WHERE email = $1 AND disabled_at IS NULL
        "#,
    )
    .bind(email)
    .fetch_optional(pool)
    .await?;
    Ok(row)
}

pub async fn create_admin(
    pool: &PgPool,
    email: &str,
    password_hash: &str,
) -> anyhow::Result<AdminUserRow> {
    let id = new_id();
    let row = sqlx::query_as::<_, AdminUserRow>(
        r#"
        INSERT INTO admin_users (id, email, password_hash, role)
        VALUES ($1, $2, $3, 'admin')
        RETURNING id, email, password_hash, role
        "#,
    )
    .bind(id)
    .bind(email)
    .bind(password_hash)
    .fetch_one(pool)
    .await?;
    Ok(row)
}

/// Append to `admin_audit_log`, inside the caller's transaction so the record and the
/// change it describes commit together.
pub async fn audit(
    conn: &mut PgConnection,
    admin_id: Uuid,
    action: &str,
    target_type: &str,
    target_id: &str,
    payload: serde_json::Value,
) -> anyhow::Result<()> {
    sqlx::query(
        r#"
        INSERT INTO admin_audit_log (id, admin_id, action, target_type, target_id, payload)
        VALUES ($1, $2, $3, $4, $5, $6)
        "#,
    )
    .bind(new_id())
    .bind(admin_id)
    .bind(action)
    .bind(target_type)
    .bind(target_id)
    .bind(payload)
    .execute(conn)
    .await?;
    Ok(())
}

/// The admin's current role, or `None` when the admin no longer exists, is disabled, or
/// this token was logged out. Checked on every admin request: the role inside the JWT
/// is only what it was at login.
pub async fn live_role(pool: &PgPool, admin_id: Uuid, jti: &str) -> anyhow::Result<Option<String>> {
    let role = sqlx::query_scalar(
        r#"
        SELECT role FROM admin_users
        WHERE id = $1
          AND disabled_at IS NULL
          AND NOT EXISTS (SELECT 1 FROM admin_token_revocations WHERE jti = $2)
        "#,
    )
    .bind(admin_id)
    .bind(jti)
    .fetch_optional(pool)
    .await?;
    Ok(role)
}

/// Admin logout: the token stops working before its 8-hour expiry.
pub async fn revoke_token(
    pool: &PgPool,
    jti: &str,
    admin_id: Uuid,
    expires_at: chrono::DateTime<chrono::Utc>,
) -> anyhow::Result<()> {
    sqlx::query(
        r#"
        INSERT INTO admin_token_revocations (jti, admin_id, expires_at)
        VALUES ($1, $2, $3)
        ON CONFLICT (jti) DO NOTHING
        "#,
    )
    .bind(jti)
    .bind(admin_id)
    .bind(expires_at)
    .execute(pool)
    .await?;
    Ok(())
}

/// [`audit`] for changes that are a single statement and have no transaction of their own.
pub async fn audit_now(
    pool: &PgPool,
    admin_id: Uuid,
    action: &str,
    target_type: &str,
    target_id: &str,
    payload: serde_json::Value,
) -> anyhow::Result<()> {
    let mut conn = pool.acquire().await?;
    audit(&mut conn, admin_id, action, target_type, target_id, payload).await
}

#[derive(Debug, Clone, sqlx::FromRow, serde::Serialize)]
pub struct AuditRow {
    pub id: Uuid,
    pub admin_id: Uuid,
    pub admin_email: Option<String>,
    pub action: String,
    pub target_type: String,
    pub target_id: String,
    pub payload: Option<serde_json::Value>,
    pub created_at: chrono::DateTime<chrono::Utc>,
}

/// Newest first; optionally only entries about one target (e.g. an account id).
pub async fn list_audit(
    pool: &PgPool,
    target_id: Option<&str>,
    before: Option<chrono::DateTime<chrono::Utc>>,
    limit: i64,
) -> anyhow::Result<Vec<AuditRow>> {
    let rows = sqlx::query_as::<_, AuditRow>(
        r#"
        SELECT l.id, l.admin_id, u.email AS admin_email, l.action, l.target_type,
               l.target_id, l.payload, l.created_at
        FROM admin_audit_log l
        LEFT JOIN admin_users u ON u.id = l.admin_id
        WHERE ($1::text IS NULL OR l.target_id = $1)
          AND ($2::timestamptz IS NULL OR l.created_at < $2)
        ORDER BY l.created_at DESC
        LIMIT $3
        "#,
    )
    .bind(target_id)
    .bind(before)
    .bind(limit)
    .fetch_all(pool)
    .await?;
    Ok(rows)
}
