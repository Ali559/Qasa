//! Tauri commands for the vault. Thin wrappers over [`super::Vault`]; every call counts as
//! activity for the auto-lock timer. New commands must also be added to
//! `isolation/index.js`.

use std::sync::Mutex;

use qasa_vault::payload::VaultPayload;
use serde::Serialize;
use tauri::State;

use super::error::{CommandError, CommandResult};
use super::{Collection, ItemSummary, Vault, VaultStatus};

pub struct VaultState(pub Mutex<Vault>);

fn with<T>(state: &State<'_, VaultState>, f: impl FnOnce(&mut Vault) -> CommandResult<T>) -> CommandResult<T> {
    let mut vault = state.0.lock().map_err(|_| CommandError::Storage("vault lock poisoned".into()))?;
    if vault.is_unlocked() {
        vault.note_activity();
    }
    f(&mut vault)
}

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
pub struct ItemList {
    pub items: Vec<ItemSummary>,
    /// Rows that could not be decrypted and are not shown.
    pub unreadable: usize,
}

#[tauri::command]
pub fn vault_status(state: State<'_, VaultState>) -> CommandResult<VaultStatus> {
    with(&state, |v| v.status())
}

// PBKDF2 at 310,000 iterations takes a moment: run these off the main thread.

#[tauri::command]
pub async fn vault_create(state: State<'_, VaultState>, password: String, biometrics: Option<bool>) -> CommandResult<()> {
    with(&state, |v| v.create(&password, biometrics.unwrap_or(false)))
}

#[tauri::command]
pub fn vault_biometric_status(state: State<'_, VaultState>) -> CommandResult<super::biometrics::BiometricStatus> {
    with(&state, |v| v.biometric_status())
}

#[tauri::command]
pub fn vault_set_biometric(state: State<'_, VaultState>, enabled: bool) -> CommandResult<super::biometrics::BiometricStatus> {
    with(&state, |v| v.set_biometric_enabled(enabled))
}

/// Opens the seal with Touch ID / Hello, outside the vault lock (the prompt can stay up
/// for a while), then checks the result against the verifier. `for_unlock` applies the
/// master password interval (`MasterPasswordDue` before any prompt appears).
async fn unseal_root(state: &State<'_, VaultState>, reason: String, for_unlock: bool) -> CommandResult<qasa_vault::RootKey> {
    let sealed = with(state, |v| if for_unlock { v.sealed_for_unlock() } else { v.sealed() })?
        .ok_or(CommandError::BiometricUnavailable)?;
    let result = tauri::async_runtime::spawn_blocking(move || {
        use super::biometrics::{Biometrics, PlatformBiometrics};
        PlatformBiometrics.unseal(&sealed, &reason)
    })
        .await
        .map_err(|e| CommandError::Storage(e.to_string()))?;
    match result {
        Ok(root) => Ok(qasa_vault::RootKey::from_bytes(root)),
        Err(qasa_biometric::Error::Cancelled) => Err(CommandError::Cancelled),
        Err(qasa_biometric::Error::Unavailable) => Err(CommandError::BiometricUnavailable),
        Err(_) => {
            // Fingerprints changed or the key is gone: fail closed and reseal later.
            with(state, |v| v.forget_seal())?;
            Err(CommandError::BiometricInvalidated)
        }
    }
}

#[tauri::command]
pub async fn vault_unlock_biometric(state: State<'_, VaultState>, reason: String) -> CommandResult<()> {
    let root = unseal_root(&state, reason, true).await?;
    with(&state, |v| v.unlock_with_root(root))
}

/// The protected-note gate with Touch ID / Hello: true when the user authenticated.
#[tauri::command]
pub async fn vault_verify_biometric(state: State<'_, VaultState>, reason: String) -> CommandResult<bool> {
    let root = unseal_root(&state, reason, false).await?;
    with(&state, |v| v.root_matches(&root))
}

#[tauri::command]
pub async fn vault_unlock(state: State<'_, VaultState>, password: String) -> CommandResult<()> {
    with(&state, |v| v.unlock(&password))
}

#[tauri::command]
pub async fn vault_verify_password(state: State<'_, VaultState>, password: String) -> CommandResult<bool> {
    with(&state, |v| v.verify_password(&password))
}

#[tauri::command]
pub fn vault_lock(state: State<'_, VaultState>, account: State<'_, crate::account::commands::AccountState>) -> CommandResult<()> {
    if let Ok(mut a) = account.0.try_lock() {
        a.on_lock();
    }
    with(&state, |v| {
        v.lock();
        Ok(())
    })
}

/// The UI reports keyboard and pointer activity (throttled) so auto-lock measures real use.
#[tauri::command]
pub fn vault_note_activity(state: State<'_, VaultState>) -> CommandResult<()> {
    with(&state, |_| Ok(()))
}

#[tauri::command]
pub fn vault_list(state: State<'_, VaultState>) -> CommandResult<ItemList> {
    with(&state, |v| v.list().map(|(items, unreadable)| ItemList { items, unreadable }))
}

#[tauri::command]
pub fn vault_get(state: State<'_, VaultState>, id: String) -> CommandResult<VaultPayload> {
    with(&state, |v| v.get(&id))
}

#[tauri::command]
pub fn vault_save(state: State<'_, VaultState>, id: Option<String>, payload: VaultPayload) -> CommandResult<ItemSummary> {
    with(&state, |v| v.save(id.as_deref(), &payload))
}

#[tauri::command]
pub fn vault_delete(state: State<'_, VaultState>, id: String) -> CommandResult<()> {
    with(&state, |v| v.delete(&id))
}

#[tauri::command]
pub fn vault_mark_used(state: State<'_, VaultState>, id: String) -> CommandResult<()> {
    with(&state, |v| v.mark_used(&id))
}

#[tauri::command]
pub fn collections_list(state: State<'_, VaultState>) -> CommandResult<Vec<Collection>> {
    with(&state, |v| v.collections())
}

#[tauri::command]
pub fn collection_create(state: State<'_, VaultState>, name: String) -> CommandResult<Collection> {
    with(&state, |v| v.create_collection(&name))
}

#[tauri::command]
pub fn collection_rename(state: State<'_, VaultState>, id: String, name: String) -> CommandResult<()> {
    with(&state, |v| v.rename_collection(&id, &name))
}

#[tauri::command]
pub fn collection_delete(state: State<'_, VaultState>, id: String) -> CommandResult<()> {
    with(&state, |v| v.delete_collection(&id))
}

#[tauri::command]
pub async fn vault_change_password(
    state: State<'_, VaultState>,
    current: String,
    new_password: String,
) -> CommandResult<()> {
    with(&state, |v| v.change_password(&current, &new_password))
}

/// Local vault reset. Forgets the backup settings and backup restore keys too (never the
/// backup file).
#[tauri::command]
pub async fn vault_wipe(
    app: tauri::AppHandle,
    state: State<'_, VaultState>,
    account: State<'_, crate::account::commands::AccountState>,
) -> CommandResult<()> {
    with(&state, |v| v.wipe())?;
    account.0.lock().await.clear_restore_keys();
    crate::backup::forget(&app);
    Ok(())
}
