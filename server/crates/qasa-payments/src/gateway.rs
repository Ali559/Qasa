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
    pub raw_payload: serde_json::Value,
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
}

#[async_trait]
pub trait PaymentGateway: Send + Sync {
    fn kind(&self) -> GatewayKind;

    async fn create_intent(&self, amount_iqd: i32, order_ref: &str) -> PaymentIntent;

    fn verify_webhook(&self, headers: &HeaderMap, body: &[u8]) -> Result<WebhookEvent, PaymentError>;
}
