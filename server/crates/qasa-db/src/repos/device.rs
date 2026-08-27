use qasa_core::new_id;
use sqlx::PgPool;
use uuid::Uuid;

use crate::models::DeviceRow;

pub async fn find_by_fingerprint(
    pool: &PgPool,
    account_id: Uuid,
    fingerprint: &str,
) -> anyhow::Result<Option<DeviceRow>> {
    let row = sqlx::query_as::<_, DeviceRow>(
        r#"
        SELECT id, account_id, fingerprint, device_name, platform,
               license_jti, last_seen_at, revoked_at, created_at
        FROM devices
        WHERE account_id = $1 AND fingerprint = $2 AND revoked_at IS NULL
        "#,
    )
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
    let rows = sqlx::query_as::<_, DeviceRow>(
        r#"
        SELECT id, account_id, fingerprint, device_name, platform,
               license_jti, last_seen_at, revoked_at, created_at
        FROM devices
        WHERE account_id = $1 AND revoked_at IS NULL
        ORDER BY created_at ASC
        "#,
    )
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
) -> anyhow::Result<DeviceRow> {
    let id = new_id();
    let row = sqlx::query_as::<_, DeviceRow>(
        r#"
        INSERT INTO devices (id, account_id, fingerprint, device_name, platform, last_seen_at)
        VALUES ($1, $2, $3, $4, $5, now())
        RETURNING id, account_id, fingerprint, device_name, platform,
                  license_jti, last_seen_at, revoked_at, created_at
        "#,
    )
    .bind(id)
    .bind(account_id)
    .bind(fingerprint)
    .bind(device_name)
    .bind(platform)
    .fetch_one(pool)
    .await?;
    Ok(row)
}

pub async fn touch_device(pool: &PgPool, device_id: Uuid) -> anyhow::Result<DeviceRow> {
    let row = sqlx::query_as::<_, DeviceRow>(
        r#"
        UPDATE devices
        SET last_seen_at = now()
        WHERE id = $1 AND revoked_at IS NULL
        RETURNING id, account_id, fingerprint, device_name, platform,
                  license_jti, last_seen_at, revoked_at, created_at
        "#,
    )
    .bind(device_id)
    .fetch_one(pool)
    .await?;
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

pub async fn is_jti_revoked(pool: &PgPool, jti: &str) -> anyhow::Result<bool> {
    let exists: bool = sqlx::query_scalar("SELECT EXISTS(SELECT 1 FROM license_revocations WHERE jti = $1)")
        .bind(jti)
        .fetch_one(pool)
        .await?;
    Ok(exists)
}

pub async fn get_device_for_account(
    pool: &PgPool,
    account_id: Uuid,
    device_id: Uuid,
) -> anyhow::Result<Option<DeviceRow>> {
    let row = sqlx::query_as::<_, DeviceRow>(
        r#"
        SELECT id, account_id, fingerprint, device_name, platform,
               license_jti, last_seen_at, revoked_at, created_at
        FROM devices
        WHERE id = $1 AND account_id = $2 AND revoked_at IS NULL
        "#,
    )
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
