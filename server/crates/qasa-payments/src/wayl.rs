//! Wayl (https://wayl.io): a hosted checkout page that takes FIB, Qi Card and other
//! wallets, with one merchant account and one settlement.
//!
//! A payment is a link: we create it with our own `referenceId` (the payment's order id)
//! and send the customer to its URL. Wayl calls our webhook when the status changes.
//!
//! Wayl does not document the webhook body, only that it is signed: HMAC-SHA256 of the
//! raw body with the `webhookSecret` we gave the link, as bare hex in
//! `x-wayl-signature-256` (some of Wayl's own plugins read `x-wayl-signature`). So the
//! webhook is used only to learn *which* link changed. Its status and amount are then
//! read back from Wayl's API, which is authenticated with our key and documented.
//!
//! Wayl has no recurring charges ("each renewal has to be a new purchase the customer
//! completes through a new link"), so renewals go out as links too.

use std::time::Duration;

use axum::http::HeaderMap;
use hmac::{Hmac, Mac};
use serde::Deserialize;
use serde_json::json;
use sha2::Sha256;

use crate::gateway::{
    GatewayKind, IntentRequest, PaymentError, PaymentGateway, PaymentIntent, RecurringCharge,
    WebhookEvent, WebhookStatus,
};

pub const DEFAULT_BASE_URL: &str = "https://api.thewayl.com";
const SIGNATURE_HEADERS: [&str; 2] = ["x-wayl-signature-256", "x-wayl-signature"];
/// Wayl's own limits on a link's lifetime.
const MIN_LINK_MINUTES: u32 = 1;
const MAX_LINK_MINUTES: u32 = 30 * 24 * 60;
/// Wayl's minimum link total.
pub const MIN_TOTAL_IQD: i32 = 1_000;

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum WaylEnv {
    Live,
    /// Links that move no money. Only allowed outside production: a test link's webhook
    /// grants Premium like a real one.
    Test,
}

impl WaylEnv {
    fn as_str(self) -> &'static str {
        match self {
            Self::Live => "live",
            Self::Test => "test",
        }
    }
}

#[derive(Debug, Clone)]
pub struct WaylConfig {
    pub api_key: String,
    /// Sent with every link and used to check its webhooks. 10 to 255 characters.
    pub webhook_secret: String,
    /// Our `/v1/payments/webhook/wayl`, as Wayl can reach it: under `QASA_PUBLIC_API_URL`,
    /// or `https://{QASA_PUBLIC_HOST}`, the name the production Caddy serves.
    pub webhook_url: String,
    /// Where Wayl sends the customer after paying.
    pub redirect_url: Option<String>,
    pub env: WaylEnv,
    pub base_url: String,
}

impl WaylConfig {
    /// `None` when `QASA_WAYL_API_KEY` is unset. A key with missing or invalid settings
    /// is an error, so a half-configured gateway fails at startup instead of at checkout.
    pub fn from_env(allow_test_links: bool) -> anyhow::Result<Option<Self>> {
        let var = |name: &str| std::env::var(name).ok().filter(|v| !v.trim().is_empty());
        let Some(api_key) = var("QASA_WAYL_API_KEY") else {
            return Ok(None);
        };
        let webhook_secret = var("QASA_WAYL_WEBHOOK_SECRET")
            .ok_or_else(|| anyhow::anyhow!("QASA_WAYL_WEBHOOK_SECRET is required with QASA_WAYL_API_KEY"))?;
        if !(10..=255).contains(&webhook_secret.len()) {
            anyhow::bail!("QASA_WAYL_WEBHOOK_SECRET must be 10 to 255 characters");
        }
        let public_url = var("QASA_PUBLIC_API_URL")
            .or_else(|| var("QASA_PUBLIC_HOST").map(|host| format!("https://{host}")))
            .ok_or_else(|| {
                anyhow::anyhow!("QASA_PUBLIC_API_URL or QASA_PUBLIC_HOST is required with QASA_WAYL_API_KEY")
            })?;
        let env = match var("QASA_WAYL_ENV").as_deref().unwrap_or("live") {
            "live" => WaylEnv::Live,
            "test" if allow_test_links => WaylEnv::Test,
            "test" => anyhow::bail!("QASA_WAYL_ENV=test is not allowed in production"),
            other => anyhow::bail!("QASA_WAYL_ENV must be live or test, not {other:?}"),
        };
        Ok(Some(Self {
            api_key,
            webhook_secret,
            webhook_url: format!("{}/v1/payments/webhook/wayl", public_url.trim_end_matches('/')),
            redirect_url: var("QASA_WAYL_REDIRECT_URL"),
            env,
            base_url: var("QASA_WAYL_BASE_URL").unwrap_or_else(|| DEFAULT_BASE_URL.to_string()),
        }))
    }
}

