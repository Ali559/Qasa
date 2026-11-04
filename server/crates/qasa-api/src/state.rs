use std::sync::Arc;

use qasa_core::{AdminTokenIssuer, AppConfig, LicenseKeyPair, LicenseTokenIssuer};
use qasa_db::DbPool;
use qasa_payments::Gateways;

#[derive(Clone)]
pub struct AppState {
    pub pool: DbPool,
    pub config: AppConfig,
    pub license_issuer: Arc<LicenseTokenIssuer>,
    pub admin_issuer: Arc<AdminTokenIssuer>,
    pub gateways: Arc<Gateways>,
    pub push: Arc<qasa_push::PushService>,
}

impl AppState {
    pub fn new(pool: DbPool, config: AppConfig, license_keys: LicenseKeyPair) -> Self {
        // The FIB, FastPay and SuperQi gateways are stubs whose webhook handler performs no
        // signature check. In prod they must therefore be unable to mark an order paid,
        // otherwise anyone who can reach the webhook URL gets a free subscription by
        // POSTing `{"order_id": "...", "status": "success"}`. Wayl, when configured, is
        // the real one; its test mode is refused in prod.
        let gateways = Gateways::from_env(config.env.is_dev(), !config.env.is_prod())
            .expect("payment gateway configuration");

        Self {
            pool,
            config: config.clone(),
            license_issuer: Arc::new(LicenseTokenIssuer::new(license_keys)),
            admin_issuer: Arc::new(AdminTokenIssuer::new(config.admin_jwt_secret)),
            gateways: Arc::new(gateways),
            push: Arc::new(qasa_push::PushService::from_env()),
        }
    }

    /// Gateways that can charge a saved method, as stored in `payment_methods.gateway`.
    pub fn recurring_gateways(&self) -> Vec<&'static str> {
        self.gateways.recurring()
    }
}
