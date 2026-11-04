use std::net::SocketAddr;

use axum::extract::{ConnectInfo, Query, State};
use axum::Json;
use chrono::Utc;
use qasa_core::{hash_password, verify_password, CoreError};
use qasa_db::models::AdminUserListItem;
use serde::Deserialize;
use serde_json::json;

use crate::auth::{AdminAuth, AdminWrite};
use crate::error::ApiResult;
use crate::state::AppState;

#[derive(Debug, Deserialize)]
pub struct AdminLoginRequest {
    pub email: String,
    pub password: String,
}

#[derive(Debug, Deserialize)]
pub struct RevokeLicenseRequest {
    /// The license token id to revoke.
    pub jti: String,
    /// Owner of the token. Admin tokens are staff-scoped and carry no account, and
    /// `license_revocations.account_id` is `NOT NULL REFERENCES accounts(id)`.
    pub account_id: uuid::Uuid,
    #[serde(default)]
    pub reason: Option<String>,
}

/// Revoke a license token by `jti`, so every request presenting it is rejected.
///
/// The `license_revocations` table was already consulted on every authenticated call
/// (`is_jti_revoked`) but nothing ever inserted into it, so this is the missing writer.
pub async fn revoke_license(
    State(state): State<AppState>,
    AdminWrite(claims): AdminWrite,
    Json(body): Json<RevokeLicenseRequest>,
) -> ApiResult<Json<serde_json::Value>> {
    let created = qasa_db::repos::device::revoke_license_jti(
        &state.pool,
        &body.jti,
        body.account_id,
        body.reason.as_deref(),
    )
    .await?;
    qasa_db::repos::admin::audit_now(
        &state.pool,
        claims.admin_id()?,
        "revoke_license",
        "account",
        &body.account_id.to_string(),
        json!({ "jti": body.jti, "reason": body.reason, "created": created }),
    )
    .await?;
    Ok(Json(json!({
        "jti": body.jti,
        "account_id": body.account_id,
        "revoked": true,
        // false means it was already revoked; the call still succeeds (idempotent).
        "created": created,
    })))
}

/// Lift a revocation previously created by [`revoke_license`].
pub async fn unrevoke_license(
    State(state): State<AppState>,
    AdminWrite(claims): AdminWrite,
    axum::extract::Path(jti): axum::extract::Path<String>,
) -> ApiResult<Json<serde_json::Value>> {
    let removed = qasa_db::repos::device::unrevoke_license_jti(&state.pool, &jti).await?;
    qasa_db::repos::admin::audit_now(
        &state.pool,
        claims.admin_id()?,
        "unrevoke_license",
        "license",
        &jti,
        json!({ "removed": removed }),
    )
    .await?;
    Ok(Json(json!({ "jti": jti, "revoked": false, "removed": removed })))
}

#[derive(Debug, Deserialize)]
pub struct GrantPremiumRequest {
    pub days: i64,
    /// Required: grants are for support cases and outages, and the reason is what the
    /// audit log is for.
    pub reason: String,
    /// Which plan's quotas apply (VPN data). Defaults to monthly.
    #[serde(default)]
    pub plan_id: Option<String>,
}

/// Give an account Premium without a payment. Stacks onto any time it already has.
pub async fn grant_premium(
    State(state): State<AppState>,
    AdminWrite(claims): AdminWrite,
    axum::extract::Path(account_id): axum::extract::Path<uuid::Uuid>,
    Json(body): Json<GrantPremiumRequest>,
) -> ApiResult<(axum::http::StatusCode, Json<serde_json::Value>)> {
    let reason = body.reason.trim();
    if reason.is_empty() {
        return Err(CoreError::BadRequest("reason is required".into()).into());
    }
    if !(1..=3650).contains(&body.days) {
        return Err(CoreError::BadRequest("days must be between 1 and 3650".into()).into());
    }
    let plan_id = qasa_db::repos::subscription::normalize_plan_id(
        body.plan_id.as_deref().unwrap_or("premium_monthly"),
    );
    qasa_db::repos::plan::get_plan(&state.pool, plan_id)
        .await?
        .ok_or_else(|| CoreError::BadRequest(format!("unknown plan: {plan_id}")))?;
    qasa_db::repos::account::get_account(&state.pool, account_id)
        .await?
        .ok_or(CoreError::NotFound)?;

    let sub = qasa_db::repos::subscription::grant_premium(
        &state.pool,
        account_id,
        plan_id,
        body.days,
        qasa_db::repos::subscription::GrantInfo {
            admin_id: claims.admin_id()?,
            reason,
        },
    )
    .await?;

    Ok((
        axum::http::StatusCode::CREATED,
        Json(json!({
            "subscription": {
                "id": sub.id,
                "account_id": sub.account_id,
                "status": sub.status,
                "plan_id": sub.plan_id,
                "started_at": sub.started_at,
                "expires_at": sub.expires_at,
            },
            "granted_days": body.days,
        })),
    ))
}

