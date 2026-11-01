use axum::extract::{Path, Query, State};
use axum::Json;
use qasa_core::CoreError;
use serde::Deserialize;
use serde_json::json;
use uuid::Uuid;

use crate::auth::{AdminAuth, AdminWrite, LicenseAuth};
use crate::error::ApiResult;
use crate::state::AppState;

#[derive(Debug, Deserialize)]
pub struct InboxQuery {
    pub limit: Option<i64>,
    /// `next_before` from the previous page.
    pub before: Option<chrono::DateTime<chrono::Utc>>,
}

/// The inbox, newest first, with the unread count for the badge.
pub async fn list_mine(
    State(state): State<AppState>,
    LicenseAuth(claims): LicenseAuth,
    Query(query): Query<InboxQuery>,
) -> ApiResult<Json<serde_json::Value>> {
    let account_id = claims.account_id()?;
    let limit = query.limit.unwrap_or(50).clamp(1, 100);
    let items =
        qasa_db::repos::notification::list_for_account(&state.pool, account_id, query.before, limit)
            .await?;
    let unread = qasa_db::repos::notification::unread_count(&state.pool, account_id).await?;
    let next_before = if items.len() as i64 == limit {
        items.last().map(|n| n.created_at)
    } else {
        None
    };
    Ok(Json(json!({
        "notifications": items,
        "unread_count": unread,
        "next_before": next_before,
    })))
}

pub async fn unread_count(
    State(state): State<AppState>,
    LicenseAuth(claims): LicenseAuth,
) -> ApiResult<Json<serde_json::Value>> {
    let n = qasa_db::repos::notification::unread_count(&state.pool, claims.account_id()?).await?;
    Ok(Json(json!({ "unread_count": n })))
}

/// One notification. Still marks it read, as current app versions rely on that; new
/// clients should call `POST /v1/notifications/{id}/read` when it is actually shown.
pub async fn get_one(
    State(state): State<AppState>,
    LicenseAuth(claims): LicenseAuth,
    Path(id): Path<Uuid>,
) -> ApiResult<Json<serde_json::Value>> {
    let account_id = claims.account_id()?;
    let item = qasa_db::repos::notification::get_for_account(&state.pool, account_id, id)
        .await?
        .ok_or(CoreError::NotFound)?;
    qasa_db::repos::notification::mark_read(&state.pool, account_id, id).await?;
    Ok(Json(json!({ "notification": item })))
}

pub async fn mark_read(
    State(state): State<AppState>,
    LicenseAuth(claims): LicenseAuth,
    Path(id): Path<Uuid>,
) -> ApiResult<Json<serde_json::Value>> {
    let account_id = claims.account_id()?;
    if !qasa_db::repos::notification::mark_read(&state.pool, account_id, id).await? {
        return Err(CoreError::NotFound.into());
    }
    let unread = qasa_db::repos::notification::unread_count(&state.pool, account_id).await?;
    Ok(Json(json!({ "ok": true, "unread_count": unread })))
}

pub async fn mark_all_read(
    State(state): State<AppState>,
    LicenseAuth(claims): LicenseAuth,
) -> ApiResult<Json<serde_json::Value>> {
    let marked =
        qasa_db::repos::notification::mark_all_read(&state.pool, claims.account_id()?).await?;
    Ok(Json(json!({ "ok": true, "marked": marked, "unread_count": 0 })))
}

/// Remove a notification from this user's inbox. Broadcasts stay for everyone else.
pub async fn delete_one(
    State(state): State<AppState>,
    LicenseAuth(claims): LicenseAuth,
    Path(id): Path<Uuid>,
) -> ApiResult<axum::http::StatusCode> {
    if !qasa_db::repos::notification::delete_for_account(&state.pool, claims.account_id()?, id)
        .await?
    {
        return Err(CoreError::NotFound.into());
    }
    Ok(axum::http::StatusCode::NO_CONTENT)
}

#[derive(Debug, Deserialize)]
pub struct UpdateDevicePrefsRequest {
    pub locale: Option<String>,
    pub push_token: Option<String>,
    pub notifications_enabled: Option<bool>,
}

/// Partial update: a field the app leaves out keeps its stored value. It used to be
/// reset — a locale-only update (which iOS sends on every language change) switched
/// notifications off and set the locale back to `en` for anything omitted.
pub async fn update_device_prefs(
    State(state): State<AppState>,
    LicenseAuth(claims): LicenseAuth,
    Json(body): Json<UpdateDevicePrefsRequest>,
) -> ApiResult<Json<serde_json::Value>> {
    let device_id = claims.device_id()?;
    let locale = body.locale.as_deref().map(normalize_locale);
    let (locale, enabled) = qasa_db::repos::device::update_prefs(
        &state.pool,
        device_id,
        locale,
        body.push_token.as_deref().map(str::trim),
        body.notifications_enabled,
    )
    .await?
    .ok_or(CoreError::NotFound)?;
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
    AdminWrite(claims): AdminWrite,
    Json(body): Json<AdminCreateNotificationRequest>,
) -> ApiResult<Json<serde_json::Value>> {
    if body.title_en.trim().is_empty() || body.body_en.trim().is_empty() {
        return Err(CoreError::BadRequest("title_en and body_en required".into()).into());
    }
    if !qasa_db::repos::notification::BROADCAST_AUDIENCES.contains(&body.audience.as_str()) {
        return Err(CoreError::BadRequest(
            "audience must be all, subscribers or free".into(),
        )
        .into());
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
    qasa_db::repos::admin::audit_now(
        &state.pool,
        claims.admin_id()?,
        "create_notification",
        "notification",
        &n.id.to_string(),
        json!({ "title_en": n.title_en, "audience": n.audience, "notified": notified }),
    )
    .await?;
    // Device pushes for the receipts go out from the worker (`broadcast_pushes`), so a
    // large audience never holds up this request.
    tracing::info!(notification_id = %n.id, notified, "admin notification created");
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
