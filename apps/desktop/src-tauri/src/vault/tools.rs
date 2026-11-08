//! Commands for the vault tools: password health, the generator, 2FA codes and CSV
//! import/export. The logic lives in `core/qasa-vault`; these only adapt it for the UI.
//! CSV files are chosen and read/written here, so plaintext exports never pass through
//! the webview.

use std::collections::HashMap;

use qasa_vault::generator::{self, GeneratorOptions};
use qasa_vault::health;
use qasa_vault::payload::VaultPayload;
use qasa_vault::totp::{self, Totp};
use serde::{Deserialize, Serialize};
use tauri::{AppHandle, State};
use tauri_plugin_dialog::DialogExt;

use super::commands::VaultState;
use super::error::{CommandError, CommandResult};

fn lock_vault<'a>(state: &'a State<'_, VaultState>) -> CommandResult<std::sync::MutexGuard<'a, super::Vault>> {
    let mut vault = state.0.lock().map_err(|_| CommandError::Storage("vault lock poisoned".into()))?;
    if vault.is_unlocked() {
        vault.note_activity();
    }
    Ok(vault)
}

fn now_secs() -> i64 {
    std::time::SystemTime::now().duration_since(std::time::UNIX_EPOCH).map(|d| d.as_secs() as i64).unwrap_or(0)
}

// MARK: health

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
pub struct HealthReport {
    pub total_passwords: usize,
    pub reused_count: usize,
    pub weak_count: usize,
    pub breached_count: usize,
    pub score: i64,
    pub reused_ids: Vec<String>,
    pub weak_ids: Vec<String>,
    pub breached_ids: Vec<String>,
    pub breach_counts: HashMap<String, u64>,
    pub breach_locked: bool,
    pub breach_check_failed: bool,
}

/// The password health report (iOS `PasswordHealthAnalyzer`). On Premium it also checks
/// every password against Pwned Passwords (`account::breach_counts`); a failed check is
/// reported as such, never as a partial "all clear". On Free the check never runs and
/// the score has no breach deduction.
#[tauri::command]
pub async fn vault_health(
    state: State<'_, VaultState>,
    account: State<'_, crate::account::commands::AccountState>,
) -> CommandResult<HealthReport> {
    let (entries, premium) = {
        let premium = account.0.lock().await.is_premium();
        (health_entries(&state)?, premium)
    };
    let mut failed = false;
    let counts = if premium {
        let passwords: Vec<String> = entries.iter().map(|e| e.password.clone()).collect();
        match account.0.lock().await.breach_counts(&passwords).await {
            Ok(c) => Some(c),
            Err(_) => {
                failed = true;
                None
            }
        }
    } else {
        None
    };
    let mut r = health::analyze(&entries, counts.as_ref(), !premium);
    r.breach_check_failed = failed;
    Ok(HealthReport {
        total_passwords: r.total_passwords,
        reused_count: r.reused_count,
        weak_count: r.weak_count,
        breached_count: r.breached_count(),
        score: r.score,
        reused_ids: r.reused_ids,
        weak_ids: r.weak_ids,
        breached_ids: r.breached_ids,
        breach_counts: r.breach_counts,
        breach_locked: r.breach_locked,
        breach_check_failed: r.breach_check_failed,
    })
}

fn health_entries(state: &State<'_, VaultState>) -> CommandResult<Vec<health::HealthLogin>> {
    let vault = lock_vault(state)?;
    let (items, _) = vault.list()?;
    let names: HashMap<String, String> = items.into_iter().map(|i| (i.id, i.display_name)).collect();
    let (payloads, _) = vault.all_payloads()?;
    let logins: Vec<(String, qasa_vault::payload::LoginFields)> = payloads
        .into_iter()
        .filter_map(|(id, p)| match p {
            VaultPayload::Login(f) => Some((id, f)),
            _ => None,
        })
        .collect();
    let mut entries = health::health_logins(logins.iter().map(|(id, f)| (id.as_str(), f)));
    for entry in &mut entries {
        if let Some(name) = names.get(&entry.id) {
            entry.display_name = name.clone();
        }
    }
    Ok(entries)
}

// MARK: generator

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct GenerateRequest {
    pub length: usize,
    pub uppercase: bool,
    pub lowercase: bool,
    pub digits: bool,
    pub symbols: bool,
}

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
pub struct Generated {
    pub password: String,
    /// `gen.weak` / `gen.fair` / `gen.strong` / `gen.veryStrong`.
    pub strength_key: &'static str,
    /// Filled segments out of 5.
    pub strength_filled: u8,
}

/// Needs no unlocked vault: the generator page works on its own.
#[tauri::command]
pub fn password_generate(options: GenerateRequest) -> CommandResult<Generated> {
    let options = GeneratorOptions {
        length: options.length,
        uppercase: options.uppercase,
        lowercase: options.lowercase,
        digits: options.digits,
        symbols: options.symbols,
    };
    let password = generator::generate(&options).map_err(|e| CommandError::Invalid(format!("{e:?}")))?;
    let strength = generator::strength(&options);
    Ok(Generated { password, strength_key: strength.l10n_key(), strength_filled: strength.filled_segments() })
}

// MARK: 2FA

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
pub struct TotpSetup {
    /// The normalised URI to store on the login.
    pub uri: String,
    pub issuer: Option<String>,
    pub account_name: Option<String>,
}

