use axum::extract::State;
use axum::Json;
use qasa_core::CoreError;
use serde::Deserialize;
use serde_json::json;
use uuid::Uuid;

use qasa_db::repos::subscription::SubscriptionRow;
use qasa_payments::RenewalMode;

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
        Some(s) => json!({ "subscription": subscription_json(&state, &s).await? }),
        None => json!({ "subscription": null }),
    }))
}

/// The subscription as the apps see it.
///
/// `renewal` says what will actually happen at `expires_at`, which the toggle alone does
/// not:
/// - `mode: "charge"`: a saved method on a recurring-capable gateway will be charged
///   (`will_renew` is true).
/// - `mode: "link"`: no gateway can charge one (Wayl cannot), so a payment link for the
///   next period is sent a few days before the end. `link` is that link once it is out.
/// - `mode: "none"`: auto-renew is off, the subscription is not active, or nothing can
///   take a payment; the customer renews by hand.
async fn subscription_json(state: &AppState, s: &SubscriptionRow) -> ApiResult<serde_json::Value> {
    let gateway = qasa_db::repos::renewal::recurring_method_gateway(
        &state.pool,
        s.account_id,
        &state.recurring_gateways(),
    )
    .await?;
    let renewing = s.auto_renew && s.status == "active";
    let mode = if !renewing {
        RenewalMode::None
    } else if gateway.is_some() {
        RenewalMode::Charge
    } else if state.gateways.renewal_links().is_some() {
        RenewalMode::Link
    } else {
        RenewalMode::None
    };
    // Renewals cost the plan's price at the time, which can differ from what was paid.
    let price = if mode == RenewalMode::None {
        None
    } else {
        qasa_db::repos::plan::get_plan(&state.pool, &s.plan_id)
            .await?
            .filter(|p| p.active)
            .map(|p| p.amount_iqd)
    };
    let mode = if price.is_none() { RenewalMode::None } else { mode };
    // An open link is shown even with auto-renew since turned off: it can still be paid.
    let link = qasa_db::repos::payment::open_renewal_link(&state.pool, s.id)
        .await?
        .map(|p| {
            json!({
                "payment_id": p.id,
                "url": p.deep_link,
                "amount_iqd": p.amount_iqd,
                "pay_by": p.pay_by,
            })
        });
    Ok(json!({
        "id": s.id,
        "status": s.status,
        "plan_id": s.plan_id,
        "amount_iqd": s.amount_iqd,
        "auto_renew": s.auto_renew,
        "started_at": s.started_at,
        "expires_at": s.expires_at,
        "cancelled_at": s.cancelled_at,
        "cancellation_reason": s.cancellation_reason,
        "renewal": {
            "mode": mode.as_str(),
            "will_renew": mode == RenewalMode::Charge,
            "gateway": gateway.filter(|_| mode == RenewalMode::Charge),
            "amount_iqd": price,
            "link": link,
        },
    }))
}

/// Forget the saved payment method. Auto-renew stays as the customer set it, but nothing
/// is charged until a later checkout saves a method again.
pub async fn delete_payment_method(
    State(state): State<AppState>,
    LicenseAuth(claims): LicenseAuth,
) -> ApiResult<Json<serde_json::Value>> {
    let account_id = claims.account_id()?;
    let removed =
        qasa_db::repos::payment_method::revoke_for_account(&state.pool, account_id).await?;
    Ok(Json(json!({ "removed": removed })))
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

    Ok(Json(json!({ "subscription": subscription_json(&state, &sub).await? })))
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

    Ok(Json(json!({ "subscription": subscription_json(&state, &sub).await? })))
}
