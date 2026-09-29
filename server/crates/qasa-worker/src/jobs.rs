use std::sync::Arc;

use chrono::Utc;
use qasa_db::DbPool;
use qasa_db::repos::push_copy::{
    BREACH_ALERT_PREMIUM, SUB_EXPIRING_3D, SUB_RENEW_FAILED,
};
use qasa_payments::GatewayKind;
use qasa_push::PushService;

pub async fn expire_subscriptions(pool: DbPool, _push: Arc<PushService>) -> anyhow::Result<()> {
    let n = qasa_db::repos::worker::expire_subscriptions(&pool).await?;
    if n > 0 {
        tracing::info!(count = n, "expired subscriptions/accounts");
    }
    Ok(())
}

pub async fn refresh_views(pool: DbPool, _push: Arc<PushService>) -> anyhow::Result<()> {
    // Propagate: a failed refresh must show up as a failed job, not a silent no-op.
    qasa_db::repos::worker::refresh_materialized_views(&pool).await?;
    tracing::debug!("refreshed materialized views");
    Ok(())
}

pub async fn stale_vpn_sessions(pool: DbPool, _push: Arc<PushService>) -> anyhow::Result<()> {
    let n = qasa_db::repos::worker::disconnect_stale_vpn_sessions(&pool, 5).await?;
    if n > 0 {
        tracing::info!(count = n, "disconnected stale VPN sessions");
    }
    // The decrement above is best-effort; recompute from vpn_sessions so a missed
    // decrement cannot leave a node permanently over-counted and starved of peers.
    let fixed = qasa_db::repos::vpn::reconcile_active_peers(&pool).await?;
    if fixed > 0 {
        tracing::info!(nodes = fixed, "reconciled VPN active_peers drift");
    }
    Ok(())
}

pub async fn purge_revocations(pool: DbPool, _push: Arc<PushService>) -> anyhow::Result<()> {
    let n = qasa_db::repos::worker::purge_old_license_revocations(&pool, 31).await?;
    if n > 0 {
        tracing::info!(count = n, "purged old license revocations");
    }
    Ok(())
}

/// Scan monitored email hashes against breach dumps → multilingual device push.
/// Only accounts with a live Premium subscription receive alerts.
pub async fn breach_alert_scan(pool: DbPool, push: Arc<PushService>) -> anyhow::Result<()> {
    let matches = qasa_db::repos::monitored_email::find_unalerted_matches(&pool, 100).await?;
    let mut sent = 0u64;
    for m in matches {
        let day = Utc::now().format("%Y-%m-%d");
        let prefix = &m.email_hash[..12.min(m.email_hash.len())];
        let dedupe = format!("breach:{}:{}:{}", m.account_id, prefix, day);
        if push
            .notify_account(&pool, m.account_id, &BREACH_ALERT_PREMIUM, &dedupe)
            .await?
            .is_some()
        {
            sent += 1;
        }
        qasa_db::repos::monitored_email::mark_alerted(&pool, m.monitor_id).await?;
    }
    if sent > 0 {
        tracing::info!(sent, "breach alert notifications created");
    }
    Ok(())
}

/// Auto-renew OFF, expires within 3 days → device push.
pub async fn subscription_expiry_warnings(
    pool: DbPool,
    push: Arc<PushService>,
) -> anyhow::Result<()> {
    let rows = qasa_db::repos::worker::subs_needing_expiry_warning(&pool).await?;
    let mut sent = 0u64;
    for s in rows {
        let dedupe = format!("sub_expiring_3d:{}", s.id);
        if push
            .notify_account(&pool, s.account_id, &SUB_EXPIRING_3D, &dedupe)
            .await?
            .is_some()
        {
            sent += 1;
        }
        qasa_db::repos::worker::mark_expiry_warned(&pool, s.id).await?;
    }
    if sent > 0 {
        tracing::info!(sent, "subscription expiry warnings sent");
    }
    Ok(())
}

/// Auto-renew ON but no recent successful payment near expiry → device push.
pub async fn subscription_renew_failed_notices(
    pool: DbPool,
    push: Arc<PushService>,
) -> anyhow::Result<()> {
    let rows = qasa_db::repos::worker::subs_needing_renew_failed_notice(&pool).await?;
    let mut sent = 0u64;
    for s in rows {
        let dedupe = format!("sub_renew_failed:{}", s.id);
        if push
            .notify_account(&pool, s.account_id, &SUB_RENEW_FAILED, &dedupe)
            .await?
            .is_some()
        {
            sent += 1;
        }
        qasa_db::repos::worker::mark_renew_failed_notified(&pool, s.id).await?;
    }
    if sent > 0 {
        tracing::info!(sent, "auto-renew failure notices sent");
    }
    Ok(())
}

pub async fn retry_webhooks(pool: DbPool, push: Arc<PushService>) -> anyhow::Result<()> {
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
                if let Some(sub) =
                    qasa_db::repos::subscription::get_current(&pool, payment.account_id).await?
                {
                    if sub.auto_renew {
                        let dedupe = format!("sub_renew_failed_pay:{}", payment.id);
                        let _ = push
                            .notify_account(
                                &pool,
                                payment.account_id,
                                &SUB_RENEW_FAILED,
                                &dedupe,
                            )
                            .await;
                    }
                }
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
