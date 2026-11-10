package alibarznji.qasa.vault

import alibarznji.qasa.credentials.AutofillIndexStore
import alibarznji.qasa.vault.crypto.VaultCrypto
import alibarznji.qasa.vault.crypto.VaultCryptoException
import alibarznji.qasa.vault.crypto.VaultSearchIndex
import alibarznji.qasa.vault.db.AppNotificationEntity
import alibarznji.qasa.vault.db.VaultCollectionEntity
import alibarznji.qasa.vault.db.VaultDao
import alibarznji.qasa.vault.db.VaultItemEntity
import alibarznji.qasa.vault.db.VaultRowRules
import alibarznji.qasa.vault.db.VaultSearchTokenEntity
import alibarznji.qasa.vault.db.VaultStoreHolder
import alibarznji.qasa.vault.db.VaultStoreUnavailableException
import androidx.room.withTransaction
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

data class VaultListRow(
    val id: String,
    val kind: VaultItemKind,
    val displayName: String,
    val subtitle: String,
    val collectionId: String?,
    val updatedAt: Long,
    val createdAt: Long,
    val lastUsedAt: Long?,
)

/** Who is writing an item, which decides the field-rule policy (ARCHITECTURE.md §5). */
enum class SaveOrigin {
    /** The add/edit form: normalize, validate, refuse while any issue remains. */
    User,

    /** CSV import: normalize, store even with issues (counted, never dropped). */
    Import,

    /**
     * A change the app makes to an existing item on the user's behalf that isn't an edit
     * of its fields (un-filing it from a deleted folder, rotating a breached password):
     * written as is, so a legacy item stays untouched rather than refused.
     */
    Internal,
}

