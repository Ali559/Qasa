use axum::extract::ConnectInfo;
use axum::extract::State;
use axum::http::{Request, StatusCode};
use axum::middleware::Next;
use axum::response::{IntoResponse, Response};
use ipnet::{IpNet, Ipv4Net};
use std::net::{IpAddr, SocketAddr};
use std::str::FromStr;

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

    let client_ip = client_ip(&state.config, &request, addr);

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

/// Resolve the address to authorize.
///
/// `X-Forwarded-For` is attacker-controlled unless the request demonstrably came from a
/// proxy we run. The previous version read it unconditionally, so anyone able to reach
/// the API directly could set `X-Forwarded-For: 100.64.1.1`, pass the Tailscale check,
/// and drive the whole admin surface. `QASA_TRUSTED_PROXY_CIDRS` now gates that trust.
fn client_ip<B>(config: &qasa_core::AppConfig, request: &Request<B>, peer: SocketAddr) -> IpAddr {
    if let Some(forwarded) = forwarded_client_ip(config, request) {
        return forwarded;
    }
    peer.ip()
}

/// Returns the leftmost-original address from `X-Forwarded-For`, but only when the
/// immediate peer is a trusted proxy.
fn forwarded_client_ip<B>(
    config: &qasa_core::AppConfig,
    request: &Request<B>,
) -> Option<IpAddr> {
    if config.trusted_proxy_cidrs.is_empty() {
        return None;
    }
    let peer = request.extensions().get::<ConnectInfo<SocketAddr>>()?.0.ip();
    if !matches_net(&config.trusted_proxy_cidrs, peer) {
        return None;
    }
    request
        .headers()
        .get("x-forwarded-for")?
        .to_str()
        .ok()?
        .split(',')
        .map(str::trim)
        .find_map(|s| s.parse::<IpAddr>().ok())
}

fn allow_ip(cidrs: &[String], ip: IpAddr) -> bool {
    matches_net(cidrs, ip)
}

fn matches_net(cidrs: &[String], ip: IpAddr) -> bool {
    cidrs.iter().any(|c| parse_net(c).is_some_and(|net| net.contains(&ip)))
}

/// Parse one allow-list entry.
///
/// Supports the `tailscale` alias plus real CIDR (`100.64.0.0/10`, `10.1.2.3/32`) and
/// bare addresses for both IPv4 and IPv6.
///
/// The old implementation only handled the hardcoded Tailscale range and a bare IPv4
/// address; anything like `10.0.0.0/8` failed to parse and silently evaluated to *deny*,
/// so a valid-looking config locked every admin out with no error.
fn parse_net(entry: &str) -> Option<IpNet> {
    let entry = entry.trim();
    if entry.eq_ignore_ascii_case("tailscale") {
        return Some(IpNet::from(
            Ipv4Net::new(std::net::Ipv4Addr::new(100, 64, 0, 0), 10).ok()?,
        ));
    }
    if let Ok(net) = IpNet::from_str(entry) {
        return Some(net);
    }
    IpAddr::from_str(entry).ok().map(IpNet::from)
}

#[cfg(test)]
mod tests {
    use super::*;

    fn v4(s: &str) -> IpAddr {
        s.parse().unwrap()
    }

    #[test]
    fn tailscale_alias_expands_to_cgnat_range() {
        let net = parse_net("tailscale").unwrap();
        assert!(net.contains(&v4("100.64.0.1")));
        assert!(net.contains(&v4("100.127.255.255")));
        assert!(!net.contains(&v4("100.128.0.1")));
        assert!(!net.contains(&v4("10.0.0.1")));
    }

    /// The regression that motivated real CIDR support: these used to parse as neither
    /// an address nor a net, so they denied everything.
    #[test]
    fn parses_real_cidrs() {
        let net = parse_net("10.0.0.0/8").unwrap();
        assert!(net.contains(&v4("10.1.2.3")));
        assert!(!net.contains(&v4("11.0.0.1")));

        let net = parse_net("192.168.1.0/24").unwrap();
        assert!(net.contains(&v4("192.168.1.255")));
        assert!(!net.contains(&v4("192.168.2.1")));
    }

    #[test]
    fn parses_bare_addresses() {
        let net = parse_net("203.0.113.7").unwrap();
        assert!(net.contains(&v4("203.0.113.7")));
        assert!(!net.contains(&v4("203.0.113.8")));
    }

    #[test]
    fn supports_ipv6() {
        let net = parse_net("fd7a:115c:a1e0::/48").unwrap();
        assert!(net.contains(&"fd7a:115c:a1e0::1".parse::<IpAddr>().unwrap()));
        assert!(!net.contains(&"fd7a:115c:a2e0::1".parse::<IpAddr>().unwrap()));

        let net = parse_net("::1").unwrap();
        assert!(net.contains(&"::1".parse::<IpAddr>().unwrap()));
    }

    #[test]
    fn mixed_family_allow_list() {
        let cidrs = vec!["tailscale".to_string(), "10.0.0.0/8".to_string()];
        assert!(allow_ip(&cidrs, v4("100.100.1.1")));
        assert!(allow_ip(&cidrs, v4("10.9.9.9")));
        assert!(!allow_ip(&cidrs, v4("8.8.8.8")));
    }

    #[test]
    fn empty_or_garbage_allow_list_denies() {
        assert!(!allow_ip(&[], v4("100.64.0.1")));
        assert!(!allow_ip(&["not-an-ip".to_string()], v4("100.64.0.1")));
        // A malformed entry must not accidentally allow the caller.
        assert!(!allow_ip(&["10.0.0.0/99".to_string()], v4("10.0.0.1")));
    }
}
