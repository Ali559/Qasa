use qasa_db::DbPool;
use qasa_payments::GatewayKind;

pub async fn expire_subscriptions(pool: DbPool) -> anyhow::Result<()> {
    let n = qasa_db::repos::worker::expire_subscriptions(&pool).await?;
    if n > 0 {
        tracing::info!(count = n, "expired subscriptions/accounts");
    }
    Ok(())
}

pub async fn refresh_views(pool: DbPool) -> anyhow::Result<()> {
    qasa_db::repos::worker::refresh_materialized_views(&pool).await?;
    tracing::debug!("refreshed materialized views");
    Ok(())
}

pub async fn stale_vpn_sessions(pool: DbPool) -> anyhow::Result<()> {
    let n = qasa_db::repos::worker::disconnect_stale_vpn_sessions(&pool, 5).await?;
    if n > 0 {
        tracing::info!(count = n, "disconnected stale VPN sessions");
    }
    Ok(())
}

pub async fn purge_revocations(pool: DbPool) -> anyhow::Result<()> {
    let n = qasa_db::repos::worker::purge_old_license_revocations(&pool, 31).await?;
    if n > 0 {
        tracing::info!(count = n, "purged old license revocations");
    }
    Ok(())
}

pub async fn retry_webhooks(pool: DbPool) -> anyhow::Result<()> {
    let pending = qasa_db::repos::worker::pending_webhook_retries(&pool, 5).await?;
    for event in pending {
        let Some(order_id) = event.gateway_order_id.as_deref() else {
            qasa_db::repos::worker::increment_webhook_retry(
                &pool,
                event.id,
                "missing gateway_order_id",
            )
            .await?;
            continue;
        };

        let Some(kind) = GatewayKind::parse(&event.gateway) else {
            qasa_db::repos::worker::increment_webhook_retry(&pool, event.id, "unknown gateway")
                .await?;
            continue;
        };

        let status = event
            .payload
            .get("status")
            .and_then(|v| v.as_str())
            .unwrap_or("pending");

        let payment = qasa_db::repos::payment::find_by_gateway_order_id(&pool, order_id).await?;
        let Some(payment) = payment else {
            qasa_db::repos::worker::increment_webhook_retry(&pool, event.id, "payment not found")
                .await?;
            continue;
        };

        if payment.status == "success" {
            qasa_db::repos::worker::mark_webhook_processed(&pool, event.id).await?;
            continue;
        }

        match status {
            "success" => {
                let sub = qasa_db::repos::subscription::activate_subscription(
                    &pool,
                    payment.account_id,
                    payment.amount_iqd,
                    &payment.plan_id,
                    payment.auto_renew,
                )
                .await?;
                qasa_db::repos::payment::mark_status(&pool, payment.id, "success", Some(sub.id))
                    .await?;
                qasa_db::repos::worker::mark_webhook_processed(&pool, event.id).await?;
                tracing::info!(order_id, gateway = kind.as_str(), "webhook retry succeeded");
            }
            "failed" => {
                qasa_db::repos::payment::mark_status(&pool, payment.id, "failed", None).await?;
                qasa_db::repos::worker::mark_webhook_processed(&pool, event.id).await?;
            }
            _ => {
                qasa_db::repos::worker::increment_webhook_retry(&pool, event.id, "still pending")
                    .await?;
            }
        }
    }
    Ok(())
}
