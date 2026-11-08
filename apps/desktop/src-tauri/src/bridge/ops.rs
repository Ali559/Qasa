//! What the extension may ask for (plan §8.2.1). Every op that touches a login checks it
//! against the page with the matcher; nothing here trusts the extension to have done so.

use qasa_vault::payload::{LoginFields, VaultPayload};
use qasa_vault::totp::Totp;
use serde_json::{json, Value};

use super::matcher::{self, FILL_THRESHOLD};
use super::{Host, PendingSave, SaveKind};
use crate::vault::error::CommandError;
use crate::vault::Vault;

/// Card numbers and passwords copied from the popup clear after this (iOS `PaymentClipboard`).
const COPY_CLEAR_MS: u64 = 30_000;
const SEARCH_LIMIT: usize = 50;

/// An op's failure, sent to the extension as `"error"`.
#[derive(Debug, PartialEq, Eq, Clone, Copy)]
pub enum OpError {
    Locked,
    NoVault,
    NotFound,
    NoMatch,
    Bad,
}

impl OpError {
    pub fn code(self) -> &'static str {
        match self {
            OpError::Locked => "locked",
            OpError::NoVault => "noVault",
            OpError::NotFound => "notFound",
            OpError::NoMatch => "noMatch",
            OpError::Bad => "bad",
        }
    }
}

impl From<CommandError> for OpError {
    fn from(e: CommandError) -> Self {
        match e {
            CommandError::Locked => OpError::Locked,
            CommandError::NoVault => OpError::NoVault,
            CommandError::NotFound => OpError::NotFound,
            _ => OpError::Bad,
        }
    }
}

type OpResult = Result<Value, OpError>;

fn arg<'a>(args: &'a Value, name: &str) -> Result<&'a str, OpError> {
    args.get(name).and_then(Value::as_str).ok_or(OpError::Bad)
}

/// The page URL, which must be a web address; the desktop matches on its host only.
fn page_url(args: &Value) -> Result<&str, OpError> {
    let url = arg(args, "url")?;
    matcher::host_of(url).ok_or(OpError::Bad)?;
    let lower = url.trim_start().to_ascii_lowercase();
    if lower.starts_with("https://") || lower.starts_with("http://") { Ok(url) } else { Err(OpError::Bad) }
}

/// The login a page may use: `noMatch` unless its website belongs to the page's site.
fn matching_login(vault: &Vault, id: &str, url: &str) -> Result<LoginFields, OpError> {
    match vault.get(id)? {
        VaultPayload::Login(f) if matcher::score(&f.website, url) >= FILL_THRESHOLD => Ok(f),
        VaultPayload::Login(_) => Err(OpError::NoMatch),
        _ => Err(OpError::NotFound),
    }
}

/// The name a login fills into a username field: the username, else the email.
fn user_of(f: &LoginFields) -> &str {
    if f.username.trim().is_empty() { &f.email } else { &f.username }
}

fn title_of(f: &LoginFields) -> String {
    if !f.item_name.trim().is_empty() {
        return f.item_name.clone();
    }
    matcher::host_of(&f.website).unwrap_or_default()
}

fn totp_of(f: &LoginFields) -> Option<Totp> {
    Totp::from_stored(f.totp.as_deref())
}

fn now_secs() -> i64 {
    std::time::SystemTime::now().duration_since(std::time::UNIX_EPOCH).map(|d| d.as_secs() as i64).unwrap_or(0)
}

fn logins(vault: &Vault) -> Result<Vec<(String, LoginFields)>, OpError> {
    let (all, _) = vault.all_payloads()?;
    Ok(all
        .into_iter()
        .filter_map(|(id, p)| match p {
            VaultPayload::Login(f) => Some((id, f)),
            _ => None,
        })
        .collect())
}

fn row(id: &str, f: &LoginFields) -> Value {
    json!({
        "id": id,
        "title": title_of(f),
        "username": user_of(f),
        "hasTotp": totp_of(f).is_some(),
    })
}

pub fn status(vault: &Vault, host: &dyn Host) -> OpResult {
    let s = vault.status().map_err(OpError::from)?;
    let state = if s.unlocked {
        "unlocked"
    } else if s.exists {
        "locked"
    } else {
        "noVault"
    };
    Ok(json!({ "state": state, "device": host.device_name() }))
}

pub fn matches(vault: &Vault, args: &Value) -> OpResult {
    let url = page_url(args)?;
    let mut found: Vec<(u32, String, LoginFields)> = logins(vault)?
        .into_iter()
        .map(|(id, f)| (matcher::score(&f.website, url), id, f))
        .filter(|(score, _, _)| *score >= FILL_THRESHOLD)
        .collect();
    found.sort_by(|a, b| b.0.cmp(&a.0).then_with(|| title_of(&a.2).to_lowercase().cmp(&title_of(&b.2).to_lowercase())));
    Ok(json!({ "items": found.iter().map(|(_, id, f)| row(id, f)).collect::<Vec<_>>() }))
}

pub fn fill(vault: &mut Vault, args: &Value) -> OpResult {
    let (id, url) = (arg(args, "itemId")?, page_url(args)?);
    let f = matching_login(vault, id, url)?;
    vault.mark_used(id).map_err(OpError::from)?;
    let mut out = json!({ "username": user_of(&f), "password": f.password });
    if let Some(t) = totp_of(&f) {
        out["totp"] = json!(t.code_at(now_secs()));
    }
    Ok(out)
}

