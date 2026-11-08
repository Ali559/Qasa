//! The SQLite vault file. Rows mirror iOS `VaultModels.swift` (`VaultItemRecord`,
//! `VaultSearchToken`, `VaultCollection`); the key material iOS keeps in the Keychain
//! (salt, verifier, kdfVersion) lives in `vault_meta`. Nothing here is plaintext vault
//! content except collection names, which iOS also stores in the clear (plan decision 5).

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
    name       TEXT NOT NULL,
    created_at INTEGER NOT NULL,
    deleted_at INTEGER
);
";

pub const META_SALT: &str = "kdf_salt";
pub const META_VERIFIER: &str = "password_verifier";
pub const META_KDF_VERSION: &str = "kdf_version";

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
    pub name: String,
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
        conn.pragma_update(None, "foreign_keys", "ON")?;
        conn.execute_batch(SCHEMA)?;
        Ok(Self { conn })
    }

    #[cfg(test)]
    pub fn in_memory() -> Self {
        let conn = Connection::open_in_memory().unwrap();
        conn.execute_batch(SCHEMA).unwrap();
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

    /// Replaces every item at once (master password change, CSV import). All or nothing.
    pub fn put_items(&mut self, rows: &[(ItemRow, Vec<String>)]) -> CommandResult<()> {
        let tx = self.conn.transaction()?;
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
        let mut stmt =
            self.conn.prepare("SELECT id, name, created_at FROM collections WHERE deleted_at IS NULL ORDER BY created_at")?;
        let rows = stmt
            .query_map([], |r| Ok(CollectionRow { id: r.get(0)?, name: r.get(1)?, created_at: r.get(2)? }))?
            .collect::<Result<Vec<_>, _>>()?;
        Ok(rows)
    }

    pub fn put_collection(&self, id: &str, name: &str, created_at: i64) -> CommandResult<()> {
        self.conn.execute(
            "INSERT INTO collections (id, name, created_at) VALUES (?1, ?2, ?3)
             ON CONFLICT(id) DO UPDATE SET name = excluded.name, deleted_at = NULL",
            params![id, name, created_at],
        )?;
        Ok(())
    }

    pub fn delete_collection(&self, id: &str) -> CommandResult<()> {
        self.conn.execute("UPDATE collections SET deleted_at = ?2 WHERE id = ?1", params![id, now_ms()])?;
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
}
