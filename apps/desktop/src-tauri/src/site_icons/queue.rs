//! When icons are asked for (docs/site-icons.md "When"): after a login is saved, and
//! lazily when a tile without a fresh entry is drawn. At most 4 requests at a time,
//! de-duplicated by host, only while the vault is unlocked and "Website icons" is on.
//! The UI gets icons as `data:image/png;base64,…` URLs: from `site_icons_get`, then from
//! the `site-icon` event as each request finishes. The webview itself never fetches.

use std::collections::{HashMap, HashSet};
use std::sync::{Arc, Mutex};

use serde::Serialize;
use tauri::{AppHandle, Emitter, Manager, State};
use tokio::sync::Semaphore;

use super::cache::Entry;
use super::fetch::{self, Outcome};
use super::host::host_of;
use crate::vault::commands::VaultState;
use crate::vault::store::now_ms;

/// Requests in flight at once.
pub const CONCURRENCY: usize = 4;
/// Hosts one `site_icons_get` call may name.
const MAX_HOSTS: usize = 1000;

pub struct SiteIconQueue {
    inflight: Mutex<HashSet<String>>,
    permits: Arc<Semaphore>,
    client: Option<reqwest::Client>,
}

impl Default for SiteIconQueue {
    fn default() -> Self {
        Self { inflight: Mutex::new(HashSet::new()), permits: Arc::new(Semaphore::new(CONCURRENCY)), client: fetch::client().ok() }
    }
}

/// The `site-icon` event: a request finished. `icon` is None when the site had none.
#[derive(Serialize, Clone)]
struct IconEvent {
    host: String,
    icon: Option<String>,
}

pub fn data_url(png_base64: &str) -> String {
    format!("data:image/png;base64,{png_base64}")
}

/// Whether the "Website icons" setting is on.
pub fn enabled(app: &AppHandle) -> bool {
    crate::prefs::load(app).site_icons
}

/// Queues a request for `host` unless one is already queued or running. Never takes the
/// vault lock itself (it is called from inside `Vault::save`); the task checks the vault
/// and the setting again before it asks and before it stores.
pub fn request(app: &AppHandle, host: String) {
    let queue = app.state::<SiteIconQueue>();
    let Some(client) = queue.client.clone() else { return };
    if !queue.inflight.lock().map(|mut set| set.insert(host.clone())).unwrap_or(false) {
        return;
    }
    let permits = queue.permits.clone();
    let app = app.clone();
    tauri::async_runtime::spawn(async move {
        let _permit = permits.acquire_owned().await;
        let wanted = || {
            enabled(&app)
                && app.state::<VaultState>().0.lock().is_ok_and(|v| v.is_unlocked() && !v.site_icon_fresh(&host, now_ms()))
        };
        if wanted() {
            let outcome = fetch::fetch_icon(&client, &host).await;
            let now = now_ms();
            let entry = match &outcome {
                Outcome::Icon(png) => Entry::icon(png, now),
                Outcome::NoIcon => Entry::none(now),
                Outcome::Failed => Entry::failed(now),
            };
            // The vault may have locked, or the setting gone off, while the site answered.
            if enabled(&app) {
                let icon = app.state::<VaultState>().0.lock().ok().filter(|v| v.is_unlocked()).and_then(|mut v| {
                    v.put_site_icon(&host, entry);
                    v.site_icon(&host).as_deref().map(data_url)
                });
                if icon.is_some() || outcome != Outcome::Failed {
                    let _ = app.emit("site-icon", IconEvent { host: host.clone(), icon });
                }
            }
        }
        if let Ok(mut set) = app.state::<SiteIconQueue>().inflight.lock() {
            set.remove(&host);
        }
    });
}

/// The cached icons for `hosts` (data URLs; null for none yet / none found), queueing a
/// request for each host without a fresh entry. Empty while locked or with the setting off.
#[tauri::command]
pub fn site_icons_get(app: AppHandle, state: State<'_, VaultState>, hosts: Vec<String>) -> HashMap<String, Option<String>> {
    let mut out = HashMap::new();
    if !enabled(&app) {
        return out;
    }
    let mut wanted = Vec::new();
    {
        let Ok(vault) = state.0.lock() else { return out };
        if !vault.is_unlocked() {
            return out;
        }
        let now = now_ms();
        for host in hosts.into_iter().take(MAX_HOSTS) {
            // Only hosts the rules allow, in their normal form.
            if host_of(&host).as_deref() != Some(host.as_str()) {
                continue;
            }
            if !vault.site_icon_fresh(&host, now) {
                wanted.push(host.clone());
            }
            out.insert(host.clone(), vault.site_icon(&host).as_deref().map(data_url));
        }
    }
    for host in wanted {
        request(&app, host);
    }
    out
}
