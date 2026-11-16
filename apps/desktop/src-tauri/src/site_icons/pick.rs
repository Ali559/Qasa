//! Picks the icons a page declares (docs/site-icons.md "The request", steps 2–3): every
//! `<link>` whose `rel` names an icon, resolved against the page, without SVG, in the order
//! they are tried. A small tolerant tag scanner: pages are untrusted and often malformed,
//! and only `<link>` attributes are read.

use std::cmp::Reverse;

use url::Url;

/// The `data:` icons that are taken as they are (no request).
const DATA_PREFIXES: [&str; 2] = ["data:image/png;base64,", "data:image/x-icon;base64,"];

struct Candidate {
    url: String,
    apple: bool,
    size: u32,
}

/// The icon URLs to try for `html` served at `page`, best first: `apple-touch-icon*`, then
/// by the largest `sizes` entry (`any` and missing count as 0), ties in document order.
/// Only `https:` URLs and PNG / ICO `data:` URLs; no SVG. The `/favicon.ico` fallback is
/// not included.
pub fn pick_icons(page: &Url, html: &str) -> Vec<String> {
    let mut found: Vec<Candidate> = links(html).into_iter().filter_map(|attrs| candidate(page, &attrs)).collect();
    found.sort_by_key(|c| (!c.apple, Reverse(c.size)));
    let mut out: Vec<String> = Vec::with_capacity(found.len());
    for c in found {
        if !out.contains(&c.url) {
            out.push(c.url);
        }
    }
    out
}

fn attr<'a>(attrs: &'a [(String, String)], name: &str) -> Option<&'a str> {
    attrs.iter().find(|(n, _)| n == name).map(|(_, v)| v.as_str())
}

fn candidate(page: &Url, attrs: &[(String, String)]) -> Option<Candidate> {
    let rel = attr(attrs, "rel")?.to_ascii_lowercase();
    let tokens: Vec<&str> = rel.split_ascii_whitespace().collect();
    let apple = tokens.iter().any(|t| matches!(*t, "apple-touch-icon" | "apple-touch-icon-precomposed"));
    if !apple && !tokens.contains(&"icon") {
        return None;
    }
    let href = attr(attrs, "href")?.trim();
    if href.is_empty() {
        return None;
    }
    if attr(attrs, "type").is_some_and(|t| t.trim().eq_ignore_ascii_case("image/svg+xml")) {
        return None;
    }
    let lower = href.to_ascii_lowercase();
    let url = if DATA_PREFIXES.iter().any(|p| lower.starts_with(p)) {
        href.to_string()
    } else {
        let resolved = page.join(href).ok()?;
        if resolved.scheme() != "https" || resolved.path().to_ascii_lowercase().ends_with(".svg") {
            return None;
        }
        resolved.to_string()
    };
    Some(Candidate { url, apple, size: largest_size(attr(attrs, "sizes").unwrap_or("")) })
}

/// The largest side named in a `sizes` attribute (`16x16 48x48` → 48); `any` and junk are 0.
fn largest_size(sizes: &str) -> u32 {
    sizes
        .split_ascii_whitespace()
        .filter_map(|s| {
            let s = s.to_ascii_lowercase();
            let (w, h) = s.split_once('x')?;
            Some(w.parse::<u32>().ok()?.max(h.parse::<u32>().ok()?))
        })
        .max()
        .unwrap_or(0)
}

/// The attributes of every `<link>` tag outside comments, names lowercased.
fn links(html: &str) -> Vec<Vec<(String, String)>> {
    // ASCII lowercasing keeps byte offsets, so positions found in `lower` index `html`.
    let lower = html.to_ascii_lowercase();
    let bytes = html.as_bytes();
    let mut out = Vec::new();
    let mut i = 0;
    while let Some(off) = lower[i..].find('<') {
        let start = i + off;
        if lower[start..].starts_with("<!--") {
            i = lower[start + 4..].find("-->").map_or(lower.len(), |e| start + 4 + e + 3);
            continue;
        }
        let after = start + 5;
        let is_link = lower[start..].starts_with("<link")
            && bytes.get(after).is_none_or(|b| b.is_ascii_whitespace() || *b == b'/' || *b == b'>');
        if !is_link {
            i = start + 1;
            continue;
        }
        let (attrs, end) = attributes(html, after);
        out.push(attrs);
        i = end;
    }
    out
}

/// Reads attributes from `pos` to the tag's `>`; returns them and the index after the tag.
fn attributes(html: &str, mut pos: usize) -> (Vec<(String, String)>, usize) {
    let bytes = html.as_bytes();
    let mut attrs = Vec::new();
    loop {
        while pos < bytes.len() && (bytes[pos].is_ascii_whitespace() || bytes[pos] == b'/') {
            pos += 1;
        }
        if pos >= bytes.len() {
            return (attrs, pos);
        }
        if bytes[pos] == b'>' {
            return (attrs, pos + 1);
        }
        let name_start = pos;
        while pos < bytes.len() && !bytes[pos].is_ascii_whitespace() && !matches!(bytes[pos], b'=' | b'>' | b'/') {
            pos += 1;
        }
        let name = html[name_start..pos].to_ascii_lowercase();
        while pos < bytes.len() && bytes[pos].is_ascii_whitespace() {
            pos += 1;
        }
        let mut value = String::new();
        if pos < bytes.len() && bytes[pos] == b'=' {
            pos += 1;
            while pos < bytes.len() && bytes[pos].is_ascii_whitespace() {
                pos += 1;
            }
            if pos < bytes.len() && (bytes[pos] == b'"' || bytes[pos] == b'\'') {
                let quote = bytes[pos];
                let value_start = pos + 1;
                let value_end = bytes[value_start..].iter().position(|b| *b == quote).map_or(bytes.len(), |e| value_start + e);
                value = html[value_start..value_end].to_string();
                pos = (value_end + 1).min(bytes.len());
            } else {
                let value_start = pos;
                while pos < bytes.len() && !bytes[pos].is_ascii_whitespace() && bytes[pos] != b'>' {
                    pos += 1;
                }
                value = html[value_start..pos].to_string();
            }
        }
        if !name.is_empty() && !attrs.iter().any(|(n, _)| *n == name) {
            attrs.push((name, unescape(&value)));
        }
    }
}

/// The entities a URL attribute commonly carries.
fn unescape(value: &str) -> String {
    if !value.contains('&') {
        return value.to_string();
    }
    value.replace("&amp;", "&").replace("&quot;", "\"").replace("&#39;", "'").replace("&apos;", "'").replace("&lt;", "<").replace("&gt;", ">")
}
