//! The Qasa API client. Mirrors iOS `QasaAPIClient` + `QasaConfig`: JSON in and out, the
//! license token as a bearer, 401 → unauthorized, 403 `device_limit_exceeded` with its
//! device list, other errors carry the server's `error` code.

use std::time::Duration;

use reqwest::{Method, StatusCode};
use serde::de::DeserializeOwned;
use serde::Serialize;
use serde_json::Value;

/// The production API. Release builds may override it at build time with `QASA_API_URL`
/// (it must be https); debug builds read `QASA_API_URL` at run time and default to the
/// local dev server, like the iOS Debug configuration.
pub fn base_url() -> String {
    if cfg!(debug_assertions) {
        std::env::var("QASA_API_URL").unwrap_or_else(|_| "http://localhost:8080".into())
    } else {
        option_env!("QASA_API_URL").unwrap_or("https://api.qasa-app.com").to_string()
    }
}

/// iOS `QasaConfig.isBaseURLValid`: a release build must talk https.
pub fn base_url_is_valid(url: &str) -> bool {
    cfg!(debug_assertions) || url.starts_with("https://")
}

#[derive(Debug, Clone, Serialize)]
#[serde(rename_all = "camelCase", tag = "code")]
pub enum ApiError {
    /// No connection, DNS, TLS or timeout.
    Offline,
    Unauthorized,
    /// 403 `device_limit_exceeded`: the body has `devices_used`, `device_limit`, `devices`.
    DeviceLimit { body: Value },
    /// Another 4xx/5xx; `server` is the response's `error` code when it has one.
    Server { status: u16, server: Option<String> },
    /// An unexpected response body.
    Decode,
    /// A release build without an https API address (iOS shows a config banner).
    Misconfigured,
}

pub type ApiResult<T> = Result<T, ApiError>;

#[derive(Clone)]
pub struct ApiClient {
    base: String,
    http: reqwest::Client,
}

impl ApiClient {
    pub fn new(base: String) -> Self {
        let http = reqwest::Client::builder()
            .timeout(Duration::from_secs(20))
            .user_agent(concat!("Qasa-Desktop/", env!("CARGO_PKG_VERSION")))
            .build()
            .expect("an HTTP client with default TLS always builds");
        Self { base: base.trim_end_matches('/').to_string(), http }
    }

    pub async fn send<T: DeserializeOwned>(
        &self,
        method: Method,
        path: &str,
        token: Option<&str>,
        body: Option<&Value>,
    ) -> ApiResult<T> {
        let value = self.send_raw(method, path, token, body).await?;
        serde_json::from_value(value).map_err(|_| ApiError::Decode)
    }

    pub async fn send_raw(&self, method: Method, path: &str, token: Option<&str>, body: Option<&Value>) -> ApiResult<Value> {
        if !base_url_is_valid(&self.base) {
            return Err(ApiError::Misconfigured);
        }
        let mut req = self.http.request(method, format!("{}{}", self.base, path)).header("Accept", "application/json");
        if let Some(token) = token {
            req = req.bearer_auth(token);
        }
        if let Some(body) = body {
            req = req.json(body);
        }
        let resp = req.send().await.map_err(|_| ApiError::Offline)?;
        let status = resp.status();
        let bytes = resp.bytes().await.map_err(|_| ApiError::Offline)?;
        let json: Value = if bytes.is_empty() { Value::Null } else { serde_json::from_slice(&bytes).unwrap_or(Value::Null) };
        if status.is_success() {
            return Ok(json);
        }
        let code = json.get("error").and_then(Value::as_str).map(str::to_string);
        Err(match status {
            StatusCode::UNAUTHORIZED => ApiError::Unauthorized,
            StatusCode::FORBIDDEN if code.as_deref() == Some("device_limit_exceeded") => ApiError::DeviceLimit { body: json },
            _ => ApiError::Server { status: status.as_u16(), server: code },
        })
    }
}
