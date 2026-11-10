//! Encrypted vault backups (`.qasabackup`, format in `core/qasa-backup`).
//!
//! - One backup per computer: the file the user picked in the save dialog, replaced by
//!   every new backup (written to a temp file in the same folder, then renamed over it).
//! - Back up now, and automatic backups (off / daily / weekly / monthly) checked when the
//!   vault unlocks and every hour while it stays unlocked. One run at a time.
//! - Restore: during setup (this computer takes the backup's vault and account, like a
//!   device joining over Wi‑Fi sync) or in Settings (merged, newest change wins).
//!
//! - Restore key: each backup carries this device's restore key, whose hash the server
//!   has (rotated before a backup once the previous key is in a written file). A computer
//!   that adopts the backup sends it when registering and may replace the device that
//!   made it. The keys live in the OS keychain (`account::secrets`), never in the webview.
//!
//! Everything goes through the Wi‑Fi sync store (`crate::sync::DesktopStore`). The data
//! key and the file's bytes stay in this process: the webview only sees the header (date,
//! device) and the counts (plan §3). Settings live in `prefs.backup`, written only here.

use std::fs;
use std::io::Write;
use std::path::{Path, PathBuf};
use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::Mutex;
use std::time::Duration;

use qasa_backup::{BackupError, Frequency};
use serde::Serialize;
use tauri::{AppHandle, Emitter, Manager, Runtime, State};
use tauri_plugin_dialog::DialogExt;

use crate::account::commands::AccountState;
use crate::prefs;
use crate::sync::DesktopStore;
use crate::vault::commands::VaultState;
use crate::vault::store::now_ms;

/// How often automatic backups are checked while the vault is unlocked.
pub const CHECK_INTERVAL: Duration = Duration::from_secs(60 * 60);
/// The UI re-reads `backup_status` when this fires (a run started or ended).
pub const EVENT: &str = "backup-changed";
/// Larger files aren't read for a restore: no real vault comes near this.
const MAX_FILE_BYTES: u64 = 512 * 1024 * 1024;

#[derive(Default)]
pub struct BackupState {
    running: AtomicBool,
    /// The file picked for a restore. Kept here so its bytes never reach the webview, and
    /// so a wrong password can be retried without picking the file again.
    pending: Mutex<Option<Vec<u8>>>,
}

/// Errors for the UI, as `{"code": …, "message": …}` like the vault's.
#[derive(Debug, Clone, PartialEq, Eq, thiserror::Error)]
pub enum Failure {
    #[error("not a Qasa backup")]
    NotABackup,
    #[error("made by a newer version of Qasa")]
    NewerVersion,
    #[error("wrong password")]
    WrongPassword,
    #[error("this device has a different vault")]
    DifferentVault,
    #[error("no vault on this device")]
    NoVault,
    /// The picked file could not be read.
    #[error("unreadable: {0}")]
    Unreadable(String),
    /// Writing the backup or the vault failed.
    #[error("storage: {0}")]
    Store(String),
    #[error("the vault is locked")]
    Locked,
    /// A backup is already running.
    #[error("a backup is already running")]
    Busy,
    #[error("no backup location")]
    NoLocation,
    /// Restore called without a picked file.
    #[error("no backup file picked")]
    NoFile,
}

impl Failure {
    fn code(&self) -> &'static str {
        match self {
            Failure::NotABackup => "notABackup",
            Failure::NewerVersion => "newerVersion",
            Failure::WrongPassword => "wrongPassword",
            Failure::DifferentVault => "differentVault",
            Failure::NoVault => "noVault",
            Failure::Unreadable(_) => "unreadable",
            Failure::Store(_) => "store",
            Failure::Locked => "locked",
            Failure::Busy => "busy",
            Failure::NoLocation => "noLocation",
            Failure::NoFile => "noFile",
        }
    }
}

impl Serialize for Failure {
    fn serialize<S: serde::Serializer>(&self, serializer: S) -> Result<S::Ok, S::Error> {
        use serde::ser::SerializeStruct;
        let mut s = serializer.serialize_struct("Failure", 2)?;
        s.serialize_field("code", self.code())?;
        s.serialize_field("message", &self.to_string())?;
        s.end()
    }
}

