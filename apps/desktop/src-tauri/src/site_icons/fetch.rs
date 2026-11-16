//! Asks one site for its icon (docs/site-icons.md "The request"). HTTPS only, no cookies
//! or credentials, no Referer, a generic User-Agent and no Accept-Language, at most 3
//! redirects each re-checked against the host rules, 8 s per request, and capped reads.

use std::time::Duration;

use base64::Engine;
use reqwest::redirect::{Attempt, Policy};
use reqwest::{header, Client, Response};
use url::Url;

use super::decode::normalise;
use super::host::allowed_url;
use super::pick::pick_icons;

pub const USER_AGENT: &str = "Mozilla/5.0 (compatible)";
pub const TIMEOUT: Duration = Duration::from_secs(8);
pub const MAX_REDIRECTS: usize = 3;
/// The page is read up to this; the rest is ignored (icons are declared in `<head>`).
pub const MAX_PAGE_BYTES: usize = 512 * 1024;
/// An icon larger than this is skipped.
pub const MAX_ICON_BYTES: usize = 256 * 1024;

/// What asking a site gave.
#[derive(Debug, PartialEq, Eq)]
pub enum Outcome {
    /// The normalised 64 × 64 PNG.
    Icon(Vec<u8>),
    /// The site answered but had no usable icon.
    NoIcon,
    /// Nothing answered (offline, timeout, TLS error, a refused redirect).
    Failed,
}

/// Follows a redirect only to an HTTPS URL on a host that may be asked, at most 3 times.
fn redirect(attempt: Attempt) -> reqwest::redirect::Action {
    // `previous` holds every URL already requested, the first one included.
    if attempt.previous().len() > MAX_REDIRECTS {
        attempt.error("too many redirects")
    } else if allowed_url(attempt.url()) {
        attempt.follow()
    } else {
        attempt.error("redirect to a host that is never asked")
    }
}

/// The client every icon request uses: its own (never the account API's), without a
/// cookie store (reqwest is built without one) and without a cache.
pub fn client() -> reqwest::Result<Client> {
    Client::builder()
        .user_agent(USER_AGENT)
        .redirect(Policy::custom(redirect))
        .referer(false)
        .https_only(true)
        .timeout(TIMEOUT)
        .connect_timeout(TIMEOUT)
        .build()
}

/// Reads at most `cap` bytes of the body; true when there was more.
async fn read_capped(mut response: Response, cap: usize) -> reqwest::Result<(Vec<u8>, bool)> {
    let mut body = Vec::new();
    while let Some(chunk) = response.chunk().await? {
        let room = cap - body.len();
        if chunk.len() > room {
            body.extend_from_slice(&chunk[..room]);
            return Ok((body, true));
        }
        body.extend_from_slice(&chunk);
    }
    Ok((body, false))
}

/// One icon URL: Some(png) when usable; Err when nothing answered.
async fn try_icon(client: &Client, url: &str) -> Result<Option<Vec<u8>>, ()> {
    if let Some(data) = url.split_once(";base64,").filter(|_| url.starts_with("data:")).map(|(_, d)| d) {
        let bytes = base64::engine::general_purpose::STANDARD.decode(data.trim()).ok();
        return Ok(bytes.filter(|b| b.len() <= MAX_ICON_BYTES).and_then(|b| normalise(&b)));
    }
    let Ok(parsed) = Url::parse(url) else { return Ok(None) };
    if !allowed_url(&parsed) {
        return Ok(None);
    }
    let response = client.get(parsed).header(header::ACCEPT, "image/*").send().await.map_err(|_| ())?;
    if !response.status().is_success() || response.content_length().is_some_and(|n| n > MAX_ICON_BYTES as u64) {
        return Ok(None);
    }
    match read_capped(response, MAX_ICON_BYTES).await {
        Ok((bytes, false)) => Ok(normalise(&bytes)),
        Ok((_, true)) => Ok(None),
        Err(_) => Err(()),
    }
}

/// Asks `host` (already checked by `host_of`) for its icon.
pub async fn fetch_icon(client: &Client, host: &str) -> Outcome {
    let Ok(home) = Url::parse(&format!("https://{host}/")) else { return Outcome::NoIcon };
    if !allowed_url(&home) {
        return Outcome::NoIcon;
    }
    let mut answered = false;
    let mut candidates = Vec::new();
    if let Ok(response) = client.get(home.clone()).header(header::ACCEPT, "text/html").send().await {
        answered = true;
        let page = response.url().clone();
        if response.status().is_success() {
            if let Ok((body, _)) = read_capped(response, MAX_PAGE_BYTES).await {
                candidates = pick_icons(&page, &String::from_utf8_lossy(&body));
            }
        }
    }
    if let Ok(fallback) = home.join("/favicon.ico") {
        let fallback = fallback.to_string();
        if !candidates.contains(&fallback) {
            candidates.push(fallback);
        }
    }
    for url in &candidates {
        match try_icon(client, url).await {
            Ok(Some(png)) => return Outcome::Icon(png),
            Ok(None) => answered = true,
            Err(()) => {}
        }
    }
    if answered { Outcome::NoIcon } else { Outcome::Failed }
}
