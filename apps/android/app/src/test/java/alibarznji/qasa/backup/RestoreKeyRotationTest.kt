package alibarznji.qasa.backup

import java.util.Base64
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class RestoreKeyRotationTest {
    private class MemorySecrets : RestoreKeySecrets {
        override var restoreKey: ByteArray? = null
        override var restoreKeyWritten: Boolean = false
        override var pendingRestoreKey: ByteArray? = null
    }

    private class FakePorts(
        var license: Boolean = true,
        var failWith: Exception? = null,
    ) : RestoreKeyRotationPorts {
        val sent = mutableListOf<RestoreKeyHashRequest>()
        private var counter: Byte = 0

        override fun hasLicense() = license
        override fun newKey(): ByteArray = ByteArray(32) { ++counter }
        // A stand-in hash: the key reversed, so the test can tell key and hash apart.
        override fun hash(key: ByteArray): ByteArray = key.reversedArray()
        override suspend fun putHash(request: RestoreKeyHashRequest) {
            failWith?.let { throw it }
            sent += request
        }
        override fun log(message: String, error: Throwable) {}
    }

    @Test fun noKeyRotatesAndSendsTheHash() = runTest {
        val secrets = MemorySecrets()
        val ports = FakePorts()
        val key = prepareRestoreKey(secrets, ports)!!
        assertArrayEquals(key, secrets.restoreKey)
        assertFalse(secrets.restoreKeyWritten)
        assertEquals(1, ports.sent.size)
        assertEquals(Base64.getEncoder().encodeToString(key.reversedArray()), ports.sent[0].key_hash)
    }

    @Test fun writtenKeyRotates() = runTest {
        val old = ByteArray(32) { 9 }
        val secrets = MemorySecrets().apply { restoreKey = old; restoreKeyWritten = true }
        val ports = FakePorts()
        val key = prepareRestoreKey(secrets, ports)!!
        assertFalse(key.contentEquals(old))
        assertArrayEquals(key, secrets.restoreKey)
        assertFalse(secrets.restoreKeyWritten)
        assertEquals(1, ports.sent.size)
    }

    @Test fun unwrittenKeyIsReusedWithoutACall() = runTest {
        val old = ByteArray(32) { 9 }
        val secrets = MemorySecrets().apply { restoreKey = old; restoreKeyWritten = false }
        val ports = FakePorts()
        assertSame(old, prepareRestoreKey(secrets, ports))
        assertTrue(ports.sent.isEmpty())
    }

    @Test fun failedPutKeepsTheOldKey() = runTest {
        val old = ByteArray(32) { 9 }
        val secrets = MemorySecrets().apply { restoreKey = old; restoreKeyWritten = true }
        val key = prepareRestoreKey(secrets, FakePorts(failWith = RuntimeException("offline")))
        assertSame(old, key)
        assertSame(old, secrets.restoreKey)
        assertTrue(secrets.restoreKeyWritten)
    }

    @Test fun failedPutWithoutKeyGivesNone() = runTest {
        val secrets = MemorySecrets()
        assertNull(prepareRestoreKey(secrets, FakePorts(failWith = RuntimeException("403"))))
        assertNull(secrets.restoreKey)
    }

    @Test fun noLicenseNeverCalls() = runTest {
        val secrets = MemorySecrets()
        val ports = FakePorts(license = false)
        assertNull(prepareRestoreKey(secrets, ports))
        assertTrue(ports.sent.isEmpty())
        // A key from an earlier license still goes into the file.
        val old = ByteArray(32) { 9 }
        secrets.restoreKey = old
        secrets.restoreKeyWritten = true
        assertSame(old, prepareRestoreKey(secrets, ports))
        assertTrue(ports.sent.isEmpty())
    }

    @Test fun writtenFlagOnlyForTheCurrentKey() {
        val secrets = MemorySecrets().apply { restoreKey = ByteArray(32) { 1 } }
        markRestoreKeyWritten(secrets, null)
        assertFalse(secrets.restoreKeyWritten)
        markRestoreKeyWritten(secrets, ByteArray(32) { 2 })
        assertFalse(secrets.restoreKeyWritten)
        markRestoreKeyWritten(secrets, ByteArray(32) { 1 })
        assertTrue(secrets.restoreKeyWritten)
    }

    @Test fun fullCycleRotatesOnlyAfterAWrite() = runTest {
        val secrets = MemorySecrets()
        val ports = FakePorts()
        val first = prepareRestoreKey(secrets, ports)
        // The write failed: the next backup reuses the same key.
        assertSame(first, prepareRestoreKey(secrets, ports))
        markRestoreKeyWritten(secrets, first)
        val second = prepareRestoreKey(secrets, ports)
        assertFalse(second!!.contentEquals(first))
        assertEquals(2, ports.sent.size)
    }

    @Test fun resetClearsAllThree() {
        val secrets = MemorySecrets().apply {
            restoreKey = ByteArray(32) { 1 }
            restoreKeyWritten = true
            pendingRestoreKey = ByteArray(32) { 2 }
        }
        clearRestoreKeys(secrets)
        assertNull(secrets.restoreKey)
        assertFalse(secrets.restoreKeyWritten)
        assertNull(secrets.pendingRestoreKey)
    }
}