impl From<BackupError> for Failure {
    fn from(e: BackupError) -> Self {
        match e {
            BackupError::NotABackup => Failure::NotABackup,
            BackupError::NewerVersion => Failure::NewerVersion,
            BackupError::WrongPassword => Failure::WrongPassword,
            BackupError::DifferentVault => Failure::DifferentVault,
            BackupError::NoVault => Failure::NoVault,
            BackupError::Store(s) => Failure::Store(s),
        }
    }
}

fn storage(e: impl std::fmt::Display) -> Failure {
    Failure::Store(e.to_string())
}

fn frequency_name(f: Frequency) -> &'static str {
    match f {
        Frequency::Off => "off",
        Frequency::Daily => "daily",
        Frequency::Weekly => "weekly",
        Frequency::Monthly => "monthly",
    }
}

// MARK: status

#[derive(Serialize, Debug, PartialEq, Eq)]
#[serde(rename_all = "camelCase")]
pub struct BackupStatus {
    /// The backup file's full path.
    pub location: Option<String>,
    /// Its file name, for the location row.
    pub location_name: Option<String>,
    /// `off`, `daily`, `weekly` or `monthly`.
    pub frequency: &'static str,
    pub last_backup_ms: Option<i64>,
    /// The last attempt failed.
    pub failed: bool,
    pub running: bool,
    /// Show the reminder card on Home.
    pub remind: bool,
}

fn status_from(backup: &prefs::BackupPrefs, running: bool, live_items: u32, now: i64) -> BackupStatus {
    let location_name = backup
        .location
        .as_deref()
        .and_then(|p| Path::new(p).file_name())
        .map(|n| n.to_string_lossy().into_owned());
    BackupStatus {
        location: backup.location.clone(),
        location_name,
        frequency: frequency_name(backup.frequency()),
        last_backup_ms: backup.last_backup_ms,
        failed: backup.failed,
        running,
        remind: qasa_backup::should_remind(backup.last_backup_ms, live_items, backup.reminder_snoozed_until_ms, now),
    }
}

fn status(app: &AppHandle) -> Result<BackupStatus, Failure> {
    let backup = prefs::load(app).backup;
    let live = app.state::<VaultState>().0.lock().map_err(storage)?.live_item_count().map_err(storage)?;
    let running = app.state::<BackupState>().running.load(Ordering::SeqCst);
    Ok(status_from(&backup, running, live, now_ms()))
}

fn save(app: &AppHandle, change: impl FnOnce(&mut prefs::BackupPrefs)) -> Result<(), Failure> {
    prefs::update(app, |p| change(&mut p.backup)).map(|_| ()).map_err(Failure::Store)
}

#[tauri::command]
pub fn backup_status(app: AppHandle) -> Result<BackupStatus, Failure> {
    status(&app)
}

// MARK: writing a backup

/// Writes `bytes` to `path` without ever leaving a half-written file there: a temp file in
/// the same folder (so the rename stays on one volume), flushed to disk, then renamed over
/// the old backup. `rename` replaces an existing file on Windows too.
pub fn write_atomic(path: &Path, bytes: &[u8]) -> std::io::Result<()> {
    let name = path
        .file_name()
        .ok_or_else(|| std::io::Error::new(std::io::ErrorKind::InvalidInput, "no file name"))?;
    let dir = path.parent().filter(|d| !d.as_os_str().is_empty()).unwrap_or(Path::new("."));
    let tmp = dir.join(format!(".{}.{}.tmp", name.to_string_lossy(), uuid::Uuid::new_v4().simple()));
    let result = (|| {
        let mut file = fs::File::create(&tmp)?;
        file.write_all(bytes)?;
        file.sync_all()?;
        drop(file);
        fs::rename(&tmp, path)
    })();
    if result.is_err() {
        let _ = fs::remove_file(&tmp);
    }
    result
}

