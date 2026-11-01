package alibarznji.qasa.vault.db


import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Update
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
)

@Entity(tableName = "vault_search_tokens")
data class VaultSearchTokenEntity(
    @PrimaryKey(autoGenerate = true) val rowId: Long = 0,
    val itemId: String,
    val tokenHash: String,
)

@Entity(tableName = "vault_collections")
data class VaultCollectionEntity(
    @PrimaryKey val id: String,
    val name: String,
    val createdAt: Long,
)

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
    @Query("SELECT * FROM vault_items ORDER BY updatedAt DESC")
    fun observeItems(): Flow<List<VaultItemEntity>>

    @Query("SELECT * FROM vault_items ORDER BY updatedAt DESC")
    suspend fun getAllItems(): List<VaultItemEntity>

    @Query("SELECT * FROM vault_items WHERE id = :id LIMIT 1")
    suspend fun getItem(id: String): VaultItemEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertItem(item: VaultItemEntity)

    @Query("DELETE FROM vault_items WHERE id = :id")
    suspend fun deleteItem(id: String)

    @Query("UPDATE vault_items SET lastUsedAt = :at, updatedAt = :at WHERE id = :id")
    suspend fun markUsed(id: String, at: Long)

    @Query("DELETE FROM vault_search_tokens WHERE itemId = :itemId")
    suspend fun deleteTokensForItem(itemId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTokens(tokens: List<VaultSearchTokenEntity>)

    @Query("SELECT DISTINCT itemId FROM vault_search_tokens WHERE tokenHash IN (:hashes)")
    suspend fun findItemIdsByHashes(hashes: List<String>): List<String>

    @Query("SELECT * FROM vault_collections ORDER BY name ASC")
    fun observeCollections(): Flow<List<VaultCollectionEntity>>

    @Query("SELECT * FROM vault_collections ORDER BY name ASC")
    suspend fun getCollections(): List<VaultCollectionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCollection(c: VaultCollectionEntity)

    @Query("DELETE FROM vault_collections WHERE id = :id")
    suspend fun deleteCollection(id: String)

    /** Rename only, so `createdAt` survives (iOS mutates `VaultCollection.name` in place). */
    @Query("UPDATE vault_collections SET name = :name WHERE id = :id")
    suspend fun renameCollection(id: String, name: String)

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
    version = 1,
    exportSchema = true,
)
abstract class VaultDatabase : RoomDatabase() {
    abstract fun vaultDao(): VaultDao
}