/// Take a grant's days back off.
pub async fn revoke_grant(
    State(state): State<AppState>,
    AdminWrite(claims): AdminWrite,
    axum::extract::Path(subscription_id): axum::extract::Path<uuid::Uuid>,
) -> ApiResult<Json<serde_json::Value>> {
    use qasa_db::repos::subscription::RevokeGrantOutcome;
    match qasa_db::repos::subscription::revoke_grant(
        &state.pool,
        claims.admin_id()?,
        subscription_id,
    )
    .await?
    {
        RevokeGrantOutcome::Revoked(sub) => Ok(Json(json!({
            "subscription": {
                "id": sub.id,
                "account_id": sub.account_id,
                "status": sub.status,
                "expires_at": sub.expires_at,
            },
            "revoked": true,
        }))),
        RevokeGrantOutcome::NotFound => Err(CoreError::NotFound.into()),
        RevokeGrantOutcome::AlreadyRevoked => {
            Err(CoreError::BadRequest("grant already revoked".into()).into())
        }
        RevokeGrantOutcome::Superseded => Err(CoreError::BadRequest(
            "grant was replaced by a later subscription; its days can no longer be separated"
                .into(),
        )
        .into()),
    }
}

pub async fn list_grants(
    State(state): State<AppState>,
    _claims: AdminAuth,
) -> ApiResult<Json<serde_json::Value>> {
    let rows = qasa_db::repos::subscription::list_grants(&state.pool, 100).await?;
    Ok(Json(json!({ "grants": rows })))
}

pub async fn list_license_revocations(
    State(state): State<AppState>,
    _claims: AdminAuth,
) -> ApiResult<Json<serde_json::Value>> {
    let rows = qasa_db::repos::device::list_license_revocations(&state.pool).await?;
    Ok(Json(json!({ "revocations": rows })))
}

pub async fn login(
    State(state): State<AppState>,
    ConnectInfo(peer): ConnectInfo<SocketAddr>,
    headers: axum::http::HeaderMap,
    Json(body): Json<AdminLoginRequest>,
) -> ApiResult<Json<serde_json::Value>> {
    let email = body.email.trim().to_lowercase();
    let ip = crate::middleware::resolve_client_ip(&state.config, &headers, peer.ip());
    // Both keys are charged on every attempt, so neither a spray across many emails
    // from one address nor many addresses on one email gets far.
    let by_email = crate::limits::check(&state, crate::limits::ADMIN_LOGIN_EMAIL, &email).await;
    let by_ip = crate::limits::check(&state, crate::limits::ADMIN_LOGIN_IP, &ip.to_string()).await;
    if let Err(limited) = by_email.and(by_ip) {
        tracing::warn!(%ip, email, "admin login rate limited");
        return Err(limited);
    }

    let admin = qasa_db::repos::admin::find_by_email(&state.pool, &email).await?;
    // Verify against a dummy hash when the email is unknown, so response time does not
    // reveal which addresses are admins.
    let hash = admin
        .as_ref()
        .map(|a| a.password_hash.as_str())
        .unwrap_or_else(|| dummy_password_hash());
    let password_ok = verify_password(&body.password, hash)?;
    let Some(admin) = admin.filter(|_| password_ok) else {
        tracing::warn!(%ip, email, "admin login failed");
        return Err(CoreError::InvalidCredentials.into());
    };

    let token = state
        .admin_issuer
        .issue(admin.id, &admin.email, &admin.role)?;

    Ok(Json(json!({
        "token": token,
        "email": admin.email,
        "role": admin.role,
    })))
}

fn dummy_password_hash() -> &'static str {
    static HASH: std::sync::OnceLock<String> = std::sync::OnceLock::new();
    HASH.get_or_init(|| hash_password("not-a-real-admin-password").expect("argon2 hash"))
}

/// End this admin session now rather than at the token's 8-hour expiry.
pub async fn logout(
    State(state): State<AppState>,
    AdminAuth(claims): AdminAuth,
) -> ApiResult<axum::http::StatusCode> {
    let expires_at = chrono::DateTime::from_timestamp(claims.exp as i64, 0)
        .unwrap_or_else(|| Utc::now() + chrono::Duration::hours(8));
    qasa_db::repos::admin::revoke_token(&state.pool, &claims.jti, claims.admin_id()?, expires_at)
        .await?;
    Ok(axum::http::StatusCode::NO_CONTENT)
}

#[derive(Debug, Deserialize)]
pub struct ListQuery {
    pub q: Option<String>,
    pub limit: Option<i64>,
    /// `X-Next-Before` / `next_before` from the previous page.
    pub before: Option<chrono::DateTime<Utc>>,
    pub status: Option<String>,
    pub gateway: Option<String>,
}

