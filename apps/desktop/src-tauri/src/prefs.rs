//! Non-secret preferences (language, theme, welcome done), stored as JSON in the OS app
//! config folder. Mirrors iOS `AppPreferences` keys `qasa.language`, `qasa.theme` and
//! `qasa.hasCompletedWelcome`. Secrets never go here; they go in the OS keychain.

use std::fs;
use std::path::PathBuf;

use serde::{Deserialize, Serialize};
use tauri::{AppHandle, Manager};

#[derive(Serialize, Deserialize, Clone, Copy, PartialEq, Eq, Debug, Default)]
#[serde(rename_all = "lowercase")]
pub enum Language {
    #[default]
    En,
    Ckb,
    Ar,
}

#[derive(Serialize, Deserialize, Clone, Copy, PartialEq, Eq, Debug, Default)]
#[serde(rename_all = "lowercase")]
pub enum ThemePreference {
    #[default]
    System,
    Light,
    Dark,
}

#[derive(Serialize, Deserialize, Clone, Debug, PartialEq, Eq)]
#[serde(rename_all = "camelCase", default)]
pub struct Prefs {
    pub language: Language,
    pub theme: ThemePreference,
    pub has_completed_welcome: bool,
    /// Minutes of inactivity before the vault locks, 1–60 (iOS default 5).
    pub auto_lock_minutes: u32,
}

impl Prefs {
    pub fn auto_lock_minutes(&self) -> u32 {
        self.auto_lock_minutes.clamp(1, 60)
    }
}

impl Default for Prefs {
    fn default() -> Self {
        Self {
            language: Language::default(),
            theme: ThemePreference::default(),
            has_completed_welcome: false,
            auto_lock_minutes: 5,
        }
    }
}

fn prefs_path(app: &AppHandle) -> Result<PathBuf, String> {
    let dir = app.path().app_config_dir().map_err(|e| e.to_string())?;
    Ok(dir.join("prefs.json"))
}

/// The saved preferences, or the defaults when nothing is saved or the file is unreadable.
/// The saved preferences (for the Rust side, e.g. the auto-lock timer).
pub fn load(app: &AppHandle) -> Prefs {
    prefs_path(app)
        .ok()
        .and_then(|path| fs::read(path).ok())
        .and_then(|bytes| serde_json::from_slice(&bytes).ok())
        .unwrap_or_default()
}

#[tauri::command]
pub fn get_prefs(app: AppHandle) -> Prefs {
    let prefs = prefs_path(&app)
        .ok()
        .and_then(|path| fs::read(path).ok())
        .and_then(|bytes| serde_json::from_slice(&bytes).ok())
        .unwrap_or_default();
    #[cfg(debug_assertions)]
    eprintln!("[qasa] get_prefs → {prefs:?}");
    prefs
}

/// Saves the preferences atomically (write to a temp file, then rename).
#[tauri::command]
pub fn set_prefs(app: AppHandle, prefs: Prefs) -> Result<(), String> {
    #[cfg(debug_assertions)]
    eprintln!("[qasa] set_prefs ← {prefs:?}");
    let path = prefs_path(&app)?;
    if let Some(dir) = path.parent() {
        fs::create_dir_all(dir).map_err(|e| e.to_string())?;
    }
    let tmp = path.with_extension("json.tmp");
    let bytes = serde_json::to_vec_pretty(&prefs).map_err(|e| e.to_string())?;
    fs::write(&tmp, bytes).map_err(|e| e.to_string())?;
    fs::rename(&tmp, &path).map_err(|e| e.to_string())
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn json_shape_matches_the_ui() {
        let prefs = Prefs { language: Language::Ckb, theme: ThemePreference::Dark, has_completed_welcome: true, auto_lock_minutes: 15 };
        assert_eq!(
            serde_json::to_string(&prefs).unwrap(),
            r#"{"language":"ckb","theme":"dark","hasCompletedWelcome":true,"autoLockMinutes":15}"#
        );
    }

    #[test]
    fn missing_fields_fall_back_to_defaults() {
        let prefs: Prefs = serde_json::from_str(r#"{"language":"ar"}"#).unwrap();
        assert_eq!(prefs, Prefs { language: Language::Ar, ..Prefs::default() });
    }
}
