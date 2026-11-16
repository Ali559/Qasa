//! Which host a login's icon is asked of, and which hosts are never asked
//! (docs/site-icons.md "Which host" and "Never asked").

use url::{Host, Url};

/// Suffixes that are never on the internet: a router or NAS login must never make Qasa
/// touch the local network.
const PRIVATE_SUFFIXES: [&str; 12] =
    ["local", "localhost", "lan", "home", "internal", "intranet", "corp", "home.arpa", "test", "example", "invalid", "onion"];

/// The host whose icon a login shows (also the cache key), read the way autofill reads the
/// website: trimmed; with `://` the scheme must be http(s); without, `https://` is assumed;
/// lowercased, one trailing `.` and a leading `www.` dropped. None when it must never be
/// asked (IP literals, single labels, private suffixes) or isn't a web address.
pub fn host_of(website: &str) -> Option<String> {
    let raw = website.trim();
    if raw.is_empty() {
        return None;
    }
    let with_scheme = if raw.contains("://") { raw.to_string() } else { format!("https://{raw}") };
    let url = Url::parse(&with_scheme).ok()?;
    if !matches!(url.scheme(), "http" | "https") {
        return None;
    }
    let Some(Host::Domain(domain)) = url.host() else { return None };
    let host = domain.to_ascii_lowercase();
    let host = host.strip_suffix('.').unwrap_or(&host);
    let host = host.strip_prefix("www.").unwrap_or(host);
    is_public(host).then(|| host.to_string())
}

/// Whether a normalised host may be asked: a dotted domain name, not an IP literal and not
/// under a private suffix.
pub fn is_public(host: &str) -> bool {
    if host.is_empty() || !host.contains('.') || host.starts_with('[') || host.parse::<std::net::IpAddr>().is_ok() {
        return false;
    }
    // A numeric last label is an IPv4 address in some spelling (`10.1`, `0x7f.1`).
    let last = host.rsplit('.').next().unwrap_or("");
    if last.is_empty() || last.chars().all(|c| c.is_ascii_digit()) || last.starts_with("0x") {
        return false;
    }
    !PRIVATE_SUFFIXES.iter().any(|s| host == *s || host.ends_with(&format!(".{s}")))
}

/// Whether a URL may be requested (the first request and every redirect hop): HTTPS to a
/// host that may be asked.
pub fn allowed_url(url: &Url) -> bool {
    if url.scheme() != "https" {
        return false;
    }
    match url.host() {
        Some(Host::Domain(d)) => {
            let host = d.to_ascii_lowercase();
            is_public(host.strip_suffix('.').unwrap_or(&host))
        }
        _ => false,
    }
}
