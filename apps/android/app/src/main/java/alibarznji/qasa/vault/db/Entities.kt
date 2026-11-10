package alibarznji.qasa.vault.db


import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Update
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "vault_items")
data class VaultItemEntity(
    @PrimaryKey val id: String,
    val kindRaw: String,
    val encryptedEnvelope: ByteArray,
    val encryptedPayload: ByteArray,
    val schemaVersion: Int = 1,
    val updatedAt: Long,
    val createdAt: Long,
    val lastUsedAt: Long? = null,
    val collectionId: String? = null,
    /**
     * Set when the item was deleted (a tombstone, so the delete reaches other devices on
     * the next Wi‑Fi sync). A tombstone keeps only its id and dates: envelope and payload
     * are emptied and its search tokens removed. Every read but the sync snapshot skips it.
     */
    val deletedAt: Long? = null,
) {
    val isDeleted: Boolean get() = deletedAt != null
}

@Entity(tableName = "vault_search_tokens")
data class VaultSearchTokenEntity(
    @PrimaryKey(autoGenerate = true) val rowId: Long = 0,
    val itemId: String,
    val tokenHash: String,
)

@Entity(tableName = "vault_collections")
data class VaultCollectionEntity(
    @PrimaryKey val id: String,
    /**
     * Legacy plaintext name (schema 1–2). Blank once the name is in [nameEnc]: names are
     * never stored or synced in plaintext (plan decision 5). A non-blank value is a
     * collection saved before v3, encrypted at the next unlock.
     */
    val name: String = "",
    val createdAt: Long,
    /** Last create or rename; the sync merge keeps the newer side. */
    @ColumnInfo(defaultValue = "0") val updatedAt: Long = createdAt,
    /** Tombstone, as for items. */
    val deletedAt: Long? = null,
    /**
     * Vault-key AES-GCM ciphertext of the UTF-8 name, the same blob format as an item's
     * envelope ([alibarznji.qasa.vault.crypto.VaultCrypto]). Null for a legacy row not yet
     * encrypted; empty for a tombstone.
     */
    val nameEnc: ByteArray? = null,
) {
    val isDeleted: Boolean get() = deletedAt != null
}


@Entity(tableName = "app_notifications")
data class AppNotificationEntity(
    @PrimaryKey val id: String,
    val title: String,
    val body: String,
    val createdAt: Long,
    val isRead: Boolean = false,
)

@Entity(tableName = "monitored_emails")
data class MonitoredEmailEntity(
    @PrimaryKey val id: String,
    val email: String,
    val statusRaw: String,
    val createdAt: Long,
)

@Dao
interface VaultDao {
    // Live reads: every query a screen, autofill, CSV, health or search reaches skips
    // tombstones. Only the `*Raw` / blocking sync queries below see them.

    @Query(VaultSql.LIVE_ITEMS)
    fun observeItems(): Flow<List<VaultItemEntity>>

    @Query(VaultSql.LIVE_ITEMS)
    suspend fun getAllItems(): List<VaultItemEntity>

    @Query(VaultSql.LIVE_ITEM)
    suspend fun getItem(id: String): VaultItemEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertItem(item: VaultItemEntity)

    /** Turns the item into a tombstone: contents dropped, dates kept for the sync merge. */
    @Query(VaultSql.TOMBSTONE_ITEM)
    suspend fun tombstoneItem(id: String, at: Long)

    @Query("UPDATE vault_items SET lastUsedAt = :at, updatedAt = :at WHERE id = :id AND deletedAt IS NULL")
    suspend fun markUsed(id: String, at: Long)