/// User directory, newest first. Still a bare array, as the dashboard expects; the
/// cursor for the next page is in the `X-Next-Before` header.
pub async fn list_users(
    State(state): State<AppState>,
    AdminAuth(_claims): AdminAuth,
    Query(query): Query<ListQuery>,
) -> ApiResult<(axum::http::HeaderMap, Json<Vec<AdminUserListItem>>)> {
    let limit = query.limit.unwrap_or(200).clamp(1, 500);
    let accounts = qasa_db::repos::admin_accounts::search(
        &state.pool,
        query.q.as_deref(),
        query.before,
        limit,
    )
    .await?;

    let mut headers = axum::http::HeaderMap::new();
    if accounts.len() as i64 == limit {
        if let Some(last) = accounts.last() {
            if let Ok(v) = last.created_at.to_rfc3339().parse() {
                headers.insert("x-next-before", v);
            }
        }
    }

    let rows = accounts
        .into_iter()
        .map(|account| {
            let live = account.live_expiry.is_some();
            let entitlements = qasa_core::Entitlements::for_tier(qasa_core::effective_tier(
                &account.tier,
                live,
            ));
            // `live_expiry` is only set while a subscription is live. A lapsed account is
            // Free for entitlements but shown as "expired" so staff can find win-backs.
            let status = if account.blocked_at.is_some() {
                "blocked"
            } else if qasa_core::is_lapsed(&account.tier, live) {
                "expired"
            } else if account.active_devices == 0 {
                "idle"
            } else {
                "active"
            };
            let expiry_alert = account
                .live_expiry
                .map(|dt| dt < Utc::now() + chrono::Duration::days(7))
                .unwrap_or(false);
            AdminUserListItem {
                id: account.public_id,
                account_id: account.id.to_string(),
                status: status.into(),
                tier: entitlements.tier.as_str().to_string(),
                devices: format!("{} / {}", account.active_devices, entitlements.device_limit),
                expiry: account
                    .live_expiry
                    .map(|dt| dt.format("%b %d, %Y").to_string())
                    .unwrap_or_else(|| "—".to_string()),
                expiry_alert: if expiry_alert { Some(true) } else { None },
            }
        })
        .collect();

    Ok((headers, Json(rows)))
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
                "meta": "live subscriptions, monthly equivalent",
                "tone": "up"
            },
            {
                "label": "Collected This Month",
                "value": format_mrr(stats.collected_this_month_iqd),
                "unit": "IQD",
                "meta": "successful payments",
                "tone": "flat"
            },
            {
                "label": "Paid Subscribers",
                "value": stats.paid_subscribers.to_string(),
                "meta": "paid through this period",
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
    Query(query): Query<ListQuery>,
) -> ApiResult<Json<serde_json::Value>> {
    let limit = query.limit.unwrap_or(100).clamp(1, 500);
    let payments = qasa_db::repos::payment::search_for_admin(
        &state.pool,
        &qasa_db::repos::payment::PaymentSearch {
            q: query.q.as_deref(),
            status: query.status.as_deref(),
            gateway: query.gateway.as_deref(),
            before: query.before,
        },
        limit,
    )
    .await?;
    let next_before = if payments.len() as i64 == limit {
        payments.last().map(|p| p.created_at)
    } else {
        None
    };
    let rows: Vec<serde_json::Value> = payments
        .into_iter()
        .map(|p| {
            json!({
                "paymentId": p.id,
                "timestamp": p.created_at.format("%Y-%m-%d %H:%M:%S").to_string(),
                "orderId": p.gateway_order_id,
                "userId": p.account_id.to_string(),
                "gateway": capitalize_gateway(&p.gateway),
                "amount": format_amount(p.amount_iqd),
                "planId": p.plan_id,
                "kind": p.kind,
                "status": p.status,
            })
        })
        .collect();
    Ok(Json(json!({ "payments": rows, "next_before": next_before })))
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

#[derive(Debug, Deserialize)]
pub struct WebhookQuery {
    pub gateway_order_id: Option<String>,
}

pub async fn list_webhooks(
    State(state): State<AppState>,
    AdminAuth(_claims): AdminAuth,
    Query(query): Query<WebhookQuery>,
) -> ApiResult<Json<Vec<qasa_db::repos::payment::WebhookEventRow>>> {
    let events = qasa_db::repos::payment::list_webhooks_for_admin(
        &state.pool,
        query.gateway_order_id.as_deref(),
        100,
    )
    .await?;
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
                "id": n.id,
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
                "maxPeers": n.max_peers,
                "endpointPort": n.endpoint_port,
                "lastHeartbeat": n.last_heartbeat,
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
        "wayl" => "Wayl".into(),
        other => other.to_string(),
    }
}