/// The restore key for the next backup. Rotates it first when due
/// (`account::should_rotate_restore_key`): a new key's hash goes to the server, and only
/// once it answers 204 does the new key replace the old one. Any failure keeps the old
/// state and never stops the backup. Blocking (call it off the async runtime); the
/// account lock is not held during the request.
fn prepare_restore_key<R: Runtime>(app: &AppHandle<R>) -> Option<[u8; qasa_backup::RESTORE_KEY_LEN]> {
    let state = app.try_state::<AccountState>()?;
    let rotation = {
        let account = state.0.blocking_lock();
        account.restore_key_rotation().map(|token| (account.api.clone(), token))
    };
    if let Some((api, token)) = rotation {
        let key = qasa_backup::new_restore_key();
        match tauri::async_runtime::block_on(crate::account::put_restore_key_hash(&api, &token, &key)) {
            Ok(()) => state.0.blocking_lock().confirm_restore_key(&key),
            Err(e) => eprintln!("[qasa] restore key not rotated: {e:?}"),
        }
    }
    let key = state.0.blocking_lock().restore_key();
    key
}

/// A backup file holding `key` is on disk.
fn note_restore_key_written<R: Runtime>(app: &AppHandle<R>, key: &[u8; qasa_backup::RESTORE_KEY_LEN]) {
    if let Some(state) = app.try_state::<AccountState>() {
        state.0.blocking_lock().mark_restore_key_written(key);
    }
}

/// The backup's bytes. Copies the data key out of the vault first: the store locks the
/// vault per call, so `create` can't run under the vault's lock.
fn create_bytes<R: Runtime>(app: &AppHandle<R>, restore_key: Option<&[u8; qasa_backup::RESTORE_KEY_LEN]>) -> Result<Vec<u8>, Failure> {
    let key = {
        let state = app.state::<VaultState>();
        let vault = state.0.lock().map_err(storage)?;
        if !vault.is_unlocked() {
            return Err(Failure::Locked);
        }
        vault.backup_key().map_err(|_| Failure::Locked)?
    };
    Ok(qasa_backup::create(&DesktopStore::new(app.clone()), &key, restore_key)?)
}

/// Clears `running` however the run ends.
struct RunGuard<'a>(&'a AtomicBool);

impl<'a> RunGuard<'a> {
    fn take(flag: &'a AtomicBool) -> Option<Self> {
        flag.compare_exchange(false, true, Ordering::SeqCst, Ordering::SeqCst).ok().map(|_| Self(flag))
    }
}

impl Drop for RunGuard<'_> {
    fn drop(&mut self) {
        self.0.store(false, Ordering::SeqCst);
    }
}

/// One backup to the chosen location, recorded in prefs. Blocking: call it off the async
/// runtime. A locked vault or a missing location is not recorded as a failure.
fn run(app: &AppHandle) -> Result<i64, Failure> {
    let state = app.state::<BackupState>();
    let result = {
        let Some(_running) = RunGuard::take(&state.running) else { return Err(Failure::Busy) };
        let _ = app.emit(EVENT, ());
        let result = (|| {
            let path = prefs::load(app).backup.location.ok_or(Failure::NoLocation)?;
            let unlocked = app.state::<VaultState>().0.lock().map_err(storage)?.is_unlocked();
            if !unlocked {
                return Err(Failure::Locked);
            }
            let restore_key = prepare_restore_key(app);
            let bytes = create_bytes(app, restore_key.as_ref())?;
            write_atomic(Path::new(&path), &bytes).map_err(storage)?;
            if let Some(key) = &restore_key {
                note_restore_key_written(app, key);
            }
            Ok(now_ms())
        })();
        let recorded = save(app, |b| match &result {
            Ok(at) => {
                b.last_backup_ms = Some(*at);
                b.failed = false;
            }
            Err(Failure::Locked | Failure::NoLocation | Failure::NoVault) => {}
            Err(_) => b.failed = true,
        });
        if let Err(e) = recorded {
            eprintln!("[qasa] could not record the backup: {e}");
        }
        result
    };
    let _ = app.emit(EVENT, ());
    if let Err(e) = &result {
        eprintln!("[qasa] backup failed: {e}");
    }
    result
}