pub fn totp(vault: &Vault, args: &Value) -> OpResult {
    let (id, url) = (arg(args, "itemId")?, page_url(args)?);
    let t = totp_of(&matching_login(vault, id, url)?).ok_or(OpError::NotFound)?;
    let now = now_secs();
    Ok(json!({ "code": t.code_at(now), "period": t.period, "remaining": t.seconds_remaining(now) }))
}

/// Popup only (the service worker enforces it): the user picked the item by hand, so any
/// login may be copied, and the desktop's clipboard clears it again.
pub fn copy(vault: &mut Vault, args: &Value, host: &dyn Host) -> OpResult {
    let id = arg(args, "itemId")?;
    let VaultPayload::Login(f) = vault.get(id)? else { return Err(OpError::NotFound) };
    let (value, clear_ms) = match arg(args, "field")? {
        "username" => (user_of(&f).to_string(), COPY_CLEAR_MS),
        "password" => (f.password.clone(), COPY_CLEAR_MS),
        "totp" => {
            let t = totp_of(&f).ok_or(OpError::NotFound)?;
            let now = now_secs();
            (t.code_at(now), u64::from(t.seconds_remaining(now)) * 1000)
        }
        _ => return Err(OpError::Bad),
    };
    if value.is_empty() {
        return Err(OpError::NotFound);
    }
    host.copy(value, clear_ms);
    vault.mark_used(id).map_err(OpError::from)?;
    Ok(json!({}))
}

pub fn search(vault: &Vault, args: &Value) -> OpResult {
    let query = arg(args, "query")?.trim().to_lowercase();
    let mut found: Vec<(String, LoginFields)> = logins(vault)?
        .into_iter()
        .filter(|(_, f)| {
            query.is_empty()
                || [&f.item_name, &f.username, &f.email, &f.website].iter().any(|s| s.to_lowercase().contains(&query))
        })
        .collect();
    found.sort_by_key(|(_, f)| title_of(f).to_lowercase());
    let items: Vec<Value> = found
        .iter()
        .take(SEARCH_LIMIT)
        .map(|(id, f)| {
            let mut r = row(id, f);
            r["website"] = json!(f.website);
            r
        })
        .collect();
    Ok(json!({ "items": items }))
}

pub fn generate() -> OpResult {
    let password = qasa_vault::generator::generate(&Default::default()).map_err(|_| OpError::Bad)?;
    Ok(json!({ "password": password }))
}

/// Decides between nothing, a new login and a password update, without telling the
/// extension anything about what is already saved.
pub fn plan_save(vault: &Vault, args: &Value) -> Result<Option<PendingSave>, OpError> {
    let url = page_url(args)?;
    let username = arg(args, "username")?.trim().to_string();
    let password = arg(args, "password")?.to_string();
    if password.is_empty() || password.len() > 4096 || username.len() > 1024 {
        return Err(OpError::Bad);
    }
    let host = matcher::host_of(url).ok_or(OpError::Bad)?;
    let same_user = |f: &LoginFields| {
        !username.is_empty() && (f.username.trim().eq_ignore_ascii_case(&username) || f.email.trim().eq_ignore_ascii_case(&username))
    };
    let site_logins: Vec<(String, LoginFields)> =
        logins(vault)?.into_iter().filter(|(_, f)| matcher::score(&f.website, url) >= FILL_THRESHOLD).collect();
    // Same login already saved with this password (also when the form had no username).
    if site_logins.iter().any(|(_, f)| f.password == password && (username.is_empty() || same_user(f))) {
        return Ok(None);
    }
    let existing = site_logins.iter().find(|(_, f)| same_user(f)).or_else(|| {
        // A change-password form often has no username field: one login for the site is it.
        (username.is_empty() && site_logins.len() == 1).then(|| &site_logins[0])
    });
    let origin = url::Url::parse(url).map(|u| u.origin().ascii_serialization()).unwrap_or_else(|_| format!("https://{host}"));
    Ok(Some(match existing {
        Some((id, f)) => PendingSave {
            kind: SaveKind::Update { item_id: id.clone(), title: title_of(f) },
            host,
            origin,
            username: user_of(f).to_string(),
            password,
        },
        None => PendingSave { kind: SaveKind::New, host, origin, username, password },
    }))
}

/// Writes a save the user confirmed in the desktop.
pub fn apply_save(vault: &mut Vault, save: &PendingSave) -> Result<(), OpError> {
    match &save.kind {
        SaveKind::New => {
            let (username, email) =
                if save.username.contains('@') { (String::new(), save.username.clone()) } else { (save.username.clone(), String::new()) };
            let fields = LoginFields {
                item_name: save.host.clone(),
                username,
                email,
                password: save.password.clone(),
                website: save.origin.clone(),
                ..Default::default()
            };
            vault.save(None, &VaultPayload::Login(fields)).map_err(OpError::from)?;
        }
        SaveKind::Update { item_id, .. } => {
            let VaultPayload::Login(mut f) = vault.get(item_id)? else { return Err(OpError::NotFound) };
            f.password = save.password.clone();
            vault.save(Some(item_id), &VaultPayload::Login(f)).map_err(OpError::from)?;
        }
    }
    Ok(())
}
