//! The account side of the desktop: identity, license, device registration, and the
//! server calls behind Linked devices, the inbox, Premium and the breached-password check.
//! Mirrors iOS `LicenseManager`, `DeviceRegistrationService`, `AppBootstrap` and
//! `PwnedPasswordsService`. The vault never touches the network; only this module does.

pub mod api;
pub mod commands;
pub mod license;
pub mod secrets;

use std::collections::HashMap;

use reqwest::Method;
use serde::{Deserialize, Serialize};
use serde_json::{json, Value};
use sha1::{Digest, Sha1};

use api::{ApiClient, ApiError, ApiResult};
use license::License;
use secrets::{Secret, SecretStore};

pub fn now_secs() -> i64 {
    std::time::SystemTime::now().duration_since(std::time::UNIX_EPOCH).map(|d| d.as_secs() as i64).unwrap_or(0)
}

/// iOS uses lowercase UUIDs for the account id and fingerprint.
fn new_lower_uuid() -> String {
    uuid::Uuid::new_v4().to_string()
}

/// What the UI shows about the account: plan, device slots, and why registration failed.
#[derive(Serialize, Clone, Debug, PartialEq)]
#[serde(rename_all = "camelCase")]
pub struct AccountStatus {
    pub account_id: Option<String>,
    pub device_id: Option<String>,
    pub is_premium: bool,
    pub tier: String,
    pub device_limit: u32,
    pub devices_used: u32,
    pub license: Option<License>,
    /// Set when the last registration hit the device limit: the server's body, with the
    /// device list the UI uses to offer an unlink.
    pub device_limit_error: Option<Value>,
    /// The last bootstrap could not reach the server.
    pub offline: bool,
    /// A release build without a valid https API address.
    pub misconfigured: bool,
}

#[derive(Deserialize)]
struct RegisterResponse {
    device_id: String,
    license_token: String,
    devices_used: u32,
    device_limit: u32,
}

#[derive(Deserialize)]
struct LicenseResponse {
    license_token: String,
}

#[derive(Deserialize)]
struct WellKnownKey {
    algorithm: String,
    public_key: String,
}

pub struct Account {
    pub api: ApiClient,
    secrets: Box<dyn SecretStore>,
    license: Option<License>,
    devices_used: u32,
    device_limit: u32,
    device_id: Option<String>,
    limit_error: Option<Value>,
    offline: bool,
    misconfigured: bool,
    /// Pwned Passwords ranges by SHA-1 prefix, for this unlocked session.
    breach_ranges: HashMap<String, HashMap<String, u64>>,
}

impl Account {
    pub fn new(api: ApiClient, secrets: Box<dyn SecretStore>) -> Self {
        let mut account = Self {
            api,
            secrets,
            license: None,
            devices_used: 0,
            device_limit: 1,
            device_id: None,
            limit_error: None,
            offline: false,
            misconfigured: false,
            breach_ranges: HashMap::new(),
        };
        account.reparse_license();
        account
    }

    fn verifying_key(&self) -> Option<[u8; 32]> {
        // A key pinned at build time wins and is never replaced by the server's.
        if let Some(pinned) = option_env!("QASA_LICENSE_PUBLIC_KEY") {
            return license::decode_key(pinned);
        }
        license::decode_key(&self.secrets.get(Secret::LicenseVerifyingKey)?)
    }

    fn reparse_license(&mut self) {
        self.license = match (self.secrets.get(Secret::LicenseToken), self.verifying_key()) {
            (Some(token), Some(key)) => license::verify(&token, &key, now_secs()),
            _ => None,
        };
    }

    /// iOS `applyToken`: store a token only once it verifies, so a bad one never replaces
    /// a good one.
    fn apply_token(&mut self, token: &str) -> bool {
        let Some(key) = self.verifying_key() else { return false };
        match license::verify(token, &key, now_secs()) {
            Some(parsed) => {
                self.secrets.set(Secret::LicenseToken, Some(token));
                self.license = Some(parsed);
                true
            }
            None => false,
        }
    }

    pub fn token(&self) -> Option<String> {
        self.secrets.get(Secret::LicenseToken)
    }

    pub fn account_id(&self) -> Option<String> {
        self.secrets.get(Secret::AccountId)
    }

    pub fn is_premium(&self) -> bool {
        self.license.as_ref().is_some_and(|l| l.is_premium(now_secs()))
    }

