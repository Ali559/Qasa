//! The bridge inside the running app: the [`Host`] over Tauri state, and the commands the
//! approval prompts and Settings → Browser extension call.

use std::sync::Mutex;

use tauri::{AppHandle, Emitter, Manager, State, UserAttentionType};

use super::clients::ClientInfo;
use super::{Auth, BridgeState, ConfirmPrompt, Host, PairPrompt, SavePrompt};
use crate::vault::commands::VaultState;
use crate::vault::error::CommandResult;
use crate::vault::Vault;

pub struct TauriHost {
    pub app: AppHandle,
}

/// The Touch ID / Hello prompt texts for bridge requests, in the UI language (the UI sets
/// them; English until it does).
pub struct AuthReasons(pub Mutex<(String, String)>);

impl Default for AuthReasons {
    fn default() -> Self {
        AuthReasons(Mutex::new(("Unlock Qasa to fill in your browser".into(), "Confirm to fill your card".into())))
    }
}

impl TauriHost {
    fn reasons(&self) -> (String, String) {
        self.app.try_state::<AuthReasons>().and_then(|r| r.0.lock().ok().map(|r| r.clone())).unwrap_or_default()
    }
}

impl Host for TauriHost {
    fn bridge(&self) -> &BridgeState {
        self.app.state::<BridgeState>().inner()
    }

    fn vault(&self) -> &Mutex<Vault> {
        &self.app.state::<VaultState>().inner().0
    }

    fn device_name(&self) -> String {
        whoami::devicename()
    }

    fn focus(&self) {
        if let Some(w) = self.app.get_webview_window("main") {
            let _ = w.unminimize();
            let _ = w.show();
            let _ = w.set_focus();
        }
    }

    fn copy(&self, value: String, clear_after_ms: u64) {
        if let Err(e) = crate::clipboard::clipboard_copy(value, Some(clear_after_ms)) {
            eprintln!("browser bridge: copy failed: {e}");
        }
    }

    fn show_pair(&self, prompt: Option<PairPrompt>) {
        let _ = self.app.emit("bridge-pair", prompt);
    }

    /// The prompt waits in the app; the Dock icon bounces once instead of the window
    /// jumping over the browser mid-sign-in.
    fn show_save(&self, prompt: SavePrompt) {
        let _ = self.app.emit("bridge-save", prompt);
        if let Some(w) = self.app.get_webview_window("main") {
            let _ = w.request_user_attention(Some(UserAttentionType::Informational));
        }
    }

    fn show_confirm(&self, prompt: Option<ConfirmPrompt>) {
        let _ = self.app.emit("bridge-confirm", prompt);
    }

    fn start_biometric(&self, auth: Auth) -> bool {
        use crate::vault::biometrics::{Biometrics, PlatformBiometrics};
        let sealed = self.vault().lock().ok().and_then(|v| v.sealed().ok().flatten());
        let Some(sealed) = sealed else { return false };
        let (unlock_reason, confirm_reason) = self.reasons();
        let app = self.app.clone();
        std::thread::spawn(move || {
            let host = TauriHost { app };
            let reason = match &auth {
                Auth::Unlock => unlock_reason,
                Auth::Confirm(p) => format!("{confirm_reason}: {}", p.item_title),
            };
            let result = PlatformBiometrics.unseal(&sealed, &reason);
            let invalidated = matches!(result, Err(ref e) if !matches!(e, qasa_biometric::Error::Cancelled));
            if invalidated {
                if let Ok(mut v) = host.vault().lock() {
                    if !matches!(result, Err(qasa_biometric::Error::Unavailable)) {
                        let _ = v.forget_seal();
                    }
                }
            }
            match auth {
                Auth::Unlock => match result {
                    Ok(root) => {
                        let unlocked = host
                            .vault()
                            .lock()
                            .map(|mut v| v.unlock_with_root(qasa_vault::RootKey::from_bytes(root)).is_ok())
                            .unwrap_or(false);
                        if unlocked {
                            let _ = host.app.emit("vault-unlocked", ());
                        } else {
                            host.focus();
                        }
                    }
                    // Dismissed, or the seal is gone: the master password in the window.
                    Err(_) => host.focus(),
                },
                Auth::Confirm(prompt) => {
                    if invalidated {
                        // No usable Touch ID / Hello: ask for the master password instead.
                        host.show_confirm(Some(prompt));
                        host.focus();
                        return;
                    }
                    let approved = match result {
                        Ok(root) => host
                            .vault()
                            .lock()
                            .ok()
                            .and_then(|v| v.root_matches(&qasa_vault::RootKey::from_bytes(root)).ok())
                            .unwrap_or(false),
                        Err(_) => false,
                    };
                    if let Ok(mut b) = host.bridge().0.lock() {
                        b.finish_confirm(&prompt.id, approved, &host);
                    }
                }
            }
        });
        true
    }
}

#[tauri::command]
pub fn bridge_clients(state: State<'_, BridgeState>) -> Vec<ClientInfo> {
    state.0.lock().map(|b| b.clients()).unwrap_or_default()
}

#[tauri::command]
pub fn bridge_revoke(state: State<'_, BridgeState>, id: String) -> bool {
    state.0.lock().map(|mut b| b.revoke(&id)).unwrap_or(false)
}

#[tauri::command]
pub fn bridge_pair_decide(app: AppHandle, state: State<'_, BridgeState>, allow: bool) {
    if let Ok(mut b) = state.0.lock() {
        b.decide_pair(allow, &TauriHost { app });
    }
}

/// Saves (or drops) a login the extension offered. `true` when something was written.
#[tauri::command]
pub fn bridge_save_decide(state: State<'_, BridgeState>, vault: State<'_, VaultState>, id: String, accept: bool) -> CommandResult<bool> {
    let mut bridge = state.0.lock().map_err(|_| crate::vault::error::CommandError::Storage("bridge busy".into()))?;
    let mut vault = vault.0.lock().map_err(|_| crate::vault::error::CommandError::Storage("vault busy".into()))?;
    bridge.decide_save(&id, accept, &mut vault)
}

/// The password prompt's answer to a payment confirmation. `password: None` is "Cancel".
/// Returns false for a wrong password (the prompt stays open).
#[tauri::command]
pub async fn bridge_confirm_decide(app: AppHandle, id: String, password: Option<String>) -> CommandResult<bool> {
    let host = TauriHost { app };
    let approved = match password {
        None => false,
        Some(p) => {
            let ok = host.vault().lock().map_err(|_| crate::vault::error::CommandError::Storage("vault busy".into()))?.verify_password(&p)?;
            if !ok {
                return Ok(false);
            }
            true
        }
    };
    if let Ok(mut b) = host.bridge().0.lock() {
        b.finish_confirm(&id, approved, &host);
    }
    Ok(true)
}

/// The UI's language changed: the Touch ID / Hello texts for bridge prompts.
#[tauri::command]
pub fn bridge_set_auth_reasons(state: State<'_, AuthReasons>, unlock: String, confirm: String) {
    if let Ok(mut r) = state.0.lock() {
        *r = (unlock, confirm);
    }
}
