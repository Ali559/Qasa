//! The bridge inside the running app: the [`Host`] over Tauri state, and the commands the
//! approval prompts and Settings → Browser extension call.

use std::sync::Mutex;

use tauri::{AppHandle, Emitter, Manager, State, UserAttentionType};

use super::clients::ClientInfo;
use super::{BridgeState, Host, PairPrompt, SavePrompt};
use crate::vault::commands::VaultState;
use crate::vault::error::CommandResult;
use crate::vault::Vault;

pub struct TauriHost {
    pub app: AppHandle,
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
