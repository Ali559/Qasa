//! Tauri commands for the account. Each wraps one server call; the UI never sees the
//! license token. New commands must also be added to `isolation/index.js`.

use reqwest::Method;
use serde_json::{json, Value};
use tauri::State;
use tokio::sync::Mutex;

use super::api::ApiError;
use super::{Account, AccountStatus};
use crate::vault::commands::VaultState;

pub struct AccountState(pub Mutex<Account>);

type Res<T> = Result<T, ApiError>;

fn device_name() -> String {
    whoami::devicename()
}

/// `macos`, `windows` or `linux`: the server's `platform` column (plan §7).
fn platform() -> &'static str {
    match std::env::consts::OS {
        "macos" => "macos",
        "windows" => "windows",
        _ => "linux",
    }
}

#[tauri::command]
pub async fn account_status(state: State<'_, AccountState>) -> Res<AccountStatus> {
    Ok(state.0.lock().await.status())
}

/// Runs after every unlock and after vault creation (iOS `AppBootstrap`).
#[tauri::command]
pub async fn account_bootstrap(state: State<'_, AccountState>, locale: String) -> Res<AccountStatus> {
    Ok(state.0.lock().await.bootstrap(&device_name(), platform(), None, &locale).await)
}

#[tauri::command]
pub async fn account_refresh_license(state: State<'_, AccountState>) -> Res<AccountStatus> {
    let mut account = state.0.lock().await;
    account.refresh_license().await;
    Ok(account.status())
}

/// iOS `syncLocaleToServer`, also on a language change.
#[tauri::command]
pub async fn account_sync_locale(state: State<'_, AccountState>, locale: String) -> Res<()> {
    state.0.lock().await.sync_locale(&locale).await;
    Ok(())
}

// MARK: devices

#[tauri::command]
pub async fn devices_list(state: State<'_, AccountState>) -> Res<Value> {
    let v = state.0.lock().await.call(Method::GET, "/v1/devices", None).await?;
    Ok(v.get("devices").cloned().unwrap_or(json!([])))
}

#[tauri::command]
pub async fn device_unlink(state: State<'_, AccountState>, id: String) -> Res<()> {
    if id.is_empty() || !id.chars().all(|c| c.is_ascii_alphanumeric() || c == '-') {
        return Err(ApiError::Decode);
    }
    state.0.lock().await.call(Method::DELETE, &format!("/v1/devices/{id}"), None).await?;
    Ok(())
}

#[tauri::command]
pub async fn devices_unlink_others(state: State<'_, AccountState>) -> Res<Value> {
    let v = state.0.lock().await.call(Method::DELETE, "/v1/devices/others", None).await?;
    Ok(v.get("devices").cloned().unwrap_or(json!([])))
}

/// "Clear local data": unlink this device on the server (best effort, so an offline
/// computer can still be cleared), forget the account, and wipe the vault.
#[tauri::command]
pub async fn account_clear_local(state: State<'_, AccountState>, vault: State<'_, VaultState>) -> Res<()> {
    let mut account = state.0.lock().await;
    if account.token().is_some() {
        let _ = account.call(Method::DELETE, "/v1/devices/me", None).await;
    }
    account.clear_identity();
    drop(account);
    let mut v = vault.0.lock().map_err(|_| ApiError::Decode)?;
    v.wipe().map_err(|_| ApiError::Decode)
}

/// `DELETE /v1/account`, then the same local wipe. The server must confirm first: an
/// offline delete fails rather than leaving a live account behind.
#[tauri::command]
pub async fn account_delete(state: State<'_, AccountState>, vault: State<'_, VaultState>) -> Res<Value> {
    let mut account = state.0.lock().await;
    let account_id = account.account_id().ok_or(ApiError::Unauthorized)?;
    let removed = account.call(Method::DELETE, "/v1/account", Some(&json!({ "confirm_account_id": account_id }))).await?;
    account.clear_identity();
    drop(account);
    let mut v = vault.0.lock().map_err(|_| ApiError::Decode)?;
    v.wipe().map_err(|_| ApiError::Decode)?;
    Ok(removed)
}

// MARK: Premium

#[tauri::command]
pub async fn pricing_get(state: State<'_, AccountState>) -> Res<Value> {
    let account = state.0.lock().await;
    account.api.send_raw(Method::GET, "/v1/pricing", None, None).await
}

#[tauri::command]
pub async fn payment_create_intent(
    state: State<'_, AccountState>,
    gateway: String,
    plan_id: String,
    amount_iqd: i64,
    auto_renew: bool,
) -> Res<Value> {
    let body = json!({
        "gateway": gateway,
        "plan_id": plan_id,
        "auto_renew": auto_renew,
        "amount_iqd": amount_iqd,
        "idempotency_key": uuid::Uuid::new_v4().to_string(),
    });
    state.0.lock().await.call(Method::POST, "/v1/payments/intents", Some(&body)).await
}

