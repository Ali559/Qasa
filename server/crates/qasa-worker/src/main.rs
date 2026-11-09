mod jobs;

use std::env;
use std::sync::Arc;
use std::time::Duration;

use qasa_core::{AppConfig, AppEnvironment};
use qasa_db::{connect, run_migrations};
use qasa_push::PushService;
use tokio::time::interval;
use tracing_subscriber::{layer::SubscriberExt, util::SubscriberInitExt};

#[tokio::main]
async fn main() -> anyhow::Result<()> {
    init_tracing();

    let config = AppConfig::from_env();
    let force = env::var("QASA_WORKER_ENABLE")
        .map(|v| v == "1" || v.eq_ignore_ascii_case("true"))
        .unwrap_or(false);

    if !config.env.is_prod() && !force {
        tracing::info!(
            env = %config.env,
            "qasa-worker idle outside production (set QASA_ENV=prod or QASA_WORKER_ENABLE=1)"
        );
        return Ok(());
    }

    tracing::info!(env = %config.env, force, "starting qasa-worker jobs");

    let pool = connect(&config.database_url).await?;
    run_migrations(&pool).await?;
    let push = Arc::new(PushService::from_env());
    // Same rule as the API: stub gateways may only settle, and so only charge, in dev.
    let gateways = Arc::new(qasa_payments::Gateways::from_env(
        config.env.is_dev(),
        !config.env.is_prod(),
    )?);

    tokio::spawn(run_interval(
        pool.clone(),
        push.clone(),
        Duration::from_secs(15 * 60),
        "expire_subscriptions",
        jobs::expire_subscriptions,
    ));
    tokio::spawn(run_interval(
        pool.clone(),
        push.clone(),
        Duration::from_secs(5 * 60),
        "purge_rate_limit_buckets",
        jobs::purge_rate_limits,
    ));
    tokio::spawn(run_interval(
        pool.clone(),
        push.clone(),
        Duration::from_secs(60),
        "broadcast_pushes",
        jobs::broadcast_pushes,
    ));
    tokio::spawn(run_interval(
        pool.clone(),
        push.clone(),
        Duration::from_secs(60),
        "webhook_retry",
        jobs::retry_webhooks,
    ));
    tokio::spawn(run_interval(
        pool.clone(),
        push.clone(),
        Duration::from_secs(5 * 60),
        "expire_abandoned_payments",
        jobs::expire_abandoned_payments,
    ));
    tokio::spawn(run_interval(
        pool.clone(),
        push.clone(),
        Duration::from_secs(60),
        "offline_vpn_nodes",
        jobs::offline_vpn_nodes,
    ));
    tokio::spawn(run_interval(
        pool.clone(),
        push.clone(),
        Duration::from_secs(60),
        "stale_vpn_sessions",
        jobs::stale_vpn_sessions,
    ));
    tokio::spawn(run_interval(
        pool.clone(),
        push.clone(),
        Duration::from_secs(24 * 60 * 60),
        "purge_ended_vpn_sessions",
        jobs::purge_ended_vpn_sessions,
    ));
    tokio::spawn(run_interval(
        pool.clone(),
        push.clone(),
        Duration::from_secs(24 * 60 * 60),
        "purge_license_revocations",
        jobs::purge_revocations,
    ));
    // Email leak alerts are parked (todo email-leak-alerts in docs/server-followups-plan.md):
    // there is no breach data source yet, so the scan has nothing to match.
    // tokio::spawn(run_interval(
    //     pool.clone(),
    //     push.clone(),
    //     Duration::from_secs(10 * 60),
    //     "breach_alert_scan",
    //     jobs::breach_alert_scan,
    // ));
    tokio::spawn(run_interval(
        pool.clone(),
        push.clone(),
        // Sends renewal links first (see the job).
        Duration::from_secs(60 * 60),
        "subscription_expiry_warnings",
        with_gateways(&gateways, jobs::subscription_expiry_warnings),
    ));
    tokio::spawn(run_interval(
        pool.clone(),
        push.clone(),
        Duration::from_secs(15 * 60),
        "charge_renewals",
        with_gateways(&gateways, jobs::charge_renewals),
    ));
    tokio::spawn(run_interval(
        pool.clone(),
        push.clone(),
        Duration::from_secs(5 * 60),
        "check_open_payments",
        with_gateways(&gateways, jobs::check_open_payments),
    ));
    tokio::spawn(run_interval(
        pool.clone(),
        push.clone(),
        Duration::from_secs(60 * 60),
        "subscription_renew_failed_notices",
        with_gateways(&gateways, jobs::subscription_renew_failed_notices),
    ));

    std::future::pending::<()>().await;
    Ok(())
}

async fn run_interval<F, Fut>(
    pool: qasa_db::DbPool,
    push: Arc<PushService>,
    every: Duration,
    name: &'static str,
    job: F,
) where
    F: Fn(qasa_db::DbPool, Arc<PushService>) -> Fut + Send + Sync + 'static,
    Fut: std::future::Future<Output = anyhow::Result<()>> + Send + 'static,
{
    let mut ticker = interval(every);
    loop {
        ticker.tick().await;
        match job(pool.clone(), push.clone()).await {
            Ok(()) => tracing::debug!(job = name, "completed"),
            Err(err) => tracing::error!(job = name, error = %err, "failed"),
        }
    }
}

/// Adapt a job that also needs the payment gateways to `run_interval`'s signature.
fn with_gateways<F, Fut>(
    gateways: &Arc<qasa_payments::Gateways>,
    job: F,
) -> impl Fn(qasa_db::DbPool, Arc<PushService>) -> Fut + Send + Sync + 'static
where
    F: Fn(qasa_db::DbPool, Arc<PushService>, Arc<qasa_payments::Gateways>) -> Fut + Send + Sync + 'static,
    Fut: std::future::Future<Output = anyhow::Result<()>> + Send + 'static,
{
    let gateways = gateways.clone();
    move |pool, push| job(pool, push, gateways.clone())
}

fn init_tracing() {
    let env = AppEnvironment::from_env();
    let filter = tracing_subscriber::EnvFilter::try_from_default_env()
        .unwrap_or_else(|_| "qasa_worker=info,sqlx=warn".into());

    if env.is_prod() {
        tracing_subscriber::registry()
            .with(filter)
            .with(tracing_subscriber::fmt::layer().json())
            .init();
    } else {
        tracing_subscriber::registry()
            .with(filter)
            .with(tracing_subscriber::fmt::layer())
            .init();
    }
}