    pub fn status(&self) -> AccountStatus {
        AccountStatus {
            account_id: self.account_id(),
            device_id: self.device_id.clone().or_else(|| self.license.as_ref().map(|l| l.device_id.clone())),
            is_premium: self.is_premium(),
            tier: if self.is_premium() { "premium".into() } else { "free".into() },
            device_limit: self.license.as_ref().map_or(self.device_limit, |l| l.device_limit),
            devices_used: self.devices_used,
            license: self.license.clone(),
            device_limit_error: self.limit_error.clone(),
            offline: self.offline,
            misconfigured: self.misconfigured,
        }
    }

    /// Creates the account id and device fingerprint the first time (iOS does this when
    /// the vault is created). They live in the OS keychain, never in the vault file.
    pub fn ensure_identity(&self) {
        if self.secrets.get(Secret::DeviceFingerprint).is_none() {
            self.secrets.set(Secret::DeviceFingerprint, Some(&new_lower_uuid()));
        }
        if self.secrets.get(Secret::AccountId).is_none() {
            self.secrets.set(Secret::AccountId, Some(&new_lower_uuid()));
        }
    }

    /// A new computer joining an existing vault takes that vault's account (only from
    /// inside the authenticated sync channel) and starts with a fresh device fingerprint.
    pub fn adopt_account(&mut self, account_id: &str) {
        self.clear_identity();
        self.secrets.set(Secret::AccountId, Some(account_id));
        self.secrets.set(Secret::DeviceFingerprint, Some(&new_lower_uuid()));
    }

    /// Forgets the account on this computer ("Clear local data", account deletion).
    pub fn clear_identity(&mut self) {
        for secret in Secret::ALL {
            if secret != Secret::LicenseVerifyingKey {
                self.secrets.set(secret, None);
            }
        }
        self.license = None;
        self.device_id = None;
        self.limit_error = None;
        self.devices_used = 0;
        self.breach_ranges.clear();
    }

    /// Drops per-session caches when the vault locks.
    pub fn on_lock(&mut self) {
        self.breach_ranges.clear();
    }

    fn note(&mut self, result: &ApiResult<impl Sized>) {
        match result {
            Err(ApiError::Offline) => self.offline = true,
            Err(ApiError::Misconfigured) => self.misconfigured = true,
            _ => self.offline = false,
        }
    }

    pub async fn refresh_verifying_key(&mut self) -> bool {
        if option_env!("QASA_LICENSE_PUBLIC_KEY").is_some() {
            return self.verifying_key().is_some();
        }
        let result: ApiResult<WellKnownKey> = self.api.send(Method::GET, "/.well-known/qasa-license-key", None, None).await;
        self.note(&result);
        if let Ok(key) = result {
            if key.algorithm == "EdDSA" && license::decode_key(&key.public_key).is_some() {
                self.secrets.set(Secret::LicenseVerifyingKey, Some(&key.public_key));
            }
        }
        self.reparse_license();
        self.verifying_key().is_some()
    }

    /// `POST /v1/devices/register`. A device-limit answer is kept for the UI.
    pub async fn register(&mut self, device_name: &str, platform: &str, locale_region: Option<&str>) {
        let (Some(account_id), Some(fingerprint)) = (self.account_id(), self.secrets.get(Secret::DeviceFingerprint)) else {
            return;
        };
        let body = json!({
            "account_id": account_id,
            "device_fingerprint": fingerprint,
            "device_name": device_name,
            "platform": platform,
            "location": locale_region,
        });
        let result: ApiResult<RegisterResponse> = self.api.send(Method::POST, "/v1/devices/register", None, Some(&body)).await;
        self.note(&result);
        match result {
            Ok(r) => {
                self.apply_token(&r.license_token);
                self.devices_used = r.devices_used;
                self.device_limit = r.device_limit;
                self.device_id = Some(r.device_id);
                self.limit_error = None;
            }
            Err(ApiError::DeviceLimit { body }) => {
                self.device_limit = body.get("device_limit").and_then(Value::as_u64).unwrap_or(1) as u32;
                self.devices_used = body.get("devices_used").and_then(Value::as_u64).unwrap_or(self.device_limit as u64) as u32;
                self.limit_error = Some(body);
            }
            // Offline or a server error: the vault keeps working; the next unlock retries.
            Err(_) => {}
        }
    }

    /// `GET /v1/license`, retrying once after re-fetching the key (a rotated key).
    pub async fn refresh_license(&mut self) -> bool {
        let Some(token) = self.token() else { return false };
        if self.verifying_key().is_none() {
            self.refresh_verifying_key().await;
        }
        let result: ApiResult<LicenseResponse> = self.api.send(Method::GET, "/v1/license", Some(&token), None).await;
        self.note(&result);
        match result {
            Ok(r) => {
                if self.apply_token(&r.license_token) {
                    return true;
                }
                self.refresh_verifying_key().await;
                self.apply_token(&r.license_token)
            }
            Err(_) => false,
        }
    }