pub struct WaylGateway {
    config: WaylConfig,
    http: reqwest::Client,
}

impl WaylGateway {
    pub fn new(config: WaylConfig) -> Self {
        let http = reqwest::Client::builder()
            .timeout(Duration::from_secs(15))
            .build()
            .expect("reqwest client");
        Self { config, http }
    }

    fn url(&self, path: &str) -> String {
        format!("{}{path}", self.config.base_url.trim_end_matches('/'))
    }

    async fn get_link(&self, reference_id: &str) -> Result<Option<Link>, PaymentError> {
        let res = self
            .http
            .get(self.url(&format!("/api/v1/links/{}", encode_path(reference_id))))
            .header("X-WAYL-AUTHENTICATION", &self.config.api_key)
            .send()
            .await
            .map_err(|e| PaymentError::Gateway(e.to_string()))?;
        if res.status() == reqwest::StatusCode::NOT_FOUND {
            return Ok(None);
        }
        let body: Envelope = read(res).await?;
        Ok(Some(body.data))
    }

    fn event_for(&self, link: &Link, raw_payload: serde_json::Value) -> WebhookEvent {
        WebhookEvent {
            gateway_order_id: link.reference_id.clone(),
            status: link_status(&link.status),
            amount_iqd: Some(parse_total(&link.total)),
            payment_token: None,
            raw_payload,
        }
    }
}

#[async_trait::async_trait]
impl PaymentGateway for WaylGateway {
    fn kind(&self) -> GatewayKind {
        GatewayKind::Wayl
    }

    fn is_live(&self) -> bool {
        true
    }

    async fn create_intent(&self, req: &IntentRequest<'_>) -> Result<PaymentIntent, PaymentError> {
        if req.amount_iqd < MIN_TOTAL_IQD {
            return Err(PaymentError::BadPayload(format!(
                "Wayl links must be at least {MIN_TOTAL_IQD} IQD"
            )));
        }
        let body = link_request(&self.config, req);
        let res = self
            .http
            .post(self.url("/api/v1/links"))
            .header("X-WAYL-AUTHENTICATION", &self.config.api_key)
            .json(&body)
            .send()
            .await
            .map_err(|e| PaymentError::Gateway(e.to_string()))?;
        let created: Envelope = read(res).await?;
        Ok(PaymentIntent {
            gateway_order_id: created.data.reference_id,
            deep_link: created.data.url,
            amount_iqd: req.amount_iqd,
        })
    }

    async fn verify_webhook(&self, headers: &HeaderMap, body: &[u8]) -> Result<WebhookEvent, PaymentError> {
        let signature = SIGNATURE_HEADERS
            .iter()
            .find_map(|h| headers.get(*h))
            .and_then(|v| v.to_str().ok())
            .ok_or(PaymentError::InvalidSignature)?;
        if !signature_matches(&self.config.webhook_secret, body, signature) {
            return Err(PaymentError::InvalidSignature);
        }

        let payload: serde_json::Value = serde_json::from_slice(body)
            .map_err(|e| PaymentError::BadPayload(e.to_string()))?;
        let reference_id = payload
            .get("referenceId")
            .and_then(|v| v.as_str())
            .ok_or_else(|| PaymentError::BadPayload("missing referenceId".into()))?
            .to_string();

        // The signature says Wayl sent it; the API says what actually happened. If Wayl's
        // API cannot be reached, keep the order pending: the poll job asks again.
        match self.get_link(&reference_id).await {
            Ok(Some(link)) if link.reference_id == reference_id => Ok(self.event_for(&link, payload)),
            Ok(_) => Err(PaymentError::BadPayload(format!("no Wayl link {reference_id}"))),
            Err(_) => Ok(WebhookEvent {
                gateway_order_id: reference_id,
                status: WebhookStatus::Pending,
                amount_iqd: None,
                payment_token: None,
                raw_payload: payload,
            }),
        }
    }

