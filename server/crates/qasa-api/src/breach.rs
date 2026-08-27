use axum::extract::{Path, State};
use axum::Json;
use chrono::Utc;
use qasa_breach::normalize_prefix;
use qasa_core::{AccountTier, CoreError};
use qasa_db::repos::push_copy::{BREACH_ALERT_FREE, BREACH_ALERT_PREMIUM};
use serde::Deserialize;
use serde_json::json;

use crate::auth::LicenseAuth;
use crate::entitlements::resolve_entitlements;
use crate::error::{ApiError, ApiResult};
use crate::state::AppState;

/// Premium: full k-anonymity suffix list. Free: forbidden (use monitor + alerts).
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

    let entitlements = resolve_entitlements(&state, account_id, &account.tier).await?;

    if entitlements.tier != AccountTier::Premium || !entitlements.breach {
        return Err(ApiError::Core(CoreError::Forbidden));
    }

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

/// Free + Premium: register an email hash for server-side breach alerts (conversion hook).
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
    let entitlements = resolve_entitlements(&state, account_id, &account.tier).await?;

    let limit = if entitlements.tier == AccountTier::Premium {
        10
    } else {
        2
    };
    let count = qasa_db::repos::monitored_email::count_for_account(&state.pool, account_id).await?;
    let existing = qasa_db::repos::monitored_email::list_for_account(&state.pool, account_id).await?;
    let already = existing.iter().any(|r| r.email_hash == hash);
    if !already && count >= limit {
        return Err(CoreError::BadRequest(format!(
            "monitor limit is {limit} for your plan"
        ))
        .into());
    }

    let row = qasa_db::repos::monitored_email::upsert_hash(
        &state.pool,
        account_id,
        &hash,
        body.label_hint.as_deref(),
    )
    .await?;

    // Immediate check — if already in dump, notify (especially free conversion).
    let matched = qasa_db::repos::monitored_email::hash_is_breached(&state.pool, &hash).await?;
    let mut alerted = false;
    if matched {
        let copy = if entitlements.tier == AccountTier::Premium {
            &BREACH_ALERT_PREMIUM
        } else {
            &BREACH_ALERT_FREE
        };
        let day = Utc::now().format("%Y-%m-%d");
        let dedupe = format!("breach:{}:{}:{}", account_id, &hash[..12], day);
        if state
            .push
            .notify_account(&state.pool, account_id, copy, &dedupe)
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
        "details_require_premium": entitlements.tier != AccountTier::Premium,
    })))
}

pub async fn list_monitors(
    State(state): State<AppState>,
    LicenseAuth(claims): LicenseAuth,
) -> ApiResult<Json<serde_json::Value>> {
    let account_id = claims.account_id()?;
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
    let ok = qasa_db::repos::monitored_email::delete_hash(
        &state.pool,
        account_id,
        &body.email_hash,
    )
    .await?;
    Ok(Json(json!({ "ok": ok })))
}