    /// `PATCH /v1/devices/prefs` with the UI language, so pushes and inbox text match it.
    pub async fn sync_locale(&mut self, locale: &str) {
        let Some(token) = self.token() else { return };
        let body = json!({ "locale": locale });
        let result = self.api.send_raw(Method::PATCH, "/v1/devices/prefs", Some(&token), Some(&body)).await;
        self.note(&result);
    }

    /// iOS `AppBootstrap.onVaultUnlocked`, after the vault is open: verifying key →
    /// register → refresh license → locale.
    pub async fn bootstrap(&mut self, device_name: &str, platform: &str, region: Option<&str>, locale: &str) -> AccountStatus {
        self.ensure_identity();
        self.offline = false;
        self.misconfigured = false;
        self.refresh_verifying_key().await;
        self.register(device_name, platform, region).await;
        if self.token().is_some() {
            self.refresh_license().await;
        }
        self.sync_locale(locale).await;
        self.status()
    }

    /// An authorized call for the account screens. 401 means the token is dead (unlinked,
    /// blocked or deleted), so the stored license is dropped, as on iOS.
    pub async fn call(&mut self, method: Method, path: &str, body: Option<&Value>) -> ApiResult<Value> {
        let token = self.token();
        let result = self.api.send_raw(method, path, token.as_deref(), body).await;
        self.note(&result);
        if matches!(result, Err(ApiError::Unauthorized)) {
            self.secrets.set(Secret::LicenseToken, None);
            self.license = None;
        }
        result
    }

    // MARK: breached passwords

    async fn range(&mut self, prefix: &str) -> Result<HashMap<String, u64>, ApiError> {
        if let Some(cached) = self.breach_ranges.get(prefix) {
            return Ok(cached.clone());
        }
        let url = format!("https://api.pwnedpasswords.com/range/{prefix}");
        let resp = reqwest::Client::new()
            .get(url)
            .header("Add-Padding", "true")
            .timeout(std::time::Duration::from_secs(15))
            .send()
            .await
            .map_err(|_| ApiError::Offline)?;
        if !resp.status().is_success() {
            return Err(ApiError::Server { status: resp.status().as_u16(), server: None });
        }
        let text = resp.text().await.map_err(|_| ApiError::Offline)?;
        let parsed = parse_range(&text);
        self.breach_ranges.insert(prefix.to_string(), parsed.clone());
        Ok(parsed)
    }

    /// iOS `PwnedPasswordsService.breachCounts`: only the first 5 hex characters of each
    /// password's SHA-1 leave the computer; matching happens here. Premium only.
    pub async fn breach_counts(&mut self, passwords: &[String]) -> Result<HashMap<String, u64>, ApiError> {
        let mut by_prefix: HashMap<String, Vec<(String, String)>> = HashMap::new();
        for password in passwords.iter().collect::<std::collections::BTreeSet<_>>() {
            let hex = sha1_hex(password);
            by_prefix.entry(hex[..5].to_string()).or_default().push((password.clone(), hex[5..].to_string()));
        }
        let mut counts = HashMap::new();
        for (prefix, entries) in by_prefix {
            let range = self.range(&prefix).await?;
            for (password, suffix) in entries {
                if let Some(&count) = range.get(&suffix) {
                    if count > 0 {
                        counts.insert(password, count);
                    }
                }
            }
        }
        Ok(counts)
    }
}

pub fn sha1_hex(password: &str) -> String {
    Sha1::digest(password.as_bytes()).iter().map(|b| format!("{b:02X}")).collect()
}

/// `SUFFIX:COUNT` lines. Padding rows have count 0 and are ignored by the caller.
pub fn parse_range(body: &str) -> HashMap<String, u64> {
    body.lines()
        .filter_map(|line| {
            let (suffix, count) = line.split_once(':')?;
            Some((suffix.trim().to_uppercase(), count.trim().parse().ok()?))
        })
        .collect()
}

#[cfg(test)]
mod tests {
    use super::*;
    use secrets::MemoryStore;

    fn account() -> Account {
        Account::new(ApiClient::new("http://127.0.0.1:9".into()), Box::new(MemoryStore::default()))
    }

    #[test]
    fn identity_is_created_once_and_cleared() {
        let mut a = account();
        a.ensure_identity();
        let id = a.account_id().unwrap();
        assert_eq!(id, id.to_lowercase());
        a.ensure_identity();
        assert_eq!(a.account_id().unwrap(), id);
        a.clear_identity();
        assert!(a.account_id().is_none());
    }

