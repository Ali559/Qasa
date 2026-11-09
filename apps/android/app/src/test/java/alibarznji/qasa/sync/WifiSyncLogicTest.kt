package alibarznji.qasa.sync

import alibarznji.qasa.R
import alibarznji.qasa.vault.CollectionNames
import alibarznji.qasa.vault.db.VaultCollectionEntity
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import uniffi.qasa_ffi.FfiCollection
import uniffi.qasa_ffi.FfiItem

class WifiSyncLogicTest {
    @Test fun failureMessagesFollowTheDesktop() {
        assertEquals(SyncMessage(R.string.sync_wrongcode, 2), failureMessage(SyncFailure(SyncFailureReason.WrongCode, 2)))
        assertEquals(SyncMessage(R.string.sync_wrongcodefinal), failureMessage(SyncFailure(SyncFailureReason.WrongCode, 0)))
        assertEquals(SyncMessage(R.string.sync_differentvault), failureMessage(SyncFailure(SyncFailureReason.DifferentVault)))
        assertEquals(SyncMessage(R.string.sync_novault), failureMessage(SyncFailure(SyncFailureReason.NoVault)))
        assertEquals(SyncMessage(R.string.sync_version), failureMessage(SyncFailure(SyncFailureReason.Version)))
        assertEquals(SyncMessage(R.string.sync_expired), failureMessage(SyncFailure(SyncFailureReason.Expired)))
        assertEquals(SyncMessage(R.string.sync_io), failureMessage(SyncFailure(SyncFailureReason.Io)))
        assertEquals(SyncMessage(R.string.sync_failed), failureMessage(SyncFailure(SyncFailureReason.Other)))
    }

    @Test fun codeIsGroupedInThrees() {
        assertEquals("123 456", WifiSyncService.groupCode("123456"))
    }

    private fun item(deleted: Long?) = FfiItem(
        id = "a", kind = "login", envelope = byteArrayOf(1), payload = byteArrayOf(2),
        schemaVersion = 1, createdAtMs = 1, updatedAtMs = 2, lastUsedAtMs = null,
        collectionId = "c", deletedAtMs = deleted,
    )

    @Test fun appliedRowsKeepTheirCiphertextAsIs() {
        val row = VaultSyncStore.fromWire(item(deleted = null))
        assertArrayEquals(byteArrayOf(1), row.encryptedEnvelope)
        assertArrayEquals(byteArrayOf(2), row.encryptedPayload)
        assertEquals(2L, row.updatedAt)
        assertEquals("c", row.collectionId)
        assertNull(row.deletedAt)
    }

    @Test fun appliedTombstonesHoldNoContents() {
        val row = VaultSyncStore.fromWire(item(deleted = 5))
        assertEquals(0, row.encryptedEnvelope.size)
        assertEquals(0, row.encryptedPayload.size)
        assertEquals(5L, row.deletedAt)
    }

    private val key = ByteArray(32) { it.toByte() }

    @Test fun collectionsMapBothWays() {
        val c = VaultSyncStore.fromWire(FfiCollection("X", byteArrayOf(), 1, 7, 9))
        assertEquals("x", c.id)
        assertEquals(7L, c.updatedAt)
        assertEquals(9L, c.deletedAt)
        assertEquals(0, c.nameEnc!!.size)
        assertEquals("", c.name)
    }

    @Test fun appliedCollectionNamesKeepTheirCiphertextAsIs() {
        val blob = CollectionNames.encrypt("Work", key)
        val c = VaultSyncStore.fromWire(FfiCollection("ABC", blob, 1, 7, null))
        assertEquals("abc", c.id)
        assertEquals("", c.name)
        assertArrayEquals(blob, c.nameEnc)
        assertEquals("Work", CollectionNames.readable(c, key)!!.name)
    }

    @Test fun snapshotCarriesTheNameCiphertext() {
        val blob = CollectionNames.encrypt("Family", key)
        val wire = VaultSyncStore.toWire(VaultCollectionEntity(id = "c1", createdAt = 1, updatedAt = 2, nameEnc = blob))!!
        assertArrayEquals(blob, wire.nameEnc)
        assertEquals("Family", CollectionNames.decrypt(wire.nameEnc, key))
        assertEquals(2L, wire.updatedAtMs)
        assertNull(wire.deletedAtMs)
    }

    @Test fun snapshotSkipsNamesNotEncryptedYet() {
        assertNull(VaultSyncStore.toWire(VaultCollectionEntity(id = "c1", name = "Plain", createdAt = 1)))
        assertNull(VaultSyncStore.toWire(VaultCollectionEntity(id = "c1", name = "Plain", createdAt = 1, nameEnc = ByteArray(0))))
    }

    @Test fun snapshotTombstoneHasAnEmptyName() {
        val wire = VaultSyncStore.toWire(
            VaultCollectionEntity(id = "c1", name = "Left", createdAt = 1, deletedAt = 5, nameEnc = CollectionNames.encrypt("x", key)),
        )!!
        assertEquals(0, wire.nameEnc.size)
        assertEquals(5L, wire.deletedAtMs)
    }
}
