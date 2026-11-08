//! Registers nm-host with the browsers on this computer (plan §8.1). The app does it on
//! every launch, so moving or updating the app fixes the registration by itself:
//!
//! - macOS and Linux: a manifest JSON in each installed browser's user-level
//!   NativeMessagingHosts folder.
//! - Windows: the manifests go in the app data folder, and a per-user registry key for
//!   each browser points at them.
//!
//! Only Qasa's own extension ids may connect (`allowed_origins` / `allowed_extensions`).

use std::path::{Path, PathBuf};

use serde_json::json;

/// Native messaging host names allow only lowercase letters, digits, `_` and `.`.
pub const HOST_NAME: &str = "com.qasa_app.desktop";
/// The unpacked development build's id (its manifest `key`, apps/extension/EXTENSION_IDS.md).
/// Store ids are added when the extension is published (phase 6).
pub const CHROME_EXTENSION_IDS: &[&str] = &["fiamcbmecfeeofaadngagmdldkgncmmd"];
pub const FIREFOX_EXTENSION_ID: &str = "extension@qasa-app.com";

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
enum Family {
    Chromium,
    Firefox,
}

/// The nm-host binary: next to the app's executable (bundled as a sidecar), or the
/// developer build in `apps/desktop/nm-host/target` when running from source.
pub fn nm_host_path() -> Option<PathBuf> {
    let file = if cfg!(windows) { "qasa-nm-host.exe" } else { "qasa-nm-host" };
    if let Some(p) = std::env::var_os("QASA_NM_HOST").map(PathBuf::from).filter(|p| p.is_file()) {
        return Some(p);
    }
    let beside = std::env::current_exe().ok()?.parent()?.join(file);
    if beside.is_file() {
        return Some(beside);
    }
    if cfg!(debug_assertions) {
        let dev = Path::new(env!("CARGO_MANIFEST_DIR")).join("../nm-host/target");
        for profile in ["debug", "release"] {
            let p = dev.join(profile).join(file);
            if p.is_file() {
                return p.canonicalize().ok();
            }
        }
    }
    None
}

fn manifest(family: Family, host: &Path) -> serde_json::Value {
    let mut m = json!({
        "name": HOST_NAME,
        "description": "Qasa password manager",
        "path": host.to_string_lossy(),
        "type": "stdio",
    });
    match family {
        Family::Chromium => {
            m["allowed_origins"] = json!(CHROME_EXTENSION_IDS.iter().map(|id| format!("chrome-extension://{id}/")).collect::<Vec<_>>())
        }
        Family::Firefox => m["allowed_extensions"] = json!([FIREFOX_EXTENSION_ID]),
    }
    m
}

/// Browser config folders (relative to home) whose NativeMessagingHosts folder we write.
#[cfg(target_os = "macos")]
fn browser_dirs() -> Vec<(Family, &'static str, &'static str)> {
    use Family::*;
    vec![
        (Chromium, "Library/Application Support/Google/Chrome", "NativeMessagingHosts"),
        (Chromium, "Library/Application Support/Chromium", "NativeMessagingHosts"),
        (Chromium, "Library/Application Support/Microsoft Edge", "NativeMessagingHosts"),
        (Chromium, "Library/Application Support/BraveSoftware/Brave-Browser", "NativeMessagingHosts"),
        (Chromium, "Library/Application Support/Vivaldi", "NativeMessagingHosts"),
        (Firefox, "Library/Application Support/Mozilla", "NativeMessagingHosts"),
    ]
}

#[cfg(all(unix, not(target_os = "macos")))]
fn browser_dirs() -> Vec<(Family, &'static str, &'static str)> {
    use Family::*;
    vec![
        (Chromium, ".config/google-chrome", "NativeMessagingHosts"),
        (Chromium, ".config/chromium", "NativeMessagingHosts"),
        (Chromium, ".config/microsoft-edge", "NativeMessagingHosts"),
        (Chromium, ".config/BraveSoftware/Brave-Browser", "NativeMessagingHosts"),
        (Chromium, ".config/vivaldi", "NativeMessagingHosts"),
        (Firefox, ".mozilla", "native-messaging-hosts"),
    ]
}

