mod jobs;

use std::time::Duration;

use qasa_core::{AppConfig, AppEnvironment};
use qasa_db::{connect, run_migrations};
use tokio::time::interval;
use tracing_subscriber::{layer::SubscriberExt, util::SubscriberInitExt};

#[tokio::main]
async fn main() -> anyhow::Result<()> {
    init_tracing();

    let config = AppConfig::from_env();
    if !config.env.is_prod() {
        tracing::info!(
            env = %config.env,
            "qasa-worker is disabled outside production (set QASA_ENV=prod to enable)"
        );
        return Ok(());
    }

    tracing::info!(env = %config.env, "starting qasa-worker production jobs");

    let pool = connect(&config.database_url).await?;
    run_migrations(&pool).await?;

    tokio::spawn(run_interval(
        pool.clone(),
        Duration::from_secs(15 * 60),
        "expire_subscriptions",
        jobs::expire_subscriptions,
    ));
    tokio::spawn(run_interval(
        pool.clone(),
        Duration::from_secs(5 * 60),
        "refresh_materialized_views",
        jobs::refresh_views,
    ));
    tokio::spawn(run_interval(
        pool.clone(),
        Duration::from_secs(60),
        "webhook_retry",
        jobs::retry_webhooks,
    ));
    tokio::spawn(run_interval(
        pool.clone(),
        Duration::from_secs(10 * 60),
        "stale_vpn_sessions",
        jobs::stale_vpn_sessions,
    ));
    tokio::spawn(run_interval(
        pool.clone(),
        Duration::from_secs(24 * 60 * 60),
        "purge_license_revocations",
        jobs::purge_revocations,
    ));

    // Keep process alive
    std::future::pending::<()>().await;
    Ok(())
}

async fn run_interval<F, Fut>(pool: qasa_db::DbPool, every: Duration, name: &'static str, job: F)
where
    F: Fn(qasa_db::DbPool) -> Fut + Send + Sync + 'static,
    Fut: std::future::Future<Output = anyhow::Result<()>> + Send + 'static,
{
    let mut ticker = interval(every);
    loop {
        ticker.tick().await;
        match job(pool.clone()).await {
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
