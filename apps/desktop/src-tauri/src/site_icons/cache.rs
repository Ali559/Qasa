//! The per-device icon cache (docs/site-icons.md "Cache"): host → `{ png | none, checkedAt }`.
//!
//! The whole map is one file in the app data folder (`site-icons.qasa`, next to
//! `vault.sqlite`), sealed with the vault's item key by the same AES-256-GCM as items
//! (`qasa_vault::encrypt`): the list of hosts is as private as the vault. It is read at
//! unlock, held in memory only while unlocked, and deleted with the vault (sign out, reset,
//! delete account) and when the setting is turned off. Never synced, backed up or exported.
//! A file that doesn't open with the current key (another vault, a damaged file) reads as
//! empty and is replaced on the next write.

use std::collections::HashMap;
use std::fs;
use std::path::{Path, PathBuf};

use serde::{Deserialize, Serialize};

const DAY_MS: i64 = 24 * 60 * 60 * 1000;
/// An icon is refreshed (in the background) after this.
pub const ICON_TTL_MS: i64 = 30 * DAY_MS;
/// The site answered but had no usable icon: asked again after this.
pub const NONE_TTL_MS: i64 = 7 * DAY_MS;
/// Offline, a timeout or a TLS error: asked again after this.
pub const FAILED_TTL_MS: i64 = DAY_MS;

/// The file's name in the app data folder.
pub const FILE_NAME: &str = "site-icons.qasa";

#[derive(Serialize, Deserialize, Clone, Copy, Debug, PartialEq, Eq)]
#[serde(rename_all = "camelCase")]
pub enum Status {
    /// `png` holds the icon.
    Icon,
    /// The site answered without a usable icon.
    None,
    /// The site couldn't be reached.
    Failed,
}

#[derive(Serialize, Deserialize, Clone, Debug, PartialEq, Eq)]
#[serde(rename_all = "camelCase")]
pub struct Entry {
    pub status: Status,
    /// The normalised 64 × 64 PNG, base64 (standard alphabet), for `Status::Icon`.
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub png: Option<String>,
    /// Unix ms of the last answer (or failure).
    pub checked_at: i64,
    /// `SHAPED` when `png` went through step 7 (docs/site-icons.md); missing in entries
    /// written before Phase 11, whose PNG is shaped when the cache is read.
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub shape: Option<u32>,
}

/// The `shape` of an entry written after step 7 existed.
pub const SHAPED: u32 = 1;

impl Entry {
    pub fn icon(png: &[u8], now: i64) -> Self {
        use base64::Engine;
        Self {
            status: Status::Icon,
            png: Some(base64::engine::general_purpose::STANDARD.encode(png)),
            checked_at: now,
            shape: Some(SHAPED),
        }
    }

    pub fn none(now: i64) -> Self {
        Self { status: Status::None, png: None, checked_at: now, shape: Some(SHAPED) }
    }

    pub fn failed(now: i64) -> Self {
        Self { status: Status::Failed, png: None, checked_at: now, shape: Some(SHAPED) }
    }

    pub fn ttl_ms(&self) -> i64 {
        match self.status {
            Status::Icon => ICON_TTL_MS,
            Status::None => NONE_TTL_MS,
            Status::Failed => FAILED_TTL_MS,
        }
    }

    /// Shapes an icon written before step 7 from its own PNG (no network). True when the
    /// entry changed and the file should be written back.
    fn shape_if_needed(&mut self) -> bool {
        use base64::Engine;
        if self.shape.is_some() {
            return false;
        }
        let engine = base64::engine::general_purpose::STANDARD;
        let Some(png) = &self.png else {
            self.shape = Some(SHAPED);
            return true;
        };
        // An undecodable PNG is left as it was (and the tile falls back to the letter).
        let Some(shaped) = engine.decode(png).ok().and_then(|bytes| super::decode::reshape(&bytes)) else { return false };
        self.png = Some(engine.encode(shaped));
        self.shape = Some(SHAPED);
        true
    }

    /// Whether it needs no new request at `now`. An entry from the future (the clock was
    /// set back) is stale, so a wrong clock can't keep a site from being asked again.
    pub fn is_fresh(&self, now: i64) -> bool {
        let age = now - self.checked_at;
        (0..self.ttl_ms()).contains(&age)
    }
}

#[derive(Serialize, Deserialize, Default)]
struct File {
    version: u32,
    entries: HashMap<String, Entry>,
}

