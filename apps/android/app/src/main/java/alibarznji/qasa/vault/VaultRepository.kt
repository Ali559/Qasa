package alibarznji.qasa.vault

import alibarznji.qasa.credentials.AutofillIndexStore
import alibarznji.qasa.vault.crypto.VaultCrypto
import alibarznji.qasa.vault.crypto.VaultCryptoException
import alibarznji.qasa.vault.crypto.VaultSearchIndex
import alibarznji.qasa.vault.db.AppNotificationEntity
import alibarznji.qasa.vault.db.VaultCollectionEntity
import alibarznji.qasa.vault.db.VaultDao
import alibarznji.qasa.vault.db.VaultItemEntity
import alibarznji.qasa.vault.db.VaultSearchTokenEntity
import alibarznji.qasa.vault.db.VaultStoreHolder
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
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

@Singleton
class VaultRepository @Inject constructor(
    private val store: VaultStoreHolder,
    private val session: VaultSession,
    private val autofillIndex: AutofillIndexStore,
) {
    /**
     * Resolved per call: the store may fail to open, and the UI shows
     * `VaultStoreErrorView` in that case rather than an empty vault.
     */
    private val dao: VaultDao get() = store.requireDao()

    fun observeRows(): Flow<List<VaultListRow>> =
        dao.observeItems().map { items -> items.mapNotNull { decryptRow(it) } }

    suspend fun getAllRows(): List<VaultListRow> =
        dao.getAllItems().mapNotNull { decryptRow(it) }

    suspend fun getPayload(id: String): VaultPayload? {
        val item = dao.getItem(id) ?: return null
        val enc = session.requireEncKey()
        return VaultJson.decodePayload(VaultCrypto.decrypt(item.encryptedPayload, enc))
    }

    suspend fun save(id: String?, payload: VaultPayload): String {
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
        dao.upsertItem(entity)
        dao.deleteTokensForItem(itemId)
        val hashes = VaultSearchIndex.searchableStrings(payload)
            .flatMap { VaultSearchIndex.tokenHashes(it, search) }
            .distinct()
            .map { VaultSearchTokenEntity(itemId = itemId, tokenHash = it) }
        if (hashes.isNotEmpty()) dao.insertTokens(hashes)
        rebuildAutofillIndex()
        return itemId
    }

    /** iOS `markUsed`: copying or opening a secret stamps "last used" (and updated, as iOS does). */
    suspend fun markUsed(id: String) = dao.markUsed(id, System.currentTimeMillis())

    suspend fun delete(id: String) {
        dao.deleteItem(id)
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

    fun observeCollections(): Flow<List<VaultCollectionEntity>> = dao.observeCollections()

    suspend fun saveCollection(id: String?, name: String): String {
        val cid = id ?: UUID.randomUUID().toString().lowercase()
        dao.upsertCollection(
            VaultCollectionEntity(
                id = cid,
                name = name,
                createdAt = System.currentTimeMillis(),
            ),
        )
        return cid
    }

    /** iOS `CollectionsView.saveRename`: trims, ignores blank, keeps the id and `createdAt`. */
    suspend fun renameCollection(id: String, name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        dao.renameCollection(id, trimmed)
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
        for (item in dao.getAllItems().filter { it.collectionId == id }) {
            val payload = getPayload(item.id) ?: continue
            val cleared = when (payload) {
                is VaultPayload.Login -> payload.copy(collectionId = null)
                is VaultPayload.SecureNote -> payload.copy(collectionId = null)
                else -> continue
            }
            save(item.id, cleared)
        }
        dao.deleteCollection(id)
    }

    suspend fun reencryptAll(oldEnc: ByteArray, newEnc: ByteArray, newSearch: ByteArray) {
        val items = dao.getAllItems()
        for (item in items) {
            val payloadBytes = VaultCrypto.decrypt(item.encryptedPayload, oldEnc)
            val envelopeBytes = VaultCrypto.decrypt(item.encryptedEnvelope, oldEnc)
            val payload = VaultJson.decodePayload(payloadBytes)
            dao.upsertItem(
                item.copy(
                    encryptedPayload = VaultCrypto.encrypt(payloadBytes, newEnc),
                    encryptedEnvelope = VaultCrypto.encrypt(envelopeBytes, newEnc),
                    updatedAt = System.currentTimeMillis(),
                ),
            )
            dao.deleteTokensForItem(item.id)
            val hashes = VaultSearchIndex.searchableStrings(payload)
                .flatMap { VaultSearchIndex.tokenHashes(it, newSearch) }
                .distinct()
                .map { VaultSearchTokenEntity(itemId = item.id, tokenHash = it) }
            if (hashes.isNotEmpty()) dao.insertTokens(hashes)
        }
        rebuildAutofillIndex()
    }

    suspend fun clearAll() {
        dao.clearItems()
        dao.clearTokens()
        dao.clearCollections()
        autofillIndex.clear()
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
