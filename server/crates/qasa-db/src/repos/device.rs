use qasa_core::new_id;
use sqlx::PgPool;
use uuid::Uuid;

use crate::models::DeviceRow;

const DEVICE_COLS: &str = r#"
        id, account_id, fingerprint, device_name, platform, location,
        license_jti, last_seen_at, revoked_at, created_at
"#;

pub async fn find_by_fingerprint(
    pool: &PgPool,
    account_id: Uuid,
    fingerprint: &str,
) -> anyhow::Result<Option<DeviceRow>> {
    let row = sqlx::query_as::<_, DeviceRow>(&format!(
        r#"
        SELECT {DEVICE_COLS}
        FROM devices
        WHERE account_id = $1 AND fingerprint = $2 AND revoked_at IS NULL
        "#
    ))
    .bind(account_id)
    .bind(fingerprint)
    .fetch_optional(pool)
    .await?;
    Ok(row)
}

pub async fn count_active(pool: &PgPool, account_id: Uuid) -> anyhow::Result<i64> {
    let count: i64 = sqlx::query_scalar(
        r#"
        SELECT COUNT(*)::bigint
        FROM devices
        WHERE account_id = $1 AND revoked_at IS NULL
        "#,
    )
    .bind(account_id)
    .fetch_one(pool)
    .await?;
    Ok(count)
}

pub async fn list_active(pool: &PgPool, account_id: Uuid) -> anyhow::Result<Vec<DeviceRow>> {
    let rows = sqlx::query_as::<_, DeviceRow>(&format!(
        r#"
        SELECT {DEVICE_COLS}
        FROM devices
        WHERE account_id = $1 AND revoked_at IS NULL
        ORDER BY created_at ASC
        "#
    ))
    .bind(account_id)
    .fetch_all(pool)
    .await?;
    Ok(rows)
}

pub async fn insert_device(
    pool: &PgPool,
    account_id: Uuid,
    fingerprint: &str,
    device_name: &str,
    platform: &str,
    location: Option<&str>,
) -> anyhow::Result<DeviceRow> {
    let id = new_id();
    let row = sqlx::query_as::<_, DeviceRow>(&format!(
        r#"
        INSERT INTO devices (id, account_id, fingerprint, device_name, platform, location, last_seen_at)
        VALUES ($1, $2, $3, $4, $5, $6, now())
        RETURNING {DEVICE_COLS}
        "#
    ))
    .bind(id)
    .bind(account_id)
    .bind(fingerprint)
    .bind(device_name)
    .bind(platform)
    .bind(location)
    .fetch_one(pool)
    .await?;
    Ok(row)
}

pub async fn touch_device(
    pool: &PgPool,
    device_id: Uuid,
    location: Option<&str>,
) -> anyhow::Result<DeviceRow> {
    let row = if let Some(loc) = location.filter(|s| !s.trim().is_empty()) {
        sqlx::query_as::<_, DeviceRow>(&format!(
            r#"
            UPDATE devices
            SET last_seen_at = now(), location = $2
            WHERE id = $1 AND revoked_at IS NULL
            RETURNING {DEVICE_COLS}
            "#
        ))
        .bind(device_id)
        .bind(loc)
        .fetch_one(pool)
        .await?
    } else {
        sqlx::query_as::<_, DeviceRow>(&format!(
            r#"
            UPDATE devices
            SET last_seen_at = now()
            WHERE id = $1 AND revoked_at IS NULL
            RETURNING {DEVICE_COLS}
            "#
        ))
        .bind(device_id)
        .fetch_one(pool)
        .await?
    };
    Ok(row)
}

pub async fn set_license_jti(pool: &PgPool, device_id: Uuid, jti: &str) -> anyhow::Result<()> {
    sqlx::query(
        r#"
        UPDATE devices SET license_jti = $2, last_seen_at = now()
        WHERE id = $1
        "#,
    )
    .bind(device_id)
    .bind(jti)
    .execute(pool)
    .await?;
    Ok(())
}

pub async fn revoke_device(pool: &PgPool, account_id: Uuid, device_id: Uuid) -> anyhow::Result<bool> {
    let result = sqlx::query(
        r#"
        UPDATE devices
        SET revoked_at = now()
        WHERE id = $1 AND account_id = $2 AND revoked_at IS NULL
        "#,
    )
    .bind(device_id)
    .bind(account_id)
    .execute(pool)
    .await?;
    Ok(result.rows_affected() > 0)
}

