//! The SQLite vault file. Rows mirror iOS `VaultModels.swift` (`VaultItemRecord`,
//! `VaultSearchToken`, `VaultCollection`); the key material iOS keeps in the Keychain
//! (salt, verifier, kdfVersion) lives in `vault_meta`. Nothing here is plaintext vault
//! content: collection names are vault-key ciphertext in `name_enc` (plan decision 5). The
//! old `name` column only holds names from before that change, until the next unlock
//! encrypts them and blanks it.

use std::path::Path;

use rusqlite::{params, Connection, OptionalExtension};

use super::error::CommandResult;

const SCHEMA: &str = "
CREATE TABLE IF NOT EXISTS vault_meta (
    key   TEXT PRIMARY KEY,
    value BLOB NOT NULL
);
CREATE TABLE IF NOT EXISTS items (
    id             TEXT PRIMARY KEY,
    kind           TEXT NOT NULL,
    envelope       BLOB NOT NULL,
    payload        BLOB NOT NULL,
    schema_version INTEGER NOT NULL DEFAULT 1,
    legacy_payload BLOB,
    created_at     INTEGER NOT NULL,
    updated_at     INTEGER NOT NULL,
    last_used_at   INTEGER,
    collection_id  TEXT,
    deleted_at     INTEGER
);
CREATE TABLE IF NOT EXISTS search_tokens (
    item_id    TEXT NOT NULL,
    token_hash TEXT NOT NULL,
    PRIMARY KEY (item_id, token_hash)
);
CREATE INDEX IF NOT EXISTS search_tokens_hash ON search_tokens (token_hash);
CREATE TABLE IF NOT EXISTS collections (
    id         TEXT PRIMARY KEY,
    name       TEXT NOT NULL DEFAULT '',
    name_enc   BLOB,
    created_at INTEGER NOT NULL,
    deleted_at INTEGER
);
";

/// The item kinds every platform stores (`ItemKind::as_str`).
pub const ITEM_KINDS: [&str; 5] = ["login", "secureNote", "payment", "personalInfo", "idDocument"];

/// Structural row rules SQLite can check (payloads are ciphertext, so field rules live in
/// `qasa_vault::validate` on the user write path). SQLite can't add a CHECK to an existing
/// table, so these are triggers; versioned names, so a later rule change adds `_v2` and
/// drops these. A row breaks them when:
/// - its kind isn't one of [`ITEM_KINDS`];
/// - its `schema_version` is below 1;
/// - it is live (`deleted_at IS NULL`) with an empty envelope or payload. Tombstones carry
///   no ciphertext.
///
/// On UPDATE a rule only applies when the write touches that value, so rows written by
/// older builds (a legacy `schema_version` 0 row with `legacy_payload`) can still be
/// marked used, tombstoned or replaced; they're just never written anew.
const ITEM_TRIGGERS: &str = "
CREATE TRIGGER IF NOT EXISTS items_structure_v1_insert BEFORE INSERT ON items
WHEN NEW.kind NOT IN ('login', 'secureNote', 'payment', 'personalInfo', 'idDocument')
  OR NEW.schema_version IS NULL OR NEW.schema_version < 1
  OR (NEW.deleted_at IS NULL AND (COALESCE(length(NEW.envelope), 0) = 0 OR COALESCE(length(NEW.payload), 0) = 0))
BEGIN
  SELECT RAISE(ABORT, 'qasa: item row breaks the structural rules');
END;
CREATE TRIGGER IF NOT EXISTS items_structure_v1_update BEFORE UPDATE OF kind, envelope, payload, schema_version, deleted_at ON items
WHEN (NEW.kind IS NOT OLD.kind AND NEW.kind NOT IN ('login', 'secureNote', 'payment', 'personalInfo', 'idDocument'))
  OR (NEW.schema_version IS NOT OLD.schema_version AND (NEW.schema_version IS NULL OR NEW.schema_version < 1))
  OR (NEW.deleted_at IS NULL
      AND (COALESCE(length(NEW.envelope), 0) = 0 OR COALESCE(length(NEW.payload), 0) = 0)
      AND NOT (NEW.schema_version < 1 AND NEW.legacy_payload IS NOT NULL
               AND NEW.envelope IS OLD.envelope AND NEW.payload IS OLD.payload))
