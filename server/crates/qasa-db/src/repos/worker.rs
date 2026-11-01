use chrono::{DateTime, Utc};
use sqlx::PgPool;
use uuid::Uuid;

pub async fn expire_subscriptions(pool: &PgPool) -> anyhow::Result<u64> {
    let mut tx = pool.begin().await?;

    let expired_subs = sqlx::query(
        r#"
        UPDATE subscriptions
        SET status = 'expired'
        WHERE status IN ('active', 'cancelled') AND expires_at < now()
        "#,
    )
    .execute(&mut *tx)
    .await?
    .rows_affected();

    // `expired` marks a lapsed account for the admin dashboard; its entitlements are
    // Free (see `qasa_core::effective_tier`), so the user keeps their primary device.
    let expired_accounts = sqlx::query(
        r#"
        UPDATE accounts a
        SET tier = 'expired', updated_at = now()
        WHERE tier = 'premium'
          AND NOT EXISTS (
            SELECT 1 FROM subscriptions s
            WHERE s.account_id = a.id
              AND s.status IN ('active', 'cancelled')
              AND s.expires_at > now()
          )
        "#,
    )
    .execute(&mut *tx)
    .await?
    .rows_affected();

    // Tear down VPN for anyone without a live subscription.
    let killed_vpn = sqlx::query(
        r#"
        UPDATE vpn_sessions vs
        SET disconnected_at = now()
        WHERE vs.disconnected_at IS NULL
          AND NOT EXISTS (
            SELECT 1 FROM subscriptions s
            WHERE s.account_id = vs.account_id
              AND s.status IN ('active', 'cancelled')
              AND s.expires_at > now()
          )
        "#,
    )
    .execute(&mut *tx)
    .await?
    .rows_affected();

    tx.commit().await?;
    Ok(expired_subs + expired_accounts + killed_vpn)
}

/// Close checkouts nobody finished: `pending` payments older than `older_than_minutes`
/// become `expired`. They used to stay pending forever.
///
/// This only tidies state. A gateway confirmation that still arrives later is honoured
/// (`settle_payment` settles any order that is not already paid), because the money did
/// move.
pub async fn expire_abandoned_payments(pool: &PgPool, older_than_minutes: i32) -> anyhow::Result<u64> {
    let result = sqlx::query(
        r#"
        UPDATE payments
        SET status = 'expired', completed_at = now()
        WHERE status = 'pending'
          AND created_at < now() - make_interval(mins => $1)
        "#,
    )
    .bind(older_than_minutes)
    .execute(pool)
    .await?;
    Ok(result.rows_affected())
}

/// Logged-out admin tokens only need remembering until they would have expired anyway.
pub async fn purge_expired_admin_token_revocations(pool: &PgPool) -> anyhow::Result<u64> {
    let result = sqlx::query("DELETE FROM admin_token_revocations WHERE expires_at < now()")
        .execute(pool)
        .await?;
    Ok(result.rows_affected())
}

pub async fn purge_old_license_revocations(pool: &PgPool, days: i32) -> anyhow::Result<u64> {
    let result = sqlx::query(
        r#"
        DELETE FROM license_revocations
        WHERE revoked_at < now() - make_interval(days => $1)
        "#,
    )
    .bind(days)
    .execute(pool)
    .await?;
    Ok(result.rows_affected())
}

/// End sessions the node no longer sees: no WireGuard handshake for
/// `handshake_minutes`, or none at all `grace_minutes` after connecting (the device took
/// the config but never brought the tunnel up). WireGuard re-handshakes about every two
/// minutes while a tunnel is up, so a stale handshake means it is gone.
///
/// This replaces a check on `connected_at` alone, which ended every session five
/// minutes after it started, however much it was in use.
pub async fn disconnect_stale_vpn_sessions(
    pool: &PgPool,
    handshake_minutes: i32,
    grace_minutes: i32,
) -> anyhow::Result<u64> {
    let mut tx = pool.begin().await?;
    let nodes: Vec<uuid::Uuid> = sqlx::query_scalar(
        r#"
        UPDATE vpn_sessions SET disconnected_at = now()
        WHERE disconnected_at IS NULL
          AND (
            last_handshake_at < now() - make_interval(mins => $1)
            OR (last_handshake_at IS NULL AND connected_at < now() - make_interval(mins => $2))
          )
        RETURNING node_id
        "#,
    )
    .bind(handshake_minutes)
    .bind(grace_minutes)
    .fetch_all(&mut *tx)
    .await?;
    if !nodes.is_empty() {
        sqlx::query(
            r#"
            UPDATE vpn_nodes n SET active_peers = (
                SELECT COUNT(*) FROM vpn_sessions s
                WHERE s.node_id = n.id AND s.disconnected_at IS NULL
            )
            WHERE n.id = ANY($1)
            "#,
        )
        .bind(&nodes)
        .execute(&mut *tx)
        .await?;
    }
    tx.commit().await?;
    Ok(nodes.len() as u64)
}

