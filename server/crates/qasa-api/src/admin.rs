use axum::extract::State;
use axum::Json;
use chrono::Utc;
use qasa_core::{verify_password, CoreError};
use qasa_db::models::AdminUserListItem;
use serde::Deserialize;
use serde_json::json;

use crate::auth::AdminAuth;
use crate::entitlements::resolve_entitlements;
use crate::error::ApiResult;
use crate::state::AppState;

#[derive(Debug, Deserialize)]
pub struct AdminLoginRequest {
    pub email: String,
    pub password: String,
}

pub async fn login(
    State(state): State<AppState>,
    Json(body): Json<AdminLoginRequest>,
) -> ApiResult<Json<serde_json::Value>> {
    let admin = qasa_db::repos::admin::find_by_email(&state.pool, &body.email)
        .await?
        .ok_or(CoreError::InvalidCredentials)?;

    if !verify_password(&body.password, &admin.password_hash)? {
        return Err(CoreError::InvalidCredentials.into());
    }

    let token = state
        .admin_issuer
        .issue(admin.id, &admin.email, &admin.role)?;

    Ok(Json(json!({
        "token": token,
        "email": admin.email,
        "role": admin.role,
    })))
}

pub async fn list_users(
    State(state): State<AppState>,
    AdminAuth(_claims): AdminAuth,
) -> ApiResult<Json<Vec<AdminUserListItem>>> {
    let accounts = qasa_db::repos::account::list_accounts_for_admin(&state.pool).await?;
    let mut rows = Vec::with_capacity(accounts.len());

    for account in accounts {
        let device_count =
            qasa_db::repos::device::count_active(&state.pool, account.id).await?;
        let entitlements =
            resolve_entitlements(&state, account.id, &account.tier).await?;
        let expiry = qasa_db::repos::subscription::latest_expiry(&state.pool, account.id).await?;

        let tier = entitlements.tier.as_str().to_string();
        let status = if tier == "expired" {
            "expired"
        } else if device_count == 0 {
            "idle"
        } else {
            "active"
        };

        let expiry_str = expiry
            .map(|dt| dt.format("%b %d, %Y").to_string())
            .unwrap_or_else(|| "—".to_string());

        let expiry_alert = expiry
            .map(|dt| dt < Utc::now() + chrono::Duration::days(7))
            .unwrap_or(false);

        rows.push(AdminUserListItem {
            id: account.public_id,
            status: status.into(),
            tier,
            devices: format!("{} / {}", device_count, entitlements.device_limit),
            expiry: expiry_str,
            expiry_alert: if expiry_alert { Some(true) } else { None },
        });
    }

    Ok(Json(rows))
}

pub async fn overview(
    State(state): State<AppState>,
    AdminAuth(_claims): AdminAuth,
) -> ApiResult<Json<serde_json::Value>> {
    let stats = qasa_db::repos::payment::overview_stats(&state.pool).await?;

    Ok(Json(json!({
        "kpis": [
            {
                "label": "Monthly Recurring Rev",
                "value": format_mrr(stats.mrr_iqd),
                "unit": "IQD",
                "meta": "current month",
                "tone": "up"
            },
            {
                "label": "Paid Subscribers",
                "value": stats.paid_subscribers.to_string(),
                "meta": "active subscriptions",
                "tone": "up"
            },
            {
                "label": "Active Free Devices",
                "value": stats.free_devices.to_string(),
                "meta": "linked devices",
                "tone": "flat"
            }
        ]
    })))
}

pub async fn list_payments(
    State(state): State<AppState>,
    AdminAuth(_claims): AdminAuth,
) -> ApiResult<Json<serde_json::Value>> {
    let payments = qasa_db::repos::payment::list_for_admin(&state.pool, 100).await?;
    let rows: Vec<serde_json::Value> = payments
        .into_iter()
        .map(|p| {
            json!({
                "timestamp": p.created_at.format("%Y-%m-%d %H:%M:%S").to_string(),
                "orderId": p.gateway_order_id,
                "userId": p.account_id.to_string(),
                "gateway": capitalize_gateway(&p.gateway),
                "amount": format_amount(p.amount_iqd),
                "planId": p.plan_id,
                "status": p.status,
            })
        })
        .collect();
    Ok(Json(json!({ "payments": rows })))
}

