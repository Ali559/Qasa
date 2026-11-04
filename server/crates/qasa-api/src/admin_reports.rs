//! Read-only admin reports: payment detail, chart series, the audit trail.

use axum::extract::{Path, Query, State};
use axum::Json;
use chrono::{DateTime, Utc};
use qasa_core::CoreError;
use serde::Deserialize;
use serde_json::json;
use uuid::Uuid;

use crate::auth::AdminAuth;
use crate::error::ApiResult;
use crate::state::AppState;

/// One payment with every webhook received for it, so support can see what the gateway
/// actually said. The dashboard used to fabricate this sheet from the list row.
pub async fn payment_detail(
    State(state): State<AppState>,
    AdminAuth(_claims): AdminAuth,
    Path(payment_id): Path<Uuid>,
) -> ApiResult<Json<serde_json::Value>> {
    let p = qasa_db::repos::payment::find_by_id(&state.pool, payment_id)
        .await?
        .ok_or(CoreError::NotFound)?;
    let webhooks = match p.gateway_order_id.as_deref() {
        Some(order) => {
            qasa_db::repos::payment::list_webhooks_for_admin(&state.pool, Some(order), 100).await?
        }
        None => Vec::new(),
    };
    Ok(Json(json!({
        "payment": {
            "payment_id": p.id,
            "account_id": p.account_id,
            "status": p.status,
            "gateway": p.gateway,
            "gateway_order_id": p.gateway_order_id,
            "amount_iqd": p.amount_iqd,
            "plan_id": p.plan_id,
            "auto_renew": p.auto_renew,
            "kind": p.kind,
            "renews_subscription_id": p.renews_subscription_id,
            "subscription_id": p.subscription_id,
            "created_at": p.created_at,
            "completed_at": p.completed_at,
        },
        "webhooks": webhooks,
    })))
}

#[derive(Debug, Deserialize)]
pub struct TimeseriesQuery {
    pub days: Option<i32>,
}

/// Series for the Overview growth chart and the gateway-mix chart, which were mock data.
pub async fn timeseries(
    State(state): State<AppState>,
    AdminAuth(_claims): AdminAuth,
    Query(query): Query<TimeseriesQuery>,
) -> ApiResult<Json<serde_json::Value>> {
    let days = query.days.unwrap_or(90).clamp(1, 366);
    let (revenue, signups, new_paid) =
        qasa_db::repos::payment::timeseries(&state.pool, days).await?;

    let mut mix: std::collections::BTreeMap<&str, (i64, i64)> = Default::default();
    for r in &revenue {
        let e = mix.entry(r.gateway.as_str()).or_default();
        e.0 += r.total_iqd;
        e.1 += r.tx_count;
    }
    let gateway_mix: Vec<_> = mix
        .into_iter()
        .map(|(g, (total, n))| json!({ "gateway": g, "total_iqd": total, "tx_count": n }))
        .collect();

    Ok(Json(json!({
        "days": days,
        "revenue_daily": revenue,
        "signups_daily": signups,
        "new_paid_daily": new_paid,
        "gateway_mix": gateway_mix,
    })))
}

#[derive(Debug, Deserialize)]
pub struct AuditQuery {
    pub target_id: Option<String>,
    pub before: Option<DateTime<Utc>>,
    pub limit: Option<i64>,
}

/// Who changed what, newest first. Also the "events" feed for the Telemetry page.
pub async fn audit_log(
    State(state): State<AppState>,
    AdminAuth(_claims): AdminAuth,
    Query(query): Query<AuditQuery>,
) -> ApiResult<Json<serde_json::Value>> {
    let limit = query.limit.unwrap_or(100).clamp(1, 500);
    let rows = qasa_db::repos::admin::list_audit(
        &state.pool,
        query.target_id.as_deref(),
        query.before,
        limit,
    )
    .await?;
    let next_before = if rows.len() as i64 == limit {
        rows.last().map(|r| r.created_at)
    } else {
        None
    };
    Ok(Json(json!({ "entries": rows, "next_before": next_before })))
}
