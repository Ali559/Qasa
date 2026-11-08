package alibarznji.qasa.sync

import alibarznji.qasa.R
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

    @Test fun collectionsMapBothWays() {
        val c = VaultSyncStore.fromWire(FfiCollection("x", "Work", 1, 7, 9))
        assertEquals(7L, c.updatedAt)
        assertEquals(9L, c.deletedAt)
    }
}
