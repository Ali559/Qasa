use axum::http::HeaderMap;
use uuid::Uuid;

use crate::gateway::{
    GatewayKind, PaymentError, PaymentGateway, PaymentIntent, WebhookEvent, WebhookStatus,
};

/// A fake gateway used for local development and demos.
///
/// **It cannot move money and it cannot verify anything.** `verify_webhook` below accepts
/// a plain JSON body with no signature at all, so any process that can reach
/// `POST /v1/payments/webhook/{gateway}` can mark any order paid. That is why the webhook
/// handler refuses to let a stub settle a payment unless [`StubGateway::settles_payments`]
/// is true, which `AppState` only enables outside production.
///
/// Do not add a code path that turns this on in prod to "unblock" a payment flow.
pub struct StubGateway {
    kind: GatewayKind,
    settles_payments: bool,
}

impl StubGateway {
    /// `settles_payments` must be `false` anywhere real orders are handled.
    pub fn new(kind: GatewayKind, settles_payments: bool) -> Self {
        Self {
            kind,
            settles_payments,
        }
    }

    /// Whether this gateway is allowed to transition a payment to `success`.
    pub fn settles_payments(&self) -> bool {
        self.settles_payments
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
        // The whole ref: it is a UUIDv7, whose leading digits are only a timestamp, so a
        // truncated prefix made every checkout within the same minute share one order id
        // (and fail on the unique constraint).
        let gateway_order_id = format!("{prefix}-{order_ref}");
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
        // The signature check a real gateway would perform is missing entirely, so refuse
        // before any order lookup happens. Failing here keeps the row in `pending`, which
        // the reconciliation job can retry once a real integration exists.
        if !self.settles_payments {
            return Err(PaymentError::GatewayNotLive(self.kind));
        }

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

        // The amount a real gateway reports must match what we asked for. Without this,
        // a caller could pay 1 IQD and have a 50,000 IQD subscription activated.
        let amount_iqd = payload
            .get("amount_iqd")
            .and_then(|v| v.as_i64())
            .map(|v| i32::try_from(v).unwrap_or(-1));

        Ok(WebhookEvent {
            gateway_order_id,
            status,
            amount_iqd,
            raw_payload: payload,
        })
    }
}

pub fn all_stub_gateways(settles_payments: bool) -> Vec<(GatewayKind, StubGateway)> {
    vec![
        (
            GatewayKind::Fib,
            StubGateway::new(GatewayKind::Fib, settles_payments),
        ),
        (
            GatewayKind::Fastpay,
            StubGateway::new(GatewayKind::Fastpay, settles_payments),
        ),
        (
            GatewayKind::Superqi,
            StubGateway::new(GatewayKind::Superqi, settles_payments),
        ),
    ]
}

/// A stub that cannot settle payments. Prefer this over [`stub_for`] outside tests: the
/// permissive variant is a footgun if it is ever constructed on a production path.
pub fn stub_for(kind: GatewayKind) -> StubGateway {
    StubGateway::new(kind, false)
}

pub fn new_order_ref() -> String {
    Uuid::now_v7().simple().to_string()
}

#[cfg(test)]
mod tests {
    use super::*;
    use axum::http::HeaderMap;

    fn body(order: &str, status: &str, amount: Option<i32>) -> Vec<u8> {
        let mut v = serde_json::json!({ "order_id": order, "status": status });
        if let Some(a) = amount {
            v["amount_iqd"] = serde_json::json!(a);
        }
        serde_json::to_vec(&v).unwrap()
    }

    /// The core regression: a non-settling stub must never accept a webhook, because
    /// its "verification" is just `serde_json` on attacker-controlled bytes.
    #[test]
    fn non_settling_stub_rejects_unsigned_webhooks() {
        for kind in [GatewayKind::Fib, GatewayKind::Fastpay, GatewayKind::Superqi] {
            let gw = StubGateway::new(kind, false);
            assert!(!gw.settles_payments());
            for body in [
                body("FIB-ORD-deadbeef", "success", None),
                body("FIB-ORD-deadbeef", "success", Some(1)),
                body("FIB-ORD-deadbeef", "failed", None),
            ] {
                match gw.verify_webhook(&HeaderMap::new(), &body) {
                    Err(PaymentError::GatewayNotLive(k)) => assert_eq!(k, kind),
                    other => panic!("{kind:?} accepted an unsigned webhook: {other:?}"),
                }
            }
        }
    }

    /// `stub_for` is the constructor most likely to be reached from a prod path, so it
    /// must default to the safe variant.
    #[tokio::test]
    async fn order_ids_are_unique_back_to_back() {
        let gw = StubGateway::new(GatewayKind::Fib, true);
        let a = gw.create_intent(10_000, &new_order_ref()).await;
        let b = gw.create_intent(10_000, &new_order_ref()).await;
        assert_ne!(a.gateway_order_id, b.gateway_order_id);
    }

    #[test]
    fn stub_for_is_not_settling() {
        assert!(!stub_for(GatewayKind::Fib).settles_payments());
    }

    #[test]
    fn settling_stub_reports_amount_for_comparison() {
        let gw = StubGateway::new(GatewayKind::Fib, true);
        let ev = gw
            .verify_webhook(&HeaderMap::new(), &body("FIB-ORD-abc", "success", Some(25_000)))
            .expect("dev stub accepts webhooks");
        assert_eq!(ev.amount_iqd, Some(25_000));
        assert_eq!(ev.gateway_order_id, "FIB-ORD-abc");
        assert_eq!(ev.status, WebhookStatus::Success);
    }

    #[test]
    fn settling_stub_reports_absent_amount_as_none() {
        let gw = StubGateway::new(GatewayKind::Fib, true);
        let ev = gw
            .verify_webhook(&HeaderMap::new(), &body("FIB-ORD-abc", "success", None))
            .unwrap();
        assert_eq!(ev.amount_iqd, None);
    }

    #[test]
    fn settling_stub_still_requires_an_order_id() {
        let gw = StubGateway::new(GatewayKind::Fib, true);
        let raw = serde_json::to_vec(&serde_json::json!({ "status": "success" })).unwrap();
        assert!(matches!(
            gw.verify_webhook(&HeaderMap::new(), &raw),
            Err(PaymentError::BadPayload(_))
        ));
    }

    /// A gateway whose integration is missing must not be constructed as settling in prod.
    #[test]
    fn all_stub_gateways_propagate_the_flag() {
        assert!(all_stub_gateways(false).iter().all(|(_, g)| !g.settles_payments()));
        assert!(all_stub_gateways(true).iter().all(|(_, g)| g.settles_payments()));
    }
}
