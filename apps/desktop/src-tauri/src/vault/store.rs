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

    /// Writes winning remote rows as they are (still vault-key ciphertext). Search tokens
    /// for these items are rebuilt at the next unlock, when the search key is available.
    pub fn apply_sync(&mut self, plan: &qasa_sync::MergePlan) -> CommandResult<u32> {
        let tx = self.conn.transaction()?;
        for i in &plan.items {
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
        Ok((plan.items.len() + plan.collections.len()) as u32)
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