/// One status check. On success the license is refreshed so Premium unlocks at once.
#[tauri::command]
pub async fn payment_status(state: State<'_, AccountState>, id: String) -> Res<Value> {
    if !id.chars().all(|c| c.is_ascii_alphanumeric() || c == '-') {
        return Err(ApiError::Decode);
    }
    let mut account = state.0.lock().await;
    let status = account.call(Method::GET, &format!("/v1/payments/{id}"), None).await?;
    if status.get("status").and_then(Value::as_str).is_some_and(|s| s.eq_ignore_ascii_case("success")) {
        account.refresh_license().await;
    }
    Ok(status)
}

/// Development only: confirm a stub wallet payment, as iOS `simulateStubPayment` does in
/// Debug. The server refuses stub settlements in production anyway.
#[tauri::command]
pub async fn payment_simulate_stub(state: State<'_, AccountState>, gateway: String, order_id: String) -> Res<()> {
    if !cfg!(debug_assertions) || !matches!(gateway.as_str(), "fib" | "fastpay" | "superqi") {
        return Err(ApiError::Server { status: 404, server: None });
    }
    let account = state.0.lock().await;
    let body = json!({ "order_id": order_id, "status": "success" });
    account.api.send_raw(Method::POST, &format!("/v1/payments/webhook/{gateway}"), None, Some(&body)).await?;
    Ok(())
}

#[tauri::command]
pub async fn subscription_get(state: State<'_, AccountState>) -> Res<Value> {
    let v = state.0.lock().await.call(Method::GET, "/v1/subscription", None).await?;
    Ok(v.get("subscription").cloned().unwrap_or(Value::Null))
}

#[tauri::command]
pub async fn subscription_set_auto_renew(state: State<'_, AccountState>, auto_renew: bool, subscription_id: Option<String>) -> Res<Value> {
    let body = json!({ "auto_renew": auto_renew, "subscription_id": subscription_id });
    state.0.lock().await.call(Method::PATCH, "/v1/subscription/auto-renew", Some(&body)).await
}

#[tauri::command]
pub async fn subscription_cancel(state: State<'_, AccountState>, reason: Option<String>, subscription_id: Option<String>) -> Res<Value> {
    let body = json!({ "reason": reason, "subscription_id": subscription_id });
    let mut account = state.0.lock().await;
    let v = account.call(Method::POST, "/v1/subscription/cancel", Some(&body)).await?;
    account.refresh_license().await;
    Ok(v)
}

#[tauri::command]
pub async fn payment_method_remove(state: State<'_, AccountState>) -> Res<Value> {
    state.0.lock().await.call(Method::DELETE, "/v1/payment-method", None).await
}

// MARK: inbox

#[tauri::command]
pub async fn notifications_list(state: State<'_, AccountState>, before: Option<String>) -> Res<Value> {
    let mut path = "/v1/notifications?limit=50".to_string();
    if let Some(before) = before {
        let encoded: String = before.chars().filter(|c| c.is_ascii_alphanumeric() || "-:.+TZ".contains(*c)).collect();
        path.push_str(&format!("&before={}", encoded.replace('+', "%2B").replace(':', "%3A")));
    }
    state.0.lock().await.call(Method::GET, &path, None).await
}

#[tauri::command]
pub async fn notifications_unread_count(state: State<'_, AccountState>) -> Res<u64> {
    let mut account = state.0.lock().await;
    if account.token().is_none() {
        return Ok(0);
    }
    let v = account.call(Method::GET, "/v1/notifications/unread-count", None).await?;
    Ok(v.get("unread_count").and_then(Value::as_u64).unwrap_or(0))
}

fn check_id(id: &str) -> Res<()> {
    if id.is_empty() || !id.chars().all(|c| c.is_ascii_alphanumeric() || c == '-') {
        return Err(ApiError::Decode);
    }
    Ok(())
}

/// Opening a notification marks it read on the server (iOS `GET /notifications/{id}`).
#[tauri::command]
pub async fn notification_open(state: State<'_, AccountState>, id: String) -> Res<Value> {
    check_id(&id)?;
    let v = state.0.lock().await.call(Method::GET, &format!("/v1/notifications/{id}"), None).await?;
    Ok(v.get("notification").cloned().unwrap_or(v))
}

#[tauri::command]
pub async fn notifications_read_all(state: State<'_, AccountState>) -> Res<Value> {
    state.0.lock().await.call(Method::POST, "/v1/notifications/read-all", None).await
}

#[tauri::command]
pub async fn notification_delete(state: State<'_, AccountState>, id: String) -> Res<()> {
    check_id(&id)?;
    state.0.lock().await.call(Method::DELETE, &format!("/v1/notifications/{id}"), None).await?;
    Ok(())
}