pub async fn list_cancellations(
    State(state): State<AppState>,
    AdminAuth(_claims): AdminAuth,
) -> ApiResult<Json<serde_json::Value>> {
    let rows = qasa_db::repos::subscription::list_cancellations(&state.pool, 100).await?;
    let items: Vec<serde_json::Value> = rows
        .into_iter()
        .map(|r| {
            json!({
                "id": r.id,
                "accountId": r.account_id,
                "planId": r.plan_id,
                "amountIqd": r.amount_iqd,
                "reason": r.cancellation_reason,
                "cancelledAt": r.cancelled_at,
                "expiresAt": r.expires_at,
            })
        })
        .collect();
    Ok(Json(json!({ "cancellations": items })))
}

pub async fn list_webhooks(
    State(state): State<AppState>,
    AdminAuth(_claims): AdminAuth,
) -> ApiResult<Json<Vec<qasa_db::repos::payment::WebhookEventRow>>> {
    let events = qasa_db::repos::payment::list_webhooks_for_admin(&state.pool, 100).await?;
    Ok(Json(events))
}

pub async fn list_vpn_nodes(
    State(state): State<AppState>,
    AdminAuth(_claims): AdminAuth,
) -> ApiResult<Json<serde_json::Value>> {
    let kpis = qasa_db::repos::vpn::vpn_admin_kpis(&state.pool).await?;
    let nodes = qasa_db::repos::vpn::list_all_nodes(&state.pool).await?;

    let node_rows: Vec<serde_json::Value> = nodes
        .into_iter()
        .map(|n| {
            json!({
                "name": n.name,
                "city": n.city,
                "status": n.status,
                "ip": n.public_ip,
                "service": n.service_name,
                "cpu": n.cpu_percent,
                "memory": n.memory_percent,
                "memoryLabel": n.memory_label.unwrap_or_else(|| format!("{}%", n.memory_percent)),
                "bandwidth": n.bandwidth_label.unwrap_or_else(|| "0 Mbps".into()),
                "peers": n.active_peers.to_string(),
            })
        })
        .collect();

    Ok(Json(json!({
        "kpis": [
            { "label": "Total Active Peers", "value": kpis.total_peers.to_string(), "meta": "live" },
            { "label": "Node Uptime", "value": format!("{:.2}%", kpis.uptime_pct), "meta": "online/total" },
        ],
        "nodes": node_rows,
    })))
}

pub async fn telemetry(
    State(state): State<AppState>,
    AdminAuth(_claims): AdminAuth,
) -> ApiResult<Json<serde_json::Value>> {
    let snap = qasa_db::repos::worker::telemetry_snapshot(&state.pool).await?;

    Ok(Json(json!({
        "metrics": [
            {
                "label": "Active VPN Sessions",
                "value": snap.active_sessions.to_string(),
                "meta": "live",
                "tone": "good"
            },
            {
                "label": "Registered Accounts",
                "value": snap.accounts_total.to_string(),
                "meta": "total",
                "tone": "good"
            },
            {
                "label": "Active Devices",
                "value": snap.devices_active.to_string(),
                "meta": "linked",
                "tone": "warn"
            },
            {
                "label": "Environment",
                "value": state.config.env.as_str(),
                "meta": "QASA_ENV",
                "tone": "good"
            }
        ]
    })))
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

fn format_amount(iqd: i32) -> String {
    let s = iqd.to_string();
    let mut out = String::new();
    for (i, ch) in s.chars().rev().enumerate() {
        if i > 0 && i % 3 == 0 {
            out.push(',');
        }
        out.push(ch);
    }
    out.chars().rev().collect()
}

fn capitalize_gateway(gateway: &str) -> String {
    match gateway {
        "fib" => "FIB".into(),
        "fastpay" => "FastPay".into(),
        "superqi" => "SuperQi".into(),
        other => other.to_string(),
    }
}
