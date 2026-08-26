use axum::http::HeaderMap;
use uuid::Uuid;

use crate::gateway::{
    GatewayKind, PaymentError, PaymentGateway, PaymentIntent, WebhookEvent, WebhookStatus,
};

pub struct StubGateway {
    kind: GatewayKind,
}

impl StubGateway {
    pub fn new(kind: GatewayKind) -> Self {
        Self { kind }
    }
}

#[async_trait::async_trait]
impl PaymentGateway for StubGateway {
    fn kind(&self) -> GatewayKind {
        self.kind
    }

    async fn create_intent(&self, amount_iqd: i32, order_ref: &str) -> PaymentIntent {
        let prefix = match self.kind {
            GatewayKind::Fib => "FIB-ORD",
            GatewayKind::Fastpay => "FP-ORD",
            GatewayKind::Superqi => "SQ-ORD",
        };
        let gateway_order_id = format!("{}-{}", prefix, &order_ref[..8.min(order_ref.len())]);
        let scheme = match self.kind {
            GatewayKind::Fib => "fib",
            GatewayKind::Fastpay => "fastpay",
            GatewayKind::Superqi => "superqi",
        };
        PaymentIntent {
            gateway_order_id,
            deep_link: format!("{scheme}://pay?amount={amount_iqd}&ref={order_ref}"),
            amount_iqd,
        }
    }

    fn verify_webhook(&self, _headers: &HeaderMap, body: &[u8]) -> Result<WebhookEvent, PaymentError> {
        let payload: serde_json::Value = serde_json::from_slice(body)
            .map_err(|e| PaymentError::BadPayload(e.to_string()))?;

        let gateway_order_id = payload
            .get("order_id")
            .or_else(|| payload.get("gateway_order_id"))
            .and_then(|v| v.as_str())
            .ok_or_else(|| PaymentError::BadPayload("missing order_id".into()))?
            .to_string();

        let status = match payload.get("status").and_then(|v| v.as_str()) {
            Some("success") => WebhookStatus::Success,
            Some("failed") => WebhookStatus::Failed,
            _ => WebhookStatus::Pending,
        };

        Ok(WebhookEvent {
            gateway_order_id,
            status,
            raw_payload: payload,
        })
    }
}

pub fn all_stub_gateways() -> Vec<(GatewayKind, StubGateway)> {
    vec![
        (GatewayKind::Fib, StubGateway::new(GatewayKind::Fib)),
        (GatewayKind::Fastpay, StubGateway::new(GatewayKind::Fastpay)),
        (GatewayKind::Superqi, StubGateway::new(GatewayKind::Superqi)),
    ]
}

pub fn stub_for(kind: GatewayKind) -> StubGateway {
    StubGateway::new(kind)
}

pub fn new_order_ref() -> String {
    Uuid::now_v7().simple().to_string()
}
