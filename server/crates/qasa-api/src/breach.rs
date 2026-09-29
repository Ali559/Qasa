use axum::extract::{Path, State};
use axum::Json;
use chrono::Utc;
use qasa_breach::normalize_prefix;
use qasa_core::CoreError;
use qasa_db::repos::push_copy::BREACH_ALERT_PREMIUM;
use serde::Deserialize;
use serde_json::json;

use crate::auth::LicenseAuth;
use crate::entitlements::{require_active_premium, PremiumFeature};
use crate::error::{ApiError, ApiResult};
use crate::state::AppState;

/// Premium only: full k-anonymity suffix list.
pub async fn lookup_prefix(
    State(state): State<AppState>,
    LicenseAuth(claims): LicenseAuth,
    Path(prefix): Path<String>,
) -> ApiResult<Json<serde_json::Value>> {
    let account_id = claims.account_id()?;

    state
        .breach_rate_limiter
        .check(&account_id.to_string())
        .map_err(|_| ApiError::RateLimited)?;

    let account = qasa_db::repos::account::get_account(&state.pool, account_id)
        .await?
        .ok_or(CoreError::NotFound)?;

    require_active_premium(&state, account_id, &account.tier, PremiumFeature::Breach).await?;

    let normalized = normalize_prefix(&prefix).map_err(|e| CoreError::BadRequest(e.to_string()))?;

    let matches = qasa_db::repos::breach::lookup_by_prefix(&state.pool, &normalized).await?;

    Ok(Json(json!({
        "prefix": normalized,
        "count": matches.len(),
        "suffixes": matches,
    })))
}

#[derive(Debug, Deserialize)]
pub struct RegisterMonitorRequest {
    /// Full SHA-256 hex of normalized email (64 chars). Never send the raw email.
    pub email_hash: String,
    pub label_hint: Option<String>,
}

/// Premium only: register an email hash for server-side breach alerts.
pub async fn register_monitor(
    State(state): State<AppState>,
    LicenseAuth(claims): LicenseAuth,
    Json(body): Json<RegisterMonitorRequest>,
) -> ApiResult<Json<serde_json::Value>> {
    let account_id = claims.account_id()?;
    let hash = body.email_hash.trim().to_lowercase();
    if hash.len() != 64 || !hash.chars().all(|c| c.is_ascii_hexdigit()) {
        return Err(CoreError::BadRequest("email_hash must be 64 hex chars".into()).into());
    }

    let account = qasa_db::repos::account::get_account(&state.pool, account_id)
        .await?
        .ok_or(CoreError::NotFound)?;
    require_active_premium(&state, account_id, &account.tier, PremiumFeature::Breach).await?;

    const LIMIT: i64 = 10;
    let count = qasa_db::repos::monitored_email::count_for_account(&state.pool, account_id).await?;
    let existing = qasa_db::repos::monitored_email::list_for_account(&state.pool, account_id).await?;
    let already = existing.iter().any(|r| r.email_hash == hash);
    if !already && count >= LIMIT {
        return Err(CoreError::BadRequest(format!("monitor limit is {LIMIT} for your plan")).into());
    }

    let row = qasa_db::repos::monitored_email::upsert_hash(
        &state.pool,
        account_id,
        &hash,
        body.label_hint.as_deref(),
    )
    .await?;

    let matched = qasa_db::repos::monitored_email::hash_is_breached(&state.pool, &hash).await?;
    let mut alerted = false;
    if matched {
        let day = Utc::now().format("%Y-%m-%d");
        let dedupe = format!("breach:{}:{}:{}", account_id, &hash[..12], day);
        if state
            .push
            .notify_account(&state.pool, account_id, &BREACH_ALERT_PREMIUM, &dedupe)
            .await?
            .is_some()
        {
            alerted = true;
        }
        let _ = qasa_db::repos::monitored_email::mark_alerted(&state.pool, row.id).await;
    }

    Ok(Json(json!({
        "ok": true,
        "email_hash_prefix": &hash[..5],
        "matched": matched,
        "alerted": alerted,
    })))
}

pub async fn list_monitors(
    State(state): State<AppState>,
    LicenseAuth(claims): LicenseAuth,
) -> ApiResult<Json<serde_json::Value>> {
    let account_id = claims.account_id()?;
    let account = qasa_db::repos::account::get_account(&state.pool, account_id)
        .await?
        .ok_or(CoreError::NotFound)?;
    require_active_premium(&state, account_id, &account.tier, PremiumFeature::Breach).await?;

    let rows = qasa_db::repos::monitored_email::list_for_account(&state.pool, account_id).await?;
    let items: Vec<_> = rows
        .into_iter()
        .map(|r| {
            json!({
                "email_hash_prefix": &r.email_hash[..5.min(r.email_hash.len())],
                "label_hint": r.label_hint,
                "last_matched_at": r.last_matched_at,
                "last_alerted_at": r.last_alerted_at,
            })
        })
        .collect();
    Ok(Json(json!({ "monitors": items })))
}

#[derive(Debug, Deserialize)]
pub struct UnregisterMonitorRequest {
    pub email_hash: String,
}

pub async fn unregister_monitor(
    State(state): State<AppState>,
    LicenseAuth(claims): LicenseAuth,
    Json(body): Json<UnregisterMonitorRequest>,
) -> ApiResult<Json<serde_json::Value>> {
    let account_id = claims.account_id()?;
    let account = qasa_db::repos::account::get_account(&state.pool, account_id)
        .await?
        .ok_or(CoreError::NotFound)?;
    require_active_premium(&state, account_id, &account.tier, PremiumFeature::Breach).await?;

    let ok = qasa_db::repos::monitored_email::delete_hash(
        &state.pool,
        account_id,
        &body.email_hash,
    )
    .await?;
    Ok(Json(json!({ "ok": ok })))
}
