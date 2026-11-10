//! The account side of the desktop: identity, license, device registration, and the
//! server calls behind Linked devices, the inbox, Premium and the breached-password check.
//! Mirrors iOS `LicenseManager`, `DeviceRegistrationService`, `AppBootstrap` and
//! `PwnedPasswordsService`. The vault never touches the network; only this module does.

pub mod api;
pub mod commands;
pub mod license;
pub mod secrets;

use std::collections::HashMap;
use std::time::Duration;

use reqwest::Method;
use serde::{Deserialize, Serialize};
use serde_json::{json, Value};
use base64::Engine;
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

const B64: base64::engine::GeneralPurpose = base64::engine::general_purpose::STANDARD;

/// The restore-key `PUT` must never hold a backup up for long.
const RESTORE_KEY_TIMEOUT: Duration = Duration::from_secs(5);

fn decode_restore_key(b64: &str) -> Option<[u8; qasa_backup::RESTORE_KEY_LEN]> {
    B64.decode(b64).ok()?.try_into().ok()
}

/// Whether the next backup makes a new restore key: only with a license token (the `PUT`
/// needs one), and only when there is no key yet or the current one is already in a
/// written backup file. A key whose file was never written is reused, so the file on disk
/// always holds the current or the previous hash the server keeps.
pub fn should_rotate_restore_key(has_token: bool, has_key: bool, written: bool) -> bool {
    has_token && (!has_key || written)
}

/// `PUT /v1/devices/me/restore-key` with base64(SHA-256(key)). Short timeout; the caller
/// only logs a failure.
pub async fn put_restore_key_hash(api: &ApiClient, token: &str, key: &[u8; qasa_backup::RESTORE_KEY_LEN]) -> ApiResult<()> {
    let body = json!({ "key_hash": B64.encode(qasa_backup::restore_key_hash(key)) });
    api.send_raw_within(Method::PUT, "/v1/devices/me/restore-key", Some(token), Some(&body), Some(RESTORE_KEY_TIMEOUT)).await?;
    Ok(())
}

/// The `POST /v1/devices/register` body. A restored backup's key goes along as
/// `restore_key`; `replace_device` asks the server to unlink the device that made it.
fn register_body(
    account_id: &str,
    fingerprint: &str,
    device_name: &str,
    platform: &str,
    location: Option<&str>,
    restore_key: Option<&[u8]>,
    replace_device: bool,
) -> Value {
    let mut body = json!({
        "account_id": account_id,
        "device_fingerprint": fingerprint,
        "device_name": device_name,
        "platform": platform,
        "location": location,
    });
    if let Some(key) = restore_key {
        body["restore_key"] = json!(B64.encode(key));
        if replace_device {
            body["replace_device"] = json!(true);
        }
    }
    body
}

/// What a device-limit 403 says about the restore key: its status (`unknown`,
/// `not_enough`, `replaceable`, or `None` when none was sent) and, when replaceable, the
/// old device's name. Both fields are taken out of the body the UI keeps.
#[derive(Debug, Default, Clone, PartialEq, Eq)]
pub struct RestoreKeyLimit {
    pub status: Option<String>,
    pub replaceable_device_name: Option<String>,
}

fn take_restore_key_limit(body: &mut Value) -> RestoreKeyLimit {
    let Some(map) = body.as_object_mut() else { return RestoreKeyLimit::default() };
    let status = map.remove("restore_key").and_then(|v| v.as_str().map(str::to_string));
    let device = map.remove("replaceable_device");
    let replaceable_device_name = match status.as_deref() {
        Some("replaceable") => device.as_ref().and_then(|d| d.get("device_name")).and_then(Value::as_str).map(str::to_string),
        _ => None,
    };
    RestoreKeyLimit { status, replaceable_device_name }
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
    /// With a device-limit error after restoring a backup: `unknown`, `not_enough` or
    /// `replaceable` (the server's `restore_key`), else `None`.
    pub restore_key_status: Option<String>,
    /// When replaceable: the name of the device that made the backup.
    pub replaceable_device_name: Option<String>,
    /// Set once, right after a registration replaced the backup's device: its name.
    pub replaced_device_name: Option<String>,
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
    #[serde(default)]
    replaced_device: Option<ReplacedDevice>,
}

