mod admin;
mod auth;
mod breach;
mod devices;
mod entitlements;
mod error;
mod health;
mod license;
mod metrics;
mod metrics_handler;
mod middleware;
mod payments;
mod state;
mod subscriptions;
mod vpn;
mod vpn_agent;

use std::net::SocketAddr;

use axum::http::{header, Method};
use axum::middleware::from_fn;
use axum::routing::{delete, get, patch, post};
use axum::Router;
use qasa_core::{hash_password, AppConfig, LicenseKeyPair};
use qasa_db::{connect, run_migrations};
use tower_http::cors::{Any, CorsLayer};
use tower_http::trace::TraceLayer;
use tracing_subscriber::{layer::SubscriberExt, util::SubscriberInitExt};

use crate::state::AppState;

#[tokio::main]
async fn main() -> anyhow::Result<()> {
    // Load server/.env when present (no-op if missing).
    let _ = dotenvy::dotenv();

    let config = AppConfig::from_env();
    init_tracing(&config);

    tracing::info!(env = %config.env, "starting qasa-api");

    let pool = connect(&config.database_url).await?;
    run_migrations(&pool).await?;

    if config.env.is_dev() {
        qasa_db::repos::vpn::seed_dev_nodes(&pool).await?;
        if let (Some(email), Some(password)) = (
            config.admin_seed_email.clone(),
            config.admin_seed_password.clone(),
        ) {
            let password_hash = hash_password(&password)?;
            qasa_db::repos::account::seed_admin_if_missing(&pool, &email, &password_hash).await?;
            tracing::info!(email = %email, "seeded admin user if missing");
        }
    }

    let license_keys = match &config.license_private_key_b64 {
        Some(key) => LicenseKeyPair::from_private_key_b64(key)?,
        None if config.env.is_prod() => {
            anyhow::bail!("QASA_LICENSE_PRIVATE_KEY must be set when QASA_ENV=prod");
        }
        None => {
            let keys = LicenseKeyPair::generate();
            tracing::warn!(
                public_key = %keys.public_key_b64(),
                "generated ephemeral license signing key; set QASA_LICENSE_PRIVATE_KEY for persistence"
            );
            keys
        }
    };

    if config.env.is_prod() {
        crate::metrics::init_production_metrics();
        tracing::info!("prometheus metrics enabled at /metrics");
    }

    let state = AppState::new(pool, config.clone(), license_keys);
    let app = build_router(state, &config);

    let addr: SocketAddr = config.bind_addr.parse()?;
    tracing::info!(%addr, env = %config.env, "qasa-api listening");
    let listener = tokio::net::TcpListener::bind(addr).await?;
    axum::serve(listener, app).await?;
    Ok(())
}

fn init_tracing(config: &AppConfig) {
    let filter = tracing_subscriber::EnvFilter::try_from_default_env()
        .unwrap_or_else(|_| "qasa_api=debug,tower_http=info,sqlx=warn".into());

    if config.env.is_prod() {
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

fn build_router(state: AppState, config: &AppConfig) -> Router {
    // Browsers reject Authorization when Allow-Headers is `*` — list explicitly.
    let cors = CorsLayer::new()
        .allow_methods([
            Method::GET,
            Method::POST,
            Method::PUT,
            Method::PATCH,
            Method::DELETE,
            Method::OPTIONS,
        ])
        .allow_headers([
            header::AUTHORIZATION,
            header::CONTENT_TYPE,
            header::ACCEPT,
        ]);

    let cors = if config.env.is_prod() {
        if let Some(origin) = &config.admin_cors_origin {
            cors.allow_origin(origin.parse::<axum::http::HeaderValue>().unwrap_or_else(|_| {
                panic!("invalid QASA_ADMIN_CORS_ORIGIN: {origin}");
            }))
        } else {
            cors.allow_origin(Any)
        }
    } else {
        cors.allow_origin(Any)
    };

    let mut router = Router::new()
        .route("/health", get(health::health))
        .route("/.well-known/qasa-license-key", get(license::public_key))
        .route("/v1/devices/register", post(devices::register))
        .route("/v1/devices", get(devices::list))
        .route("/v1/devices/{device_id}", delete(devices::unlink))
        .route("/v1/license", get(license::refresh))
        .route("/v1/subscription", get(subscriptions::get_current))
        .route("/v1/subscription/auto-renew", patch(subscriptions::set_auto_renew))
        .route("/v1/subscription/cancel", post(subscriptions::cancel))
        .route("/v1/payments/intents", post(payments::create_intent))
        .route("/v1/payments/{payment_id}", get(payments::get_payment))
        .route("/v1/payments/webhook/fib", post(payments::webhook_fib))
        .route("/v1/payments/webhook/fastpay", post(payments::webhook_fastpay))
        .route("/v1/payments/webhook/superqi", post(payments::webhook_superqi))
        .route("/v1/breach/prefix/{prefix}", get(breach::lookup_prefix))
        .route("/v1/vpn/connect", post(vpn::connect))
        .route("/v1/vpn/disconnect", post(vpn::disconnect))
        .route("/v1/vpn/status", get(vpn::status))
        .route(
            "/v1/internal/vpn/nodes/{node_id}/heartbeat",
            post(vpn_agent::heartbeat),
        )
        .route("/v1/admin/auth/login", post(admin::login))
        .route("/v1/admin/overview", get(admin::overview))
        .route("/v1/admin/users", get(admin::list_users))
        .route("/v1/admin/payments", get(admin::list_payments))
        .route("/v1/admin/cancellations", get(admin::list_cancellations))
        .route("/v1/admin/webhooks", get(admin::list_webhooks))
        .route("/v1/admin/vpn/nodes", get(admin::list_vpn_nodes))
        .route("/v1/admin/telemetry", get(admin::telemetry));

    if config.env.is_prod() {
        router = router
            .route("/metrics", get(metrics_handler::prometheus_metrics))
            .layer(from_fn(middleware::count_requests));
    }

    router
        .layer(cors)
        .layer(TraceLayer::new_for_http())
        .with_state(state)
}