    #[test]
    fn tokens_apply_only_when_they_verify() {
        let mut a = account();
        let key = license::tests::signing_key().verifying_key().to_bytes();
        use base64::Engine;
        a.secrets.set(Secret::LicenseVerifyingKey, Some(&base64::engine::general_purpose::STANDARD.encode(key)));
        let good = license::tests::token(license::tests::claims("premium", now_secs() + 3600), "EdDSA");
        assert!(a.apply_token(&good));
        assert!(a.status().is_premium);
        // A bad token never replaces the good one.
        assert!(!a.apply_token("a.b.c"));
        assert_eq!(a.token().as_deref(), Some(good.as_str()));
        assert!(a.status().is_premium);
    }

    #[tokio::test]
    async fn offline_bootstrap_keeps_working() {
        let mut a = account();
        let status = a.bootstrap("Mac", "macos", None, "en").await;
        assert!(status.offline);
        assert!(!status.is_premium);
        assert!(status.account_id.is_some());
    }

    /// End to end against a running dev API (`make up` + `qasa-api`):
    /// `QASA_LIVE_API=http://localhost:8099 cargo test live_account -- --ignored --nocapture`
    #[tokio::test]
    #[ignore]
    async fn live_account_flow() {
        let base = std::env::var("QASA_LIVE_API").expect("set QASA_LIVE_API");
        let mut a = Account::new(ApiClient::new(base), Box::new(MemoryStore::default()));

        // Register as a new Free account.
        let status = a.bootstrap("Test Mac", "macos", None, "ckb").await;
        assert!(!status.offline, "API unreachable");
        assert!(status.license.is_some(), "license token did not verify");
        assert!(!status.is_premium);
        assert_eq!(status.device_limit, 1);
        println!("registered {:?} as device {:?}", status.account_id, status.device_id);

        let devices = a.call(Method::GET, "/v1/devices", None).await.unwrap();
        assert_eq!(devices["devices"].as_array().unwrap().len(), 1);
        let unread = a.call(Method::GET, "/v1/notifications/unread-count", None).await.unwrap();
        assert!(unread.get("unread_count").is_some());

        // Buy Premium through the stub wallet, the way the desktop does in development.
        let pricing = a.api.send_raw(Method::GET, "/v1/pricing", None, None).await.unwrap();
        let plan = &pricing["plans"][0];
        let intent = a
            .call(
                Method::POST,
                "/v1/payments/intents",
                Some(&json!({
                    "gateway": "fib", "plan_id": plan["id"], "auto_renew": false,
                    "amount_iqd": plan["amount_iqd"], "idempotency_key": new_lower_uuid(),
                })),
            )
            .await
            .unwrap();
        println!("intent {} → {}", intent["payment_id"], intent["deep_link"]);
        a.api
            .send_raw(
                Method::POST,
                "/v1/payments/webhook/fib",
                None,
                Some(&json!({ "order_id": intent["gateway_order_id"], "status": "success" })),
            )
            .await
            .unwrap();
        let paid = a.call(Method::GET, &format!("/v1/payments/{}", intent["payment_id"].as_str().unwrap()), None).await.unwrap();
        assert_eq!(paid["status"], "success");
        assert!(a.refresh_license().await);
        assert!(a.status().is_premium, "Premium did not unlock after payment");
        assert_eq!(a.status().device_limit, 5);

        let sub = a.call(Method::GET, "/v1/subscription", None).await.unwrap();
        assert_eq!(sub["subscription"]["status"], "active");

        // The real breach check (needs the internet).
        let counts = a.breach_counts(&["password".into(), "a-very-unlikely-qasa-test-passphrase-91f3".into()]).await;
        if let Ok(counts) = counts {
            assert!(counts.get("password").copied().unwrap_or(0) > 1000);
            assert!(!counts.contains_key("a-very-unlikely-qasa-test-passphrase-91f3"));
        }

        // Delete the account: the token dies.
        let account_id = a.account_id().unwrap();
        a.call(Method::DELETE, "/v1/account", Some(&json!({ "confirm_account_id": account_id }))).await.unwrap();
        assert!(matches!(a.call(Method::GET, "/v1/devices", None).await, Err(ApiError::Unauthorized)));
        assert!(!a.status().is_premium, "a dead token must drop Premium");
    }

    #[test]
    fn pwned_range_parsing() {
        assert_eq!(sha1_hex("password"), "5BAA61E4C9B93F3F0682250B6CF8331B7EE68FD8");
        let range = parse_range("1E4C9B93F3F0682250B6CF8331B7EE68FD8:9545824\r\nABCDEF:0\n");
        assert_eq!(range.get("1E4C9B93F3F0682250B6CF8331B7EE68FD8"), Some(&9545824));
        assert_eq!(range.get("ABCDEF"), Some(&0));
    }
}