/// Parses pasted text (a setup key or an `otpauth://` link). Errors: `invalid`, with
/// message `notOtpAuth` or `invalid`.
#[tauri::command]
pub fn totp_parse(input: String, scanned: Option<bool>) -> CommandResult<TotpSetup> {
    let parsed = if scanned.unwrap_or(false) { Totp::parse_scanned(&input) } else { Totp::parse(&input) };
    let t = parsed.map_err(|e| CommandError::Invalid(format!("{e:?}")))?;
    Ok(TotpSetup { uri: t.uri, issuer: t.issuer, account_name: t.account_name })
}

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
pub struct TotpCode {
    pub code: String,
    /// "123 456", for display.
    pub grouped: String,
    pub seconds_remaining: u32,
    pub period: u32,
}

fn code_for(stored: &str) -> CommandResult<TotpCode> {
    let t = Totp::from_stored(Some(stored)).ok_or_else(|| CommandError::Invalid("no 2FA code".into()))?;
    let now = now_secs();
    let code = t.code_at(now);
    Ok(TotpCode { grouped: totp::grouped(&code), code, seconds_remaining: t.seconds_remaining(now), period: t.period })
}

/// Reads a 2FA QR code from an image file the user picks (a screenshot of the site's
/// setup page). The desktop's stand-in for the phone camera. `Ok(None)` when cancelled;
/// errors: `invalid` with message `noQr` or `notOtpAuth` / `Invalid`.
#[tauri::command]
pub async fn totp_from_image(app: AppHandle) -> CommandResult<Option<TotpSetup>> {
    let Some(path) = app.dialog().file().add_filter("Image", &["png", "jpg", "jpeg", "webp"]).blocking_pick_file() else {
        return Ok(None);
    };
    let path = path.into_path().map_err(|e| CommandError::Storage(e.to_string()))?;
    let img = image::open(&path).map_err(|_| CommandError::Invalid("noQr".into()))?.to_luma8();
    let mut prepared = rqrr::PreparedImage::prepare(img);
    let text = prepared
        .detect_grids()
        .into_iter()
        .find_map(|grid| grid.decode().ok().map(|(_, content)| content))
        .ok_or_else(|| CommandError::Invalid("noQr".into()))?;
    let t = Totp::parse_scanned(&text).map_err(|e| CommandError::Invalid(format!("{e:?}")))?;
    Ok(Some(TotpSetup { uri: t.uri, issuer: t.issuer, account_name: t.account_name }))
}

/// The current code of a saved login.
#[tauri::command]
pub fn totp_code(state: State<'_, VaultState>, id: String) -> CommandResult<TotpCode> {
    let vault = lock_vault(&state)?;
    match vault.get(&id)? {
        VaultPayload::Login(f) => code_for(f.totp.as_deref().unwrap_or_default()),
        _ => Err(CommandError::Invalid("not a login".into())),
    }
}

/// The current code of a URI that isn't saved yet (the editor's preview).
#[tauri::command]
pub fn totp_preview(uri: String) -> CommandResult<TotpCode> {
    code_for(&uri)
}

// MARK: CSV

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
pub struct ExportResult {
    pub cancelled: bool,
    pub exported: usize,
    /// Items that could not be decrypted and are missing from the file.
    pub failures: usize,
}

/// Asks where to save, then writes every readable item as CSV (iOS `VaultCSV` format).
#[tauri::command]
pub async fn vault_export_csv(app: AppHandle, state: State<'_, VaultState>) -> CommandResult<ExportResult> {
    let (payloads, failures) = {
        let vault = lock_vault(&state)?;
        vault.all_payloads()?
    };
    let payloads: Vec<VaultPayload> = payloads.into_iter().map(|(_, p)| p).collect();
    if payloads.is_empty() {
        return Ok(ExportResult { cancelled: false, exported: 0, failures });
    }
    let Some(path) = app
        .dialog()
        .file()
        .set_file_name("qasa-export.csv")
        .add_filter("CSV", &["csv"])
        .blocking_save_file()
    else {
        return Ok(ExportResult { cancelled: true, exported: 0, failures });
    };
    let path = path.into_path().map_err(|e| CommandError::Storage(e.to_string()))?;
    std::fs::write(&path, qasa_vault::csv::export_csv(&payloads)).map_err(|e| CommandError::Storage(e.to_string()))?;
    Ok(ExportResult { cancelled: false, exported: payloads.len(), failures })
}

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
pub struct ImportResult {
    pub cancelled: bool,
    pub imported: usize,
    /// Rows already in the vault.
    pub skipped: usize,
}

/// Asks for a CSV file and adds the rows that aren't already in the vault, all at once.
/// Errors: `invalid` with message `empty` or `invalidHeader`.
#[tauri::command]
pub async fn vault_import_csv(app: AppHandle, state: State<'_, VaultState>) -> CommandResult<ImportResult> {
    let Some(path) = app.dialog().file().add_filter("CSV", &["csv", "txt"]).blocking_pick_file() else {
        return Ok(ImportResult { cancelled: true, imported: 0, skipped: 0 });
    };
    let path = path.into_path().map_err(|e| CommandError::Storage(e.to_string()))?;
    let text = std::fs::read_to_string(&path).map_err(|e| CommandError::Storage(e.to_string()))?;
    let mut vault = lock_vault(&state)?;
    let (existing, _) = vault.all_payloads()?;
    let existing: Vec<VaultPayload> = existing.into_iter().map(|(_, p)| p).collect();
    let outcome = qasa_vault::csv::import_csv(&text, &existing).map_err(|e| match e {
        qasa_vault::csv::CsvError::Empty => CommandError::Invalid("empty".into()),
        _ => CommandError::Invalid("invalidHeader".into()),
    })?;
    let imported = vault.insert_many(&outcome.imported)?;
    Ok(ImportResult { cancelled: false, imported, skipped: outcome.skipped })
}