    async fn fetch_status(&self, gateway_order_id: &str) -> Result<Option<WebhookEvent>, PaymentError> {
        let link = self.get_link(gateway_order_id).await?;
        Ok(link.map(|l| {
            let raw = json!({ "source": "poll", "referenceId": l.reference_id, "status": l.status, "total": l.total });
            self.event_for(&l, raw)
        }))
    }

    fn supports_recurring(&self) -> bool {
        false
    }

    fn supports_renewal_links(&self) -> bool {
        true
    }

    async fn charge_saved(&self, _: &str, _: i32, _: &str) -> Result<RecurringCharge, PaymentError> {
        Err(PaymentError::RecurringUnsupported(GatewayKind::Wayl))
    }
}

#[derive(Debug, Deserialize)]
struct Envelope {
    data: Link,
}

#[derive(Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
struct Link {
    reference_id: String,
    #[serde(default)]
    url: String,
    /// A string in Wayl's responses ("5000"), though the request takes a number.
    total: serde_json::Value,
    status: String,
}

async fn read<T: serde::de::DeserializeOwned>(res: reqwest::Response) -> Result<T, PaymentError> {
    let status = res.status();
    let text = res.text().await.map_err(|e| PaymentError::Gateway(e.to_string()))?;
    if !status.is_success() {
        let snippet: String = text.chars().take(300).collect();
        return Err(PaymentError::Gateway(format!("Wayl answered {status}: {snippet}")));
    }
    serde_json::from_str(&text).map_err(|e| PaymentError::Gateway(format!("unexpected Wayl response: {e}")))
}

fn link_request(config: &WaylConfig, req: &IntentRequest<'_>) -> serde_json::Value {
    let mut body = json!({
        "env": config.env.as_str(),
        "referenceId": req.order_ref,
        "total": req.amount_iqd,
        "currency": "IQD",
        "lineItem": [{ "label": req.label, "amount": req.amount_iqd, "type": "increase" }],
        "webhookUrl": config.webhook_url,
        "webhookSecret": config.webhook_secret,
        "linkExpiresIn": link_expires_in(req.expires_in_minutes),
    });
    if let Some(url) = &config.redirect_url {
        body["redirectionUrl"] = json!(url);
    }
    body
}

/// Wayl takes a number and one unit (m, h or d), between 1m and 30d.
fn link_expires_in(minutes: u32) -> String {
    let minutes = minutes.clamp(MIN_LINK_MINUTES, MAX_LINK_MINUTES);
    if minutes.is_multiple_of(24 * 60) {
        format!("{}d", minutes / (24 * 60))
    } else if minutes.is_multiple_of(60) {
        format!("{}h", minutes / 60)
    } else {
        format!("{minutes}m")
    }
}

/// Wayl's link statuses. It documents the names only, so this is the cautious reading:
/// only Complete and Delivered count as paid.
fn link_status(status: &str) -> WebhookStatus {
    match status {
        "Complete" | "Delivered" => WebhookStatus::Success,
        "Cancelled" | "Rejected" | "Returned" => WebhookStatus::Failed,
        _ => WebhookStatus::Pending,
    }
}

/// An amount we cannot read is reported as -1, which never matches the charged amount,
/// so nothing is granted on it.
fn parse_total(total: &serde_json::Value) -> i32 {
    let value = match total {
        serde_json::Value::Number(n) => n.as_f64(),
        serde_json::Value::String(s) => s.trim().parse::<f64>().ok(),
        _ => None,
    };
    match value {
        Some(v) if v.fract() == 0.0 && v >= 0.0 && v <= i32::MAX as f64 => v as i32,
        _ => -1,
    }
}

fn signature_matches(secret: &str, body: &[u8], signature: &str) -> bool {
    let Ok(expected) = hex::decode(signature.trim()) else {
        return false;
    };
    let mut mac = Hmac::<Sha256>::new_from_slice(secret.as_bytes()).expect("HMAC takes any key");
    mac.update(body);
    mac.verify_slice(&expected).is_ok()
}

