package alibarznji.qasa.sync

import alibarznji.qasa.vault.PayloadShapes
import alibarznji.qasa.vault.VaultSecureStore
import alibarznji.qasa.vault.VaultSession
import alibarznji.qasa.vault.crypto.VaultCrypto
import alibarznji.qasa.vault.db.VaultCollectionEntity
import alibarznji.qasa.vault.db.VaultItemEntity
import alibarznji.qasa.vault.db.VaultRowRules
import alibarznji.qasa.vault.db.VaultStoreHolder
import android.content.Context
import android.os.Build
import android.provider.Settings
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import uniffi.qasa_ffi.FfiCollection
import uniffi.qasa_ffi.FfiIntro
import uniffi.qasa_ffi.FfiItem
import uniffi.qasa_ffi.FfiVaultMeta
import uniffi.qasa_ffi.SyncStoreCallback

/**
 * This device's vault as the shared Rust sync session sees it (`qasa-ffi`
 * `SyncStoreCallback`). Called on the session's thread, so everything here blocks.
 *
 * Payload rule: Room keeps Android's `{"type": …}` JSON; the wire carries the canonical
 * (iOS) JSON. Sending decrypts, converts and re-encrypts each payload with a fresh nonce;
 * applying writes rows exactly as received, and [alibarznji.qasa.vault.VaultJson.decodePayload]
 * reads either shape later.
 *
 * No method may throw: an exception escaping a UniFFI callback would panic the session.
 */
