mod gateway;
mod stub;

pub use gateway::{
    GatewayKind, PaymentError, PaymentGateway, PaymentIntent, WebhookEvent, WebhookStatus,
};
pub use stub::{all_stub_gateways, new_order_ref, stub_for, StubGateway};

/// Whether the server actually charges auto-renew subscriptions when they come due.
///
/// It does not yet: `auto_renew` is only a stored preference, and every payment is a
/// checkout the user starts. While this is `false`, nothing may tell a user that a
/// renewal failed (there was no renewal), and the 3-day expiry reminder goes to every
/// subscriber, auto-renew or not, since no subscription will renew by itself. Flip it in
/// the same change that adds recurring charges.
pub const RENEWALS_ARE_CHARGED: bool = false;