/// The cache as the vault holds it. `entries` is None while locked.
#[derive(Default)]
pub struct Cache {
    path: Option<PathBuf>,
    entries: Option<HashMap<String, Entry>>,
}

impl Cache {
    /// A cache stored at `path` (None: memory only, for tests).
    pub fn new(path: Option<PathBuf>) -> Self {
        Self { path, entries: None }
    }

    /// The file next to the vault database.
    pub fn beside(db: &Path) -> Self {
        Self::new(db.parent().map(|dir| dir.join(FILE_NAME)))
    }

    /// Reads the file with the vault key (at unlock). Anything unreadable is an empty cache.
    /// Entries written before step 7 are shaped from their PNG and the file written back.
    pub fn load(&mut self, key: &[u8; 32]) {
        let mut entries = self
            .path
            .as_ref()
            .and_then(|p| fs::read(p).ok())
            .and_then(|blob| qasa_vault::decrypt(&blob, key).ok())
            .and_then(|json| serde_json::from_slice::<File>(&json).ok())
            .map(|f| f.entries)
            .unwrap_or_default();
        let mut changed = false;
        for entry in entries.values_mut() {
            changed |= entry.shape_if_needed();
        }
        self.entries = Some(entries);
        if changed {
            let _ = self.save(key);
        }
    }

    /// Forgets the decrypted map (at lock); the file stays.
    pub fn unload(&mut self) {
        self.entries = None;
    }

    /// Forgets everything and deletes the file (setting off, vault wiped).
    pub fn delete(&mut self) {
        if self.entries.is_some() {
            self.entries = Some(HashMap::new());
        }
        if let Some(path) = &self.path {
            let _ = fs::remove_file(path);
            let _ = fs::remove_file(path.with_extension("qasa.tmp"));
        }
    }

    pub fn get(&self, host: &str) -> Option<&Entry> {
        self.entries.as_ref()?.get(host)
    }

    /// Whether `host` needs no request at `now` (false while locked).
    pub fn is_fresh(&self, host: &str, now: i64) -> bool {
        self.get(host).is_some_and(|e| e.is_fresh(now))
    }

    /// The cached PNG (base64) for `host`, stale or not.
    pub fn png(&self, host: &str) -> Option<&str> {
        self.get(host)?.png.as_deref()
    }

    /// Records an answer and saves the file. A failure never replaces a cached icon: the
    /// old icon keeps showing and is asked again after a day.
    pub fn put(&mut self, key: &[u8; 32], host: &str, entry: Entry) -> std::io::Result<()> {
        let Some(entries) = self.entries.as_mut() else { return Ok(()) };
        let entry = match (entries.get(host), entry.status) {
            (Some(old @ Entry { status: Status::Icon, .. }), Status::Failed) => {
                Entry { checked_at: entry.checked_at - (ICON_TTL_MS - FAILED_TTL_MS), ..old.clone() }
            }
            _ => entry,
        };
        entries.insert(host.to_string(), entry);
        self.save(key)
    }