@Singleton
class VaultRepository @Inject constructor(
    private val store: VaultStoreHolder,
    private val session: VaultSession,
    private val autofillIndex: AutofillIndexStore,
    private val secureStore: VaultSecureStore,
) {
    /**
     * Resolved per call: the store may fail to open, and the UI shows
     * `VaultStoreErrorView` in that case rather than an empty vault.
     */
    private val dao: VaultDao get() = store.requireDao()

    fun observeRows(): Flow<List<VaultListRow>> =
        dao.observeItems().map { items -> items.mapNotNull { decryptRow(it) } }

    /** How many live items the vault holds (no decryption), e.g. for the backup reminder. */
    fun observeLiveCount(): Flow<Int> = dao.observeItems().map { it.size }

    suspend fun getAllRows(): List<VaultListRow> =
        dao.getAllItems().mapNotNull { decryptRow(it) }

    suspend fun getPayload(id: String): VaultPayload? {
        val item = dao.getItem(id) ?: return null
        val enc = session.requireEncKey()
        return VaultJson.decodePayload(VaultCrypto.decrypt(item.encryptedPayload, enc))
    }

    /**
     * The single write path for an item's contents. A user save (the add/edit form) is
     * normalized, then validated: any remaining issue throws [VaultValidationException]
     * and nothing is written. See [SaveOrigin] for the other origins.
     */
    suspend fun save(id: String?, payload: VaultPayload, origin: SaveOrigin = SaveOrigin.User): String =
        write(id, payload, origin).id

    /**
     * CSV import: stored normalized even when fields still break a rule (an import never
     * drops data). Returns whether the stored item still has issues, for
     * `validation.importFlaggedFmt`.
     */
    suspend fun import(payload: VaultPayload): Boolean = write(null, payload, SaveOrigin.Import).issues.isNotEmpty()

    private class Written(val id: String, val issues: List<uniffi.qasa_ffi.FfiFieldIssue>)

    private suspend fun write(id: String?, input: VaultPayload, origin: SaveOrigin): Written {
        // Throws VaultValidationException for a user save with issues, before anything is written.
        val (payload, issues) = VaultValidation.prepareForWrite(input, origin)
        val enc = session.requireEncKey()
        val search = session.requireSearchKey()
        val itemId = id ?: UUID.randomUUID().toString().lowercase()
        val now = System.currentTimeMillis()
        val existing = id?.let { dao.getItem(it) }
        val envelope = VaultEnvelope(
            displayName = payload.displayName(),
            subtitle = payload.subtitle(),
            collectionId = payload.collectionId(),
        )
        val entity = VaultItemEntity(
            id = itemId,
            kindRaw = payload.kind.raw,
            encryptedEnvelope = VaultCrypto.encrypt(VaultJson.encodeEnvelope(envelope), enc),
            encryptedPayload = VaultCrypto.encrypt(VaultJson.encodePayload(payload), enc),
            schemaVersion = 1,
            updatedAt = now,
            createdAt = existing?.createdAt ?: now,
            lastUsedAt = existing?.lastUsedAt,
            collectionId = payload.collectionId(),
        )
        check(VaultRowRules.isValid(entity)) { "refusing a structurally invalid row" }
        dao.upsertItem(entity)
        dao.deleteTokensForItem(itemId)
        val hashes = VaultSearchIndex.searchableStrings(payload)
            .flatMap { VaultSearchIndex.tokenHashes(it, search) }
            .distinct()
            .map { VaultSearchTokenEntity(itemId = itemId, tokenHash = it) }
        if (hashes.isNotEmpty()) dao.insertTokens(hashes)
        rebuildAutofillIndex()
        return Written(itemId, issues)
    }

    /** iOS `markUsed`: copying or opening a secret stamps "last used" (and updated, as iOS does). */
    suspend fun markUsed(id: String) = dao.markUsed(id, System.currentTimeMillis())

    /**
     * A tombstone, not a row delete, so the delete reaches the user's other devices on the
     * next Wi‑Fi sync (the merge keeps tombstones for 90 days).
     */
    suspend fun delete(id: String) {
        dao.tombstoneItem(id, System.currentTimeMillis())
        dao.deleteTokensForItem(id)
        rebuildAutofillIndex()
    }

    suspend fun search(query: String): List<VaultListRow> {
        val q = query.trim()
        if (q.isEmpty()) return getAllRows()
        val search = session.requireSearchKey()
        val hashes = VaultSearchIndex.tokenHashes(q, search)
        if (hashes.isEmpty()) return emptyList()
        val ids = dao.findItemIdsByHashes(hashes).toSet()
        return getAllRows().filter { it.id in ids }
    }

    private val database get() = store.open() ?: throw VaultStoreUnavailableException(store.failure.value)

    /**
     * Live collections with their names decrypted, sorted by name. Empty while locked;
     * a name that doesn't decrypt is skipped, like an unreadable item.
     */
    fun observeCollections(): Flow<List<VaultCollection>> =
        combine(dao.observeCollections(), session.unlocked) { rows, unlocked ->
            if (!unlocked) {
                emptyList()
            } else {
                try {
                    CollectionNames.readableSorted(rows, session.requireEncKey())
                } catch (_: VaultCryptoException.VaultLocked) {
                    emptyList()
                }
            }
        }

    /** Creates (or overwrites) a collection; only the ciphertext of [name] is stored. */
    suspend fun saveCollection(id: String?, name: String): String {
        val nameEnc = CollectionNames.encrypt(name, session.requireEncKey())
        val cid = id ?: UUID.randomUUID().toString().lowercase()
        val now = System.currentTimeMillis()
        val existing = id?.let { old -> dao.getCollections().firstOrNull { it.id == old } }
        dao.upsertCollection(
            VaultCollectionEntity(
                id = cid,
                name = "",
                nameEnc = nameEnc,
                createdAt = existing?.createdAt ?: now,
                updatedAt = now,
            ),
        )
        return cid
    }

    /** iOS `CollectionsView.saveRename`: trims, ignores blank, keeps the id and `createdAt`. */
    suspend fun renameCollection(id: String, name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        val nameEnc = CollectionNames.encrypt(trimmed, session.requireEncKey())
        dao.renameCollection(id, nameEnc, System.currentTimeMillis())
    }

    /**
     * Encrypts collection names saved before names were encrypted (schema < 3) and blanks
     * the plaintext. Runs at every unlock; after the first one there is nothing to do.
     * Not an edit: `updatedAt` is unchanged, so it never wins a sync merge by itself.
     */
    suspend fun encryptLegacyCollectionNames() {
        if (!session.isUnlocked) return
        val rows = dao.collectionsWithPlaintextNames()
        if (rows.isEmpty()) return
        val updates = CollectionNames.legacyUpdates(rows, session.requireEncKey())
        database.withTransaction {
            for ((id, nameEnc) in updates) dao.setCollectionNameEnc(id, nameEnc)
        }
    }

    // On-device notifications (iOS `AppNotification` SwiftData model, `NotificationsView`).
    fun observeLocalNotifications(): Flow<List<AppNotificationEntity>> = dao.observeLocalNotifications()

    suspend fun markLocalNotificationRead(id: String) = dao.markNotificationRead(id)

    suspend fun markAllLocalNotificationsRead() = dao.markAllNotificationsRead()

    suspend fun deleteLocalNotification(id: String) = dao.deleteNotification(id)

    /**
     * Same as iOS `CollectionsView.confirmDelete`: items stay in the vault but are
     * un-assigned first, so nothing keeps pointing at a collection that no longer exists.
     */
    suspend fun deleteCollection(id: String) {
        for (item in dao.getAllItems().filter { it.collectionId.equals(id, ignoreCase = true) }) {
            val payload = getPayload(item.id) ?: continue
            val cleared = when (payload) {
                is VaultPayload.Login -> payload.copy(collectionId = null)
                is VaultPayload.SecureNote -> payload.copy(collectionId = null)
                else -> continue
            }
            // Not the user's edit: the item's other fields are written back untouched.
            save(item.id, cleared, SaveOrigin.Internal)
        }
        dao.tombstoneCollection(id, System.currentTimeMillis())
    }

    /**
     * Master password change: every item and every collection name re-encrypted under the
     * new key, all or nothing. Everything is decrypted and re-encrypted in memory first, so
     * an unreadable row refuses the change before anything is written; the writes then go
     * in one transaction.
     */
    suspend fun reencryptAll(oldEnc: ByteArray, newEnc: ByteArray, newSearch: ByteArray) {
        val now = System.currentTimeMillis()
        val rekeyed = dao.getAllItems().map { item ->
            val payloadBytes = VaultCrypto.decrypt(item.encryptedPayload, oldEnc)
            val envelopeBytes = VaultCrypto.decrypt(item.encryptedEnvelope, oldEnc)
            val payload = VaultJson.decodePayload(payloadBytes)
            val entity = item.copy(
                encryptedPayload = VaultCrypto.encrypt(payloadBytes, newEnc),
                encryptedEnvelope = VaultCrypto.encrypt(envelopeBytes, newEnc),
                updatedAt = now,
            )
            val hashes = VaultSearchIndex.searchableStrings(payload)
                .flatMap { VaultSearchIndex.tokenHashes(it, newSearch) }
                .distinct()
                .map { VaultSearchTokenEntity(itemId = item.id, tokenHash = it) }
            entity to hashes
        }
        // Encrypted names move to the new key; any plaintext name left is encrypted
        // straight under it (and applied last, so it wins for that row).
        val names = CollectionNames.rekey(dao.collectionsWithEncryptedNames(), oldEnc, newEnc) +
            CollectionNames.legacyUpdates(dao.collectionsWithPlaintextNames(), newEnc)
        database.withTransaction {
            for ((entity, hashes) in rekeyed) {
                dao.upsertItem(entity)
                dao.deleteTokensForItem(entity.id)
                if (hashes.isNotEmpty()) dao.insertTokens(hashes)
            }
            for ((id, nameEnc) in names) dao.setCollectionNameEnc(id, nameEnc)
        }
        rebuildAutofillIndex()
    }

    suspend fun clearAll() {
        dao.clearItems()
        dao.clearTokens()
        dao.clearCollections()
        autofillIndex.clear()
    }

    /**
     * After a Wi‑Fi sync wrote rows: list screens follow Room on their own; the autofill
     * index is rebuilt here, and search tokens now when unlocked (else at the next unlock).
     */
    suspend fun refreshAfterSync() {
        if (session.isUnlocked) reindexSearchIfPending()
        rebuildAutofillIndex()
    }

    /**
     * Rebuilds search tokens for every item when synced rows arrived without them
     * ([VaultSecureStore.searchReindexPending]). Runs right after unlock, when the search
     * key exists. Items that don't decrypt keep no tokens rather than failing the unlock.
     */
    suspend fun reindexSearchIfPending() {
        if (!secureStore.searchReindexPending || !session.isUnlocked) return
        val enc = session.requireEncKey()
        val search = session.requireSearchKey()
        for (item in dao.getAllItems()) {
            val payload = try {
                VaultJson.decodePayload(VaultCrypto.decrypt(item.encryptedPayload, enc))
            } catch (_: Exception) {
                continue
            }
            dao.deleteTokensForItem(item.id)
            val hashes = VaultSearchIndex.searchableStrings(payload)
                .flatMap { VaultSearchIndex.tokenHashes(it, search) }
                .distinct()
                .map { VaultSearchTokenEntity(itemId = item.id, tokenHash = it) }
            if (hashes.isNotEmpty()) dao.insertTokens(hashes)
        }
        secureStore.searchReindexPending = false
        rebuildAutofillIndex()
    }

    private fun decryptRow(item: VaultItemEntity): VaultListRow? = try {
        val enc = session.requireEncKey()
        val env = VaultJson.decodeEnvelope(VaultCrypto.decrypt(item.encryptedEnvelope, enc))
        VaultListRow(
            id = item.id,
            kind = VaultItemKind.fromRaw(item.kindRaw),
            displayName = env.displayName,
            subtitle = env.subtitle,
            collectionId = env.collectionId ?: item.collectionId,
            updatedAt = item.updatedAt,
            createdAt = item.createdAt,
            lastUsedAt = item.lastUsedAt,
        )
    } catch (_: Exception) {
        null
    }

    /**
     * Unlock: rebuilds the autofill index once when it was written in an older format
     * (before logins carried their 2FA code). The rebuild stamps the current version, so
     * later unlocks skip it.
     */
    suspend fun rebuildAutofillIndexIfOutdated() {
        if (!session.isUnlocked || !autofillIndex.isOutdated()) return
        rebuildAutofillIndex()
    }

    private suspend fun rebuildAutofillIndex() {
        if (!session.isUnlocked) return
        try {
            val enc = session.requireEncKey()
            val entries = dao.getAllItems().mapNotNull { item ->
                val payload = VaultJson.decodePayload(VaultCrypto.decrypt(item.encryptedPayload, enc))
                autofillIndex.entryFromPayload(item.id, payload)
            }
            autofillIndex.write(entries)
        } catch (_: Exception) {
            // Autofill index best-effort
        }
    }
}