/// An automatic backup when one is due, a location is set and the vault is unlocked.
pub fn run_if_due(app: &AppHandle) {
    let backup = prefs::load(app).backup;
    if backup.location.is_none() || !qasa_backup::is_due(backup.frequency(), backup.last_backup_ms, now_ms()) {
        return;
    }
    let unlocked = app.state::<VaultState>().0.lock().map(|v| v.is_unlocked()).unwrap_or(false);
    if unlocked {
        let _ = run(app);
    }
}

/// The hourly check while the app runs (it only backs up while unlocked).
pub fn spawn_timer(app: AppHandle) {
    std::thread::spawn(move || loop {
        std::thread::sleep(CHECK_INTERVAL);
        run_if_due(&app);
    });
}

/// The UI calls this after every unlock (and after a vault is created or restored).
#[tauri::command]
pub fn backup_check_due(app: AppHandle) {
    std::thread::spawn(move || run_if_due(&app));
}

/// The save dialog, in Documents with this computer's file name. False when cancelled.
fn choose_location(app: &AppHandle) -> Result<bool, Failure> {
    let current = prefs::load(app).backup.location.map(PathBuf::from);
    let mut dialog = app
        .dialog()
        .file()
        .set_file_name(qasa_backup::file_name(&whoami::devicename()))
        .add_filter("Qasa backup", &[qasa_backup::EXTENSION]);
    let folder = current.as_deref().and_then(Path::parent).map(Path::to_path_buf).or_else(|| app.path().document_dir().ok());
    if let Some(folder) = folder {
        dialog = dialog.set_directory(folder);
    }
    let Some(path) = dialog.blocking_save_file() else { return Ok(false) };
    let path = path.into_path().map_err(storage)?;
    let path = path.to_string_lossy().into_owned();
    save(app, |b| {
        b.location = Some(path);
        b.failed = false;
    })?;
    Ok(true)
}

#[tauri::command]
pub async fn backup_choose_location(app: AppHandle) -> Result<BackupStatus, Failure> {
    if choose_location(&app)? {
        // A new location with automatic backups on and none made yet: make one now.
        std::thread::spawn({
            let app = app.clone();
            move || run_if_due(&app)
        });
    }
    status(&app)
}

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
pub struct BackupNow {
    /// The user closed the location dialog; nothing was written.
    pub cancelled: bool,
    pub status: BackupStatus,
}

/// Settings → Back up now. Asks for a location first when there is none.
#[tauri::command]
pub async fn backup_now(app: AppHandle) -> Result<BackupNow, Failure> {
    if prefs::load(&app).backup.location.is_none() && !choose_location(&app)? {
        return Ok(BackupNow { cancelled: true, status: status(&app)? });
    }
    let handle = app.clone();
    tauri::async_runtime::spawn_blocking(move || run(&handle)).await.map_err(storage)??;
    Ok(BackupNow { cancelled: false, status: status(&app)? })
}

#[tauri::command]
pub fn backup_set_frequency(app: AppHandle, frequency: String) -> Result<BackupStatus, Failure> {
    let name = frequency_name(Frequency::parse(&frequency));
    save(&app, |b| b.frequency = name.into())?;
    let handle = app.clone();
    std::thread::spawn(move || run_if_due(&handle));
    status(&app)
}

/// Home → reminder → Not now.
#[tauri::command]
pub fn backup_snooze_reminder(app: AppHandle) -> Result<BackupStatus, Failure> {
    let until = now_ms() + qasa_backup::REMINDER_AFTER_MS;
    save(&app, |b| b.reminder_snoozed_until_ms = Some(until))?;
    status(&app)
}

/// Local vault reset, Clear local data, account deletion: forget the settings. The backup
/// file itself is never deleted.
pub fn forget(app: &AppHandle) {
    if let Err(e) = save(app, |b| *b = prefs::BackupPrefs::default()) {
        eprintln!("[qasa] could not clear the backup settings: {e}");
    }
    if let Ok(mut pending) = app.state::<BackupState>().pending.lock() {
        *pending = None;
    }
}

