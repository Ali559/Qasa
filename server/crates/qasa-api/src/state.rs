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
}

impl AppState {
    pub fn new(pool: DbPool, config: AppConfig, license_keys: LicenseKeyPair) -> Self {
        let gateways = qasa_payments::all_stub_gateways()
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
        }
    }

    pub fn gateway(&self, kind: GatewayKind) -> Option<&StubGateway> {
        self.gateways.get(&kind)
    }
}