/// Nodes whose agent has not reported for `stale_secs` stop taking peers. A heartbeat
/// brings them back; drained and retired nodes keep their status.
pub async fn mark_offline_nodes(pool: &PgPool, stale_secs: i32) -> anyhow::Result<u64> {
    let n = sqlx::query(
        r#"
        UPDATE vpn_nodes SET status = 'offline'
        WHERE status IN ('online', 'warn')
          AND (last_heartbeat IS NULL OR last_heartbeat < now() - make_interval(secs => $1))
        "#,
    )
    .bind(stale_secs as f64)
    .execute(pool)
    .await?
    .rows_affected();
    Ok(n)
}
pub async fn pending_webhook_retries(pool: &PgPool, max_retries: i32) -> anyhow::Result<Vec<WebhookRetryRow>> {
    let rows = sqlx::query_as::<_, WebhookRetryRow>(
        r#"
        SELECT id, gateway, gateway_order_id, payload, retry_count,
               event_status, reported_amount_iqd
        FROM webhook_events
        WHERE processed = false
          AND signature_valid = true
          AND retry_count < $1
          AND received_at < now() - interval '1 minute'
        ORDER BY received_at ASC
        LIMIT 50
        "#,
    )
    .bind(max_retries)
    .fetch_all(pool)
    .await?;
    Ok(rows)
}

pub async fn increment_webhook_retry(pool: &PgPool, id: uuid::Uuid, error: &str) -> anyhow::Result<()> {
    sqlx::query(
        r#"
        UPDATE webhook_events
        SET retry_count = retry_count + 1, error_message = $2
        WHERE id = $1
        "#,
    )
    .bind(id)
    .bind(error)
    .execute(pool)
    .await?;
    Ok(())
}

#[derive(Debug, sqlx::FromRow)]
pub struct WebhookRetryRow {
    pub id: uuid::Uuid,
    pub gateway: String,
    pub gateway_order_id: Option<String>,
    pub payload: serde_json::Value,
    pub retry_count: i32,
    pub event_status: Option<String>,
    pub reported_amount_iqd: Option<i32>,
}

pub async fn telemetry_snapshot(pool: &PgPool) -> anyhow::Result<TelemetrySnapshot> {
    let active_sessions: i64 = sqlx::query_scalar(
        r#"
        SELECT COUNT(*)::bigint FROM vpn_sessions WHERE disconnected_at IS NULL
        "#,
    )
    .fetch_one(pool)
    .await?;

    let accounts_total: i64 = sqlx::query_scalar("SELECT COUNT(*)::bigint FROM accounts WHERE deleted_at IS NULL")
        .fetch_one(pool)
        .await?;

    let devices_active: i64 = sqlx::query_scalar(
        r#"SELECT COUNT(*)::bigint FROM devices WHERE revoked_at IS NULL"#,
    )
    .fetch_one(pool)
    .await?;

    Ok(TelemetrySnapshot {
        active_sessions,
        accounts_total,
        devices_active,
    })
}

#[derive(Debug, Clone, serde::Serialize)]
pub struct TelemetrySnapshot {
    pub active_sessions: i64,
    pub accounts_total: i64,
    pub devices_active: i64,
}

#[derive(Debug, Clone, sqlx::FromRow)]
pub struct ExpiringSubRow {
    pub id: Uuid,
    pub account_id: Uuid,
    pub auto_renew: bool,
    pub expires_at: DateTime<Utc>,
}

/// Premium ending within 3 days, warned once. Auto-renew subscriptions are included only
/// when `include_auto_renew` (i.e. while renewals are not charged automatically).
pub async fn subs_needing_expiry_warning(
    pool: &PgPool,
    include_auto_renew: bool,
) -> anyhow::Result<Vec<ExpiringSubRow>> {
    let rows = sqlx::query_as::<_, ExpiringSubRow>(
        r#"
        SELECT id, account_id, auto_renew, expires_at
        FROM subscriptions
        WHERE status IN ('active', 'cancelled')
          AND (auto_renew = false OR $1)
          AND expiry_warned_at IS NULL
          AND expires_at > now()
          AND expires_at <= now() + interval '3 days'
        ORDER BY expires_at ASC
        LIMIT 200
        "#,
    )
    .bind(include_auto_renew)
    .fetch_all(pool)
    .await?;
    Ok(rows)
}

pub async fn mark_expiry_warned(pool: &PgPool, subscription_id: Uuid) -> anyhow::Result<()> {
    sqlx::query(
        r#"
        UPDATE subscriptions SET expiry_warned_at = now() WHERE id = $1
        "#,
    )
    .bind(subscription_id)
    .execute(pool)
    .await?;
    Ok(())
}

/// Auto-renew on, expires within 24h, no successful payment in last 2 days — renew likely failed.
pub async fn subs_needing_renew_failed_notice(pool: &PgPool) -> anyhow::Result<Vec<ExpiringSubRow>> {
    let rows = sqlx::query_as::<_, ExpiringSubRow>(
        r#"
        SELECT s.id, s.account_id, s.auto_renew, s.expires_at
        FROM subscriptions s
        WHERE s.status = 'active'
          AND s.auto_renew = true
          AND s.renew_failed_notified_at IS NULL
          AND s.expires_at > now()
          AND s.expires_at <= now() + interval '1 day'
          AND NOT EXISTS (
            SELECT 1 FROM payments p
            WHERE p.account_id = s.account_id
              AND p.status = 'success'
              AND p.completed_at > now() - interval '2 days'
          )
        ORDER BY s.expires_at ASC
        LIMIT 200
        "#,
    )
    .fetch_all(pool)
    .await?;
    Ok(rows)
}

pub async fn mark_renew_failed_notified(pool: &PgPool, subscription_id: Uuid) -> anyhow::Result<()> {
    sqlx::query(
        r#"
        UPDATE subscriptions SET renew_failed_notified_at = now() WHERE id = $1
        "#,
    )
    .bind(subscription_id)
    .execute(pool)
    .await?;
    Ok(())
}
