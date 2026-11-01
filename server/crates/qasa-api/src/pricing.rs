use axum::extract::State;
use axum::Json;
use qasa_core::CoreError;
use serde::Deserialize;
use serde_json::json;

use crate::auth::AdminAuth;
use crate::error::ApiResult;
use crate::state::AppState;

pub async fn list_public(
    State(state): State<AppState>,
) -> ApiResult<Json<serde_json::Value>> {
    let plans = qasa_db::repos::plan::list_active(&state.pool).await?;
    Ok(Json(json!({ "plans": plans })))
}

pub async fn list_admin(
    State(state): State<AppState>,
    AdminAuth(_claims): AdminAuth,
) -> ApiResult<Json<serde_json::Value>> {
    let plans = qasa_db::repos::plan::list_all(&state.pool).await?;
    Ok(Json(json!({ "plans": plans })))
}

#[derive(Debug, Deserialize)]
pub struct UpsertPlanRequest {
    pub id: String,
    pub title: String,
    pub interval: String,
    pub amount_iqd: i32,
    pub days: i32,
    #[serde(default = "default_quota")]
    pub vpn_quota_gb: i32,
    #[serde(default = "default_true")]
    pub active: bool,
    #[serde(default)]
    pub sort_order: i32,
    #[serde(default)]
    pub features: Vec<String>,
    /// When true, create an inbox notification about pricing change.
    #[serde(default)]
    pub notify_subscribers: bool,
}

fn default_quota() -> i32 {
    100
}
fn default_true() -> bool {
    true
}

pub async fn upsert_admin(
    State(state): State<AppState>,
    AdminAuth(claims): AdminAuth,
    Json(body): Json<UpsertPlanRequest>,
) -> ApiResult<Json<serde_json::Value>> {
    if body.amount_iqd <= 0 || body.days <= 0 {
        return Err(CoreError::BadRequest("amount_iqd and days must be positive".into()).into());
    }
    if body.interval != "monthly" && body.interval != "yearly" {
        return Err(CoreError::BadRequest("interval must be monthly or yearly".into()).into());
    }

    let features = serde_json::Value::Array(
        body.features
            .iter()
            .map(|s| serde_json::Value::String(s.clone()))
            .collect(),
    );

    let plan = qasa_db::repos::plan::upsert_plan(
        &state.pool,
        &body.id,
        &body.title,
        &body.interval,
        body.amount_iqd,
        body.days,
        body.vpn_quota_gb,
        body.active,
        body.sort_order,
        features,
    )
    .await?;

    let mut notified = 0i64;
    if body.notify_subscribers {
        let n = qasa_db::repos::notification::create(
            &state.pool,
            "Pricing updated",
            Some("نرخی نوێکرایەوە"),
            Some("تم تحديث الأسعار"),
            &format!(
                "{} is now {} IQD / {}.",
                plan.title, plan.amount_iqd, plan.interval
            ),
            None,
            None,
            "subscribers",
            Some(claims.sub.parse().ok()).flatten(),
        )
        .await?;
        notified = qasa_db::repos::notification::fanout_receipts(&state.pool, n.id).await?;
        // Push delivery uses FCM (iOS + Android) when QASA_FCM_* is configured.
        tracing::info!(notification_id = %n.id, notified, "pricing change notified");
    }

    Ok(Json(json!({ "plan": plan, "notified_accounts": notified })))
}
