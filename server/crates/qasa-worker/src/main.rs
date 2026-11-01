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
        "purge_license_revocations",
        jobs::purge_revocations,
    ));
    tokio::spawn(run_interval(
        pool.clone(),
        push.clone(),
        Duration::from_secs(10 * 60),
        "breach_alert_scan",
        jobs::breach_alert_scan,
    ));
    tokio::spawn(run_interval(
        pool.clone(),
        push.clone(),
        Duration::from_secs(60 * 60),
        "subscription_expiry_warnings",
        jobs::subscription_expiry_warnings,
    ));
    // "Renewal failed" is only true once renewals are actually charged; until then this
    // job would tell every auto-renew subscriber their renewal failed a day before expiry.
    if qasa_payments::RENEWALS_ARE_CHARGED {
        tokio::spawn(run_interval(
            pool.clone(),
            push.clone(),
            Duration::from_secs(60 * 60),
            "subscription_renew_failed_notices",
            jobs::subscription_renew_failed_notices,
        ));
    }

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
