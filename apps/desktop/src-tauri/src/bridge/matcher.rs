//! Which logins belong to a page (plan §8.2): the host scores of iOS
//! `AutofillContextMatcher`, with registrable domains taken from the Public Suffix List
//! instead of "the last two labels", so `a.co.uk` and `b.co.uk` are different sites and
//! `user.github.io` doesn't match every other `github.io` page.

use std::net::IpAddr;

/// The extension only sees, and only fills, logins at or above this score: the same
/// registrable domain or better. Weaker matches (a substring) stay in search.
pub const FILL_THRESHOLD: u32 = 60;

/// The lowercased host of an `http(s)` URL, without a leading `www.`. A bare host is
/// read as `https://<host>`, the way items often store their website.
pub fn host_of(raw: &str) -> Option<String> {
    let raw = raw.trim();
    if raw.is_empty() {
        return None;
    }
    let with_scheme = if raw.contains("://") { raw.to_string() } else { format!("https://{raw}") };
    let url = url::Url::parse(&with_scheme).ok()?;
    if !matches!(url.scheme(), "http" | "https") {
        return None;
    }
    let host = url.host_str()?.trim_end_matches('.').to_ascii_lowercase();
    let host = host.strip_prefix("www.").unwrap_or(&host).to_string();
    (!host.is_empty()).then_some(host)
}

/// The site a host belongs to. IP addresses and single-label hosts (`localhost`) are
/// their own site; the PSL's default `*` rule would otherwise merge `10.0.0.1` with
/// `127.0.0.1`.
pub fn registrable_domain(host: &str) -> String {
    let bare = host.trim_start_matches('[').trim_end_matches(']');
    if bare.parse::<IpAddr>().is_ok() || !host.contains('.') {
        return host.to_string();
    }
    psl::domain_str(host).unwrap_or(host).to_string()
}

/// iOS `scoreEntry` for one login: exact host 100, a subdomain of the same site 80, the
/// same site 60, one host containing the other 30, else 0.
pub fn host_score(item_host: &str, page_host: &str) -> u32 {
    if item_host == page_host {
        return 100;
    }
    let same_site = registrable_domain(item_host) == registrable_domain(page_host);
    if same_site && (item_host.ends_with(&format!(".{page_host}")) || page_host.ends_with(&format!(".{item_host}"))) {
        80
    } else if same_site {
        60
    } else if item_host.contains(page_host) || page_host.contains(item_host) {
        30
    } else {
        0
    }
}

/// The score of an item's website for a page URL; 0 if either isn't a web address.
pub fn score(item_website: &str, page_url: &str) -> u32 {
    match (host_of(item_website), host_of(page_url)) {
        (Some(item), Some(page)) => host_score(&item, &page),
        _ => 0,
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn reads_hosts_like_ios() {
        assert_eq!(host_of("https://www.GitHub.com/login?x=1").as_deref(), Some("github.com"));
        assert_eq!(host_of("github.com").as_deref(), Some("github.com"));
        assert_eq!(host_of("http://127.0.0.1:8080/").as_deref(), Some("127.0.0.1"));
        assert_eq!(host_of("javascript:alert(1)"), None);
        assert_eq!(host_of("ftp://example.com"), None);
        assert_eq!(host_of("  "), None);
    }

    #[test]
    fn scores_match_the_ios_table() {
        assert_eq!(score("github.com", "https://github.com/login"), 100);
        assert_eq!(score("github.com", "https://gist.github.com/"), 80);
        assert_eq!(score("accounts.google.com", "https://mail.google.com"), 60);
        assert_eq!(score("google.com", "https://google.com.evil.io"), 30);
        assert_eq!(score("github.com", "https://gitlab.com"), 0);
    }

    #[test]
    fn public_suffixes_separate_sites() {
        assert_eq!(registrable_domain("a.example.co.uk"), "example.co.uk");
        assert!(score("alice.github.io", "https://mallory.github.io") < FILL_THRESHOLD);
        assert!(score("github.io", "https://mallory.github.io") < FILL_THRESHOLD);
        assert!(score("shop.co.uk", "https://evil.co.uk") < FILL_THRESHOLD);
    }

    /// The bundled site icons (`assets/site-icons`) are matched by walking a host's parent
    /// domains on every platform, which only equals a registrable-domain match while each
    /// listed domain is itself a registrable domain, never a public suffix like `github.io`.
    #[test]
    fn site_icon_domains_are_registrable_domains() {
        let sites: serde_json::Value =
            serde_json::from_str(include_str!("../../../../../assets/site-icons/sites.json")).unwrap();
        let sites = sites["sites"].as_array().unwrap();
        assert!(sites.len() >= 50);
        for site in sites {
            for domain in site["domains"].as_array().unwrap() {
                let domain = domain.as_str().unwrap();
                assert_eq!(registrable_domain(domain), domain, "{domain} is not a registrable domain");
            }
        }
    }

    #[test]
    fn ip_addresses_and_localhost_are_their_own_site() {
        assert!(score("http://10.0.0.1", "http://127.0.0.1") < FILL_THRESHOLD);
        assert_eq!(score("http://127.0.0.1:3000", "http://127.0.0.1:4000/login"), 100);
        assert_eq!(score("localhost", "http://localhost:5173"), 100);
        assert!(score("localhost", "http://127.0.0.1") < FILL_THRESHOLD);
    }
}
