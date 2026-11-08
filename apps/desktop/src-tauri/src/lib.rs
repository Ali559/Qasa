//! The Qasa desktop core process. The webview only draws screens: keys, the vault and
//! every network call live here (docs/desktop-extension-plan.md §3).

mod account;
mod bridge;
mod clipboard;
mod prefs;
mod sync;
mod vault;

use std::sync::Mutex;
use std::time::Duration;

use tauri::{Emitter, Manager};

use vault::commands::{self as vc, VaultState};
use vault::tools as vt;
use account::commands::{self as ac, AccountState};
use vault::Vault;

/// How often the auto-lock timer checks for inactivity (iOS `AutoLockMonitor`: 15 s).
const AUTO_LOCK_TICK: Duration = Duration::from_secs(15);

pub fn run() {
    tauri::Builder::default()
        .plugin(tauri_plugin_dialog::init())
        .plugin(tauri_plugin_opener::init())
        .setup(|app| {
            let dir = app.path().app_data_dir()?;
            std::fs::create_dir_all(&dir)?;
            let vault = Vault::open(&dir.join("vault.sqlite")).map_err(|e| e.to_string())?;
            app.manage(VaultState(Mutex::new(vault)));
            let api = account::api::ApiClient::new(account::api::base_url());
            let account = account::Account::new(api, Box::new(account::secrets::KeyringStore));
            app.manage(AccountState(tokio::sync::Mutex::new(account)));
            app.manage(sync::SyncState::default());
            app.manage(bridge::BridgeState(Mutex::new(bridge::Bridge::new(Box::new(bridge::clients::KeyringClients)))));
            // The browser extension: register nm-host with the browsers, then listen.
            bridge::manifest::install(&dir);
            let host = std::sync::Arc::new(bridge::commands::TauriHost { app: app.handle().clone() });
            bridge::server::start(host, bridge::server::socket_path(&dir));
            spawn_auto_lock(app.handle().clone());
            Ok(())
        })
        .invoke_handler(tauri::generate_handler![
            clipboard::clipboard_copy,
            open_url,
            prefs::get_prefs,
            prefs::set_prefs,
            vc::vault_status,
            vc::vault_create,
            vc::vault_unlock,
            vc::vault_lock,
            vc::vault_verify_password,
            vc::vault_biometric_status,
            vc::vault_set_biometric,
            vc::vault_unlock_biometric,
            vc::vault_verify_biometric,
            vc::vault_note_activity,
            vc::vault_list,
            vc::vault_get,
            vc::vault_save,
            vc::vault_delete,
            vc::vault_mark_used,
            vc::collections_list,
            vc::collection_create,
            vc::collection_rename,
            vc::collection_delete,
            vc::vault_change_password,
            vc::vault_wipe,
            vt::vault_health,
            vt::password_generate,
            vt::totp_parse,
            vt::totp_code,
            vt::totp_preview,
            vt::totp_from_image,
            vt::vault_export_csv,
            vt::vault_import_csv,
            sync::sync_host_start,
            sync::sync_browse_start,
            sync::sync_stop,
            sync::sync_join,
            bridge::commands::bridge_clients,
            bridge::commands::bridge_revoke,
            bridge::commands::bridge_pair_decide,
            bridge::commands::bridge_save_decide,
            ac::account_status,
            ac::account_bootstrap,
            ac::account_refresh_license,
            ac::account_sync_locale,
            ac::devices_list,
            ac::device_unlink,
            ac::devices_unlink_others,
            ac::account_clear_local,
            ac::account_delete,
            ac::pricing_get,
            ac::payment_create_intent,
            ac::payment_status,
            ac::payment_simulate_stub,
            ac::subscription_get,
            ac::subscription_set_auto_renew,
            ac::subscription_cancel,
            ac::payment_method_remove,
            ac::notifications_list,
            ac::notifications_unread_count,
            ac::notification_open,
            ac::notifications_read_all,
            ac::notification_delete,
        ])
        .run(tauri::generate_context!())
        .expect("error while running Qasa");
}

/// Opens a website from an item in the default browser. Only http(s), so an item can't
/// launch a local program through a crafted URL.
#[tauri::command]
fn open_url(url: String) -> Result<(), String> {
    let lower = url.to_ascii_lowercase();
    if !(lower.starts_with("https://") || lower.starts_with("http://")) {
        return Err("only web links can be opened".into());
    }
    tauri_plugin_opener::open_url(url, None::<&str>).map_err(|e| e.to_string())
}

/// Locks the vault after the user's auto-lock minutes without activity and tells the UI.
fn spawn_auto_lock(app: tauri::AppHandle) {
    std::thread::spawn(move || loop {
        std::thread::sleep(AUTO_LOCK_TICK);
        let limit = u64::from(prefs::load(&app).auto_lock_minutes()) * 60;
        let state = app.state::<VaultState>();
        let locked = match state.0.lock() {
            Ok(mut v) if v.is_unlocked() && v.idle_seconds() >= limit => {
                v.lock();
                true
            }
            _ => false,
        };
        if locked {
            if let Ok(mut a) = app.state::<AccountState>().0.try_lock() {
                a.on_lock();
            }
            let _ = app.emit("vault-locked", ());
        }
    });
}
