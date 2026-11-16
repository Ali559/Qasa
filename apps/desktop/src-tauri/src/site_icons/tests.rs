//! The shared cases in docs/site-icons-vectors.json: every platform runs them all.

use serde_json::Value;
use url::Url;

use super::host::{allowed_url, host_of};
use super::letter;
use super::pick::pick_icons;

fn vectors() -> Value {
    let path = concat!(env!("CARGO_MANIFEST_DIR"), "/../../../docs/site-icons-vectors.json");
    serde_json::from_str(&std::fs::read_to_string(path).expect("docs/site-icons-vectors.json")).unwrap()
}

fn cases<'a>(v: &'a Value, name: &str) -> &'a Vec<Value> {
    let list = v[name].as_array().unwrap_or_else(|| panic!("no {name} cases"));
    assert!(!list.is_empty());
    list
}

#[test]
fn host_vectors() {
    let v = vectors();
    for case in cases(&v, "host") {
        let website = case["website"].as_str().unwrap();
        assert_eq!(host_of(website).as_deref(), case["host"].as_str(), "host of {website:?}");
    }
}

#[test]
fn letter_vectors() {
    let v = vectors();
    for case in cases(&v, "letter") {
        let name = case["name"].as_str().unwrap();
        assert_eq!(letter(name), case["letter"].as_str().unwrap(), "letter of {name:?}");
    }
}

#[test]
fn pick_icons_vectors() {
    let v = vectors();
    for case in cases(&v, "pickIcons") {
        let page = Url::parse(case["url"].as_str().unwrap()).unwrap();
        let html = case["html"].as_str().unwrap();
        let want: Vec<&str> = case["icons"].as_array().unwrap().iter().map(|u| u.as_str().unwrap()).collect();
        assert_eq!(pick_icons(&page, html), want, "icons of {html}");
    }
}

#[test]
fn data_icons_are_kept_and_other_data_urls_dropped() {
    let page = Url::parse("https://example.com/").unwrap();
    let html = r#"<!-- <link rel="icon" href="/commented.png"> --><link rel=icon href="data:image/png;base64,AAAA"><link rel="icon" href="data:image/svg+xml;base64,AAAA"><link rel='icon' href='javascript:alert(1)'>"#;
    assert_eq!(pick_icons(&page, html), ["data:image/png;base64,AAAA"]);
}

#[test]
fn redirects_are_checked_like_the_first_request() {
    let ok = |u: &str| allowed_url(&Url::parse(u).unwrap());
    assert!(ok("https://github.com/login"));
    assert!(ok("https://cdn.example.com/a.png"));
    assert!(!ok("http://github.com/"), "never plain http");
    assert!(!ok("https://192.168.1.1/"));
    assert!(!ok("https://[::1]/"));
    assert!(!ok("https://nas.local/"));
    assert!(!ok("https://localhost/"));
    assert!(!ok("https://10.1/"), "a short IPv4 spelling");
}
