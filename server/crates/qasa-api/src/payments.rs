use axum::extract::{Path, State};
use axum::http::{HeaderMap, StatusCode};
use axum::Json;
use qasa_core::CoreError;
use qasa_db::repos::push_copy::SUB_RENEW_FAILED;
use qasa_payments::{GatewayKind, PaymentError, PaymentGateway, WebhookStatus};
use serde::Deserialize;
use serde_json::json;
use uuid::Uuid;

use crate::auth::LicenseAuth;
use crate::error::ApiResult;
use crate::state::AppState;

#[derive(Debug, Deserialize)]
pub struct CreatePaymentIntentRequest {
    pub gateway: String,
    #[serde(default = "default_plan")]
    pub plan_id: String,
    #[serde(default = "default_auto_renew")]
    pub auto_renew: bool,
    pub amount_iqd: Option<i32>,
    pub idempotency_key: Option<String>,
}

fn default_plan() -> String {
    "premium_monthly".into()
}

fn default_auto_renew() -> bool {
    true
}

pub async fn create_intent(
    State(state): State<AppState>,
    LicenseAuth(claims): LicenseAuth,
    Json(body): Json<CreatePaymentIntentRequest>,
) -> ApiResult<Json<serde_json::Value>> {
    let account_id = claims.account_id()?;
    let plan_id = qasa_db::repos::subscription::normalize_plan_id(&body.plan_id);
    let amount_iqd = match body.amount_iqd {
        Some(v) => v,
        None => qasa_db::repos::plan::plan_amount(&state.pool, plan_id).await?,
    };

    let kind = GatewayKind::parse(&body.gateway)
        .ok_or_else(|| CoreError::BadRequest(format!("unsupported gateway: {}", body.gateway)))?;
    let gateway = state
        .gateway(kind)
        .ok_or_else(|| CoreError::BadRequest("gateway unavailable".into()))?;

    // Outside dev, no gateway can actually move money, so refuse up front rather than
    // handing back a deep link that opens nothing and leaving the user waiting on a
    // payment that can never be confirmed.
    if !gateway.settles_payments() {
        return Err(CoreError::BadRequest(
            "payments are not available for this gateway".into(),
        )
        .into());
    }

    let order_ref = qasa_payments::new_order_ref();
    let intent = gateway.create_intent(amount_iqd, &order_ref).await;

    let payment = qasa_db::repos::payment::create_payment(
        &state.pool,
        account_id,
        kind.as_str(),
        &intent.gateway_order_id,
        intent.amount_iqd,
        &intent.deep_link,
        plan_id,
        body.auto_renew,
        body.idempotency_key.as_deref(),
    )
    .await?;

    Ok(Json(json!({
        "payment_id": payment.id,
        "gateway": kind.as_str(),
        "gateway_order_id": intent.gateway_order_id,
        "deep_link": intent.deep_link,
        "amount_iqd": intent.amount_iqd,
        "plan_id": payment.plan_id,
        "auto_renew": payment.auto_renew,
        "status": payment.status,
    })))
}

pub async fn get_payment(
    State(state): State<AppState>,
    LicenseAuth(claims): LicenseAuth,
    Path(payment_id): Path<Uuid>,
) -> ApiResult<Json<serde_json::Value>> {
    let account_id = claims.account_id()?;
    let payment = qasa_db::repos::payment::find_by_id(&state.pool, payment_id)
        .await?
        .ok_or(CoreError::NotFound)?;

    if payment.account_id != account_id {
        return Err(CoreError::Forbidden.into());
    }

    Ok(Json(json!({
        "payment_id": payment.id,
        "status": payment.status,
        "gateway": payment.gateway,
        "gateway_order_id": payment.gateway_order_id,
        "amount_iqd": payment.amount_iqd,
        "plan_id": payment.plan_id,
        "auto_renew": payment.auto_renew,
        "completed_at": payment.completed_at,
    })))
}

pub async fn webhook_fib(
    State(state): State<AppState>,
    headers: HeaderMap,
    body: axum::body::Bytes,
) -> ApiResult<StatusCode> {
    handle_webhook(state, GatewayKind::Fib, headers, body).await
}

