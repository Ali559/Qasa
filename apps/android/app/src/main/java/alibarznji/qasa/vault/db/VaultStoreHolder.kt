package alibarznji.qasa.vault.db

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Recorded when the on-disk vault store cannot be opened.
 *
 * The store file is deliberately left in place. Destroying it to "recover" turns a
 * transient failure (out of storage, SQLite lock, WAL contention) into permanent,
 * unrecoverable loss of every password the user has.
 */
data class VaultStoreFailure(
    val underlying: String,
    val storePath: String,
)

/** Thrown when code reaches the vault store before [VaultStoreHolder.open] has succeeded. */
class VaultStoreUnavailableException(val failure: VaultStoreFailure?) :
    IllegalStateException(failure?.underlying ?: "The vault store could not be opened")

/**
 * Owns the Room database and, crucially, its failure state.
 *
 * Room opens lazily on first query, so a migration failure used to surface as a crash
 * from an arbitrary repository call — and the only "recovery" on offer was
 * `fallbackToDestructiveMigration`, which deletes the vault. This opens eagerly inside a
 * `runCatching` and publishes the failure so the UI can show `VaultStoreErrorView`
 * instead. There is deliberately no retry and no reset: the store file is the user's
 * data, not a cache.
 */
@Singleton
class VaultStoreHolder @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val _failure = MutableStateFlow<VaultStoreFailure?>(null)
    val failure: StateFlow<VaultStoreFailure?> = _failure.asStateFlow()

    private val _database = MutableStateFlow<VaultDatabase?>(null)
    val database: StateFlow<VaultDatabase?> = _database.asStateFlow()

    val storePath: String get() = context.getDatabasePath(DATABASE_NAME).absolutePath

    /** Opens the store. Safe to call repeatedly; only the first call does work. */
    fun open(): VaultDatabase? {
        _database.value?.let { return it }
        val store = try {
            build().also { db ->
                // Force the open now so a migration failure is reported here rather
                // than from whichever repository method happens to query first.
                db.openHelper.writableDatabase
            }
        } catch (e: Throwable) {
            _failure.value = VaultStoreFailure(
                underlying = e.message ?: e::class.java.simpleName,
                storePath = storePath,
            )
            return null
        }
        _database.value = store
        return store
    }

    /**
     * The DAO, or `null` when the store could not be opened. Callers must degrade
     * gracefully rather than assume a usable vault.
     */
    fun dao(): VaultDao? = open()?.vaultDao()

    /**
     * The DAO for callers that only run once [RootNav] has confirmed the store opened.
     * Throws [VaultStoreUnavailableException] otherwise: a broken store must never be
     * papered over with an empty vault.
     */
    fun requireDao(): VaultDao = dao() ?: throw VaultStoreUnavailableException(_failure.value)

    private fun build(): VaultDatabase {
        // No destructive fallback, not even in debug: a missing migration must surface as
        // `VaultStoreErrorView`, never as a silently emptied vault.
        return Room.databaseBuilder(context, VaultDatabase::class.java, DATABASE_NAME)
            .addMigrations(*VaultMigrations.ALL)
            .addCallback(SecureDeleteCallback)
            .addCallback(VaultMigrations.CreateTriggersCallback)
            .build()
    }

    /**
     * `secure_delete` zeroes freed content instead of leaving it in free pages, so a name
     * blanked after encryption (or a tombstoned item) doesn't linger in the file. The
     * pragma is per connection, hence on every open. It returns a row, so it goes through
     * `query`, not `execSQL`.
     */
    private object SecureDeleteCallback : RoomDatabase.Callback() {
        override fun onOpen(db: SupportSQLiteDatabase) {
            db.query("PRAGMA secure_delete = ON").use { it.moveToFirst() }
        }
    }

    private companion object {
        const val DATABASE_NAME = "qasa_vault.db"
    }
}
