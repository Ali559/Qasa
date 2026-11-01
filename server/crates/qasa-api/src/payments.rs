use axum::extract::{Path, Query, State};
use axum::http::{HeaderMap, StatusCode};
use axum::Json;
use qasa_core::CoreError;
use qasa_db::repos::push_copy::SUB_RENEW_FAILED;
use qasa_db::repos::settlement::{settle_payment, SettleOutcome, SettleStatus};
use qasa_payments::{GatewayKind, PaymentGateway, WebhookStatus};
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

    let idempotency_key = body
        .idempotency_key
        .as_deref()
        .map(str::trim)
        .filter(|k| !k.is_empty());
    if let Some(key) = idempotency_key {
        if let Some(existing) =
            qasa_db::repos::payment::find_by_idempotency_key(&state.pool, key).await?
        {
            return replay_intent(existing, account_id);
        }
    }

    // The price always comes from the plan. Clients used to be able to send their own
    // `amount_iqd`, and the webhook only compared the settled amount against that, so a
    // 1 IQD intent for the yearly plan settled as a full yearly subscription.
    let plan_id = qasa_db::repos::subscription::normalize_plan_id(&body.plan_id);
    let plan = qasa_db::repos::plan::get_plan(&state.pool, plan_id)
        .await?
        .filter(|p| p.active)
        .ok_or_else(|| CoreError::BadRequest(format!("unknown plan: {plan_id}")))?;
    if let Some(sent) = body.amount_iqd {
        if sent != plan.amount_iqd {
            return Err(CoreError::BadRequest(format!(
                "amount_iqd {sent} does not match the {} price of {} IQD",
                plan.id, plan.amount_iqd
            ))
            .into());
        }
    }

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
    let intent = gateway.create_intent(plan.amount_iqd, &order_ref).await;

    let created = qasa_db::repos::payment::create_payment(
        &state.pool,
        account_id,
        kind.as_str(),
        &intent.gateway_order_id,
        intent.amount_iqd,
        &intent.deep_link,
        &plan.id,
        body.auto_renew,
        idempotency_key,
    )
    .await?;

    let payment = match created {
        Some(payment) => payment,
        // Lost a race with a concurrent request carrying the same key.
        None => {
            let key = idempotency_key.expect("only a keyed insert can conflict");
            let existing = qasa_db::repos::payment::find_by_idempotency_key(&state.pool, key)
                .await?
                .ok_or(CoreError::NotFound)?;
            return replay_intent(existing, account_id);
        }
    };

    Ok(Json(intent_json(&payment)))
}

/// Answer a retried intent with the payment the key already created.
fn replay_intent(
    existing: qasa_db::repos::payment::PaymentRow,
    account_id: Uuid,
) -> ApiResult<Json<serde_json::Value>> {
    if existing.account_id != account_id {
        return Err(CoreError::BadRequest("idempotency_key already used".into()).into());
    }
    Ok(Json(intent_json(&existing)))
}

fn intent_json(payment: &qasa_db::repos::payment::PaymentRow) -> serde_json::Value {
    json!({
        "payment_id": payment.id,
        "gateway": payment.gateway,
        "gateway_order_id": payment.gateway_order_id,
        "deep_link": payment.deep_link,
        "amount_iqd": payment.amount_iqd,
        "plan_id": payment.plan_id,
        "auto_renew": payment.auto_renew,
        "status": payment.status,
    })
}

#[derive(Debug, Deserialize)]
pub struct ListPaymentsQuery {
    pub limit: Option<i64>,
    /// `next_before` from the previous page.
    pub before: Option<chrono::DateTime<chrono::Utc>>,
}

/// The account's payment history, newest first.
pub async fn list_mine(
    State(state): State<AppState>,
    LicenseAuth(claims): LicenseAuth,
    Query(query): Query<ListPaymentsQuery>,
) -> ApiResult<Json<serde_json::Value>> {
    let account_id = claims.account_id()?;
    let limit = query.limit.unwrap_or(20).clamp(1, 100);
    let rows =
        qasa_db::repos::payment::list_for_account(&state.pool, account_id, query.before, limit)
            .await?;
    let next_before = if rows.len() as i64 == limit {
        rows.last().map(|p| p.created_at)
    } else {
        None
    };
    let payments: Vec<_> = rows
        .iter()
        .map(|p| {
            json!({
                "payment_id": p.id,
                "status": p.status,
                "gateway": p.gateway,
                "gateway_order_id": p.gateway_order_id,
                "amount_iqd": p.amount_iqd,
                "plan_id": p.plan_id,
                "auto_renew": p.auto_renew,
                "subscription_id": p.subscription_id,
                "created_at": p.created_at,
                "completed_at": p.completed_at,
            })
        })
        .collect();
    Ok(Json(json!({ "payments": payments, "next_before": next_before })))
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

    let event = match gateway.verify_webhook(&headers, &body) {
        Ok(event) => event,
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

    let status = settle_status(event.status);
    let event_id = qasa_db::repos::payment::insert_verified_webhook_event(
        &state.pool,
        kind.as_str(),
        &event.gateway_order_id,
        &event.raw_payload,
        status.as_str(),
        event.amount_iqd,
    )
    .await?;

    let outcome = settle_payment(
        &state.pool,
        kind.as_str(),
        &event.gateway_order_id,
        status,
        event.amount_iqd,
    )
    .await?;

    if outcome.is_terminal(status) {
        qasa_db::repos::payment::finish_webhook_event(
            &state.pool,
            event_id,
            outcome.error_message().as_deref(),
        )
        .await?;
    }

    match outcome {
        SettleOutcome::NotFound => Err(CoreError::NotFound.into()),
        SettleOutcome::AmountMismatch { .. } => {
            Err(CoreError::BadRequest("payment amount mismatch".into()).into())
        }
        SettleOutcome::Failed { payment } => {
            notify_renew_failed(&state, &payment).await;
            Ok(StatusCode::OK)
        }
        SettleOutcome::AccountDeleted { payment } => {
            // Accepted so the gateway stops retrying; the event row records the refund.
            tracing::warn!(payment_id = %payment.id, "payment settled for a deleted account; refund required");
            Ok(StatusCode::OK)
        }
        SettleOutcome::Settled { .. } | SettleOutcome::Unchanged => Ok(StatusCode::OK),
    }
}

fn settle_status(status: WebhookStatus) -> SettleStatus {
    match status {
        WebhookStatus::Success => SettleStatus::Success,
        WebhookStatus::Failed => SettleStatus::Failed,
        WebhookStatus::Pending => SettleStatus::Pending,
    }
}

/// Live webhook failure → device push for auto-renew subscribers (never email).
async fn notify_renew_failed(state: &AppState, payment: &qasa_db::repos::payment::PaymentRow) {
    // Every payment today is a checkout the user started and watches in the app; a
    // failure there is not a failed renewal.
    if !qasa_payments::RENEWALS_ARE_CHARGED {
        return;
    }
    let Ok(Some(sub)) =
        qasa_db::repos::subscription::get_current(&state.pool, payment.account_id).await
    else {
        return;
    };
    if sub.auto_renew {
        let dedupe = format!("sub_renew_failed_pay:{}", payment.id);
        let _ = state
            .push
            .notify_account(&state.pool, payment.account_id, &SUB_RENEW_FAILED, &dedupe)
            .await;
    }
}
