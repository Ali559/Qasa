//! Non-secret preferences (language, theme, welcome done, the user's name), stored as JSON
//! in the OS app config folder. Mirrors iOS `AppPreferences` keys `qasa.language`,
//! `qasa.theme`, `qasa.hasCompletedWelcome` and `qasa.userName`. Secrets never go here; they go in the OS keychain.
//!
//! The `backup` block belongs to the core (`crate::backup`): the webview saves the other
//! fields with `set_prefs`, which keeps the block as it is on disk, so a stale copy in the
//! UI can never undo a backup the core just recorded.

use std::fs;
use std::path::{Path, PathBuf};
use std::sync::Mutex;

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

/// This computer's backup settings and last result (spec: backups). Per device, never synced.
#[derive(Serialize, Deserialize, Clone, Debug, PartialEq, Eq)]
#[serde(rename_all = "camelCase", default)]
pub struct BackupPrefs {
    /// The backup file's full path, chosen in the save dialog.
    #[serde(skip_serializing_if = "Option::is_none")]
    pub location: Option<String>,
    /// `off`, `daily`, `weekly` or `monthly`; anything else reads as off. A string, so a
    /// value from a newer build never makes the whole file unreadable.
    pub frequency: String,
    /// Unix ms of the last backup that was written, automatic or manual.
    #[serde(skip_serializing_if = "Option::is_none")]
    pub last_backup_ms: Option<i64>,
    /// The last attempt failed (shown on the Backups screen until one succeeds).
    pub failed: bool,
    /// The home reminder stays hidden until then (Unix ms).
    #[serde(skip_serializing_if = "Option::is_none")]
    pub reminder_snoozed_until_ms: Option<i64>,
}

impl Default for BackupPrefs {
    fn default() -> Self {
        Self { location: None, frequency: "off".into(), last_backup_ms: None, failed: false, reminder_snoozed_until_ms: None }
    }
}

impl BackupPrefs {
    pub fn is_default(&self) -> bool {
        *self == Self::default()
    }

    pub fn frequency(&self) -> qasa_backup::Frequency {
        qasa_backup::Frequency::parse(&self.frequency)
    }
}

#[derive(Serialize, Deserialize, Clone, Debug, PartialEq, Eq)]
#[serde(rename_all = "camelCase", default)]
pub struct Prefs {
    pub language: Language,
    pub theme: ThemePreference,
    pub has_completed_welcome: bool,
    /// Minutes of inactivity before the vault locks, 1–60 (iOS default 5).
    pub auto_lock_minutes: u32,
    /// What the user asked to be called (first launch or Settings), trimmed by the UI. Kept
    /// on this computer only; left out while empty.
    #[serde(skip_serializing_if = "String::is_empty")]
    pub user_name: String,
    /// Written by the core only (see the module docs). Left out while untouched.
    #[serde(skip_serializing_if = "BackupPrefs::is_default")]
    pub backup: BackupPrefs,
}

impl Prefs {
    pub fn auto_lock_minutes(&self) -> u32 {
        self.auto_lock_minutes.clamp(1, 60)
    }

    /// What `set_prefs` saves: the webview's fields, with the core's backup block as on disk.
    pub fn from_ui(ui: Prefs, on_disk: &Prefs) -> Prefs {
        Prefs { backup: on_disk.backup.clone(), ..ui }
    }
}

impl Default for Prefs {
    fn default() -> Self {
        Self {
            language: Language::default(),
            theme: ThemePreference::default(),
            has_completed_welcome: false,
            auto_lock_minutes: 5,
            user_name: String::new(),
            backup: BackupPrefs::default(),
        }
    }
}

/// One writer at a time: the webview's `set_prefs` and the core's backup updates.
static WRITE: Mutex<()> = Mutex::new(());

fn prefs_path(app: &AppHandle) -> Result<PathBuf, String> {
    let dir = app.path().app_config_dir().map_err(|e| e.to_string())?;
    Ok(dir.join("prefs.json"))
}

fn read(path: &Path) -> Prefs {
    fs::read(path).ok().and_then(|bytes| serde_json::from_slice(&bytes).ok()).unwrap_or_default()
}

/// Saves atomically (write to a temp file, then rename).
fn write(path: &Path, prefs: &Prefs) -> Result<(), String> {
    if let Some(dir) = path.parent() {
        fs::create_dir_all(dir).map_err(|e| e.to_string())?;
    }
    let tmp = path.with_extension("json.tmp");
    let bytes = serde_json::to_vec_pretty(prefs).map_err(|e| e.to_string())?;
    fs::write(&tmp, bytes).map_err(|e| e.to_string())?;
    fs::rename(&tmp, path).map_err(|e| e.to_string())
}

/// The saved preferences, or the defaults when nothing is saved or the file is unreadable
/// (for the Rust side, e.g. the auto-lock timer and backups).
pub fn load(app: &AppHandle) -> Prefs {
    prefs_path(app).map(|p| read(&p)).unwrap_or_default()
}