BEGIN
  SELECT RAISE(ABORT, 'qasa: item row breaks the structural rules');
END;
";

/// The same rules as [`ITEM_TRIGGERS`] for a row about to be written, so a sync or restore
/// can skip one bad remote row instead of aborting the whole merge on the trigger.
pub fn row_is_well_formed(kind: &str, envelope: &[u8], payload: &[u8], schema_version: i64, deleted_at: Option<i64>) -> bool {
    ITEM_KINDS.contains(&kind) && schema_version >= 1 && (deleted_at.is_some() || (!envelope.is_empty() && !payload.is_empty()))
}

/// Additive schema changes for vault files made by earlier builds.
fn migrate(conn: &Connection) -> rusqlite::Result<()> {
    let has_updated: bool = conn
        .prepare("SELECT 1 FROM pragma_table_info('collections') WHERE name = 'updated_at'")?
        .exists([])?;
    if !has_updated {
        // Wi‑Fi sync merges collections by last change.
        conn.execute_batch(
            "ALTER TABLE collections ADD COLUMN updated_at INTEGER NOT NULL DEFAULT 0;
             UPDATE collections SET updated_at = created_at;",
        )?;
    }
    let has_name_enc: bool = conn.prepare("SELECT 1 FROM pragma_table_info('collections') WHERE name = 'name_enc'")?.exists([])?;
    if !has_name_enc {
        // Encrypted names (plan decision 5). Existing names are encrypted at the next unlock.
        conn.execute_batch("ALTER TABLE collections ADD COLUMN name_enc BLOB;")?;
    }
    // Structural item rules. Existing rows are not re-checked (triggers only see writes).
    conn.execute_batch(ITEM_TRIGGERS)?;
    Ok(())
}

fn set_names(tx: &rusqlite::Transaction<'_>, names: &[(String, Vec<u8>)]) -> rusqlite::Result<()> {
    let mut update = tx.prepare_cached("UPDATE collections SET name_enc = ?2, name = '' WHERE id = ?1")?;
    for (id, enc) in names {
        update.execute(params![id, enc])?;
    }
    Ok(())
}

pub const META_SALT: &str = "kdf_salt";
pub const META_VERIFIER: &str = "password_verifier";
pub const META_KDF_VERSION: &str = "kdf_version";
/// `1` when the user turned on fingerprint / Hello unlock (sealed or waiting to be).
pub const META_BIOMETRIC_ENABLED: &str = "biometric_enabled";
/// The sealed root key (`qasa_biometric::Sealed` as JSON).
pub const META_BIOMETRIC_SEALED: &str = "biometric_sealed";

/// One encrypted item row.
pub struct ItemRow {
    pub id: String,
    pub kind: String,
    pub envelope: Vec<u8>,
    pub payload: Vec<u8>,
    pub schema_version: i64,
    pub legacy_payload: Option<Vec<u8>>,
    pub created_at: i64,
    pub updated_at: i64,
    pub last_used_at: Option<i64>,
    pub collection_id: Option<String>,
}

pub struct CollectionRow {
    pub id: String,
    /// Vault-key ciphertext of the name; `None` only for a name from before encryption.
    pub name_enc: Option<Vec<u8>>,
    /// A name from before encryption, until the next unlock encrypts it. Empty otherwise.
    pub legacy_name: String,
    pub created_at: i64,
}

pub struct Store {
    conn: Connection,
}