@Singleton
class VaultSyncStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val holder: VaultStoreHolder,
    private val secure: VaultSecureStore,
    private val session: VaultSession,
) : SyncStoreCallback {

    override fun intro(): FfiIntro {
        val salt = secure.kdfSalt
        val verifier = secure.passwordVerifier
        val vault = if (secure.vaultExists && salt != null && verifier != null) {
            FfiVaultMeta(salt = salt, kdfVersion = secure.kdfVersion.toUInt(), verifier = verifier)
        } else {
            null
        }
        return FfiIntro(
            deviceName = deviceName(context),
            platform = "android",
            accountId = secure.accountId,
            vault = vault,
        )
    }

    override fun snapshot(): List<FfiItem> = try {
        val rows = holder.requireDao().allItemsForSync()
        val enc = if (session.isUnlocked) session.requireEncKey() else null
        rows.mapNotNull { row -> toWire(row, enc) }
    } catch (e: Exception) {
        Log.w(TAG, "snapshot failed", e)
        emptyList()
    }

    override fun collections(): List<FfiCollection> = try {
        holder.requireDao().allCollectionsForSync().mapNotNull(::toWire)
    } catch (e: Exception) {
        Log.w(TAG, "collections failed", e)
        emptyList()
    }

    /** Only a device without a vault adopts; an existing vault is never overwritten. */
    override fun adopt(accountId: String?, vault: FfiVaultMeta): Boolean = try {
        if (secure.vaultExists) {
            false
        } else {
            // Rows left from an earlier, wiped vault can't belong to this one.
            holder.open()?.runInTransaction {
                val dao = holder.requireDao()
                dao.clearItemsBlocking()
                dao.clearTokensBlocking()
                dao.clearCollectionsBlocking()
            }
            session.adoptVault(
                salt = vault.salt,
                verifier = vault.verifier,
                kdfVersion = vault.kdfVersion.toInt(),
                accountId = accountId,
            )
        }
    } catch (e: Exception) {
        Log.w(TAG, "adopt failed", e)
        false
    }

    override fun apply(items: List<FfiItem>, collections: List<FfiCollection>): Long = try {
        val db = holder.open() ?: throw IllegalStateException("vault store unavailable")
        val dao = db.vaultDao()
        // Ciphertext is stored as received (never decrypted or rewritten here), so only the
        // structural rules apply; a row breaking them is skipped rather than aborting the
        // whole apply on the store's trigger.
        val (rows, rejected) = guardRows(items)
        if (rejected > 0) Log.w(TAG, "skipped $rejected structurally invalid item(s)")
        db.runInTransaction {
            if (rows.isNotEmpty()) {
                dao.upsertItemsForSync(rows)
                // Tokens were built from the old contents (or don't exist yet); rebuilt
                // with the search key after the session, or at the next unlock.
                dao.deleteTokensForItemsBlocking(rows.map { it.id })
            }
            if (collections.isNotEmpty()) dao.upsertCollectionsForSync(collections.map(::fromWire))
        }
        if (rows.any { !it.isDeleted }) secure.searchReindexPending = true
        (rows.size + collections.size).toLong()
    } catch (e: Exception) {
        Log.w(TAG, "apply failed", e)
        -1L
    }

    override fun nowMs(): Long = System.currentTimeMillis()

    private fun toWire(row: VaultItemEntity, enc: ByteArray?): FfiItem? {
        val payload = if (row.isDeleted) {
            ByteArray(0)
        } else {
            // A live item that can't be read (locked, or a corrupt row) is skipped, not sent.
            enc ?: return null
            try {
                val plain = VaultCrypto.decrypt(row.encryptedPayload, enc)
                VaultCrypto.encrypt(PayloadShapes.toCanonical(plain), enc)
            } catch (e: Exception) {
                Log.w(TAG, "skipping unreadable item")
                return null
            }
        }
        return FfiItem(
            id = row.id,
            kind = row.kindRaw,
            envelope = if (row.isDeleted) ByteArray(0) else row.encryptedEnvelope,
            payload = payload,
            schemaVersion = row.schemaVersion.toLong(),
            createdAtMs = row.createdAt,
            updatedAtMs = row.updatedAt,
            lastUsedAtMs = row.lastUsedAt,
            collectionId = row.collectionId,
            deletedAtMs = row.deletedAt,
        )
    }

    companion object {
        private const val TAG = "VaultSyncStore"

        /**
         * The stored ciphertext as is (no key needed). A live collection whose name isn't
         * encrypted yet (saved before v3, before the next unlock) is skipped: no plaintext
         * name ever goes on the wire. A tombstone carries an empty name.
         */
        fun toWire(c: VaultCollectionEntity): FfiCollection? {
            val nameEnc = if (c.isDeleted) {
                ByteArray(0)
            } else {
                c.nameEnc?.takeIf { it.isNotEmpty() } ?: return null
            }
            return FfiCollection(
                id = c.id,
                nameEnc = nameEnc,
                createdAtMs = c.createdAt,
                updatedAtMs = c.updatedAt,
                deletedAtMs = c.deletedAt,
            )
        }

        /**
         * Wi‑Fi sync and backup restore: the received rows that pass [VaultRowRules]
         * (kind, non-empty contents unless a tombstone, schema version), and how many didn't.
         */
        fun guardRows(items: List<FfiItem>): Pair<List<VaultItemEntity>, Int> {
            val rows = items.map(::fromWire)
            val valid = rows.filter(VaultRowRules::isValid)
            return valid to rows.size - valid.size
        }

        /** Rows as received: ciphertext is never re-encrypted on apply. */
        // Android's own ids are lowercase; the wire is uppercase (sync §6). Lowercasing on
        // the way in keeps one row per item, and folder ids are compared ignoring case.
        fun fromWire(i: FfiItem) = VaultItemEntity(
            id = i.id.lowercase(),
            kindRaw = i.kind,
            encryptedEnvelope = if (i.deletedAtMs != null) ByteArray(0) else i.envelope,
            encryptedPayload = if (i.deletedAtMs != null) ByteArray(0) else i.payload,
            schemaVersion = i.schemaVersion.toInt(),
            updatedAt = i.updatedAtMs,
            createdAt = i.createdAtMs,
            lastUsedAt = i.lastUsedAtMs,
            collectionId = i.collectionId?.lowercase(),
            deletedAt = i.deletedAtMs,
        )

        fun fromWire(c: FfiCollection) = VaultCollectionEntity(
            id = c.id.lowercase(),
            name = "",
            nameEnc = if (c.deletedAtMs != null) ByteArray(0) else c.nameEnc,
            createdAt = c.createdAtMs,
            updatedAt = c.updatedAtMs,
            deletedAt = c.deletedAtMs,
        )

        /** `UIDevice.current.name` on Android: the user-set device name, else the model. */
        fun deviceName(context: Context): String =
            Settings.Global.getString(context.contentResolver, Settings.Global.DEVICE_NAME)
                ?.takeIf { it.isNotBlank() }
                ?: Build.MODEL
    }
}
