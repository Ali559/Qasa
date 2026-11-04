mod gateway;
mod stub;

pub use gateway::{
    GatewayKind, PaymentError, PaymentGateway, PaymentIntent, RecurringCharge, WebhookEvent,
    WebhookStatus,
};
pub use stub::{all_stub_gateways, new_order_ref, stub_for, StubGateway};

/// How many times the worker tries to charge a renewal before it gives up and tells the
/// customer to pay by hand.
pub const RENEWAL_MAX_ATTEMPTS: i32 = 3;

/// Renewal charges start this long before a subscription ends, so every retry still
/// lands before the customer loses Premium.
pub const RENEWAL_LEAD_HOURS: i32 = 24;

/// Minimum gap between two renewal attempts for one subscription.
pub const RENEWAL_RETRY_HOURS: i32 = 6;