/// Revoke every active device except the oldest (primary / first linked).
/// Returns the count of devices revoked.
pub async fn revoke_all_except_primary(pool: &PgPool, account_id: Uuid) -> anyhow::Result<u64> {
    let result = sqlx::query(
        r#"
        UPDATE devices
        SET revoked_at = now()
        WHERE account_id = $1
          AND revoked_at IS NULL
          AND id <> (
            SELECT id FROM devices
            WHERE account_id = $1 AND revoked_at IS NULL
            ORDER BY created_at ASC
            LIMIT 1
          )
        "#,
    )
    .bind(account_id)
    .execute(pool)
    .await?;
    Ok(result.rows_affected())
}

pub async fn is_jti_revoked(pool: &PgPool, jti: &str) -> anyhow::Result<bool> {
    let exists: bool = sqlx::query_scalar("SELECT EXISTS(SELECT 1 FROM license_revocations WHERE jti = $1)")
        .bind(jti)
        .fetch_one(pool)
        .await?;
    Ok(exists)
}

/// Revoke a license token by its `jti`.
///
/// `license_revocations` was previously only ever read and purged — nothing inserted a
/// row, so `is_jti_revoked` could never return true and revocation was silently a no-op.
/// Idempotent: re-revoking an already-revoked token is not an error.
///
/// Returns `true` when this call inserted a new revocation.
pub async fn revoke_license_jti(
    pool: &PgPool,
    jti: &str,
    account_id: Uuid,
    reason: Option<&str>,
) -> anyhow::Result<bool> {
    let jti = jti.trim();
    if jti.is_empty() {
        anyhow::bail!("jti must not be empty");
    }
    let inserted = sqlx::query(
        r#"
        INSERT INTO license_revocations (jti, account_id, reason)
        VALUES ($1, $2, $3)
        ON CONFLICT (jti) DO NOTHING
        "#,
    )
    .bind(jti)
    .bind(account_id)
    .bind(reason)
    .execute(pool)
    .await?
    .rows_affected();
    Ok(inserted > 0)
}

/// Lift a revocation. Returns `true` when a row was actually removed.
pub async fn unrevoke_license_jti(pool: &PgPool, jti: &str) -> anyhow::Result<bool> {
    let removed = sqlx::query("DELETE FROM license_revocations WHERE jti = $1")
        .bind(jti)
        .execute(pool)
        .await?
        .rows_affected();
    Ok(removed > 0)
}

/// List current revocations, newest first.
pub async fn list_license_revocations(
    pool: &PgPool,
) -> anyhow::Result<Vec<LicenseRevocationRow>> {
    let rows = sqlx::query_as::<_, LicenseRevocationRow>(
        r#"
        SELECT jti, account_id, reason, revoked_at
        FROM license_revocations
        ORDER BY revoked_at DESC
        LIMIT 500
        "#,
    )
    .fetch_all(pool)
    .await?;
    Ok(rows)
}

#[derive(Debug, Clone, serde::Serialize, sqlx::FromRow)]
pub struct LicenseRevocationRow {
    pub jti: String,
    pub account_id: Uuid,
    pub reason: Option<String>,
    pub revoked_at: chrono::DateTime<chrono::Utc>,
}

pub async fn get_device_for_account(
    pool: &PgPool,
    account_id: Uuid,
    device_id: Uuid,
) -> anyhow::Result<Option<DeviceRow>> {
    let row = sqlx::query_as::<_, DeviceRow>(&format!(
        r#"
        SELECT {DEVICE_COLS}
        FROM devices
        WHERE id = $1 AND account_id = $2 AND revoked_at IS NULL
        "#
    ))
    .bind(device_id)
    .bind(account_id)
    .fetch_optional(pool)
    .await?;
    Ok(row)
}

pub async fn update_locale_and_push(
    pool: &PgPool,
    device_id: Uuid,
    locale: &str,
    push_token: Option<&str>,
    notifications_enabled: bool,
) -> anyhow::Result<()> {
    sqlx::query(
        r#"
        UPDATE devices
        SET locale = $2,
            push_token = COALESCE($3, push_token),
            notifications_enabled = $4,
            last_seen_at = now()
        WHERE id = $1 AND revoked_at IS NULL
        "#,
    )
    .bind(device_id)
    .bind(locale)
    .bind(push_token)
    .bind(notifications_enabled)
    .execute(pool)
    .await?;
    Ok(())
}