pub fn now_ms() -> i64 {
    std::time::SystemTime::now()
        .duration_since(std::time::UNIX_EPOCH)
        .map(|d| d.as_millis() as i64)
        .unwrap_or_default()
}

impl Store {
    pub fn open(path: &Path) -> CommandResult<Self> {
        let conn = Connection::open(path)?;
        conn.pragma_update(None, "journal_mode", "WAL")?;
        // Overwritten and deleted rows are zeroed, so a folder name blanked after encryption
        // (or a deleted item) doesn't linger in free pages of the file.
        conn.pragma_update(None, "secure_delete", "ON")?;
        conn.pragma_update(None, "foreign_keys", "ON")?;
        conn.execute_batch(SCHEMA)?;
        migrate(&conn)?;
        Ok(Self { conn })
    }

    #[cfg(test)]
    pub fn in_memory() -> Self {
        let conn = Connection::open_in_memory().unwrap();
        conn.execute_batch(SCHEMA).unwrap();
        migrate(&conn).unwrap();
        Self { conn }
    }

    // MARK: meta

    pub fn meta(&self, key: &str) -> CommandResult<Option<Vec<u8>>> {
        Ok(self
            .conn
            .query_row("SELECT value FROM vault_meta WHERE key = ?1", [key], |r| r.get(0))
            .optional()?)
    }

    pub fn set_meta(&self, key: &str, value: &[u8]) -> CommandResult<()> {
        self.conn.execute(
            "INSERT INTO vault_meta (key, value) VALUES (?1, ?2)
             ON CONFLICT(key) DO UPDATE SET value = excluded.value",
            params![key, value],
        )?;
        Ok(())
    }

    pub fn delete_meta(&self, key: &str) -> CommandResult<()> {
        self.conn.execute("DELETE FROM vault_meta WHERE key = ?1", [key])?;
        Ok(())
    }

    pub fn vault_exists(&self) -> CommandResult<bool> {
        Ok(self.meta(META_VERIFIER)?.is_some())
    }

    // MARK: items

    fn map_item(r: &rusqlite::Row<'_>) -> rusqlite::Result<ItemRow> {
        Ok(ItemRow {
            id: r.get(0)?,
            kind: r.get(1)?,
            envelope: r.get(2)?,
            payload: r.get(3)?,
            schema_version: r.get(4)?,
            legacy_payload: r.get(5)?,
            created_at: r.get(6)?,
            updated_at: r.get(7)?,
            last_used_at: r.get(8)?,
            collection_id: r.get(9)?,
        })
    }

    const ITEM_COLUMNS: &'static str = "id, kind, envelope, payload, schema_version, legacy_payload, \
         created_at, updated_at, last_used_at, collection_id";

    /// Live (not deleted) items, newest change first.
    pub fn items(&self) -> CommandResult<Vec<ItemRow>> {
        let mut stmt = self.conn.prepare(&format!(
            "SELECT {} FROM items WHERE deleted_at IS NULL ORDER BY updated_at DESC",
            Self::ITEM_COLUMNS
        ))?;
        let rows = stmt.query_map([], Self::map_item)?.collect::<Result<Vec<_>, _>>()?;
        Ok(rows)
    }

    /// Live (not deleted) items, without reading them (the backup reminder).
    pub fn live_item_count(&self) -> CommandResult<u32> {
        Ok(self.conn.query_row("SELECT COUNT(*) FROM items WHERE deleted_at IS NULL", [], |r| r.get::<_, i64>(0))? as u32)
    }

    pub fn item(&self, id: &str) -> CommandResult<Option<ItemRow>> {
        Ok(self
            .conn
            .query_row(
                &format!("SELECT {} FROM items WHERE id = ?1 AND deleted_at IS NULL", Self::ITEM_COLUMNS),
                [id],
                Self::map_item,
            )
            .optional()?)
    }

