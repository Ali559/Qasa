use std::sync::Arc;

use crate::gateway::{GatewayKind, PaymentGateway};
use crate::stub::all_stub_gateways;
use crate::wayl::{WaylConfig, WaylGateway};

/// How a subscription with auto-renew on gets renewed.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum RenewalMode {
    /// The worker charges the saved wallet.
    Charge,
    /// The worker sends a payment link before the period ends, and the customer pays it.
    Link,
    /// Nothing renews it; the customer is reminded to pay by hand.
    None,
}

impl RenewalMode {
    pub fn as_str(self) -> &'static str {
        match self {
            Self::Charge => "charge",
            Self::Link => "link",
            Self::None => "none",
        }
    }
}

/// Every payment gateway this process can use, built once at startup by both the API
/// and the worker so they agree on which ones are live.
pub struct Gateways {
    all: Vec<Arc<dyn PaymentGateway>>,
}

impl Gateways {
    /// The stubs (which may only settle in dev, `stubs_settle`) and Wayl when it is
    /// configured. `allow_test_links` lets Wayl run in its test mode, outside production.
    pub fn from_env(stubs_settle: bool, allow_test_links: bool) -> anyhow::Result<Self> {
        let mut all: Vec<Arc<dyn PaymentGateway>> = all_stub_gateways(stubs_settle)
            .into_iter()
            .map(|(_, g)| Arc::new(g) as Arc<dyn PaymentGateway>)
            .collect();
        if let Some(config) = WaylConfig::from_env(allow_test_links)? {
            all.push(Arc::new(WaylGateway::new(config)));
        }
        Ok(Self::new(all))
    }

    pub fn new(all: Vec<Arc<dyn PaymentGateway>>) -> Self {
        Self { all }
    }

    pub fn get(&self, kind: GatewayKind) -> Option<&dyn PaymentGateway> {
        self.all.iter().find(|g| g.kind() == kind).map(|g| g.as_ref())
    }

    fn live(&self, kind: GatewayKind) -> Option<&dyn PaymentGateway> {
        self.get(kind).filter(|g| g.is_live())
    }

    /// The gateway that takes a checkout for the wallet the customer picked. Wayl, once
    /// live, takes them all: its page offers the same wallets, and it is the only one of
    /// these that moves real money. Otherwise the picked one, live or not (the caller
    /// refuses a gateway that is not).
    pub fn for_checkout(&self, picked: GatewayKind) -> Option<&dyn PaymentGateway> {
        self.live(GatewayKind::Wayl).or_else(|| self.get(picked))
    }

    /// Gateways that can charge a saved method, as stored in `payment_methods.gateway`.
    pub fn recurring(&self) -> Vec<&'static str> {
        self.all
            .iter()
            .filter(|g| g.is_live() && g.supports_recurring())
            .map(|g| g.kind().as_str())
            .collect()
    }

    /// The gateway renewal links are sent through: Wayl when live, else any live one
    /// whose links can stay open for days (the dev stubs).
    pub fn renewal_links(&self) -> Option<&dyn PaymentGateway> {
        self.live(GatewayKind::Wayl)
            .filter(|g| g.supports_renewal_links())
            .or_else(|| {
                self.all
                    .iter()
                    .find(|g| g.is_live() && g.supports_renewal_links())
                    .map(|g| g.as_ref())
            })
    }

    /// How a checkout on `picked` with auto-renew on will renew: by charging the wallet it
    /// saves, or by a link before each period ends.
    pub fn renewal_mode_for_checkout(&self, picked: GatewayKind) -> RenewalMode {
        match self.for_checkout(picked).filter(|g| g.is_live()) {
            Some(g) if g.supports_recurring() => RenewalMode::Charge,
            _ if self.renewal_links().is_some() => RenewalMode::Link,
            _ => RenewalMode::None,
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::stub::StubGateway;
    use crate::wayl::{WaylEnv, DEFAULT_BASE_URL};

    fn stubs(settle: bool) -> Vec<Arc<dyn PaymentGateway>> {
        all_stub_gateways(settle)
            .into_iter()
            .map(|(_, g)| Arc::new(g) as Arc<dyn PaymentGateway>)
            .collect()
    }

    fn wayl() -> Arc<dyn PaymentGateway> {
        Arc::new(WaylGateway::new(WaylConfig {
            api_key: "k".into(),
            webhook_secret: "0123456789".into(),
            webhook_url: "https://x.test/v1/payments/webhook/wayl".into(),
            redirect_url: None,
            env: WaylEnv::Live,
            base_url: DEFAULT_BASE_URL.into(),
        }))
    }

    #[test]
    fn production_without_wayl_has_nothing_live() {
        let g = Gateways::new(stubs(false));
        assert!(!g.for_checkout(GatewayKind::Fib).unwrap().is_live());
        assert!(g.recurring().is_empty());
        assert!(g.renewal_links().is_none());
        assert_eq!(g.renewal_mode_for_checkout(GatewayKind::Fib), RenewalMode::None);
    }

    #[test]
    fn wayl_takes_every_checkout_and_renews_by_link() {
        let mut all = stubs(false);
        all.push(wayl());
        let g = Gateways::new(all);
        for picked in [GatewayKind::Fib, GatewayKind::Fastpay, GatewayKind::Superqi] {
            assert_eq!(g.for_checkout(picked).unwrap().kind(), GatewayKind::Wayl);
            assert_eq!(g.renewal_mode_for_checkout(picked), RenewalMode::Link);
        }
        assert!(g.recurring().is_empty());
        assert_eq!(g.renewal_links().unwrap().kind(), GatewayKind::Wayl);
    }

    #[test]
    fn dev_stubs_charge_and_can_also_send_links() {
        let g = Gateways::new(stubs(true));
        assert_eq!(g.for_checkout(GatewayKind::Fastpay).unwrap().kind(), GatewayKind::Fastpay);
        assert_eq!(g.renewal_mode_for_checkout(GatewayKind::Fib), RenewalMode::Charge);
        assert_eq!(g.recurring().len(), 3);
        assert!(g.renewal_links().is_some());
    }

    #[test]
    fn a_stub_that_cannot_settle_is_never_recurring() {
        let g = Gateways::new(vec![Arc::new(StubGateway::new(GatewayKind::Fib, false))]);
        assert!(g.recurring().is_empty());
    }
}