    @Query("DELETE FROM vault_search_tokens WHERE itemId = :itemId")
    suspend fun deleteTokensForItem(itemId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTokens(tokens: List<VaultSearchTokenEntity>)

    @Query("SELECT DISTINCT itemId FROM vault_search_tokens WHERE tokenHash IN (:hashes)")
    suspend fun findItemIdsByHashes(hashes: List<String>): List<String>

    @Query(VaultSql.LIVE_COLLECTIONS)
    fun observeCollections(): Flow<List<VaultCollectionEntity>>

    @Query(VaultSql.LIVE_COLLECTIONS)
    suspend fun getCollections(): List<VaultCollectionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCollection(c: VaultCollectionEntity)

    @Query(VaultSql.TOMBSTONE_COLLECTION)
    suspend fun tombstoneCollection(id: String, at: Long)

    /** Rename only, so `createdAt` survives (iOS mutates `VaultCollection.name` in place). */
    @Query(VaultSql.RENAME_COLLECTION)
    suspend fun renameCollection(id: String, nameEnc: ByteArray, at: Long)

    /** Every row with a plaintext name left (saved before names were encrypted). */
    @Query(VaultSql.PLAINTEXT_COLLECTION_NAMES)
    suspend fun collectionsWithPlaintextNames(): List<VaultCollectionEntity>

    /** Every row that holds an encrypted name (master password change). */
    @Query(VaultSql.ENCRYPTED_COLLECTION_NAMES)
    suspend fun collectionsWithEncryptedNames(): List<VaultCollectionEntity>

    /**
     * Stores an encrypted name and blanks the plaintext one. Not an edit: `updatedAt`
     * stays, so this never wins a sync merge on its own.
     */
    @Query(VaultSql.SET_COLLECTION_NAME_ENC)
    suspend fun setCollectionNameEnc(id: String, nameEnc: ByteArray)

    // ---- Wi‑Fi sync (blocking: called from the Rust session's thread) ---------------

    /** Every row, tombstones included. */
    @Query("SELECT * FROM vault_items")
    fun allItemsForSync(): List<VaultItemEntity>

    @Query("SELECT * FROM vault_collections")
    fun allCollectionsForSync(): List<VaultCollectionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsertItemsForSync(items: List<VaultItemEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsertCollectionsForSync(collections: List<VaultCollectionEntity>)

    @Query("DELETE FROM vault_search_tokens WHERE itemId IN (:itemIds)")
    fun deleteTokensForItemsBlocking(itemIds: List<String>)

    @Query("DELETE FROM vault_items")
    fun clearItemsBlocking()

    @Query("DELETE FROM vault_search_tokens")
    fun clearTokensBlocking()

    @Query("DELETE FROM vault_collections")
    fun clearCollectionsBlocking()

    @Query("SELECT * FROM app_notifications ORDER BY createdAt DESC")
    fun observeLocalNotifications(): Flow<List<AppNotificationEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertNotification(n: AppNotificationEntity)

    @Query("UPDATE app_notifications SET isRead = 1 WHERE id = :id")
    suspend fun markNotificationRead(id: String)

    @Query("UPDATE app_notifications SET isRead = 1 WHERE isRead = 0")
    suspend fun markAllNotificationsRead()

    @Query("DELETE FROM app_notifications WHERE id = :id")
    suspend fun deleteNotification(id: String)

    @Query("SELECT * FROM monitored_emails ORDER BY createdAt DESC")
    fun observeMonitoredEmails(): Flow<List<MonitoredEmailEntity>>

    @Query("SELECT * FROM monitored_emails ORDER BY createdAt DESC")
    suspend fun getMonitoredEmails(): List<MonitoredEmailEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMonitoredEmail(e: MonitoredEmailEntity)

    @Query("DELETE FROM monitored_emails WHERE id = :id")
    suspend fun deleteMonitoredEmail(id: String)

    @Query("DELETE FROM vault_items")
    suspend fun clearItems()

    @Query("DELETE FROM vault_search_tokens")
    suspend fun clearTokens()

    @Query("DELETE FROM vault_collections")
    suspend fun clearCollections()
}

@Database(
    entities = [
        VaultItemEntity::class,
        VaultSearchTokenEntity::class,
        VaultCollectionEntity::class,
        AppNotificationEntity::class,
        MonitoredEmailEntity::class,
    ],
    version = 4,
    exportSchema = true,
)
abstract class VaultDatabase : RoomDatabase() {
    abstract fun vaultDao(): VaultDao
}

/** SQL shared by the DAO and the JVM tests that run it against a real SQLite. */
object VaultSql {
    const val LIVE_ITEMS = "SELECT * FROM vault_items WHERE deletedAt IS NULL ORDER BY updatedAt DESC"
    const val LIVE_ITEM = "SELECT * FROM vault_items WHERE id = :id AND deletedAt IS NULL LIMIT 1"
    /** Names are ciphertext, so callers sort by the decrypted name. */
    const val LIVE_COLLECTIONS = "SELECT * FROM vault_collections WHERE deletedAt IS NULL ORDER BY createdAt ASC"
    const val TOMBSTONE_ITEM =
        "UPDATE vault_items SET encryptedEnvelope = X'', encryptedPayload = X'', " +
            "deletedAt = :at, updatedAt = :at WHERE id = :id"
    /** A tombstone keeps no name at all, plaintext or encrypted. */
    const val TOMBSTONE_COLLECTION =
        "UPDATE vault_collections SET name = '', nameEnc = X'', deletedAt = :at, updatedAt = :at WHERE id = :id"
    const val RENAME_COLLECTION =
        "UPDATE vault_collections SET name = '', nameEnc = :nameEnc, updatedAt = :at WHERE id = :id AND deletedAt IS NULL"
    const val PLAINTEXT_COLLECTION_NAMES = "SELECT * FROM vault_collections WHERE name <> ''"
    const val ENCRYPTED_COLLECTION_NAMES =
        "SELECT * FROM vault_collections WHERE nameEnc IS NOT NULL AND length(nameEnc) > 0"
    const val SET_COLLECTION_NAME_ENC =
        "UPDATE vault_collections SET name = '', nameEnc = :nameEnc WHERE id = :id"
}

/**
 * Schema migrations. The vault is never rebuilt destructively: a missing migration shows
 * `VaultStoreErrorView` instead of deleting the user's passwords.
 */
object VaultMigrations {
    /** v2: tombstones on items and collections, and `updatedAt` on collections (Wi‑Fi sync). */
    val MIGRATION_1_2_SQL = listOf(
        "ALTER TABLE vault_items ADD COLUMN deletedAt INTEGER",
        "ALTER TABLE vault_collections ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0",
        "UPDATE vault_collections SET updatedAt = createdAt",
        "ALTER TABLE vault_collections ADD COLUMN deletedAt INTEGER",
    )

    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            MIGRATION_1_2_SQL.forEach(db::execSQL)
        }
    }

