package alibarznji.qasa.vault

import alibarznji.qasa.vault.crypto.VaultCrypto
import alibarznji.qasa.vault.db.VaultCollectionEntity
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction

/** A collection ("folder") as screens see it: the name decrypted with the vault key. */
data class VaultCollection(
    val id: String,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
)

/**
 * Collection names at rest and on the wire (plan decision 5): vault-key AES-GCM over the
 * UTF-8 name, the same blob format as an item's envelope, so every app and the sync wire
 * (`FfiCollection.nameEnc`) read one another's names.
 *
 * Pure functions over rows and keys, so the JVM tests run them without Room or a session.
 */
object CollectionNames {
    fun encrypt(name: String, enc: ByteArray): ByteArray =
        VaultCrypto.encrypt(name.toByteArray(Charsets.UTF_8), enc)

    /** Throws when the blob doesn't open with [enc] or isn't valid UTF-8. */
    fun decrypt(nameEnc: ByteArray, enc: ByteArray): String =
        Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(VaultCrypto.decrypt(nameEnc, enc)))
            .toString()

    /**
     * The row as the UI shows it, or null when its name can't be read (skipped, like an
     * unreadable item). A legacy row not yet encrypted (no `nameEnc`) shows its plaintext
     * name until the next unlock encrypts it.
     */
    fun readable(row: VaultCollectionEntity, enc: ByteArray): VaultCollection? {
        if (row.isDeleted) return null
        val stored = row.nameEnc
        val name = when {
            stored == null -> row.name
            stored.isEmpty() -> return null
            else -> try {
                decrypt(stored, enc)
            } catch (_: Exception) {
                return null
            }
        }
        return VaultCollection(row.id, name, row.createdAt, row.updatedAt)
    }

    /** Live collections, decrypted and sorted by name (case-insensitively). */
    fun readableSorted(rows: List<VaultCollectionEntity>, enc: ByteArray): List<VaultCollection> =
        rows.mapNotNull { readable(it, enc) }.sortedWith(compareBy({ it.name.lowercase() }, { it.createdAt }))

    /**
     * `(id, nameEnc)` for every row that still has a plaintext name: live rows get the
     * name encrypted with [enc]; a tombstone gets an empty blob (it keeps no name).
     */
    fun legacyUpdates(rows: List<VaultCollectionEntity>, enc: ByteArray): List<Pair<String, ByteArray>> =
        rows.filter { it.name.isNotEmpty() }.map { row ->
            row.id to if (row.isDeleted) ByteArray(0) else encrypt(row.name, enc)
        }

    /**
     * `(id, nameEnc)` re-encrypted from [oldEnc] to [newEnc] for every row with an
     * encrypted name. Throws if any name doesn't decrypt: a re-key never silently drops
     * one (as on the desktop, the whole password change is refused).
     */
    fun rekey(rows: List<VaultCollectionEntity>, oldEnc: ByteArray, newEnc: ByteArray): List<Pair<String, ByteArray>> =
        rows.filter { (it.nameEnc?.size ?: 0) > 0 }.map { row ->
            row.id to encrypt(decrypt(row.nameEnc!!, oldEnc), newEnc)
        }
}