// MARK: restore

#[derive(Serialize, Debug)]
#[serde(rename_all = "camelCase")]
pub struct BackupFileInfo {
    pub created_at_ms: i64,
    pub device_name: String,
    pub platform: String,
}

/// Reads a picked file. The size check comes first, so a huge file is never loaded.
fn read_backup_file(path: &Path) -> Result<Vec<u8>, Failure> {
    let len = fs::metadata(path).map_err(|e| Failure::Unreadable(e.to_string()))?.len();
    if len > MAX_FILE_BYTES {
        return Err(Failure::NotABackup);
    }
    fs::read(path).map_err(|e| Failure::Unreadable(e.to_string()))
}

/// Asks for a backup file (any type: providers often drop the extension) and reads its
/// header. `None` when cancelled. The file stays here for `backup_restore`.
#[tauri::command]
pub async fn backup_pick_restore(app: AppHandle, state: State<'_, BackupState>) -> Result<Option<BackupFileInfo>, Failure> {
    let mut dialog = app.dialog().file();
    if let Ok(folder) = app.path().document_dir() {
        dialog = dialog.set_directory(folder);
    }
    let Some(path) = dialog.blocking_pick_file() else { return Ok(None) };
    let path = path.into_path().map_err(|e| Failure::Unreadable(e.to_string()))?;
    let bytes = read_backup_file(&path)?;
    let info = qasa_backup::info(&bytes)?;
    *state.pending.lock().map_err(storage)? = Some(bytes);
    Ok(Some(BackupFileInfo { created_at_ms: info.created_at, device_name: info.device_name, platform: info.platform }))
}

#[derive(Serialize, Debug, PartialEq, Eq)]
#[serde(rename_all = "camelCase")]
pub struct RestoreOutcome {
    /// This computer had no vault and took the backup's vault and account; the vault is
    /// now unlocked with the password just typed.
    pub adopted: bool,
    pub written: u32,
    pub items_in_backup: u32,
}

/// Restores into this computer's vault. After an adoption it unlocks with the same
/// password, so setup carries on as after a Wi‑Fi adoption and unlock (the UI then
/// registers the device under the adopted account). Blocking: PBKDF2, twice on adoption.
fn restore_into<R: Runtime>(app: &AppHandle<R>, file: &[u8], password: &str) -> Result<RestoreOutcome, Failure> {
    let mut store = DesktopStore::new(app.clone());
    let restored = qasa_backup::restore(file, password, &mut store)?;
    // After the adoption (which reset the account to the backup's): the registration that
    // follows presents this key.
    if restored.adopted {
        if let (Some(key), Some(account)) = (&restored.restore_key, app.try_state::<AccountState>()) {
            account.0.blocking_lock().set_pending_restore_key(key);
        }
    }
    let vault = app.state::<VaultState>();
    let mut vault = vault.0.lock().map_err(storage)?;
    if restored.adopted {
        vault.unlock(password).map_err(storage)?;
    } else if vault.is_unlocked() {
        vault.note_activity();
    }
    Ok(RestoreOutcome { adopted: restored.adopted, written: restored.written, items_in_backup: restored.items_in_backup })
}

#[tauri::command]
pub async fn backup_restore(app: AppHandle, password: String) -> Result<RestoreOutcome, Failure> {
    let file = app.state::<BackupState>().pending.lock().map_err(storage)?.clone().ok_or(Failure::NoFile)?;
    let handle = app.clone();
    let outcome = tauri::async_runtime::spawn_blocking(move || restore_into(&handle, &file, &password)).await.map_err(storage)??;
    if let Ok(mut pending) = app.state::<BackupState>().pending.lock() {
        *pending = None;
    }
    Ok(outcome)
}