pub async fn webhook_fastpay(
    State(state): State<AppState>,
    headers: HeaderMap,
    body: axum::body::Bytes,
) -> ApiResult<StatusCode> {
    handle_webhook(state, GatewayKind::Fastpay, headers, body).await
}

pub async fn webhook_superqi(
    State(state): State<AppState>,
    headers: HeaderMap,
    body: axum::body::Bytes,
) -> ApiResult<StatusCode> {
    handle_webhook(state, GatewayKind::Superqi, headers, body).await
}

async fn handle_webhook(
    state: AppState,
    kind: GatewayKind,
    headers: HeaderMap,
    body: axum::body::Bytes,
) -> ApiResult<StatusCode> {
    let gateway = state
        .gateway(kind)
        .ok_or_else(|| CoreError::BadRequest("gateway unavailable".into()))?;

    let verify_result = gateway.verify_webhook(&headers, &body);
    let (event, signature_valid) = match verify_result {
        Ok(event) => (event, true),
        Err(e) => {
            let payload: serde_json::Value =
                serde_json::from_slice(&body).unwrap_or(json!({ "raw": "invalid" }));
            qasa_db::repos::payment::insert_webhook_event(
                &state.pool,
                kind.as_str(),
                None,
                &payload,
                false,
                false,
                Some(&e.to_string()),
            )
            .await?;
            return Err(CoreError::BadRequest("invalid webhook".into()).into());
        }
    };

    qasa_db::repos::payment::insert_webhook_event(
        &state.pool,
        kind.as_str(),
        Some(&event.gateway_order_id),
        &event.raw_payload,
        signature_valid,
        false,
        None,
    )
    .await?;

    let payment = qasa_db::repos::payment::find_by_gateway_order_id(
        &state.pool,
        &event.gateway_order_id,
    )
    .await?
    .ok_or(CoreError::NotFound)?;

    if payment.status == "success" {
        return Ok(StatusCode::OK);
    }

    // Second gate, independent of the gateway implementation: a webhook may only settle
    // an order for the amount we actually charged. A gateway that under-reports the
    // amount must not be able to unlock a full-price subscription.
    if event.status == WebhookStatus::Success {
        if let Some(reported) = event.amount_iqd {
            if reported != payment.amount_iqd {
                let err = PaymentError::AmountMismatch {
                    reported,
                    expected: payment.amount_iqd,
                };
                qasa_db::repos::payment::insert_webhook_event(
                    &state.pool,
                    kind.as_str(),
                    Some(&event.gateway_order_id),
                    &event.raw_payload,
                    signature_valid,
                    false,
                    Some(&err.to_string()),
                )
                .await?;
                return Err(CoreError::BadRequest("payment amount mismatch".into()).into());
            }
        }
    }

    let (status, subscription_id) = match event.status {
        WebhookStatus::Success => {
            let sub = qasa_db::repos::subscription::activate_subscription(
                &state.pool,
                payment.account_id,
                payment.amount_iqd,
                &payment.plan_id,
                payment.auto_renew,
            )
            .await?;
            ("success", Some(sub.id))
        }
        WebhookStatus::Failed => ("failed", None),
        WebhookStatus::Pending => ("pending", None),
    };

    qasa_db::repos::payment::mark_status(
        &state.pool,
        payment.id,
        status,
        subscription_id,
    )
    .await?;

    // Live webhook failure → device push for auto-renew subscribers (never email).
    if matches!(event.status, WebhookStatus::Failed) {
        if let Some(sub) =
            qasa_db::repos::subscription::get_current(&state.pool, payment.account_id).await?
        {
            if sub.auto_renew {
                let dedupe = format!("sub_renew_failed_pay:{}", payment.id);
                let _ = state
                    .push
                    .notify_account(&state.pool, payment.account_id, &SUB_RENEW_FAILED, &dedupe)
                    .await;
            }
        }
    }

    Ok(StatusCode::OK)
}
