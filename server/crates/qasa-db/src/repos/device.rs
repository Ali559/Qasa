use qasa_core::new_id;
use sqlx::PgPool;
use uuid::Uuid;

use crate::models::DeviceRow;

const DEVICE_COLS: &str = r#"
        id, account_id, fingerprint, device_name, platform, location,
        license_jti, last_seen_at, revoked_at, created_at
"#;

/// The device row for this fingerprint, **including an unlinked one**.
///
/// `(account_id, fingerprint)` is unique, so an unlinked device that registers again has
/// to get its old row back (see [`reactivate_device`]). Filtering revoked rows out here
/// made the caller insert a duplicate and fail with a 500, so an unlinked phone could
/// never rejoin its account.
pub async fn find_by_fingerprint(
    pool: &PgPool,
    account_id: Uuid,
    fingerprint: &str,
) -> anyhow::Result<Option<DeviceRow>> {
    let row = sqlx::query_as::<_, DeviceRow>(&format!(
        r#"
        SELECT {DEVICE_COLS}
        FROM devices
        WHERE account_id = $1 AND fingerprint = $2
        "#
    ))
    .bind(account_id)
    .bind(fingerprint)
    .fetch_optional(pool)
    .await?;
    Ok(row)
}

/// Re-link a previously unlinked device. It counts as newly linked, so `created_at`
/// moves to now and it cannot take over as primary from devices that stayed linked.
/// Tokens issued before the unlink stay invalid through `tokens_valid_after`.
pub async fn reactivate_device(
    pool: &PgPool,
    device_id: Uuid,
    device_name: &str,
    platform: &str,
    location: Option<&str>,
) -> anyhow::Result<DeviceRow> {
    let row = sqlx::query_as::<_, DeviceRow>(&format!(
        r#"
        UPDATE devices
        SET revoked_at = NULL,
            device_name = $2,
            platform = $3,
            location = COALESCE($4, location),
            created_at = now(),
            last_seen_at = now()
        WHERE id = $1
        RETURNING {DEVICE_COLS}
        "#
    ))
    .bind(device_id)
    .bind(device_name)
    .bind(platform)
    .bind(location)
    .fetch_one(pool)
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
        ORDER BY created_at ASC, id ASC
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

/// Unlink a device. Every license token issued to it so far stops working at once
/// (`tokens_valid_after`); before, only `revoked_at` was set, which `LicenseAuth` never
/// looked at, so an unlinked device kept using the API until its 30-day token expired.
///
/// Unlinking also stops pushes to it and ends its VPN session (see [`revoke_where`]).
pub async fn revoke_device(pool: &PgPool, account_id: Uuid, device_id: Uuid) -> anyhow::Result<bool> {
    let revoked = revoke_where(
        pool,
        account_id,
        "id = $2",
        Some(device_id),
    )
    .await?;
    Ok(revoked > 0)
}

/// Revoke every active device except the oldest (primary / first linked).
/// Returns the count of devices revoked.
pub async fn revoke_all_except_primary(pool: &PgPool, account_id: Uuid) -> anyhow::Result<u64> {
    revoke_where(
        pool,
        account_id,
        // `$2` is unused here but must still appear, or Postgres cannot type it.
        r#"$2::uuid IS NULL AND id <> (
            SELECT id FROM devices
            WHERE account_id = $1 AND revoked_at IS NULL
            ORDER BY created_at ASC, id ASC
            LIMIT 1
        )"#,
        None,
    )
    .await
}

/// Unlink the account's linked devices matching `filter` (SQL over `devices`, `$1` is
/// the account id and `$2` the optional device id), in one transaction:
///
/// - the device is marked revoked and its tokens invalidated;
/// - its push token is dropped and notifications switched off, so an unlinked phone
///   stops receiving alerts for an account it no longer belongs to;
/// - its VPN session is disconnected and the node's peer count corrected.
async fn revoke_where(
    pool: &PgPool,
    account_id: Uuid,
    filter: &str,
    device_id: Option<Uuid>,
) -> anyhow::Result<u64> {
    let mut tx = pool.begin().await?;

    let revoked: Vec<Uuid> = sqlx::query_scalar(&format!(
        r#"
        UPDATE devices
        SET revoked_at = now(),
            tokens_valid_after = now(),
            push_token = NULL,
            notifications_enabled = false
        WHERE account_id = $1
          AND revoked_at IS NULL
          AND {filter}
        RETURNING id
        "#
    ))
    .bind(account_id)
    .bind(device_id)
    .fetch_all(&mut *tx)
    .await?;

    end_vpn_sessions(&mut tx, &revoked).await?;
    tx.commit().await?;
    Ok(revoked.len() as u64)
}

