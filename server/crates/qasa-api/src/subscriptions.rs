use axum::extract::State;
use axum::Json;
use qasa_core::CoreError;
use serde::Deserialize;
use serde_json::json;
use uuid::Uuid;

use crate::auth::LicenseAuth;
use crate::error::ApiResult;
use crate::state::AppState;

pub async fn get_current(
    State(state): State<AppState>,
    LicenseAuth(claims): LicenseAuth,
) -> ApiResult<Json<serde_json::Value>> {
    let account_id = claims.account_id()?;
    let sub = qasa_db::repos::subscription::get_current(&state.pool, account_id).await?;

    Ok(Json(match sub {
        Some(s) => json!({
            "subscription": {
                "id": s.id,
                "status": s.status,
                "plan_id": s.plan_id,
                "amount_iqd": s.amount_iqd,
                "auto_renew": s.auto_renew,
                "started_at": s.started_at,
                "expires_at": s.expires_at,
                "cancelled_at": s.cancelled_at,
                "cancellation_reason": s.cancellation_reason,
            }
        }),
        None => json!({ "subscription": null }),
    }))
}

#[derive(Debug, Deserialize)]
pub struct SetAutoRenewRequest {
    pub auto_renew: bool,
    pub subscription_id: Option<Uuid>,
}

pub async fn set_auto_renew(
    State(state): State<AppState>,
    LicenseAuth(claims): LicenseAuth,
    Json(body): Json<SetAutoRenewRequest>,
) -> ApiResult<Json<serde_json::Value>> {
    let account_id = claims.account_id()?;
    let subscription_id = match body.subscription_id {
        Some(id) => id,
        None => qasa_db::repos::subscription::get_current(&state.pool, account_id)
            .await?
            .ok_or(CoreError::NotFound)?
            .id,
    };

    let sub = qasa_db::repos::subscription::set_auto_renew(
        &state.pool,
        account_id,
        subscription_id,
        body.auto_renew,
    )
    .await?
    .ok_or(CoreError::NotFound)?;

    Ok(Json(json!({
        "subscription": {
            "id": sub.id,
            "status": sub.status,
            "plan_id": sub.plan_id,
            "amount_iqd": sub.amount_iqd,
            "auto_renew": sub.auto_renew,
            "started_at": sub.started_at,
            "expires_at": sub.expires_at,
            "cancelled_at": sub.cancelled_at,
            "cancellation_reason": sub.cancellation_reason,
        }
    })))
}

#[derive(Debug, Deserialize)]
pub struct CancelSubscriptionRequest {
    pub subscription_id: Option<Uuid>,
    pub reason: Option<String>,
}

pub async fn cancel(
    State(state): State<AppState>,
    LicenseAuth(claims): LicenseAuth,
    Json(body): Json<CancelSubscriptionRequest>,
) -> ApiResult<Json<serde_json::Value>> {
    let account_id = claims.account_id()?;
    let subscription_id = match body.subscription_id {
        Some(id) => id,
        None => qasa_db::repos::subscription::get_current(&state.pool, account_id)
            .await?
            .ok_or(CoreError::NotFound)?
            .id,
    };

    let sub = qasa_db::repos::subscription::cancel_subscription(
        &state.pool,
        account_id,
        subscription_id,
        body.reason.as_deref(),
    )
    .await?
    .ok_or_else(|| CoreError::BadRequest("no active subscription to cancel".into()))?;

    Ok(Json(json!({
        "subscription": {
            "id": sub.id,
            "status": sub.status,
            "plan_id": sub.plan_id,
            "amount_iqd": sub.amount_iqd,
            "auto_renew": sub.auto_renew,
            "started_at": sub.started_at,
            "expires_at": sub.expires_at,
            "cancelled_at": sub.cancelled_at,
            "cancellation_reason": sub.cancellation_reason,
        }
    })))
}
