use std::collections::HashMap;
use std::sync::Arc;
use std::time::Duration;

use qasa_breach::RateLimiter;
use qasa_core::{AdminTokenIssuer, AppConfig, LicenseKeyPair, LicenseTokenIssuer};
use qasa_db::DbPool;
use qasa_payments::{GatewayKind, StubGateway};

#[derive(Clone)]
pub struct AppState {
    pub pool: DbPool,
    pub config: AppConfig,
    pub license_issuer: Arc<LicenseTokenIssuer>,
    pub admin_issuer: Arc<AdminTokenIssuer>,
    pub gateways: Arc<HashMap<GatewayKind, StubGateway>>,
    pub breach_rate_limiter: Arc<RateLimiter>,
    pub push: Arc<qasa_push::PushService>,
}

impl AppState {
    pub fn new(pool: DbPool, config: AppConfig, license_keys: LicenseKeyPair) -> Self {
        // No payment gateway has a real integration yet — all three are stubs whose
        // webhook handler performs no signature check. In prod they must therefore be
        // unable to mark an order paid, otherwise anyone who can reach the webhook URL
        // gets a free subscription by POSTing `{"order_id": "...", "status": "success"}`.
        let settles_payments = config.env.is_dev();
        let gateways = qasa_payments::all_stub_gateways(settles_payments)
            .into_iter()
            .map(|(kind, gw)| (kind, gw))
            .collect();

        Self {
            pool,
            config: config.clone(),
            license_issuer: Arc::new(LicenseTokenIssuer::new(license_keys)),
            admin_issuer: Arc::new(AdminTokenIssuer::new(config.admin_jwt_secret)),
            gateways: Arc::new(gateways),
            breach_rate_limiter: Arc::new(RateLimiter::new(60, Duration::from_secs(3600))),
            push: Arc::new(qasa_push::PushService::from_env()),
        }
    }

    pub fn gateway(&self, kind: GatewayKind) -> Option<&StubGateway> {
        self.gateways.get(&kind)
    }
}