    /// Writes the map sealed with `key`, atomically (also after a master password change,
    /// with the new key).
    pub fn save(&self, key: &[u8; 32]) -> std::io::Result<()> {
        let (Some(path), Some(entries)) = (&self.path, &self.entries) else { return Ok(()) };
        let json = serde_json::to_vec(&File { version: 1, entries: entries.clone() }).map_err(std::io::Error::other)?;
        if let Some(dir) = path.parent() {
            fs::create_dir_all(dir)?;
        }
        let tmp = path.with_extension("qasa.tmp");
        fs::write(&tmp, qasa_vault::encrypt(&json, key))?;
        fs::rename(&tmp, path)
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    const KEY: [u8; 32] = [7; 32];
    const NOW: i64 = 1_800_000_000_000;

    #[test]
    fn ttls_follow_the_spec_with_an_injected_clock() {
        let icon = Entry::icon(b"png", NOW);
        assert!(icon.is_fresh(NOW));
        assert!(icon.is_fresh(NOW + 30 * DAY_MS - 1));
        assert!(!icon.is_fresh(NOW + 30 * DAY_MS));
        assert!(!icon.is_fresh(NOW - 1), "an entry from the future is stale");

        let none = Entry::none(NOW);
        assert!(none.is_fresh(NOW + 7 * DAY_MS - 1));
        assert!(!none.is_fresh(NOW + 7 * DAY_MS));

        let failed = Entry::failed(NOW);
        assert!(failed.is_fresh(NOW + DAY_MS - 1));
        assert!(!failed.is_fresh(NOW + DAY_MS));

        let mut cache = Cache::new(None);
        assert!(!cache.is_fresh("github.com", NOW), "locked: nothing is fresh");
        cache.load(&KEY);
        cache.put(&KEY, "github.com", Entry::none(NOW)).unwrap();
        assert!(cache.is_fresh("github.com", NOW + 6 * DAY_MS));
        assert!(!cache.is_fresh("github.com", NOW + 8 * DAY_MS));
    }

    #[test]
    fn a_failure_keeps_the_old_icon_and_retries_after_a_day() {
        let mut cache = Cache::new(None);
        cache.load(&KEY);
        cache.put(&KEY, "a.com", Entry::icon(b"png", NOW)).unwrap();
        let later = NOW + 31 * DAY_MS;
        cache.put(&KEY, "a.com", Entry::failed(later)).unwrap();
        assert!(cache.png("a.com").is_some());
        assert!(cache.is_fresh("a.com", later + DAY_MS - 1));
        assert!(!cache.is_fresh("a.com", later + DAY_MS));
    }

    #[test]
    fn the_file_is_sealed_with_the_vault_key() {
        let dir = tempfile::tempdir().unwrap();
        let db = dir.path().join("vault.sqlite");
        let mut cache = Cache::beside(&db);
        cache.load(&KEY);
        cache.put(&KEY, "secret-bank.example.com", Entry::icon(b"png", NOW)).unwrap();
        let raw = fs::read(dir.path().join(FILE_NAME)).unwrap();
        assert!(!raw.windows(11).any(|w| w == b"secret-bank"), "hosts are in the file in the clear");

        let mut again = Cache::beside(&db);
        again.load(&KEY);
        assert_eq!(again.png("secret-bank.example.com"), cache.png("secret-bank.example.com"));
        // Another key reads nothing.
        let mut other = Cache::beside(&db);
        other.load(&[8; 32]);
        assert!(other.get("secret-bank.example.com").is_none());

        again.delete();
        assert!(!dir.path().join(FILE_NAME).exists());
        assert!(again.get("secret-bank.example.com").is_none());
    }

    fn png_of(img: &image::RgbaImage) -> Vec<u8> {
        let mut out = Vec::new();
        image::DynamicImage::ImageRgba8(img.clone())
            .write_to(&mut std::io::Cursor::new(&mut out), image::ImageFormat::Png)
            .unwrap();
        out
    }

    #[test]
    fn an_unshaped_entry_is_shaped_on_read_and_written_back() {
        use base64::Engine;
        let engine = base64::engine::general_purpose::STANDARD;
        // An entry from before Phase 11: a wide solid logo fitted into 64 × 64 as it was then,
        // transparent above and below, with no `shape`.
        let mut old = image::RgbaImage::new(64, 64);
        for y in 16..48 {
            for x in 0..64 {
                old.put_pixel(x, y, image::Rgba([17, 17, 17, 255]));
            }
        }
        let dir = tempfile::tempdir().unwrap();
        let db = dir.path().join("vault.sqlite");
        let mut cache = Cache::beside(&db);
        cache.load(&KEY);
        let unshaped = Entry { shape: None, ..Entry::icon(&png_of(&old), NOW) };
        cache.put(&KEY, "wide.example.com", unshaped.clone()).unwrap();
        cache.put(&KEY, "none.example.com", Entry { shape: None, ..Entry::none(NOW) }).unwrap();

        let mut again = Cache::beside(&db);
        again.load(&KEY);
        let entry = again.get("wide.example.com").unwrap();
        assert_eq!(entry.shape, Some(SHAPED));
        assert_eq!(entry.checked_at, NOW, "shaping is not a new answer from the site");
        let shaped = image::load_from_memory(&engine.decode(entry.png.as_ref().unwrap()).unwrap()).unwrap().to_rgba8();
        assert_eq!(*shaped.get_pixel(32, 2), image::Rgba([17, 17, 17, 255]), "now on its edge colour");

        // Written back: the file itself holds the shaped entry.
        let blob = fs::read(dir.path().join(FILE_NAME)).unwrap();
        let file: File = serde_json::from_slice(&qasa_vault::decrypt(&blob, &KEY).unwrap()).unwrap();
        assert_eq!(file.entries["wide.example.com"], *entry);
        assert_eq!(file.entries["none.example.com"].shape, Some(SHAPED));
        assert_ne!(file.entries["wide.example.com"].png, unshaped.png);
    }
}