/// Disconnect any live VPN session of these devices and recount the affected nodes.
pub(crate) async fn end_vpn_sessions(
    conn: &mut sqlx::PgConnection,
    device_ids: &[Uuid],
) -> anyhow::Result<()> {
    if device_ids.is_empty() {
        return Ok(());
    }
    let nodes: Vec<Uuid> = sqlx::query_scalar(
        r#"
        UPDATE vpn_sessions SET disconnected_at = now()
        WHERE device_id = ANY($1) AND disconnected_at IS NULL
        RETURNING node_id
        "#,
    )
    .bind(device_ids)
    .fetch_all(&mut *conn)
    .await?;
    if !nodes.is_empty() {
        sqlx::query(
            r#"
            UPDATE vpn_nodes n
            SET active_peers = (
                SELECT COUNT(*) FROM vpn_sessions s
                WHERE s.node_id = n.id AND s.disconnected_at IS NULL
            )
            WHERE n.id = ANY($1)
            "#,
        )
        .bind(&nodes)
        .execute(&mut *conn)
        .await?;
    }
    Ok(())
}

/// Whether a verified license token may still be used: its device is linked, its
/// account is neither blocked nor deleted, it was issued after the device was last
/// unlinked, and its `jti` is not individually revoked.
///
/// `iat` has one-second resolution, so a token must be issued in a later second than the
/// unlink. Re-linking within the same second as an unlink is the only case that loses.
pub async fn is_license_live(
    pool: &PgPool,
    account_id: Uuid,
    device_id: Uuid,
    jti: &str,
    issued_at: u64,
) -> anyhow::Result<bool> {
    let live: bool = sqlx::query_scalar(
        r#"
        SELECT EXISTS(
            SELECT 1 FROM devices d
            JOIN accounts a ON a.id = d.account_id
            WHERE d.id = $1
              AND d.account_id = $2
              AND d.revoked_at IS NULL
              AND a.blocked_at IS NULL
              AND a.deleted_at IS NULL
              AND (
                d.tokens_valid_after IS NULL
                OR $3 > floor(extract(epoch FROM d.tokens_valid_after))::bigint
              )
        )
        AND NOT EXISTS(SELECT 1 FROM license_revocations WHERE jti = $4)
        "#,
    )
    .bind(device_id)
    .bind(account_id)
    .bind(i64::try_from(issued_at).unwrap_or(i64::MAX))
    .bind(jti)
    .fetch_one(pool)
    .await?;
    Ok(live)
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

/// Apply a partial preferences update: only the fields that are `Some` change. An empty
/// `push_token` clears it (the app turned pushes off or lost its token).
///
/// Returns the stored `(locale, notifications_enabled)` after the update, or `None` when
/// the device is not linked.
pub async fn update_prefs(
    pool: &PgPool,
    device_id: Uuid,
    locale: Option<&str>,
    push_token: Option<&str>,
    notifications_enabled: Option<bool>,
) -> anyhow::Result<Option<(String, bool)>> {
    let row = sqlx::query_as::<_, (String, bool)>(
        r#"
        UPDATE devices
        SET locale = COALESCE($2, locale),
            push_token = CASE
              WHEN $3::text IS NULL THEN push_token
              WHEN $3 = '' THEN NULL
              ELSE $3
            END,
            notifications_enabled = COALESCE($4, notifications_enabled),
            last_seen_at = now()
        WHERE id = $1 AND revoked_at IS NULL
        RETURNING locale, notifications_enabled
        "#,
    )
    .bind(device_id)
    .bind(locale)
    .bind(push_token)
    .bind(notifications_enabled)
    .fetch_optional(pool)
    .await?;
    Ok(row)
}

/// Whether `device_id` is among the first `limit` linked devices of the account, oldest
/// first. The oldest is the primary.
///
/// An account can hold more linked devices than its tier allows: Premium links up to 5,
/// and when the subscription lapses the limit drops to 1. Nothing is unlinked
/// automatically; instead only the devices within the limit keep getting licenses, so
/// the primary keeps working and the others are asked to free a slot or upgrade.
pub async fn is_within_limit(
    pool: &PgPool,
    account_id: Uuid,
    device_id: Uuid,
    limit: u32,
) -> anyhow::Result<bool> {
    let rank: Option<i64> = sqlx::query_scalar(
        r#"
        SELECT rank FROM (
            SELECT id, row_number() OVER (ORDER BY created_at ASC, id ASC) AS rank
            FROM devices
            WHERE account_id = $1 AND revoked_at IS NULL
        ) ranked
        WHERE id = $2
        "#,
    )
    .bind(account_id)
    .bind(device_id)
    .fetch_optional(pool)
    .await?;
    Ok(rank.is_some_and(|r| r <= i64::from(limit)))
}
