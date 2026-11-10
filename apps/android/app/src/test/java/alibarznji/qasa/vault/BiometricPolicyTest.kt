package alibarznji.qasa.vault

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BiometricPolicyTest {
    private val now = 1_800_000_000_000L
    private val interval = BiometricPolicy.MASTER_PASSWORD_INTERVAL.inWholeMilliseconds

    @Test
    fun `interval is fourteen days`() {
        assertEquals(14, BiometricPolicy.MASTER_PASSWORD_INTERVAL_DAYS)
    }

    @Test
    fun `no record is due`() {
        assertTrue(BiometricPolicy.isMasterPasswordDue(null, now))
    }

    @Test
    fun `a recent master password unlock is not due`() {
        assertFalse(BiometricPolicy.isMasterPasswordDue(now - 1, now))
        assertFalse(BiometricPolicy.isMasterPasswordDue(now, now))
        assertFalse(BiometricPolicy.isMasterPasswordDue(now - interval + 1, now))
    }

    @Test
    fun `due once the interval has passed`() {
        assertTrue(BiometricPolicy.isMasterPasswordDue(now - interval, now))
        assertTrue(BiometricPolicy.isMasterPasswordDue(now - interval * 3, now))
    }

    @Test
    fun `a record in the future means the clock went back, so it is due`() {
        assertTrue(BiometricPolicy.isMasterPasswordDue(now + 1, now))
    }

    @Test
    fun `a zero or negative record is due`() {
        assertTrue(BiometricPolicy.isMasterPasswordDue(0L, now))
        assertTrue(BiometricPolicy.isMasterPasswordDue(-5L, now))
    }
}
