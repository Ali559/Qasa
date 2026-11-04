use async_trait::async_trait;
use axum::http::HeaderMap;
use serde::{Deserialize, Serialize};
use thiserror::Error;

#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash, Serialize, Deserialize)]
#[serde(rename_all = "lowercase")]
pub enum GatewayKind {
    Fib,
    Fastpay,
    Superqi,
}

impl GatewayKind {
    pub fn as_str(self) -> &'static str {
        match self {
            Self::Fib => "fib",
            Self::Fastpay => "fastpay",
            Self::Superqi => "superqi",
        }
    }

    pub fn parse(value: &str) -> Option<Self> {
        match value.to_lowercase().as_str() {
            "fib" => Some(Self::Fib),
            "fastpay" => Some(Self::Fastpay),
            "superqi" => Some(Self::Superqi),
            _ => None,
        }
    }
}

#[derive(Debug, Clone, Serialize)]
pub struct PaymentIntent {
    pub gateway_order_id: String,
    pub deep_link: String,
    pub amount_iqd: i32,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct WebhookEvent {
    pub gateway_order_id: String,
    pub status: WebhookStatus,
    /// Amount the gateway says it settled, when it reports one. Compared against the
    /// amount we asked for before any entitlement is granted.
    pub amount_iqd: Option<i32>,
    /// A reusable token for the customer's wallet or card, when the checkout asked the
    /// gateway to save it (`save_method`). Only stored if the payment had auto-renew on.
    pub payment_token: Option<String>,
    pub raw_payload: serde_json::Value,
}

/// What the gateway answered when asked to charge a saved payment method.
#[derive(Debug, Clone)]
pub struct RecurringCharge {
    pub gateway_order_id: String,
    /// `Pending` means the outcome arrives later by webhook, like a checkout.
    pub status: WebhookStatus,
    pub amount_iqd: Option<i32>,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "lowercase")]
pub enum WebhookStatus {
    Success,
    Failed,
    Pending,
}

#[derive(Debug, Error)]
pub enum PaymentError {
    #[error("unsupported gateway")]
    UnsupportedGateway,
    #[error("invalid webhook signature")]
    InvalidSignature,
    #[error("bad payload: {0}")]
    BadPayload(String),
    /// The gateway has no live integration behind it, so it cannot settle anything.
    #[error("gateway {0:?} is not live in this environment")]
    GatewayNotLive(GatewayKind),
    #[error("webhook amount {reported} does not match the charged amount {expected}")]
    AmountMismatch { reported: i32, expected: i32 },
    /// The gateway cannot charge a saved method at all.
    #[error("gateway {0:?} does not support recurring charges")]
    RecurringUnsupported(GatewayKind),
    /// The saved token is no longer valid (the customer removed it at the gateway, or it
    /// expired). The method should be dropped; retrying cannot succeed.
    #[error("saved payment method was rejected by the gateway")]
    TokenRejected,
    /// The charge could not be placed (network, gateway outage). Worth retrying.
    #[error("gateway error: {0}")]
    Gateway(String),
}

#[async_trait]
pub trait PaymentGateway: Send + Sync {
    fn kind(&self) -> GatewayKind;

    /// `save_method` asks the gateway to return a reusable token with the payment, for
    /// later renewals. Set only when the customer left auto-renew on.
    async fn create_intent(&self, amount_iqd: i32, order_ref: &str, save_method: bool)
        -> PaymentIntent;

    fn verify_webhook(&self, headers: &HeaderMap, body: &[u8]) -> Result<WebhookEvent, PaymentError>;

    /// Whether this gateway can charge a saved method without the customer approving
    /// each payment. Renewals are only attempted on gateways that can.
    fn supports_recurring(&self) -> bool;

    /// Charge a saved method `amount_iqd`. `order_ref` is our payment id, passed so the
    /// gateway can refuse a duplicate charge if the call is retried.
    async fn charge_saved(
        &self,
        token: &str,
        amount_iqd: i32,
        order_ref: &str,
    ) -> Result<RecurringCharge, PaymentError>;
}