/// Our references are hex ids, but never trust that in a URL path.
fn encode_path(segment: &str) -> String {
    segment
        .bytes()
        .map(|b| match b {
            b'A'..=b'Z' | b'a'..=b'z' | b'0'..=b'9' | b'-' | b'_' | b'.' => (b as char).to_string(),
            _ => format!("%{b:02X}"),
        })
        .collect()
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::sync::{Arc, Mutex};

    use axum::extract::{Path, State};
    use axum::routing::{get, post};
    use axum::{Json, Router};

    const SECRET: &str = "test-webhook-secret";

    fn sign(body: &[u8]) -> String {
        let mut mac = Hmac::<Sha256>::new_from_slice(SECRET.as_bytes()).unwrap();
        mac.update(body);
        hex::encode(mac.finalize().into_bytes())
    }

    fn config(base_url: String) -> WaylConfig {
        WaylConfig {
            api_key: "key".into(),
            webhook_secret: SECRET.into(),
            webhook_url: "https://api.example.test/v1/payments/webhook/wayl".into(),
            redirect_url: None,
            env: WaylEnv::Test,
            base_url,
        }
    }

    /// A stand-in for Wayl's API: remembers created links and serves them back.
    #[derive(Clone, Default)]
    struct FakeWayl {
        links: Arc<Mutex<Vec<serde_json::Value>>>,
    }

    async fn fake_wayl() -> (FakeWayl, String) {
        let fake = FakeWayl::default();
        let app = Router::new()
            .route(
                "/api/v1/links",
                post(|State(f): State<FakeWayl>, Json(body): Json<serde_json::Value>| async move {
                    let link = json!({
                        "referenceId": body["referenceId"],
                        "url": format!("https://link.thewayl.com/pay/{}", body["referenceId"].as_str().unwrap()),
                        "total": body["total"].to_string(),
                        "status": "Created",
                        "request": body,
                    });
                    f.links.lock().unwrap().push(link.clone());
                    (axum::http::StatusCode::CREATED, Json(json!({ "data": link, "message": "ok" })))
                }),
            )
            .route(
                "/api/v1/links/{reference}",
                get(|State(f): State<FakeWayl>, Path(reference): Path<String>| async move {
                    let links = f.links.lock().unwrap();
                    match links.iter().find(|l| l["referenceId"] == reference.as_str()) {
                        Some(l) => Ok(Json(json!({ "data": l }))),
                        None => Err(axum::http::StatusCode::NOT_FOUND),
                    }
                }),
            )
            .with_state(fake.clone());
        let listener = tokio::net::TcpListener::bind("127.0.0.1:0").await.unwrap();
        let addr = listener.local_addr().unwrap();
        tokio::spawn(async move { axum::serve(listener, app).await.unwrap() });
        (fake, format!("http://{addr}"))
    }

    impl FakeWayl {
        fn set_status(&self, reference: &str, status: &str, total: &str) {
            let mut links = self.links.lock().unwrap();
            let link = links.iter_mut().find(|l| l["referenceId"] == reference).unwrap();
            link["status"] = json!(status);
            link["total"] = json!(total);
        }
    }

    fn headers(signature: &str) -> HeaderMap {
        let mut h = HeaderMap::new();
        h.insert("x-wayl-signature-256", signature.parse().unwrap());
        h
    }

    fn intent(order_ref: &str, minutes: u32) -> IntentRequest<'_> {
        IntentRequest {
            amount_iqd: 5_000,
            order_ref,
            label: "Qasa Premium (monthly)",
            save_method: false,
            expires_in_minutes: minutes,
        }
    }

    #[test]
    fn signature_is_hmac_sha256_hex_of_the_raw_body() {
        let body = br#"{"referenceId":"abc","paymentStatus":"Paid"}"#;
        assert!(signature_matches(SECRET, body, &sign(body)));
        assert!(signature_matches(SECRET, body, &sign(body).to_uppercase()));
        assert!(!signature_matches(SECRET, body, &sign(b"{}")));
        assert!(!signature_matches("another-secret", body, &sign(body)));
        assert!(!signature_matches(SECRET, body, "not hex"));
        assert!(!signature_matches(SECRET, body, ""));
    }

    #[test]
    fn only_complete_and_delivered_count_as_paid() {
        assert_eq!(link_status("Complete"), WebhookStatus::Success);
        assert_eq!(link_status("Delivered"), WebhookStatus::Success);
        for s in ["Cancelled", "Rejected", "Returned"] {
            assert_eq!(link_status(s), WebhookStatus::Failed);
        }
        for s in ["Created", "Pending", "Processing", "Paid", ""] {
            assert_eq!(link_status(s), WebhookStatus::Pending, "{s}");
        }
    }

    #[test]
    fn totals_parse_from_strings_and_numbers() {
        assert_eq!(parse_total(&json!("5000")), 5_000);
        assert_eq!(parse_total(&json!("5000.00")), 5_000);
        assert_eq!(parse_total(&json!(50000)), 50_000);
        assert_eq!(parse_total(&json!("5000.5")), -1);
        assert_eq!(parse_total(&json!("abc")), -1);
        assert_eq!(parse_total(&json!(null)), -1);
    }

    #[test]
    fn link_lifetimes_use_the_largest_whole_unit_within_limits() {
        assert_eq!(link_expires_in(60), "1h");
        assert_eq!(link_expires_in(90), "90m");
        assert_eq!(link_expires_in(4 * 24 * 60), "4d");
        assert_eq!(link_expires_in(0), "1m");
        assert_eq!(link_expires_in(90 * 24 * 60), "30d");
    }

    #[test]
    fn link_request_carries_our_reference_and_secret() {
        let cfg = config("http://unused".into());
        let body = link_request(&cfg, &intent("0193abc", 60));
        assert_eq!(body["referenceId"], "0193abc");
        assert_eq!(body["total"], 5_000);
        assert_eq!(body["env"], "test");
        assert_eq!(body["webhookSecret"], SECRET);
        assert_eq!(body["lineItem"][0]["amount"], 5_000);
        assert_eq!(body["linkExpiresIn"], "1h");
        assert!(body.get("redirectionUrl").is_none());
    }

    #[tokio::test]
    async fn webhook_status_comes_from_the_api_not_the_body() {
        let (fake, base) = fake_wayl().await;
        let gw = WaylGateway::new(config(base));
        let created = gw.create_intent(&intent("ref-1", 60)).await.unwrap();
        assert_eq!(created.gateway_order_id, "ref-1");
        assert!(created.deep_link.ends_with("/pay/ref-1"));

        // The body claims it is paid; Wayl's API still says Created.
        let body = br#"{"referenceId":"ref-1","paymentStatus":"Paid"}"#;
        let ev = gw.verify_webhook(&headers(&sign(body)), body).await.unwrap();
        assert_eq!(ev.status, WebhookStatus::Pending);

        fake.set_status("ref-1", "Complete", "5000");
        let ev = gw.verify_webhook(&headers(&sign(body)), body).await.unwrap();
        assert_eq!(ev.status, WebhookStatus::Success);
        assert_eq!(ev.amount_iqd, Some(5_000));
        assert_eq!(ev.gateway_order_id, "ref-1");
        assert_eq!(ev.payment_token, None);

        let polled = gw.fetch_status("ref-1").await.unwrap().unwrap();
        assert_eq!(polled.status, WebhookStatus::Success);
        assert!(gw.fetch_status("missing").await.unwrap().is_none());
    }

    #[tokio::test]
    async fn unsigned_or_unknown_webhooks_are_refused() {
        let (_fake, base) = fake_wayl().await;
        let gw = WaylGateway::new(config(base));
        let body = br#"{"referenceId":"ref-x"}"#;
        assert!(matches!(
            gw.verify_webhook(&HeaderMap::new(), body).await,
            Err(PaymentError::InvalidSignature)
        ));
        assert!(matches!(
            gw.verify_webhook(&headers(&sign(b"other")), body).await,
            Err(PaymentError::InvalidSignature)
        ));
        assert!(matches!(
            gw.verify_webhook(&headers(&sign(body)), body).await,
            Err(PaymentError::BadPayload(_))
        ));
    }

    #[tokio::test]
    async fn an_unreachable_api_leaves_the_order_pending() {
        // Nothing listens on this port.
        let gw = WaylGateway::new(config("http://127.0.0.1:9".into()));
        let body = br#"{"referenceId":"ref-2"}"#;
        let ev = gw.verify_webhook(&headers(&sign(body)), body).await.unwrap();
        assert_eq!(ev.status, WebhookStatus::Pending);
        assert_eq!(ev.amount_iqd, None);
    }

    #[tokio::test]
    async fn wayl_cannot_charge_saved_methods() {
        let gw = WaylGateway::new(config("http://unused".into()));
        assert!(!gw.supports_recurring());
        assert!(gw.supports_renewal_links());
        assert!(matches!(
            gw.charge_saved("tok", 5_000, "ref").await,
            Err(PaymentError::RecurringUnsupported(GatewayKind::Wayl))
        ));
        assert!(gw.create_intent(&IntentRequest { amount_iqd: 999, ..intent("r", 60) }).await.is_err());
    }
}