/// Writes or refreshes the registrations. Returns how many browsers were registered.
pub fn install(app_data_dir: &Path) -> usize {
    let Some(host) = nm_host_path() else {
        eprintln!("browser bridge: qasa-nm-host not found, extension can't connect");
        return 0;
    };
    install_with(&host, app_data_dir)
}

#[cfg(unix)]
fn install_with(host: &Path, _app_data_dir: &Path) -> usize {
    let Some(home) = std::env::var_os("HOME").map(PathBuf::from) else { return 0 };
    let mut count = 0;
    for (family, browser, sub) in browser_dirs() {
        let browser_dir = home.join(browser);
        // Only browsers that are installed (their profile folder exists).
        if !browser_dir.is_dir() {
            continue;
        }
        if write_manifest(&browser_dir.join(sub), family, host) {
            count += 1;
        }
    }
    count
}

#[cfg(windows)]
fn install_with(host: &Path, app_data_dir: &Path) -> usize {
    let dir = app_data_dir.join("NativeMessagingHosts");
    let chromium = dir.join(format!("{HOST_NAME}.chromium.json"));
    let firefox = dir.join(format!("{HOST_NAME}.firefox.json"));
    if !write_manifest_at(&chromium, Family::Chromium, host) || !write_manifest_at(&firefox, Family::Firefox, host) {
        return 0;
    }
    let keys = [
        (r"Software\Google\Chrome\NativeMessagingHosts", &chromium),
        (r"Software\Chromium\NativeMessagingHosts", &chromium),
        (r"Software\Microsoft\Edge\NativeMessagingHosts", &chromium),
        (r"Software\BraveSoftware\Brave-Browser\NativeMessagingHosts", &chromium),
        (r"Software\Mozilla\NativeMessagingHosts", &firefox),
    ];
    keys.iter()
        .filter(|(base, path)| {
            // reg.exe keeps this free of a registry crate; HKCU needs no admin rights.
            std::process::Command::new("reg")
                .args(["add", &format!(r"HKCU\{base}\{HOST_NAME}"), "/ve", "/t", "REG_SZ", "/d"])
                .arg(path.as_os_str())
                .arg("/f")
                .output()
                .is_ok_and(|o| o.status.success())
        })
        .count()
}

#[cfg(unix)]
fn write_manifest(dir: &Path, family: Family, host: &Path) -> bool {
    write_manifest_at(&dir.join(format!("{HOST_NAME}.json")), family, host)
}

/// Writes only when the content changed, so launches don't touch browser folders.
fn write_manifest_at(path: &Path, family: Family, host: &Path) -> bool {
    let body = serde_json::to_string_pretty(&manifest(family, host)).unwrap_or_default() + "\n";
    if std::fs::read_to_string(path).is_ok_and(|old| old == body) {
        return true;
    }
    let Some(parent) = path.parent() else { return false };
    std::fs::create_dir_all(parent).is_ok() && std::fs::write(path, body).is_ok()
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn manifests_allow_only_qasa() {
        let chrome = manifest(Family::Chromium, Path::new("/opt/qasa-nm-host"));
        assert_eq!(chrome["name"], HOST_NAME);
        assert_eq!(chrome["type"], "stdio");
        assert!(chrome["allowed_origins"][0].as_str().unwrap().starts_with("chrome-extension://"));
        assert!(chrome.get("allowed_extensions").is_none());
        let firefox = manifest(Family::Firefox, Path::new("/opt/qasa-nm-host"));
        assert_eq!(firefox["allowed_extensions"][0], FIREFOX_EXTENSION_ID);
    }

    #[test]
    fn writes_once_and_only_when_changed() {
        let dir = tempfile::tempdir().unwrap();
        let path = dir.path().join("NativeMessagingHosts/x.json");
        assert!(write_manifest_at(&path, Family::Firefox, Path::new("/a")));
        let first = std::fs::metadata(&path).unwrap().modified().unwrap();
        assert!(write_manifest_at(&path, Family::Firefox, Path::new("/a")));
        assert_eq!(std::fs::metadata(&path).unwrap().modified().unwrap(), first);
        assert!(write_manifest_at(&path, Family::Firefox, Path::new("/b")));
        assert!(std::fs::read_to_string(&path).unwrap().contains("\"/b\""));
    }
}