    /**
     * v3: encrypted collection names (plan decision 5). The plaintext `name` column stays
     * (Room can't drop it without a table rebuild) and is blanked as each name is
     * encrypted at the next unlock, which needs the vault key this migration doesn't
     * have. Tombstones keep no name, so theirs are blanked now.
     */
    val MIGRATION_2_3_SQL = listOf(
        "ALTER TABLE vault_collections ADD COLUMN nameEnc BLOB",
        "UPDATE vault_collections SET name = '', nameEnc = X'' WHERE deletedAt IS NOT NULL",
    )

    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            MIGRATION_2_3_SQL.forEach(db::execSQL)
        }
    }

    /**
     * v4: structural checks on item rows ([VaultRowRules]) as SQLite triggers, since Room
     * entities can't declare CHECK constraints. Existing rows are left as they are (the
     * update trigger only fires on the checked columns); a stray `schemaVersion < 1` is
     * raised to 1 first so a later edit of that row isn't refused.
     */
    val MIGRATION_3_4_SQL: List<String> =
        listOf("UPDATE vault_items SET schemaVersion = 1 WHERE schemaVersion < 1") + VaultRowRules.TRIGGERS_SQL

    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            MIGRATION_3_4_SQL.forEach(db::execSQL)
        }
    }

    val ALL: Array<Migration> = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)

    /** Fresh installs: Room creates the v4 tables itself; the triggers are added here. */
    object CreateTriggersCallback : RoomDatabase.Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            VaultRowRules.TRIGGERS_SQL.forEach(db::execSQL)
        }
    }
}

/**
 * The structural rules every stored item row obeys, whatever wrote it (user save, CSV
 * import, Wi‑Fi sync, backup restore). The payload is ciphertext, so these are the only
 * checks the store itself can make; field rules run on the plaintext before a user save
 * ([alibarznji.qasa.vault.VaultValidation]).
 *
 * - `kindRaw` is one of the five item kinds.
 * - A live row (no `deletedAt`) has a non-empty envelope and payload; a tombstone may not.
 * - `schemaVersion >= 1`.
 */
object VaultRowRules {
    val KINDS: List<String> = listOf("login", "secureNote", "payment", "personalInfo", "idDocument")

    fun isValid(row: VaultItemEntity): Boolean =
        row.kindRaw in KINDS &&
            row.schemaVersion >= 1 &&
            (row.deletedAt != null || (row.encryptedEnvelope.isNotEmpty() && row.encryptedPayload.isNotEmpty()))

    private val kindList = KINDS.joinToString(",") { "'$it'" }

    private fun violation(kindCheck: String) =
        "$kindCheck OR NEW.schemaVersion < 1 OR " +
            "(NEW.deletedAt IS NULL AND (length(NEW.encryptedEnvelope) = 0 OR length(NEW.encryptedPayload) = 0))"

    /**
     * BEFORE INSERT covers `INSERT OR REPLACE` (every `upsert`). The update trigger is
     * scoped to the checked columns, so `markUsed` on a legacy row never aborts, and its
     * kind check only applies when the kind itself changes.
     */
    val TRIGGERS_SQL: List<String> = listOf(
        "CREATE TRIGGER IF NOT EXISTS vault_items_check_insert BEFORE INSERT ON vault_items " +
            "WHEN ${violation("NEW.kindRaw NOT IN ($kindList)")} " +
            "BEGIN SELECT RAISE(ABORT, 'vault_items: invalid row'); END",
        "CREATE TRIGGER IF NOT EXISTS vault_items_check_update " +
            "BEFORE UPDATE OF kindRaw, encryptedEnvelope, encryptedPayload, schemaVersion, deletedAt ON vault_items " +
            "WHEN ${violation("(NEW.kindRaw IS NOT OLD.kindRaw AND NEW.kindRaw NOT IN ($kindList))")} " +
            "BEGIN SELECT RAISE(ABORT, 'vault_items: invalid row'); END",
    )
}