/// Read, change and save in one step, for the core's own fields (backups).
pub fn update(app: &AppHandle, change: impl FnOnce(&mut Prefs)) -> Result<Prefs, String> {
    let path = prefs_path(app)?;
    let _guard = WRITE.lock().unwrap_or_else(|e| e.into_inner());
    let mut prefs = read(&path);
    change(&mut prefs);
    write(&path, &prefs)?;
    Ok(prefs)
}

#[tauri::command]
pub fn get_prefs(app: AppHandle) -> Prefs {
    let prefs = load(&app);
    #[cfg(debug_assertions)]
    eprintln!("[qasa] get_prefs → {prefs:?}");
    prefs
}

/// Saves the webview's preferences; the backup block stays as the core last wrote it.
#[tauri::command]
pub fn set_prefs(app: AppHandle, prefs: Prefs) -> Result<(), String> {
    #[cfg(debug_assertions)]
    eprintln!("[qasa] set_prefs ← {prefs:?}");
    let path = prefs_path(&app)?;
    let _guard = WRITE.lock().unwrap_or_else(|e| e.into_inner());
    let merged = Prefs::from_ui(prefs, &read(&path));
    write(&path, &merged)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn json_shape_matches_the_ui() {
        let prefs = Prefs {
            language: Language::Ckb,
            theme: ThemePreference::Dark,
            has_completed_welcome: true,
            auto_lock_minutes: 15,
            user_name: String::new(),
            backup: BackupPrefs::default(),
        };
        assert_eq!(
            serde_json::to_string(&prefs).unwrap(),
            r#"{"language":"ckb","theme":"dark","hasCompletedWelcome":true,"autoLockMinutes":15}"#
        );
        let named = Prefs { user_name: "Sara".into(), ..prefs };
        assert!(serde_json::to_string(&named).unwrap().ends_with(r#""autoLockMinutes":15,"userName":"Sara"}"#));
    }

    #[test]
    fn missing_fields_fall_back_to_defaults() {
        let prefs: Prefs = serde_json::from_str(r#"{"language":"ar"}"#).unwrap();
        assert_eq!(prefs, Prefs { language: Language::Ar, ..Prefs::default() });
        assert_eq!(prefs.backup.frequency(), qasa_backup::Frequency::Off);
    }

    #[test]
    fn backup_block_json_shape() {
        let prefs = Prefs {
            backup: BackupPrefs {
                location: Some("/Users/ali/Documents/Qasa backup - Mac.qasabackup".into()),
                frequency: "weekly".into(),
                last_backup_ms: Some(1_700_000_000_000),
                failed: true,
                reminder_snoozed_until_ms: Some(1_800_000_000_000),
            },
            ..Prefs::default()
        };
        let json = serde_json::to_string(&prefs).unwrap();
        assert!(json.ends_with(
            r#""backup":{"location":"/Users/ali/Documents/Qasa backup - Mac.qasabackup","frequency":"weekly","lastBackupMs":1700000000000,"failed":true,"reminderSnoozedUntilMs":1800000000000}}"#
        ), "{json}");
        assert_eq!(serde_json::from_str::<Prefs>(&json).unwrap(), prefs);
        assert_eq!(prefs.backup.frequency(), qasa_backup::Frequency::Weekly);
    }

    #[test]
    fn older_files_and_unknown_frequencies_read_as_off() {
        let old: Prefs = serde_json::from_str(r#"{"language":"en","theme":"system","hasCompletedWelcome":true,"autoLockMinutes":5}"#).unwrap();
        assert_eq!(old.backup, BackupPrefs::default());
        let newer: Prefs = serde_json::from_str(r#"{"language":"ar","backup":{"frequency":"hourly","lastBackupMs":5}}"#).unwrap();
        assert_eq!(newer.language, Language::Ar);
        assert_eq!(newer.backup.frequency(), qasa_backup::Frequency::Off);
        assert_eq!(newer.backup.last_backup_ms, Some(5));
    }

    #[test]
    fn the_ui_never_overwrites_the_backup_block() {
        let on_disk = Prefs {
            backup: BackupPrefs { last_backup_ms: Some(42), frequency: "daily".into(), ..BackupPrefs::default() },
            ..Prefs::default()
        };
        // The webview still holds the block from before the backup ran, or none at all.
        let ui = Prefs { theme: ThemePreference::Light, ..Prefs::default() };
        let saved = Prefs::from_ui(ui, &on_disk);
        assert_eq!(saved.theme, ThemePreference::Light);
        assert_eq!(saved.backup, on_disk.backup);
    }

    #[test]
    fn update_round_trips_through_the_file() {
        let dir = tempfile::tempdir().unwrap();
        let path = dir.path().join("prefs.json");
        write(&path, &Prefs { auto_lock_minutes: 9, ..Prefs::default() }).unwrap();
        let mut prefs = read(&path);
        prefs.backup.failed = true;
        write(&path, &prefs).unwrap();
        let back = read(&path);
        assert_eq!(back.auto_lock_minutes, 9);
        assert!(back.backup.failed);
    }
}
