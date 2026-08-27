use axum::extract::ConnectInfo;
use axum::extract::State;
use axum::http::{Request, StatusCode};
use axum::middleware::Next;
use axum::response::{IntoResponse, Response};
use std::net::{IpAddr, Ipv4Addr, SocketAddr};

use crate::state::AppState;

pub async fn count_requests(request: Request<axum::body::Body>, next: Next) -> Response {
    crate::metrics::record_http_request();
    next.run(request).await
}

/// Restrict `/v1/admin/*` when `QASA_ADMIN_ALLOW_CIDRS` is set (e.g. `tailscale`).
pub async fn admin_tailscale_guard(
    State(state): State<AppState>,
    ConnectInfo(addr): ConnectInfo<SocketAddr>,
    request: Request<axum::body::Body>,
    next: Next,
) -> Response {
    let path = request.uri().path();
    if !path.starts_with("/v1/admin") || state.config.admin_allow_cidrs.is_empty() {
        return next.run(request).await;
    }

    let client_ip = request
        .headers()
        .get("x-forwarded-for")
        .and_then(|v| v.to_str().ok())
        .and_then(|s| s.split(',').next())
        .and_then(|s| s.trim().parse::<IpAddr>().ok())
        .unwrap_or_else(|| addr.ip());

    if allow_ip(&state.config.admin_allow_cidrs, client_ip) {
        return next.run(request).await;
    }

    tracing::warn!(%client_ip, path, "admin request blocked (not in allow CIDRs)");
    (
        StatusCode::FORBIDDEN,
        "admin access restricted to allowed networks",
    )
        .into_response()
}

fn allow_ip(cidrs: &[String], ip: IpAddr) -> bool {
    let IpAddr::V4(v4) = ip else {
        return false;
    };
    cidrs.iter().any(|c| match c.as_str() {
        "tailscale" | "100.64.0.0/10" => is_tailscale(v4),
        other => other.parse::<Ipv4Addr>().map(|exact| exact == v4).unwrap_or(false),
    })
}

fn is_tailscale(v4: Ipv4Addr) -> bool {
    let o = v4.octets();
    o[0] == 100 && (64..128).contains(&o[1])
}