/// Restore closed without restoring: drop the picked file.
#[tauri::command]
pub fn backup_cancel_restore(state: State<'_, BackupState>) {
    if let Ok(mut pending) = state.pending.lock() {
        *pending = None;
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::account::{api::ApiClient, secrets::MemoryStore, Account};
    use crate::vault::Vault;
    use qasa_vault::payload::{LoginFields, VaultPayload};
    use tauri::test::{mock_app, MockRuntime};

    fn app() -> tauri::App<MockRuntime> {
        let app = mock_app();
        app.manage(VaultState(Mutex::new(Vault::in_memory())));
        let account = Account::new(ApiClient::new("http://127.0.0.1:9".into()), Box::new(MemoryStore::default()));
        app.manage(AccountState(tokio::sync::Mutex::new(account)));
        app.manage(BackupState::default());
        app
    }

    fn login(name: &str) -> VaultPayload {
        VaultPayload::Login(LoginFields { item_name: name.into(), password: "pw".into(), ..Default::default() })
    }

    fn names(app: &tauri::App<MockRuntime>) -> Vec<String> {
        let mut n: Vec<String> = app.state::<VaultState>().0.lock().unwrap().list().unwrap().0.into_iter().map(|i| i.display_name).collect();
        n.sort();
        n
    }

    fn account_id(app: &tauri::App<MockRuntime>) -> Option<String> {
        app.state::<AccountState>().0.blocking_lock().account_id()
    }

    /// Through the real Wi‑Fi sync store: back up one computer, restore on a new one (it
    /// adopts the vault and account and ends up unlocked), restore again (nothing to do).
    #[test]
    fn backup_and_restore_through_the_desktop_store() {
        let source = app();
        {
            let state = source.state::<VaultState>();
            let mut v = state.0.lock().unwrap();
            v.create("correct horse", false).unwrap();
            v.save(None, &login("GitHub")).unwrap();
            v.save(None, &login("Mail")).unwrap();
            v.create_collection("Work").unwrap();
        }
        source.state::<AccountState>().0.blocking_lock().ensure_identity();
        let file = create_bytes(source.handle(), None).unwrap();
        assert!(qasa_backup::info(&file).is_ok());

        let target = app();
        assert_eq!(restore_into(target.handle(), &file, "wrong horse"), Err(Failure::WrongPassword));
        assert!(!target.state::<VaultState>().0.lock().unwrap().status().unwrap().exists, "a failed restore changes nothing");

        let outcome = restore_into(target.handle(), &file, "correct horse").unwrap();
        assert!(outcome.adopted);
        assert_eq!(outcome.items_in_backup, 2);
        assert!(outcome.written >= 3, "two items and a folder: {outcome:?}");
        assert!(target.state::<VaultState>().0.lock().unwrap().is_unlocked());
        assert_eq!(names(&target), ["GitHub", "Mail"]);
        assert_eq!(target.state::<VaultState>().0.lock().unwrap().collections().unwrap()[0].name, "Work");
        assert_eq!(account_id(&target), account_id(&source));
        assert!(account_id(&target).is_some());

        let again = restore_into(target.handle(), &file, "correct horse").unwrap();
        assert_eq!((again.adopted, again.written), (false, 0));
    }

    /// The restore key rides in the backup; adopting it leaves it pending for registration,
    /// and a merge restore (not an adoption) doesn't.
    #[test]
    fn the_restore_key_travels_with_the_backup() {
        let source = app();
        source.state::<VaultState>().0.lock().unwrap().create("correct horse", false).unwrap();
        source.state::<AccountState>().0.blocking_lock().ensure_identity();
        // No license token here: no rotation, no key.
        assert_eq!(prepare_restore_key(source.handle()), None);
        let key = [5u8; 32];
        source.state::<AccountState>().0.blocking_lock().confirm_restore_key(&key);
        assert_eq!(prepare_restore_key(source.handle()), Some(key), "an unwritten key is reused");
        let file = create_bytes(source.handle(), Some(&key)).unwrap();

        let target = app();
        restore_into(target.handle(), &file, "correct horse").unwrap();
        let body = target.state::<AccountState>().0.blocking_lock().status();
        assert!(body.restore_key_status.is_none());
        assert!(pending_key(&target).is_some_and(|k| k == key));

        let other = app();
        other.state::<VaultState>().0.lock().unwrap().create("correct horse", false).unwrap();
        let plain = create_bytes(other.handle(), None).unwrap();
        let fresh = app();
        restore_into(fresh.handle(), &plain, "correct horse").unwrap();
        assert!(pending_key(&fresh).is_none(), "a backup without a key leaves none");
    }

    fn pending_key(app: &tauri::App<MockRuntime>) -> Option<[u8; 32]> {
        app.state::<AccountState>().0.blocking_lock().pending_restore_key_for_tests()
    }

    #[test]
    fn a_different_vault_is_refused_and_a_locked_vault_is_not_backed_up() {
        let source = app();
        source.state::<VaultState>().0.lock().unwrap().create("password one", false).unwrap();
        let file = create_bytes(source.handle(), None).unwrap();

        let other = app();
        {
            let state = other.state::<VaultState>();
            let mut v = state.0.lock().unwrap();
            v.create("password two", false).unwrap();
            v.save(None, &login("Mine")).unwrap();
        }
        assert_eq!(restore_into(other.handle(), &file, "password one"), Err(Failure::DifferentVault));
        assert_eq!(names(&other), ["Mine"]);

        other.state::<VaultState>().0.lock().unwrap().lock();
        assert_eq!(create_bytes(other.handle(), None), Err(Failure::Locked));
        assert_eq!(restore_into(other.handle(), b"hello", "x"), Err(Failure::NotABackup));
    }

    #[test]
    fn write_atomic_replaces_the_previous_backup_and_leaves_no_temp_files() {
        let dir = tempfile::tempdir().unwrap();
        let path = dir.path().join("Qasa backup - Mac.qasabackup");
        write_atomic(&path, b"first").unwrap();
        write_atomic(&path, b"second").unwrap();
        assert_eq!(fs::read(&path).unwrap(), b"second");
        assert_eq!(fs::read_dir(dir.path()).unwrap().count(), 1);
        // A folder that is gone fails cleanly.
        assert!(write_atomic(&dir.path().join("missing/x.qasabackup"), b"x").is_err());
    }

    #[test]
    fn status_reflects_prefs_and_the_reminder_rules() {
        let day = 24 * 60 * 60 * 1000;
        let now = 100 * day;
        let mut b = prefs::BackupPrefs::default();
        let s = status_from(&b, false, 3, now);
        assert_eq!((s.frequency, s.location_name.as_deref(), s.remind), ("off", None, true));
        assert!(!status_from(&b, false, 0, now).remind, "an empty vault isn't nagged");

        b.location = Some("/tmp/backups/Qasa backup - Mac.qasabackup".into());
        b.frequency = "monthly".into();
        b.last_backup_ms = Some(now - day);
        let s = status_from(&b, true, 3, now);
        assert_eq!(s.location_name.as_deref(), Some("Qasa backup - Mac.qasabackup"));
        assert_eq!((s.frequency, s.running, s.remind), ("monthly", true, false));

        b.last_backup_ms = Some(now - 31 * day);
        assert!(status_from(&b, false, 3, now).remind);
        b.reminder_snoozed_until_ms = Some(now + 1);
        assert!(!status_from(&b, false, 3, now).remind);
        b.frequency = "yearly".into();
        assert_eq!(status_from(&b, false, 3, now).frequency, "off");
    }

    #[test]
    fn failures_serialize_with_a_code() {
        assert_eq!(serde_json::to_value(Failure::WrongPassword).unwrap()["code"], "wrongPassword");
        assert_eq!(serde_json::to_value(Failure::from(BackupError::Store("x".into()))).unwrap()["code"], "store");
        assert_eq!(serde_json::to_value(Failure::Unreadable("gone".into())).unwrap()["code"], "unreadable");
    }

    #[test]
    fn huge_files_are_not_read() {
        let dir = tempfile::tempdir().unwrap();
        let path = dir.path().join("big.qasabackup");
        let f = fs::File::create(&path).unwrap();
        f.set_len(MAX_FILE_BYTES + 1).unwrap();
        assert_eq!(read_backup_file(&path), Err(Failure::NotABackup));
        assert!(matches!(read_backup_file(&dir.path().join("none")), Err(Failure::Unreadable(_))));
    }
}
