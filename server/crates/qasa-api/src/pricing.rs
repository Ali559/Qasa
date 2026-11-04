use axum::extract::State;
use axum::Json;
use qasa_core::CoreError;
use serde::Deserialize;
use qasa_payments::GatewayKind;
use serde_json::json;

use crate::auth::{AdminAuth, AdminWrite};
use crate::error::ApiResult;
use crate::state::AppState;

pub async fn list_public(
    State(state): State<AppState>,
) -> ApiResult<Json<serde_json::Value>> {
    let plans = qasa_db::repos::plan::list_active(&state.pool).await?;
    // What leaving auto-renew on means for each wallet the apps offer, so the checkout
    // screen can say it truthfully: "charge" (we charge the saved wallet), "link" (we
    // send a payment link before each period ends) or "none".
    let auto_renew: serde_json::Map<String, serde_json::Value> =
        [GatewayKind::Fib, GatewayKind::Fastpay, GatewayKind::Superqi]
            .into_iter()
            .map(|k| {
                let mode = state.gateways.renewal_mode_for_checkout(k).as_str();
                (k.as_str().to_string(), json!(mode))
            })
            .collect();
    Ok(Json(json!({ "plans": plans, "auto_renew": auto_renew })))
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
    AdminWrite(claims): AdminWrite,
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

    qasa_db::repos::admin::audit_now(
        &state.pool,
        claims.admin_id()?,
        "upsert_plan",
        "plan",
        &plan.id,
        json!({
            "title": plan.title,
            "amount_iqd": plan.amount_iqd,
            "days": plan.days,
            "vpn_quota_gb": plan.vpn_quota_gb,
            "active": plan.active,
            "notify_subscribers": body.notify_subscribers,
        }),
    )
    .await?;

    let mut notified = 0i64;
    if body.notify_subscribers {
        let (body_en, body_ckb, body_ar) = pricing_notice_bodies(&plan);
        let n = qasa_db::repos::notification::create(
            &state.pool,
            "Pricing updated",
            Some("نرخی نوێکرایەوە"),
            Some("تم تحديث الأسعار"),
            &body_en,
            Some(&body_ckb),
            Some(&body_ar),
            "subscribers",
            Some(claims.sub.parse().ok()).flatten(),
        )
        .await?;
        notified = qasa_db::repos::notification::fanout_receipts(&state.pool, n.id).await?;
        tracing::info!(notification_id = %n.id, notified, "pricing change notified");
    }

    Ok(Json(json!({ "plan": plan, "notified_accounts": notified })))
}

/// The pricing-change notice body in each app language. It used to be English only, so
/// Kurdish and Arabic users got an English body under a translated title.
///
/// The ckb and ar wording awaits native review with the other 2026-11-01 strings.
fn pricing_notice_bodies(plan: &qasa_db::repos::plan::PlanRow) -> (String, String, String) {
    let amount = group_thousands(plan.amount_iqd);
    let (per_en, per_ckb, per_ar) = match plan.interval.as_str() {
        "yearly" => ("year", "ساڵ", "سنة"),
        _ => ("month", "مانگ", "شهر"),
    };
    (
        format!("{} is now {} IQD / {}.", plan.title, amount, per_en),
        format!("{} ئێستا {} دینارە بۆ هەر {}.", plan.title, amount, per_ckb),
        format!("أصبح سعر {} الآن {} دينار لكل {}.", plan.title, amount, per_ar),
    )
}

fn group_thousands(n: i32) -> String {
    let digits = n.to_string();
    let mut out = String::new();
    for (i, ch) in digits.chars().enumerate() {
        if i > 0 && (digits.len() - i) % 3 == 0 {
            out.push(',');
        }
        out.push(ch);
    }
    out
}

#[cfg(test)]
mod tests {
    use super::*;

    fn plan(interval: &str, amount: i32) -> qasa_db::repos::plan::PlanRow {
        qasa_db::repos::plan::PlanRow {
            id: "p".into(),
            title: "Premium".into(),
            interval: interval.into(),
            amount_iqd: amount,
            days: 30,
            vpn_quota_gb: 100,
            active: true,
            sort_order: 0,
            features_json: serde_json::json!([]),
            updated_at: chrono::Utc::now(),
            created_at: chrono::Utc::now(),
        }
    }

    #[test]
    fn pricing_notice_is_localised() {
        let (en, ckb, ar) = pricing_notice_bodies(&plan("yearly", 100_000));
        assert_eq!(en, "Premium is now 100,000 IQD / year.");
        assert!(ckb.contains("100,000") && ckb.contains("ساڵ"), "{ckb}");
        assert!(ar.contains("100,000") && ar.contains("سنة"), "{ar}");

        let (en, _, ar) = pricing_notice_bodies(&plan("monthly", 9_500));
        assert_eq!(en, "Premium is now 9,500 IQD / month.");
        assert!(ar.contains("شهر"));
    }
}
