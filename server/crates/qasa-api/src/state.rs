use std::collections::HashMap;
use std::sync::Arc;

use qasa_core::{AdminTokenIssuer, AppConfig, LicenseKeyPair, LicenseTokenIssuer};
use qasa_db::DbPool;
use qasa_payments::{GatewayKind, PaymentGateway, StubGateway};

#[derive(Clone)]
pub struct AppState {
    pub pool: DbPool,
    pub config: AppConfig,
    pub license_issuer: Arc<LicenseTokenIssuer>,
    pub admin_issuer: Arc<AdminTokenIssuer>,
    pub gateways: Arc<HashMap<GatewayKind, StubGateway>>,
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
            .collect();

        Self {
            pool,
            config: config.clone(),
            license_issuer: Arc::new(LicenseTokenIssuer::new(license_keys)),
            admin_issuer: Arc::new(AdminTokenIssuer::new(config.admin_jwt_secret)),
            gateways: Arc::new(gateways),
            push: Arc::new(qasa_push::PushService::from_env()),
        }
    }

    pub fn gateway(&self, kind: GatewayKind) -> Option<&StubGateway> {
        self.gateways.get(&kind)
    }

    /// Gateways that can charge a saved method, as stored in `payment_methods.gateway`.
    pub fn recurring_gateways(&self) -> Vec<&'static str> {
        self.gateways
            .values()
            .filter(|g| g.supports_recurring())
            .map(|g| g.kind().as_str())
            .collect()
    }
}
