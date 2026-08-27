use axum::extract::{Path, State};
use axum::Json;
use qasa_core::CoreError;
use serde::Deserialize;
use serde_json::json;
use uuid::Uuid;

use crate::auth::{AdminAuth, LicenseAuth};
use crate::error::ApiResult;
use crate::state::AppState;

pub async fn list_mine(
    State(state): State<AppState>,
    LicenseAuth(claims): LicenseAuth,
) -> ApiResult<Json<serde_json::Value>> {
    let account_id = claims.account_id()?;
    let items = qasa_db::repos::notification::list_for_account(&state.pool, account_id, 50).await?;
    Ok(Json(json!({ "notifications": items })))
}

pub async fn get_one(
    State(state): State<AppState>,
    LicenseAuth(claims): LicenseAuth,
    Path(id): Path<Uuid>,
) -> ApiResult<Json<serde_json::Value>> {
    let account_id = claims.account_id()?;
    let item = qasa_db::repos::notification::get_for_account(&state.pool, account_id, id)
        .await?
        .ok_or(CoreError::NotFound)?;
    let _ = qasa_db::repos::notification::mark_read(&state.pool, account_id, id).await;
    Ok(Json(json!({ "notification": item })))
}

#[derive(Debug, Deserialize)]
pub struct UpdateDevicePrefsRequest {
    pub locale: Option<String>,
    pub push_token: Option<String>,
    pub notifications_enabled: Option<bool>,
}

pub async fn update_device_prefs(
    State(state): State<AppState>,
    LicenseAuth(claims): LicenseAuth,
    Json(body): Json<UpdateDevicePrefsRequest>,
) -> ApiResult<Json<serde_json::Value>> {
    let device_id = claims.device_id()?;
    let locale = body
        .locale
        .as_deref()
        .map(normalize_locale)
        .unwrap_or("en");
    let enabled = body.notifications_enabled.unwrap_or(false);
    qasa_db::repos::device::update_locale_and_push(
        &state.pool,
        device_id,
        locale,
        body.push_token.as_deref(),
        enabled,
    )
    .await?;
    Ok(Json(json!({
        "ok": true,
        "locale": locale,
        "notifications_enabled": enabled,
    })))
}

#[derive(Debug, Deserialize)]
pub struct AdminCreateNotificationRequest {
    pub title_en: String,
    pub title_ckb: Option<String>,
    pub title_ar: Option<String>,
    pub body_en: String,
    pub body_ckb: Option<String>,
    pub body_ar: Option<String>,
    #[serde(default = "default_audience")]
    pub audience: String,
}

fn default_audience() -> String {
    "all".into()
}

pub async fn admin_create(
    State(state): State<AppState>,
    AdminAuth(claims): AdminAuth,
    Json(body): Json<AdminCreateNotificationRequest>,
) -> ApiResult<Json<serde_json::Value>> {
    if body.title_en.trim().is_empty() || body.body_en.trim().is_empty() {
        return Err(CoreError::BadRequest("title_en and body_en required".into()).into());
    }
    let n = qasa_db::repos::notification::create(
        &state.pool,
        &body.title_en,
        body.title_ckb.as_deref(),
        body.title_ar.as_deref(),
        &body.body_en,
        body.body_ckb.as_deref(),
        body.body_ar.as_deref(),
        &body.audience,
        Some(claims.sub.parse().ok()).flatten(),
    )
    .await?;
    let notified = qasa_db::repos::notification::fanout_receipts(&state.pool, n.id).await?;
    // Admin broadcasts: inbox only for now; targeted alerts use qasa-push (FCM).
    tracing::info!(
        notification_id = %n.id,
        notified,
        "admin notification created (inbox fanout; FCM via targeted notify_account)"
    );
    Ok(Json(json!({ "notification": n, "notified_accounts": notified })))
}

pub async fn admin_list(
    State(state): State<AppState>,
    AdminAuth(_claims): AdminAuth,
) -> ApiResult<Json<serde_json::Value>> {
    let items = qasa_db::repos::notification::list_admin(&state.pool, 100).await?;
    Ok(Json(json!({ "notifications": items })))
}

fn normalize_locale(raw: &str) -> &'static str {
    match raw.to_lowercase().as_str() {
        "ckb" | "ku" | "kurdish" => "ckb",
        "ar" | "arabic" => "ar",
        _ => "en",
    }
}