    /// Inserts or replaces an item and its search tokens in one transaction.
    pub fn put_item(&mut self, row: &ItemRow, token_hashes: &[String]) -> CommandResult<()> {
        let tx = self.conn.transaction()?;
        tx.execute(
            "INSERT INTO items (id, kind, envelope, payload, schema_version, legacy_payload,
                                created_at, updated_at, last_used_at, collection_id, deleted_at)
             VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, ?9, ?10, NULL)
             ON CONFLICT(id) DO UPDATE SET
                kind = excluded.kind, envelope = excluded.envelope, payload = excluded.payload,
                schema_version = excluded.schema_version, legacy_payload = excluded.legacy_payload,
                updated_at = excluded.updated_at, last_used_at = excluded.last_used_at,
                collection_id = excluded.collection_id, deleted_at = NULL",
            params![
                row.id,
                row.kind,
                row.envelope,
                row.payload,
                row.schema_version,
                row.legacy_payload,
                row.created_at,
                row.updated_at,
                row.last_used_at,
                row.collection_id
            ],
        )?;
        tx.execute("DELETE FROM search_tokens WHERE item_id = ?1", [&row.id])?;
        {
            let mut insert = tx.prepare("INSERT OR IGNORE INTO search_tokens (item_id, token_hash) VALUES (?1, ?2)")?;
            for hash in token_hashes {
                insert.execute(params![row.id, hash])?;
            }
        }
        tx.commit()?;
        Ok(())
    }

    /// Replaces every item at once (master password change, CSV import), and, for a master
    /// password change, every collection name re-encrypted. All or nothing.
    pub fn put_items(&mut self, rows: &[(ItemRow, Vec<String>)], collection_names: &[(String, Vec<u8>)]) -> CommandResult<()> {
        let tx = self.conn.transaction()?;
        set_names(&tx, collection_names)?;
        for (row, hashes) in rows {
            tx.execute(
                "INSERT INTO items (id, kind, envelope, payload, schema_version, legacy_payload,
                                    created_at, updated_at, last_used_at, collection_id, deleted_at)
                 VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, ?9, ?10, NULL)
                 ON CONFLICT(id) DO UPDATE SET
                    kind = excluded.kind, envelope = excluded.envelope, payload = excluded.payload,
                    schema_version = excluded.schema_version, legacy_payload = excluded.legacy_payload,
                    updated_at = excluded.updated_at, last_used_at = excluded.last_used_at,
                    collection_id = excluded.collection_id, deleted_at = NULL",
                params![
                    row.id,
                    row.kind,
                    row.envelope,
                    row.payload,
                    row.schema_version,
                    row.legacy_payload,
                    row.created_at,
                    row.updated_at,
                    row.last_used_at,
                    row.collection_id
                ],
            )?;
            tx.execute("DELETE FROM search_tokens WHERE item_id = ?1", [&row.id])?;
            let mut insert = tx.prepare_cached("INSERT OR IGNORE INTO search_tokens (item_id, token_hash) VALUES (?1, ?2)")?;
            for hash in hashes {
                insert.execute(params![row.id, hash])?;
            }
        }
        tx.commit()?;
        Ok(())
    }

    /// A tombstone, kept for Wi‑Fi sync (plan §6.3). The ciphertext is dropped.
    pub fn delete_item(&mut self, id: &str) -> CommandResult<bool> {
        let tx = self.conn.transaction()?;
        let changed = tx.execute(
            "UPDATE items SET deleted_at = ?2, updated_at = ?2, envelope = x'', payload = x'',
                              legacy_payload = NULL
             WHERE id = ?1 AND deleted_at IS NULL",
            params![id, now_ms()],
        )?;
        tx.execute("DELETE FROM search_tokens WHERE item_id = ?1", [id])?;
        tx.commit()?;
        Ok(changed > 0)
    }

    pub fn mark_used(&self, id: &str) -> CommandResult<()> {
        let now = now_ms();
        self.conn.execute(
            "UPDATE items SET last_used_at = ?2, updated_at = ?2 WHERE id = ?1 AND deleted_at IS NULL",
            params![id, now],
        )?;
        Ok(())
    }

    // MARK: collections

    pub fn collections(&self) -> CommandResult<Vec<CollectionRow>> {
        let mut stmt = self
            .conn
            .prepare("SELECT id, name_enc, name, created_at FROM collections WHERE deleted_at IS NULL ORDER BY created_at")?;
        let rows = stmt
            .query_map([], |r| Ok(CollectionRow { id: r.get(0)?, name_enc: r.get(1)?, legacy_name: r.get(2)?, created_at: r.get(3)? }))?
            .collect::<Result<Vec<_>, _>>()?;
        Ok(rows)
    }

    /// Creates or renames a collection. `name_enc` is the vault-key ciphertext of the name.
    pub fn put_collection(&self, id: &str, name_enc: &[u8], created_at: i64) -> CommandResult<()> {
        self.conn.execute(
            "INSERT INTO collections (id, name, name_enc, created_at, updated_at) VALUES (?1, '', ?2, ?3, ?4)
             ON CONFLICT(id) DO UPDATE SET name = '', name_enc = excluded.name_enc, deleted_at = NULL,
                updated_at = excluded.updated_at",
            params![id, name_enc, created_at, now_ms()],
        )?;
        Ok(())
    }

    /// Collections whose name is still plaintext (made before names were encrypted).
    pub fn plaintext_collection_names(&self) -> CommandResult<Vec<(String, String)>> {
        let mut stmt = self.conn.prepare("SELECT id, name FROM collections WHERE name <> ''")?;
        let rows = stmt.query_map([], |r| Ok((r.get(0)?, r.get(1)?)))?.collect::<Result<Vec<_>, _>>()?;
        Ok(rows)
    }

    /// Every encrypted collection name, tombstones aside (master password change).
    pub fn collection_names_enc(&self) -> CommandResult<Vec<(String, Vec<u8>)>> {
        let mut stmt = self.conn.prepare("SELECT id, name_enc FROM collections WHERE name_enc IS NOT NULL AND length(name_enc) > 0")?;
        let rows = stmt.query_map([], |r| Ok((r.get(0)?, r.get(1)?)))?.collect::<Result<Vec<_>, _>>()?;
        Ok(rows)
    }

    /// Stores encrypted names and blanks the plaintext ones, in one transaction. Not an edit:
    /// `updated_at` stays, so this never wins a sync merge on its own.
    pub fn set_collection_names_enc(&mut self, names: &[(String, Vec<u8>)]) -> CommandResult<()> {
        let tx = self.conn.transaction()?;
        set_names(&tx, names)?;
        tx.commit()?;
        Ok(())
    }

    pub fn delete_collection(&self, id: &str) -> CommandResult<()> {
        let now = now_ms();
        // A tombstone carries no name, not even encrypted.
        self.conn.execute(
            "UPDATE collections SET deleted_at = ?2, updated_at = ?2, name = '', name_enc = x'' WHERE id = ?1",
            params![id, now],
        )?;
        Ok(())
    }

    // MARK: sync (raw rows, tombstones included)

    /// Every item row, deleted ones too, for a Wi‑Fi sync snapshot.
    pub fn sync_items(&self) -> CommandResult<Vec<qasa_sync::WireItem>> {
        let mut stmt = self.conn.prepare(
            "SELECT id, kind, envelope, payload, schema_version, created_at, updated_at, last_used_at,
                    collection_id, deleted_at FROM items",
        )?;
        let rows = stmt
            .query_map([], |r| {
                Ok(qasa_sync::WireItem {
                    id: r.get(0)?,
                    kind: r.get(1)?,
                    envelope: r.get(2)?,
                    payload: r.get(3)?,
                    schema_version: r.get(4)?,
                    created_at: r.get(5)?,
                    updated_at: r.get(6)?,
                    last_used_at: r.get(7)?,
                    collection_id: r.get(8)?,
                    deleted_at: r.get(9)?,
                })
            })?
            .collect::<Result<Vec<_>, _>>()?;
        Ok(rows)
    }

    /// Every collection for a sync snapshot, as ciphertext. A name not yet encrypted (made
    /// before names were, and no unlock since) is left out until it is.
    pub fn sync_collections(&self) -> CommandResult<Vec<qasa_sync::WireCollection>> {
        let mut stmt = self.conn.prepare(
            "SELECT id, COALESCE(name_enc, x''), created_at, updated_at, deleted_at FROM collections
             WHERE name_enc IS NOT NULL OR deleted_at IS NOT NULL",
        )?;
        let rows = stmt
            .query_map([], |r| {
                Ok(qasa_sync::WireCollection {
                    id: r.get(0)?,
                    name_enc: r.get(1)?,
                    created_at: r.get(2)?,
                    updated_at: r.get(3)?,
                    deleted_at: r.get(4)?,
                })
            })?
            .collect::<Result<Vec<_>, _>>()?;
        Ok(rows)
    }

    /// Writes winning remote rows as they are (still vault-key ciphertext, never decrypted
    /// or rewritten here: Wi‑Fi sync and backup restore). Only the structural rules apply;
    /// a malformed remote row is skipped (and not counted) rather than failing the merge.
    /// Search tokens for these items are rebuilt at the next unlock, when the search key is
    /// available.
    pub fn apply_sync(&mut self, plan: &qasa_sync::MergePlan) -> CommandResult<u32> {
        let tx = self.conn.transaction()?;
        let mut skipped = 0usize;
        for i in &plan.items {
            if !row_is_well_formed(&i.kind, &i.envelope, &i.payload, i.schema_version, i.deleted_at) {
                eprintln!("[qasa] sync: skipped a malformed item row ({})", i.id);
                skipped += 1;
                continue;
            }
            tx.execute(
                "INSERT INTO items (id, kind, envelope, payload, schema_version, legacy_payload,
                                    created_at, updated_at, last_used_at, collection_id, deleted_at)
                 VALUES (?1, ?2, ?3, ?4, ?5, NULL, ?6, ?7, ?8, ?9, ?10)
                 ON CONFLICT(id) DO UPDATE SET
                    kind = excluded.kind, envelope = excluded.envelope, payload = excluded.payload,
                    schema_version = excluded.schema_version, legacy_payload = NULL,
                    created_at = excluded.created_at, updated_at = excluded.updated_at,
                    last_used_at = excluded.last_used_at, collection_id = excluded.collection_id,
                    deleted_at = excluded.deleted_at",
                params![i.id, i.kind, i.envelope, i.payload, i.schema_version, i.created_at, i.updated_at, i.last_used_at, i.collection_id, i.deleted_at],
            )?;
            tx.execute("DELETE FROM search_tokens WHERE item_id = ?1", [&i.id])?;
        }
        for c in &plan.collections {
            tx.execute(
                "INSERT INTO collections (id, name, name_enc, created_at, updated_at, deleted_at) VALUES (?1, '', ?2, ?3, ?4, ?5)
                 ON CONFLICT(id) DO UPDATE SET name = '', name_enc = excluded.name_enc, created_at = excluded.created_at,
                    updated_at = excluded.updated_at, deleted_at = excluded.deleted_at",
                params![c.id, c.name_enc, c.created_at, c.updated_at, c.deleted_at],
            )?;
        }
        tx.commit()?;
        Ok((plan.items.len() - skipped + plan.collections.len()) as u32)
    }

    /// Item ids with no search tokens (synced in while locked).
    pub fn items_missing_tokens(&self) -> CommandResult<Vec<String>> {
        let mut stmt = self.conn.prepare(
            "SELECT id FROM items i WHERE deleted_at IS NULL
               AND NOT EXISTS (SELECT 1 FROM search_tokens t WHERE t.item_id = i.id)",
        )?;
        let ids = stmt.query_map([], |r| r.get(0))?.collect::<Result<Vec<String>, _>>()?;
        Ok(ids)
    }

    pub fn put_tokens(&mut self, id: &str, hashes: &[String]) -> CommandResult<()> {
        let tx = self.conn.transaction()?;
        tx.execute("DELETE FROM search_tokens WHERE item_id = ?1", [id])?;
        {
            let mut insert = tx.prepare("INSERT OR IGNORE INTO search_tokens (item_id, token_hash) VALUES (?1, ?2)")?;
            for h in hashes {
                insert.execute(params![id, h])?;
            }
        }
        tx.commit()?;
        Ok(())
    }

    /// Wipes everything: "Clear local data". The file stays, empty.
    pub fn wipe(&mut self) -> CommandResult<()> {
        let tx = self.conn.transaction()?;
        tx.execute_batch("DELETE FROM search_tokens; DELETE FROM items; DELETE FROM collections; DELETE FROM vault_meta;")?;
        tx.commit()?;
        self.conn.execute_batch("VACUUM;")?;
        Ok(())
    }

    /// A collection as builds before name encryption wrote it (tests of the migration).
    #[cfg(test)]
    pub fn insert_legacy_collection(&self, id: &str, name: &str) {
        self.conn
            .execute("INSERT INTO collections (id, name, created_at, updated_at) VALUES (?1, ?2, 1, 1)", params![id, name])
            .unwrap();
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn row(id: &str, kind: &str, payload: &[u8]) -> ItemRow {
        ItemRow {
            id: id.into(),
            kind: kind.into(),
            envelope: b"env".to_vec(),
            payload: payload.to_vec(),
            schema_version: 1,
            legacy_payload: None,
            created_at: 1,
            updated_at: 1,
            last_used_at: None,
            collection_id: None,
        }
    }

    fn raw_insert(store: &Store, id: &str, kind: &str, payload: &[u8], schema_version: i64, deleted_at: Option<i64>) -> rusqlite::Result<usize> {
        store.conn.execute(
            "INSERT INTO items (id, kind, envelope, payload, schema_version, created_at, updated_at, deleted_at)
             VALUES (?1, ?2, ?3, ?3, ?4, 1, 1, ?5)",
            params![id, kind, payload, schema_version, deleted_at],
        )
    }

    #[test]
    fn triggers_reject_malformed_live_rows_and_accept_tombstones() {
        let mut store = Store::in_memory();
        // A live row needs ciphertext.
        assert!(store.put_item(&row("A", "login", b""), &[]).is_err());
        assert!(raw_insert(&store, "A", "login", b"", 1, None).is_err());
        // Unknown kind, schema version 0.
        assert!(raw_insert(&store, "B", "Login", b"x", 1, None).is_err());
        assert!(raw_insert(&store, "C", "login", b"x", 0, None).is_err());
        // A tombstone carries nothing.
        raw_insert(&store, "D", "login", b"", 1, Some(5)).unwrap();
        // Well-formed rows go in; a delete makes a tombstone; reviving it empty fails.
        for kind in ITEM_KINDS {
            store.put_item(&row(kind, kind, b"ct"), &[]).unwrap();
        }
        assert!(store.delete_item("login").unwrap());
        assert!(store.conn.execute("UPDATE items SET deleted_at = NULL WHERE id = 'login'", []).is_err());
        assert!(store.conn.execute("UPDATE items SET payload = x'' WHERE id = 'payment'", []).is_err());
        assert!(store.conn.execute("UPDATE items SET kind = 'card' WHERE id = 'payment'", []).is_err());
        store.mark_used("payment").unwrap();
        assert_eq!(store.items().unwrap().len(), 4);
    }

    #[test]
    fn sync_skips_a_malformed_remote_row_instead_of_failing() {
        let mut store = Store::in_memory();
        let item = |id: &str, kind: &str, payload: &[u8], deleted_at: Option<i64>| qasa_sync::WireItem {
            id: id.into(),
            kind: kind.into(),
            envelope: payload.to_vec(),
            payload: payload.to_vec(),
            schema_version: 1,
            created_at: 1,
            updated_at: 2,
            last_used_at: None,
            collection_id: None,
            deleted_at,
        };
        let plan = qasa_sync::MergePlan {
            items: vec![item("GOOD", "login", b"ct", None), item("BAD", "login", b"", None), item("GONE", "secureNote", b"", Some(3))],
            collections: vec![],
        };
        assert_eq!(store.apply_sync(&plan).unwrap(), 2);
        let ids: Vec<String> = store.sync_items().unwrap().into_iter().map(|i| i.id).collect();
        assert!(ids.contains(&"GOOD".to_string()) && ids.contains(&"GONE".to_string()) && !ids.contains(&"BAD".to_string()));
    }

    /// A vault file from a build before the triggers (and before `collections.updated_at`
    /// and `name_enc`), with a legacy row and a tombstone in it.
    #[test]
    fn migration_adds_the_triggers_to_an_existing_file() {
        let dir = tempfile::tempdir().unwrap();
        let path = dir.path().join("vault.sqlite");
        {
            let conn = Connection::open(&path).unwrap();
            conn.execute_batch(
                "CREATE TABLE vault_meta (key TEXT PRIMARY KEY, value BLOB NOT NULL);
                 CREATE TABLE items (id TEXT PRIMARY KEY, kind TEXT NOT NULL, envelope BLOB NOT NULL, payload BLOB NOT NULL,
                     schema_version INTEGER NOT NULL DEFAULT 1, legacy_payload BLOB, created_at INTEGER NOT NULL,
                     updated_at INTEGER NOT NULL, last_used_at INTEGER, collection_id TEXT, deleted_at INTEGER);
                 CREATE TABLE search_tokens (item_id TEXT NOT NULL, token_hash TEXT NOT NULL, PRIMARY KEY (item_id, token_hash));
                 CREATE TABLE collections (id TEXT PRIMARY KEY, name TEXT NOT NULL DEFAULT '', created_at INTEGER NOT NULL, deleted_at INTEGER);
                 INSERT INTO collections (id, name, created_at) VALUES ('F', 'Family', 7);
                 INSERT INTO items VALUES ('LEGACY', 'login', x'', x'', 0, CAST('{}' AS BLOB), 1, 1, NULL, NULL, NULL);
                 INSERT INTO items VALUES ('TOMB', 'login', x'', x'', 1, NULL, 1, 1, NULL, NULL, 9);
                 INSERT INTO items VALUES ('LIVE', 'payment', x'01', x'02', 1, NULL, 1, 1, NULL, NULL, NULL);",
            )
            .unwrap();
        }
        let mut store = Store::open(&path).unwrap();
        let triggers: i64 = store
            .conn
            .query_row("SELECT COUNT(*) FROM sqlite_master WHERE type = 'trigger' AND name LIKE 'items_structure_v1_%'", [], |r| r.get(0))
            .unwrap();
        assert_eq!(triggers, 2);
        // Existing rows are untouched and still usable.
        assert_eq!(store.items().unwrap().len(), 2);
        assert_eq!(store.plaintext_collection_names().unwrap(), [("F".to_string(), "Family".to_string())]);
        store.mark_used("LEGACY").unwrap();
        assert!(store.delete_item("LEGACY").unwrap());
        // New writes are checked.
        assert!(raw_insert(&store, "NEW", "login", b"", 1, None).is_err());
        drop(store);
        // Opening again is a no-op.
        let store = Store::open(&path).unwrap();
        assert_eq!(store.items().unwrap().len(), 1);
    }
}
