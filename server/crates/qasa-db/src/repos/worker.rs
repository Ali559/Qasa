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

pub async fn refresh_materialized_views(pool: &PgPool) -> anyhow::Result<()> {
    // Each view is retried CONCURRENTLY first and falls back to a blocking REFRESH.
    // Previously every call ended in `.ok()`, so a broken view, a missing unique index,
    // or a revoked permission all failed silently and the dashboard just showed stale
    // numbers with nothing in the logs. Report the real error instead.
    for view in ["mv_revenue_daily", "mv_active_subscribers"] {
        refresh_one(pool, view).await?;
    }
    Ok(())
}

async fn refresh_one(pool: &PgPool, view: &str) -> anyhow::Result<()> {
    let concurrent = sqlx::query(&format!("REFRESH MATERIALIZED VIEW CONCURRENTLY {view}"))
        .execute(pool)
        .await;
    match concurrent {
        Ok(_) => return Ok(()),
        Err(err) => {
            // CONCURRENTLY needs a unique index; on the very first run it does not exist.
            let blocking = sqlx::query(&format!("REFRESH MATERIALIZED VIEW {view}"))
                .execute(pool)
                .await;
            if let Err(fallback_err) = blocking {
                anyhow::bail!("failed to refresh {view}: {fallback_err} (concurrent attempt: {err})");
            }
            tracing::debug!(view, error = %err, "refreshed without CONCURRENTLY");
        }
    }
    Ok(())
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

pub async fn disconnect_stale_vpn_sessions(pool: &PgPool, stale_minutes: i32) -> anyhow::Result<u64> {
    let stale = sqlx::query_as::<_, (uuid::Uuid, uuid::Uuid)>(
        r#"
        SELECT id, node_id
        FROM vpn_sessions
        WHERE disconnected_at IS NULL
          AND connected_at < now() - make_interval(mins => $1)
        "#,
    )
    .bind(stale_minutes)
    .fetch_all(pool)
    .await?;

    let mut disconnected = 0u64;
    for (session_id, node_id) in stale {
        let updated = sqlx::query(
            r#"
            UPDATE vpn_sessions SET disconnected_at = now()
            WHERE id = $1 AND disconnected_at IS NULL
            "#,
        )
        .bind(session_id)
        .execute(pool)
        .await?;

        if updated.rows_affected() > 0 {
            sqlx::query(
                r#"
                UPDATE vpn_nodes
                SET active_peers = GREATEST(active_peers - 1, 0)
                WHERE id = $1
                "#,
            )
            .bind(node_id)
            .execute(pool)
            .await?;
            disconnected += 1;
        }
    }
    Ok(disconnected)
}

pub async fn pending_webhook_retries(pool: &PgPool, max_retries: i32) -> anyhow::Result<Vec<WebhookRetryRow>> {
    let rows = sqlx::query_as::<_, WebhookRetryRow>(
        r#"
        SELECT id, gateway, gateway_order_id, payload, retry_count
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

pub async fn mark_webhook_processed(pool: &PgPool, id: uuid::Uuid) -> anyhow::Result<()> {
    sqlx::query("UPDATE webhook_events SET processed = true WHERE id = $1")
        .bind(id)
        .execute(pool)
        .await?;
    Ok(())
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
}

pub async fn telemetry_snapshot(pool: &PgPool) -> anyhow::Result<TelemetrySnapshot> {
    let active_sessions: i64 = sqlx::query_scalar(
        r#"
        SELECT COUNT(*)::bigint FROM vpn_sessions WHERE disconnected_at IS NULL
        "#,
    )
    .fetch_one(pool)
    .await?;

    let accounts_total: i64 = sqlx::query_scalar("SELECT COUNT(*)::bigint FROM accounts")
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

/// Premium ending in 2–3 days with auto_renew off (once).
pub async fn subs_needing_expiry_warning(pool: &PgPool) -> anyhow::Result<Vec<ExpiringSubRow>> {
    let rows = sqlx::query_as::<_, ExpiringSubRow>(
        r#"
        SELECT id, account_id, auto_renew, expires_at
        FROM subscriptions
        WHERE status IN ('active', 'cancelled')
          AND auto_renew = false
          AND expiry_warned_at IS NULL
          AND expires_at > now()
          AND expires_at <= now() + interval '3 days'
        ORDER BY expires_at ASC
        LIMIT 200
        "#,
    )
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
