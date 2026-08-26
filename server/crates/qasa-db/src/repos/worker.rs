use sqlx::PgPool;

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

    tx.commit().await?;
    Ok(expired_subs + expired_accounts)
}

pub async fn refresh_materialized_views(pool: &PgPool) -> anyhow::Result<()> {
    sqlx::query("REFRESH MATERIALIZED VIEW CONCURRENTLY mv_revenue_daily")
        .execute(pool)
        .await
        .ok(); // may fail if no unique index on first run — non-concurrent fallback
    sqlx::query("REFRESH MATERIALIZED VIEW mv_revenue_daily")
        .execute(pool)
        .await
        .ok();

    sqlx::query("REFRESH MATERIALIZED VIEW CONCURRENTLY mv_active_subscribers")
        .execute(pool)
        .await
        .ok();
    sqlx::query("REFRESH MATERIALIZED VIEW mv_active_subscribers")
        .execute(pool)
        .await
        .ok();

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
