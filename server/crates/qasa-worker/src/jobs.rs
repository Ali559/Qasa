use std::sync::Arc;

use chrono::Utc;
use qasa_db::DbPool;
use qasa_db::repos::push_copy::{
    BREACH_ALERT_PREMIUM, SUB_EXPIRING_3D, SUB_RENEWAL_LINK, SUB_RENEW_FAILED,
};
use qasa_db::repos::settlement::{
    settle_payment, settle_payment_with_token, SettleOutcome, SettleStatus,
};
use qasa_payments::{
    GatewayKind, Gateways, IntentRequest, PaymentError, WebhookStatus, RENEWAL_LEAD_HOURS,
    RENEWAL_LINK_LEAD_HOURS, RENEWAL_MAX_ATTEMPTS, RENEWAL_RETRY_HOURS,
};
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

/// Expires within 3 days, will not renew by itself, and no renewal link went out →
/// device push.
///
/// Renewal links are sent first, in the same run: a subscription already inside both
/// windows (as every one ending soon is on the day links are switched on) then gets the
/// link and not the reminder as well.
pub async fn subscription_expiry_warnings(
    pool: DbPool,
    push: Arc<PushService>,
    gateways: Arc<Gateways>,
) -> anyhow::Result<()> {
    if let Err(err) = send_renewal_links(pool.clone(), push.clone(), gateways.clone()).await {
        tracing::error!(error = %err, "renewal links before expiry warnings failed");
    }
    let rows =
        qasa_db::repos::worker::subs_needing_expiry_warning(&pool, &gateways.recurring()).await?;
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

/// The worker tried to renew and could not → device push, once per subscription.
pub async fn subscription_renew_failed_notices(
    pool: DbPool,
    push: Arc<PushService>,
    gateways: Arc<Gateways>,
) -> anyhow::Result<()> {
    let rows = qasa_db::repos::worker::subs_needing_renew_failed_notice(
        &pool,
        &gateways.recurring(),
        RENEWAL_MAX_ATTEMPTS,
    )
    .await?;
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

/// Charge saved payment methods for auto-renew subscriptions that end soon.
///
/// Attempts start `RENEWAL_LEAD_HOURS` before the end and repeat every
/// `RENEWAL_RETRY_HOURS`, up to `RENEWAL_MAX_ATTEMPTS`, so every retry lands while the
/// customer still has Premium. A paid renewal goes through `settle_payment` like any
/// checkout, so it stacks onto the current end date. When the attempts run out,
/// `subscription_renew_failed_notices` tells the customer to pay by hand.
pub async fn charge_renewals(
    pool: DbPool,
    _push: Arc<PushService>,
    gateways: Arc<Gateways>,
) -> anyhow::Result<()> {
    let recurring = gateways.recurring();
    if recurring.is_empty() {
        return Ok(());
    }
    let due = qasa_db::repos::renewal::due(
        &pool,
        &recurring,
        RENEWAL_LEAD_HOURS,
        RENEWAL_MAX_ATTEMPTS,
        RENEWAL_RETRY_HOURS,
        50,
    )
    .await?;
    for renewal in &due {
        if let Err(err) = renew_one(&pool, &gateways, renewal).await {
            tracing::error!(
                error = %err,
                subscription_id = %renewal.subscription_id,
                "renewal attempt failed"
            );
        }
    }
    Ok(())
}

async fn renew_one(
    pool: &DbPool,
    gateways: &Gateways,
    r: &qasa_db::repos::renewal::DueRenewal,
) -> anyhow::Result<()> {
    let Some(gateway) = GatewayKind::parse(&r.gateway)
        .and_then(|k| gateways.get(k))
        .filter(|g| g.is_live())
    else {
        return Ok(());
    };
    let kind = gateway.kind();

    // Counted before charging, so a crash mid-charge cannot turn into a charge every run.
    let Some(attempt) =
        qasa_db::repos::renewal::claim_attempt(pool, r.subscription_id, r.renewal_attempts).await?
    else {
        return Ok(());
    };

    // The price at renewal time, not what was paid last period. A plan that can no longer
    // be bought is not renewed; the customer is told to choose one.
    let Some(plan) = qasa_db::repos::plan::get_plan(pool, &r.plan_id)
        .await?
        .filter(|p| p.active)
    else {
        qasa_db::repos::renewal::give_up(pool, r.subscription_id, RENEWAL_MAX_ATTEMPTS).await?;
        tracing::info!(subscription_id = %r.subscription_id, plan_id = %r.plan_id, "plan inactive; not renewing");
        return Ok(());
    };

    let Some(payment) = qasa_db::repos::payment::create_renewal_payment(
        pool,
        r.account_id,
        r.subscription_id,
        kind.as_str(),
        plan.amount_iqd,
        &plan.id,
        attempt,
    )
    .await?
    else {
        return Ok(());
    };

    let order_ref = payment.id.simple().to_string();
    match gateway.charge_saved(&r.token, plan.amount_iqd, &order_ref).await {
        Ok(charge) => {
            qasa_db::repos::payment::set_gateway_order_id(pool, payment.id, &charge.gateway_order_id)
                .await?;
            let status = match charge.status {
                WebhookStatus::Success => SettleStatus::Success,
                WebhookStatus::Failed => SettleStatus::Failed,
                // The outcome arrives by webhook, like a checkout.
                WebhookStatus::Pending => return Ok(()),
            };
            let outcome = settle_payment(
                pool,
                kind.as_str(),
                &charge.gateway_order_id,
                status,
                charge.amount_iqd,
            )
            .await?;
            match outcome {
                SettleOutcome::Settled { subscription, .. } => tracing::info!(
                    account_id = %r.account_id,
                    expires_at = %subscription.expires_at,
                    "subscription renewed"
                ),
                SettleOutcome::Failed { .. } => tracing::info!(
                    subscription_id = %r.subscription_id,
                    attempt,
                    "renewal declined"
                ),
                SettleOutcome::AmountMismatch { reported, expected } => tracing::warn!(
                    subscription_id = %r.subscription_id,
                    reported,
                    expected,
                    "renewal amount mismatch; left pending"
                ),
                other => tracing::warn!(?other, subscription_id = %r.subscription_id, "renewal not settled"),
            }
        }
        Err(PaymentError::TokenRejected) => {
            // The customer removed it at the gateway, or it expired. Retrying cannot work;
            // without a method the renew-failed notice goes out.
            qasa_db::repos::payment::fail_unplaced(pool, payment.id).await?;
            qasa_db::repos::payment_method::revoke(pool, r.method_id).await?;
            tracing::info!(subscription_id = %r.subscription_id, "saved method rejected; dropped");
        }
        Err(err) => {
            qasa_db::repos::payment::fail_unplaced(pool, payment.id).await?;
            tracing::warn!(error = %err, subscription_id = %r.subscription_id, attempt, "renewal charge not placed");
        }
    }
    Ok(())
}

/// Send a payment link for the next period to auto-renew subscriptions that no gateway
/// can charge (Wayl cannot), `RENEWAL_LINK_LEAD_HOURS` before they end. Runs at the start
/// of `subscription_expiry_warnings`.
///
/// The link is a renewal payment like a charged one: paying it goes through
/// `settle_payment`, so the time stacks onto the current end date. It stays open until
/// the period ends; after that the customer renews in the app. If the link cannot be
/// created, the next run tries again, and the 3-day reminder goes out in its place.
pub async fn send_renewal_links(
    pool: DbPool,
    push: Arc<PushService>,
    gateways: Arc<Gateways>,
) -> anyhow::Result<()> {
    let Some(gateway) = gateways.renewal_links() else {
        return Ok(());
    };
    let due = qasa_db::repos::renewal::due_links(
        &pool,
        &gateways.recurring(),
        RENEWAL_LINK_LEAD_HOURS,
        50,
    )
    .await?;
    let mut sent = 0u32;
    for r in &due {
        match link_one(&pool, &push, gateway, r).await {
            Ok(true) => sent += 1,
            Ok(false) => {}
            Err(err) => tracing::error!(
                error = %err,
                subscription_id = %r.subscription_id,
                "renewal link not sent"
            ),
        }
    }
    if sent > 0 {
        tracing::info!(sent, "renewal links sent");
    }
    Ok(())
}

async fn link_one(
    pool: &DbPool,
    push: &PushService,
    gateway: &dyn qasa_payments::PaymentGateway,
    r: &qasa_db::repos::renewal::DueLink,
) -> anyhow::Result<bool> {
    // A plan that can no longer be bought is not renewed; the 3-day reminder tells the
    // customer to choose one.
    let Some(plan) = qasa_db::repos::plan::get_plan(pool, &r.plan_id)
        .await?
        .filter(|p| p.active)
    else {
        return Ok(false);
    };
    if !qasa_db::repos::renewal::claim_link(pool, r.subscription_id).await? {
        return Ok(false);
    }

    let Some(payment) = qasa_db::repos::payment::create_renewal_link_payment(
        pool,
        r.account_id,
        r.subscription_id,
        gateway.kind().as_str(),
        plan.amount_iqd,
        &plan.id,
        r.expires_at,
    )
    .await?
    else {
        return Ok(false);
    };

    let order_ref = payment.id.simple().to_string();
    let minutes_left = (r.expires_at - Utc::now()).num_minutes().max(60);
    let label = format!("Qasa {}", plan.title);
    let intent = gateway
        .create_intent(&IntentRequest {
            amount_iqd: plan.amount_iqd,
            order_ref: &order_ref,
            label: &label,
            save_method: false,
            expires_in_minutes: u32::try_from(minutes_left).unwrap_or(u32::MAX),
        })
        .await;
    let intent = match intent {
        Ok(intent) => intent,
        Err(err) => {
            qasa_db::repos::payment::fail_unplaced(pool, payment.id).await?;
            qasa_db::repos::renewal::release_link(pool, r.subscription_id).await?;
            return Err(err.into());
        }
    };
    qasa_db::repos::payment::set_link(pool, payment.id, &intent.gateway_order_id, &intent.deep_link)
        .await?;

    let dedupe = format!("sub_renewal_link:{}", r.subscription_id);
    push.notify_account(pool, r.account_id, &SUB_RENEWAL_LINK, &dedupe).await?;
    Ok(true)
}

/// Minimum gap between two status checks of one payment.
const CHECK_EVERY_MINUTES: i32 = 10;

/// Ask gateways that can look an order up (Wayl) about payments still open, so a missed
/// webhook does not leave a paid customer without Premium. Settles exactly as a webhook
/// would.
pub async fn check_open_payments(
    pool: DbPool,
    _push: Arc<PushService>,
    gateways: Arc<Gateways>,
) -> anyhow::Result<()> {
    // Only gateways that really move money; the dev stubs have nothing to look up.
    let pollable: Vec<&str> = [GatewayKind::Wayl]
        .into_iter()
        .filter(|k| gateways.get(*k).is_some_and(|g| g.is_live()))
        .map(|k| k.as_str())
        .collect();
    if pollable.is_empty() {
        return Ok(());
    }
    let rows =
        qasa_db::repos::worker::payments_to_check(&pool, &pollable, CHECK_EVERY_MINUTES, 100)
            .await?;
    for p in rows {
        qasa_db::repos::worker::mark_checked(&pool, p.id).await?;
        let Some(gateway) = GatewayKind::parse(&p.gateway).and_then(|k| gateways.get(k)) else {
            continue;
        };
        let event = match gateway.fetch_status(&p.gateway_order_id).await {
            Ok(Some(event)) => event,
            Ok(None) => continue,
            Err(err) => {
                tracing::warn!(error = %err, payment_id = %p.id, "payment status check failed");
                continue;
            }
        };
        let status = match event.status {
            WebhookStatus::Success => SettleStatus::Success,
            WebhookStatus::Failed => SettleStatus::Failed,
            WebhookStatus::Pending => continue,
        };
        let outcome = settle_payment(&pool, &p.gateway, &p.gateway_order_id, status, event.amount_iqd)
            .await?;
        match outcome {
            SettleOutcome::Settled { subscription, .. } => tracing::info!(
                payment_id = %p.id,
                expires_at = %subscription.expires_at,
                "payment settled by status check"
            ),
            SettleOutcome::AmountMismatch { reported, expected } => tracing::warn!(
                payment_id = %p.id,
                reported,
                expected,
                "status check: amount mismatch"
            ),
            SettleOutcome::AccountDeleted { payment } => {
                tracing::warn!(payment_id = %payment.id, "status check: account deleted; refund required")
            }
            _ => {}
        }
    }
    Ok(())
}

/// Re-attempt settlement for verified webhooks that have not been handled yet.
///
/// Goes through the same `settle_payment` as the API, so the amount check and the
/// row lock apply here too. The previous copy of this logic skipped the amount check.
pub async fn retry_webhooks(pool: DbPool, _push: Arc<PushService>) -> anyhow::Result<()> {
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

        let outcome = settle_payment_with_token(
            &pool,
            &event.gateway,
            order_id,
            status,
            reported_amount,
            event.payment_token.as_deref(),
        )
        .await?;

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
