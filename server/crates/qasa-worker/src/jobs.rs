use std::sync::Arc;

use chrono::Utc;
use qasa_db::DbPool;
use qasa_db::repos::push_copy::{
    BREACH_ALERT_PREMIUM, SUB_EXPIRING_3D, SUB_RENEW_FAILED,
};
use qasa_db::repos::settlement::{settle_payment, SettleOutcome, SettleStatus};
use qasa_payments::GatewayKind;
use qasa_push::PushService;

pub async fn expire_subscriptions(pool: DbPool, _push: Arc<PushService>) -> anyhow::Result<()> {
    let n = qasa_db::repos::worker::expire_subscriptions(&pool).await?;
    if n > 0 {
        tracing::info!(count = n, "expired subscriptions/accounts");
    }
    Ok(())
}

/// Drop rate-limit windows that ended over a day ago; every limit's window is shorter.
pub async fn purge_rate_limits(pool: DbPool, _push: Arc<PushService>) -> anyhow::Result<()> {
    let n = qasa_db::repos::rate_limit::purge_stale(&pool, std::time::Duration::from_secs(86_400))
        .await?;
    if n > 0 {
        tracing::debug!(count = n, "purged stale rate-limit windows");
    }
    Ok(())
}

/// WireGuard re-handshakes about every 2 minutes while a tunnel is up.
const STALE_HANDSHAKE_MINUTES: i32 = 5;
/// How long a device has to bring the tunnel up after taking a config.
const NO_HANDSHAKE_GRACE_MINUTES: i32 = 10;
/// The agent reports every 30 seconds; four missed reports and the node takes no peers.
const NODE_OFFLINE_AFTER_SECS: i32 = 120;

pub async fn offline_vpn_nodes(pool: DbPool, _push: Arc<PushService>) -> anyhow::Result<()> {
    let n = qasa_db::repos::worker::mark_offline_nodes(&pool, NODE_OFFLINE_AFTER_SECS).await?;
    if n > 0 {
        tracing::warn!(count = n, "VPN nodes marked offline (no agent heartbeat)");
    }
    Ok(())
}

pub async fn stale_vpn_sessions(pool: DbPool, _push: Arc<PushService>) -> anyhow::Result<()> {
    let n = qasa_db::repos::worker::disconnect_stale_vpn_sessions(
        &pool,
        STALE_HANDSHAKE_MINUTES,
        NO_HANDSHAKE_GRACE_MINUTES,
    )
    .await?;
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

/// How long a checkout may stay `pending` before it counts as abandoned. Generous on
/// purpose: wallet hand-offs can take a while, and a late confirmation still settles.
const ABANDONED_PAYMENT_MINUTES: i32 = 60;

pub async fn expire_abandoned_payments(pool: DbPool, _push: Arc<PushService>) -> anyhow::Result<()> {
    let n = qasa_db::repos::worker::expire_abandoned_payments(&pool, ABANDONED_PAYMENT_MINUTES)
        .await?;
    if n > 0 {
        tracing::info!(count = n, "expired abandoned payments");
    }
    Ok(())
}

pub async fn purge_revocations(pool: DbPool, _push: Arc<PushService>) -> anyhow::Result<()> {
    let n = qasa_db::repos::worker::purge_old_license_revocations(&pool, 31).await?;
    if n > 0 {
        tracing::info!(count = n, "purged old license revocations");
    }
    let n = qasa_db::repos::worker::purge_expired_admin_token_revocations(&pool).await?;
    if n > 0 {
        tracing::info!(count = n, "purged expired admin token revocations");
    }
    Ok(())
}

/// Scan monitored email hashes against breach dumps → multilingual device push.
/// Only accounts with a live Premium subscription receive alerts.
/// Not scheduled while email leak alerts are parked (see `main.rs`).
#[allow(dead_code)]
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

/// Broadcasts older than this are not pushed; the inbox still has them.
const BROADCAST_PUSH_MAX_AGE_HOURS: i32 = 24;
/// Receipts per run. At one run a minute this is ~30k pushes an hour.
const BROADCAST_PUSH_BATCH: i64 = 500;

/// Push admin broadcasts (and pricing notices) to the devices of every account in their
/// audience. The API only writes the inbox receipts; delivering here keeps a large
/// audience from holding up the admin's request. Each receipt is pushed once.
pub async fn broadcast_pushes(pool: DbPool, push: Arc<PushService>) -> anyhow::Result<()> {
    use std::borrow::Cow;

    let pending = qasa_db::repos::notification::pending_broadcast_pushes(
        &pool,
        BROADCAST_PUSH_MAX_AGE_HOURS,
        BROADCAST_PUSH_BATCH,
    )
    .await?;
    let mut sent = 0u32;
    for p in &pending {
        // A broadcast may lack a translation; fall back to English rather than send an
        // empty notification.
        let or_en = |v: &Option<String>, en: &str| -> Cow<'static, str> {
            Cow::Owned(
                v.clone()
                    .filter(|s| !s.trim().is_empty())
                    .unwrap_or_else(|| en.to_string()),
            )
        };
        let notice = qasa_db::repos::push_copy::LocalizedNotice {
            kind: Cow::Owned(p.kind.clone()),
            title_en: Cow::Owned(p.title_en.clone()),
            title_ckb: or_en(&p.title_ckb, &p.title_en),
            title_ar: or_en(&p.title_ar, &p.title_en),
            body_en: Cow::Owned(p.body_en.clone()),
            body_ckb: or_en(&p.body_ckb, &p.body_en),
            body_ar: or_en(&p.body_ar, &p.body_en),
        };
        match push.deliver_account(&pool, p.account_id, &notice, p.notification_id).await {
            Ok(n) => sent += n,
            Err(err) => {
                tracing::warn!(error = %err, account_id = %p.account_id, "broadcast push failed")
            }
        }
        // Marked even when it failed or the account has no device that takes pushes:
        // retrying every minute would only repeat the failure, and the inbox has it.
        qasa_db::repos::notification::mark_pushed(&pool, p.notification_id, p.account_id).await?;
    }
    if !pending.is_empty() {
        tracing::info!(receipts = pending.len(), devices = sent, "broadcast pushes delivered");
    }
    Ok(())
}

