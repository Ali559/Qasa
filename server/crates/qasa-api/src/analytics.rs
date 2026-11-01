use axum::extract::State;
use axum::Json;
use serde_json::json;

use crate::auth::AdminAuth;
use crate::error::ApiResult;
use crate::state::AppState;

/// DB-backed analytics for the admin Analytics page.
pub async fn analytics(
    State(state): State<AppState>,
    AdminAuth(_claims): AdminAuth,
) -> ApiResult<Json<serde_json::Value>> {
    let stats = qasa_db::repos::payment::overview_stats(&state.pool).await?;
    let cancels = qasa_db::repos::subscription::list_cancellations(&state.pool, 20).await?;
    let (vpn_bytes, vpn_throttled) =
        qasa_db::repos::vpn_usage::analytics_totals(&state.pool).await?;
    let snap = qasa_db::repos::worker::telemetry_snapshot(&state.pool).await?;

    let payment_funnel = sqlx_funnel(&state).await?;

    Ok(Json(json!({
        "kpis": [
            {
                "label": "MRR",
                "value": format_mrr(stats.mrr_iqd),
                "unit": "IQD",
                "meta": "live subscriptions, monthly equivalent"
            },
            {
                "label": "Paid Subscribers",
                "value": stats.paid_subscribers.to_string(),
                "meta": "paid through this period"
            },
            {
                "label": "Free Devices",
                "value": stats.free_devices.to_string(),
                "meta": "linked"
            },
            {
                "label": "VPN Bytes (period)",
                "value": format_bytes(vpn_bytes),
                "meta": format!("{} throttled", vpn_throttled)
            },
            {
                "label": "Accounts",
                "value": snap.accounts_total.to_string(),
                "meta": "registered"
            },
            {
                "label": "Active Devices",
                "value": snap.devices_active.to_string(),
                "meta": "linked"
            }
        ],
        "funnel": payment_funnel,
        "cancellations": cancels.into_iter().map(|c| json!({
            "id": c.id,
            "planId": c.plan_id,
            "reason": c.cancellation_reason,
            "cancelledAt": c.cancelled_at,
        })).collect::<Vec<_>>(),
    })))
}

async fn sqlx_funnel(state: &AppState) -> ApiResult<serde_json::Value> {
    let intents: i64 = sqlx::query_scalar("SELECT COUNT(*)::bigint FROM payments")
        .fetch_one(&state.pool)
        .await
        .unwrap_or(0);
    let success: i64 =
        sqlx::query_scalar("SELECT COUNT(*)::bigint FROM payments WHERE status = 'success'")
            .fetch_one(&state.pool)
            .await
            .unwrap_or(0);
    let failed: i64 =
        sqlx::query_scalar("SELECT COUNT(*)::bigint FROM payments WHERE status = 'failed'")
            .fetch_one(&state.pool)
            .await
            .unwrap_or(0);
    Ok(json!({
        "intents": intents,
        "success": success,
        "failed": failed,
        "conversion_pct": if intents > 0 {
            (success as f64 / intents as f64) * 100.0
        } else {
            0.0
        }
    }))
}

fn format_mrr(iqd: i64) -> String {
    if iqd >= 1_000_000 {
        format!("{:.1}M", iqd as f64 / 1_000_000.0)
    } else if iqd >= 1_000 {
        format!("{:.1}K", iqd as f64 / 1_000.0)
    } else {
        iqd.to_string()
    }
}

fn format_bytes(bytes: i64) -> String {
    const GB: f64 = 1024.0 * 1024.0 * 1024.0;
    if bytes as f64 >= GB {
        format!("{:.1} GB", bytes as f64 / GB)
    } else if bytes >= 1024 * 1024 {
        format!("{:.1} MB", bytes as f64 / (1024.0 * 1024.0))
    } else {
        format!("{} B", bytes)
    }
}
