mod gateway;
mod registry;
mod stub;
mod wayl;

pub use gateway::{
    GatewayKind, IntentRequest, PaymentError, PaymentGateway, PaymentIntent, RecurringCharge,
    WebhookEvent, WebhookStatus,
};
pub use registry::{Gateways, RenewalMode};
pub use stub::{all_stub_gateways, new_order_ref, stub_for, StubGateway};
pub use wayl::{WaylConfig, WaylEnv, WaylGateway};

/// How many times the worker tries to charge a renewal before it gives up and tells the
/// customer to pay by hand.
pub const RENEWAL_MAX_ATTEMPTS: i32 = 3;

/// Renewal charges start this long before a subscription ends, so every retry still
/// lands before the customer loses Premium.
pub const RENEWAL_LEAD_HOURS: i32 = 24;

/// Minimum gap between two renewal attempts for one subscription.
pub const RENEWAL_RETRY_HOURS: i32 = 6;

/// Renewal links go out this long before a subscription ends: before the 3-day "ending
/// soon" reminder, which a sent link replaces.
pub const RENEWAL_LINK_LEAD_HOURS: i32 = 96;

/// How long a checkout link stays open. Matches the worker's abandoned-payment cutoff.
pub const CHECKOUT_LINK_MINUTES: u32 = 60;