#[derive(Deserialize)]
struct ReplacedDevice {
    #[serde(default)]
    device_name: String,
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
    restore_limit: RestoreKeyLimit,
    /// The replaced device's name, until a status carries it to the UI once.
    replaced_notice: Option<String>,
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
            restore_limit: RestoreKeyLimit::default(),
            replaced_notice: None,
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
            restore_key_status: self.restore_limit.status.clone(),
            replaceable_device_name: self.restore_limit.replaceable_device_name.clone(),
            replaced_device_name: self.replaced_notice.clone(),
            offline: self.offline,
            misconfigured: self.misconfigured,
        }
    }

    /// The status, handing the "device replaced" notice to the UI only once.
    pub fn take_status(&mut self) -> AccountStatus {
        let status = self.status();
        self.replaced_notice = None;
        status
    }

    // MARK: backup restore key

    /// This device's current restore key, whose hash the server confirmed.
    pub fn restore_key(&self) -> Option<[u8; qasa_backup::RESTORE_KEY_LEN]> {
        decode_restore_key(&self.secrets.get(Secret::RestoreKey)?)
    }

    fn restore_key_written(&self) -> bool {
        self.secrets.get(Secret::RestoreKeyWritten).as_deref() == Some("1")
    }

    /// The license token to send a new key's hash with, when the next backup should
    /// rotate the key ([`should_rotate_restore_key`]); `None` to keep the current one.
    pub fn restore_key_rotation(&self) -> Option<String> {
        let token = self.token()?;
        should_rotate_restore_key(true, self.restore_key().is_some(), self.restore_key_written()).then_some(token)
    }

    /// The server took `key`'s hash: it becomes the current key, not yet in a file.
    pub fn confirm_restore_key(&self, key: &[u8; qasa_backup::RESTORE_KEY_LEN]) {
        self.secrets.set(Secret::RestoreKey, Some(&B64.encode(key)));
        self.secrets.set(Secret::RestoreKeyWritten, None);
    }

    /// A backup file holding `key` was written (ignored if the key changed meanwhile).
    pub fn mark_restore_key_written(&self, key: &[u8; qasa_backup::RESTORE_KEY_LEN]) {
        if self.restore_key().as_ref() == Some(key) {
            self.secrets.set(Secret::RestoreKeyWritten, Some("1"));
        }
    }

    /// The key of a backup this device just adopted, for the registration that follows.
    pub fn set_pending_restore_key(&self, key: &[u8]) {
        if key.len() == qasa_backup::RESTORE_KEY_LEN {
            self.secrets.set(Secret::PendingRestoreKey, Some(&B64.encode(key)));
        }
    }

    fn pending_restore_key(&self) -> Option<[u8; qasa_backup::RESTORE_KEY_LEN]> {
        decode_restore_key(&self.secrets.get(Secret::PendingRestoreKey)?)
    }

    #[cfg(test)]
    pub fn pending_restore_key_for_tests(&self) -> Option<[u8; qasa_backup::RESTORE_KEY_LEN]> {
        self.pending_restore_key()
    }

    /// Local vault reset: the restore secrets go with the vault.
    pub fn clear_restore_keys(&mut self) {
        for secret in Secret::RESTORE {
            self.secrets.set(secret, None);
        }
        self.restore_limit = RestoreKeyLimit::default();
        self.replaced_notice = None;
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
        self.restore_limit = RestoreKeyLimit::default();
        self.replaced_notice = None;
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

    /// `POST /v1/devices/register`. A device-limit answer is kept for the UI. A restored
    /// backup's key goes along until a registration succeeds; with `replace_device` the
    /// server unlinks the device that made that backup.
    pub async fn register(&mut self, device_name: &str, platform: &str, locale_region: Option<&str>, replace_device: bool) -> ApiResult<()> {
        let (Some(account_id), Some(fingerprint)) = (self.account_id(), self.secrets.get(Secret::DeviceFingerprint)) else {
            return Err(ApiError::Decode);
        };
        let pending = self.pending_restore_key();
        let body = register_body(
            &account_id,
            &fingerprint,
            device_name,
            platform,
            locale_region,
            pending.as_ref().map(|k| k.as_slice()),
            replace_device,
        );
        let result: ApiResult<RegisterResponse> = self.api.send(Method::POST, "/v1/devices/register", None, Some(&body)).await;
        self.note(&result);
        match result {
            Ok(r) => {
                self.apply_token(&r.license_token);
                self.devices_used = r.devices_used;
                self.device_limit = r.device_limit;
                self.device_id = Some(r.device_id);
                self.limit_error = None;
                self.restore_limit = RestoreKeyLimit::default();
                self.secrets.set(Secret::PendingRestoreKey, None);
                if let Some(replaced) = r.replaced_device {
                    self.replaced_notice = Some(replaced.device_name);
                }
                Ok(())
            }
            Err(ApiError::DeviceLimit { mut body }) => {
                self.device_limit = body.get("device_limit").and_then(Value::as_u64).unwrap_or(1) as u32;
                self.devices_used = body.get("devices_used").and_then(Value::as_u64).unwrap_or(self.device_limit as u64) as u32;
                self.restore_limit = take_restore_key_limit(&mut body);
                self.limit_error = Some(body.clone());
                Err(ApiError::DeviceLimit { body })
            }
            // Offline or a server error: the vault keeps working; the next unlock retries.
            Err(e) => Err(e),
        }
    }

    /// Device limit → "Unlink and continue": register again with the restored backup's
    /// key and `replace_device`, then carry on like any successful registration.
    pub async fn replace_device(&mut self, device_name: &str, platform: &str, region: Option<&str>, locale: &str) -> ApiResult<AccountStatus> {
        if self.pending_restore_key().is_none() {
            return Err(ApiError::Decode);
        }
        self.register(device_name, platform, region, true).await?;
        if self.token().is_some() {
            self.refresh_license().await;
        }
        self.sync_locale(locale).await;
        Ok(self.take_status())
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
        let _ = self.register(device_name, platform, region, false).await;
        if self.token().is_some() {
            self.refresh_license().await;
        }
        self.sync_locale(locale).await;
        self.take_status()
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

    /// End to end against a running dev API (`make db` + `qasa-api`):
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
    fn restore_key_rotation_rules() {
        // No key → rotate; written → rotate; not yet written → reuse; never without a token.
        assert!(should_rotate_restore_key(true, false, false));
        assert!(should_rotate_restore_key(true, true, true));
        assert!(!should_rotate_restore_key(true, true, false));
        assert!(!should_rotate_restore_key(false, false, false));
        assert!(!should_rotate_restore_key(false, true, true));

        let a = account();
        assert_eq!(a.restore_key_rotation(), None, "no license token: no rotation");
        a.secrets.set(Secret::LicenseToken, Some("tok"));
        assert_eq!(a.restore_key_rotation().as_deref(), Some("tok"), "no key yet");
        let key = [7u8; 32];
        a.confirm_restore_key(&key);
        assert_eq!(a.restore_key(), Some(key));
        assert_eq!(a.restore_key_rotation(), None, "the key isn't in a file yet: reuse it");
        a.mark_restore_key_written(&[8u8; 32]);
        assert_eq!(a.restore_key_rotation(), None, "another key's file doesn't count");
        a.mark_restore_key_written(&key);
        assert_eq!(a.restore_key_rotation().as_deref(), Some("tok"), "written: rotate");
        a.confirm_restore_key(&[9u8; 32]);
        assert_eq!(a.restore_key(), Some([9u8; 32]));
        assert!(!a.restore_key_written());
    }

    /// A failed `PUT` (here: nothing listening) is an error the caller keeps the old key on.
    #[tokio::test]
    async fn restore_key_put_failure_is_reported() {
        let api = ApiClient::new("http://127.0.0.1:9".into());
        assert!(matches!(put_restore_key_hash(&api, "tok", &[1u8; 32]).await, Err(ApiError::Offline)));
    }

    #[test]
    fn register_body_with_and_without_a_restore_key() {
        let plain = register_body("acc", "fp", "Mac", "macos", None, None, true);
        assert!(plain.get("restore_key").is_none());
        assert!(plain.get("replace_device").is_none());
        assert_eq!(plain["account_id"], "acc");

        let key = [3u8; 32];
        let with_key = register_body("acc", "fp", "Mac", "macos", None, Some(&key), false);
        assert_eq!(B64.decode(with_key["restore_key"].as_str().unwrap()).unwrap(), key);
        assert!(with_key.get("replace_device").is_none());
        let replace = register_body("acc", "fp", "Mac", "macos", None, Some(&key), true);
        assert_eq!(replace["replace_device"], true);
    }

    #[test]
    fn device_limit_restore_key_fields() {
        let mut body = json!({
            "error": "device_limit_exceeded", "device_limit": 1, "devices_used": 1, "devices": [],
            "restore_key": "replaceable",
            "replaceable_device": { "id": "d1", "device_name": "Old Mac", "platform": "macos" },
        });
        let limit = take_restore_key_limit(&mut body);
        assert_eq!(limit.status.as_deref(), Some("replaceable"));
        assert_eq!(limit.replaceable_device_name.as_deref(), Some("Old Mac"));
        assert!(body.get("replaceable_device").is_none() && body.get("restore_key").is_none());
        assert_eq!(body["device_limit"], 1);

        for status in ["unknown", "not_enough"] {
            let mut body = json!({ "restore_key": status });
            assert_eq!(take_restore_key_limit(&mut body), RestoreKeyLimit { status: Some(status.into()), replaceable_device_name: None });
        }
        let mut none = json!({ "restore_key": null, "device_limit": 1 });
        assert_eq!(take_restore_key_limit(&mut none), RestoreKeyLimit::default());
        assert_eq!(take_restore_key_limit(&mut json!({})), RestoreKeyLimit::default());
    }

    #[test]
    fn resets_clear_the_restore_secrets() {
        let mut a = account();
        a.ensure_identity();
        a.confirm_restore_key(&[1u8; 32]);
        a.mark_restore_key_written(&[1u8; 32]);
        a.set_pending_restore_key(&[2u8; 32]);
        a.set_pending_restore_key(&[2u8; 5]);
        assert_eq!(a.pending_restore_key(), Some([2u8; 32]), "a short key is ignored");
        a.clear_restore_keys();
        for secret in Secret::RESTORE {
            assert!(a.secrets.get(secret).is_none(), "{secret:?}");
        }
        assert!(a.account_id().is_some(), "a vault reset keeps the account");

        a.confirm_restore_key(&[1u8; 32]);
        a.set_pending_restore_key(&[2u8; 32]);
        a.clear_identity();
        for secret in Secret::RESTORE {
            assert!(a.secrets.get(secret).is_none(), "{secret:?}");
        }
    }

    #[test]
    fn pwned_range_parsing() {
        assert_eq!(sha1_hex("password"), "5BAA61E4C9B93F3F0682250B6CF8331B7EE68FD8");
        let range = parse_range("1E4C9B93F3F0682250B6CF8331B7EE68FD8:9545824\r\nABCDEF:0\n");
        assert_eq!(range.get("1E4C9B93F3F0682250B6CF8331B7EE68FD8"), Some(&9545824));
        assert_eq!(range.get("ABCDEF"), Some(&0));
    }
}