/// Expires within 3 days and will not renew by itself → device push.
pub async fn subscription_expiry_warnings(
    pool: DbPool,
    push: Arc<PushService>,
) -> anyhow::Result<()> {
    // Without recurring charges an auto-renew subscription lapses like any other, so its
    // owner needs the reminder too.
    let include_auto_renew = !qasa_payments::RENEWALS_ARE_CHARGED;
    let rows =
        qasa_db::repos::worker::subs_needing_expiry_warning(&pool, include_auto_renew).await?;
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

/// Re-attempt settlement for verified webhooks that have not been handled yet.
///
/// Goes through the same `settle_payment` as the API, so the amount check and the
/// row lock apply here too. The previous copy of this logic skipped the amount check.
pub async fn retry_webhooks(pool: DbPool, push: Arc<PushService>) -> anyhow::Result<()> {
    let pending = qasa_db::repos::worker::pending_webhook_retries(&pool, 5).await?;
    for event in pending {
        let Some(order_id) = event.gateway_order_id.as_deref() else {
            qasa_db::repos::payment::finish_webhook_event(
                &pool,
                event.id,
                Some("missing gateway_order_id"),
            )
            .await?;
            continue;
        };
        if GatewayKind::parse(&event.gateway).is_none() {
            qasa_db::repos::payment::finish_webhook_event(&pool, event.id, Some("unknown gateway"))
                .await?;
            continue;
        }

        // Rows written before `event_status` existed carry the outcome only in the raw
        // payload, in the dev stub's shape. Anything unreadable is treated as pending.
        let status = event
            .event_status
            .as_deref()
            .or_else(|| event.payload.get("status").and_then(|v| v.as_str()))
            .and_then(SettleStatus::parse)
            .unwrap_or(SettleStatus::Pending);
        let reported_amount = event.reported_amount_iqd.or_else(|| {
            event
                .payload
                .get("amount_iqd")
                .and_then(|v| v.as_i64())
                .map(|v| i32::try_from(v).unwrap_or(-1))
        });

        let outcome =
            settle_payment(&pool, &event.gateway, order_id, status, reported_amount).await?;

        if !outcome.is_terminal(status) {
            qasa_db::repos::worker::increment_webhook_retry(&pool, event.id, "still pending")
                .await?;
            continue;
        }
        qasa_db::repos::payment::finish_webhook_event(
            &pool,
            event.id,
            outcome.error_message().as_deref(),
        )
        .await?;

        match outcome {
            SettleOutcome::Settled { .. } => {
                tracing::info!(order_id, gateway = %event.gateway, "webhook retry succeeded");
            }
            SettleOutcome::Failed { payment } if qasa_payments::RENEWALS_ARE_CHARGED => {
                if let Some(sub) =
                    qasa_db::repos::subscription::get_current(&pool, payment.account_id).await?
                {
                    if sub.auto_renew {
                        let dedupe = format!("sub_renew_failed_pay:{}", payment.id);
                        let _ = push
                            .notify_account(&pool, payment.account_id, &SUB_RENEW_FAILED, &dedupe)
                            .await;
                    }
                }
            }
            SettleOutcome::AccountDeleted { payment } => {
                tracing::warn!(payment_id = %payment.id, "webhook retry: account deleted; refund required");
            }
            SettleOutcome::AmountMismatch { reported, expected } => {
                tracing::warn!(order_id, reported, expected, "webhook retry: amount mismatch");
            }
            SettleOutcome::Failed { .. } | SettleOutcome::NotFound | SettleOutcome::Unchanged => {}
        }
    }
    Ok(())
}
