package alibarznji.qasa.services

import alibarznji.qasa.services.PremiumRefreshPolicy.EXPIRY_GRACE_MS
import alibarznji.qasa.services.PremiumRefreshPolicy.FOREGROUND_THROTTLE_MS
import alibarznji.qasa.services.PremiumRefreshPolicy.PASSED_EXPIRY_RETRY_MS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PremiumRefreshPolicyTest {

    private val now = 1_800_000_000_000L
    private val minute = 60_000L

    @Test
    fun effectiveExpiryIsTheEarlierKnownOne() {
        assertEquals(5L, PremiumRefreshPolicy.effectiveExpiryMs(9L, 5L))
        assertEquals(9L, PremiumRefreshPolicy.effectiveExpiryMs(9L, null))
        assertEquals(5L, PremiumRefreshPolicy.effectiveExpiryMs(null, 5L))
        assertNull(PremiumRefreshPolicy.effectiveExpiryMs(null, null))
    }

    @Test
    fun premiumSchedulesJustAfterExpiry() {
        val expiry = now + 2 * 60 * minute
        assertEquals(
            2 * 60 * minute + EXPIRY_GRACE_MS,
            PremiumRefreshPolicy.expiryCheckDelayMs(now, isPremium = true, expiryMs = expiry),
        )
    }

    @Test
    fun freeOrUnknownExpirySchedulesNothing() {
        assertNull(PremiumRefreshPolicy.expiryCheckDelayMs(now, isPremium = false, expiryMs = now + minute))
        assertNull(PremiumRefreshPolicy.expiryCheckDelayMs(now, isPremium = true, expiryMs = null))
    }

    @Test
    fun passedExpiryRetriesLaterInsteadOfSpinning() {
        // Still Premium after the expiry check (renewal not booked, or offline).
        assertEquals(PASSED_EXPIRY_RETRY_MS, PremiumRefreshPolicy.expiryCheckDelayMs(now, true, now - minute))
        assertEquals(PASSED_EXPIRY_RETRY_MS, PremiumRefreshPolicy.expiryCheckDelayMs(now, true, now - EXPIRY_GRACE_MS))
    }

    @Test
    fun foregroundIsThrottled() {
        assertFalse(PremiumRefreshPolicy.shouldRefreshOnForeground(now, lastSyncMs = now - minute, expiryMs = null))
        assertFalse(
            PremiumRefreshPolicy.shouldRefreshOnForeground(now, now - FOREGROUND_THROTTLE_MS + 1, null),
        )
        assertTrue(PremiumRefreshPolicy.shouldRefreshOnForeground(now, now - FOREGROUND_THROTTLE_MS, null))
    }

    @Test
    fun foregroundAlwaysRefreshesOnceExpiryPassedSinceLastSync() {
        val expiry = now - minute
        assertTrue(PremiumRefreshPolicy.shouldRefreshOnForeground(now, lastSyncMs = expiry - minute, expiryMs = expiry))
        // Already re-synced after that expiry: back to the throttle.
        assertFalse(PremiumRefreshPolicy.shouldRefreshOnForeground(now, lastSyncMs = expiry + 1, expiryMs = expiry))
        // Expiry still ahead: the throttle decides.
        assertFalse(PremiumRefreshPolicy.shouldRefreshOnForeground(now, now - minute, now + minute))
    }

    @Test
    fun foregroundBeforeTheUnlockSyncWaitsForIt() {
        assertFalse(PremiumRefreshPolicy.shouldRefreshOnForeground(now, lastSyncMs = null, expiryMs = now - minute))
    }

    @Test
    fun clockSetBackDoesNotRefresh() {
        assertFalse(PremiumRefreshPolicy.shouldRefreshOnForeground(now, lastSyncMs = now + 60 * minute, expiryMs = null))
    }

    @Test
    fun subscriptionExpiryParsesFutureIsoOnly() {
        val parsed = PremiumRefreshPolicy.subscriptionExpiryMs("2027-01-15T12:00:00Z", now)
        assertEquals(java.time.Instant.parse("2027-01-15T12:00:00Z").toEpochMilli(), parsed)
        assertEquals(
            java.time.Instant.parse("2027-01-15T10:00:00Z").toEpochMilli(),
            PremiumRefreshPolicy.subscriptionExpiryMs("2027-01-15T12:00:00+02:00", now),
        )
        assertNull(PremiumRefreshPolicy.subscriptionExpiryMs("2020-01-01T00:00:00Z", now))
        assertNull(PremiumRefreshPolicy.subscriptionExpiryMs("not a date", now))
        assertNull(PremiumRefreshPolicy.subscriptionExpiryMs(null, now))
        assertNull(PremiumRefreshPolicy.subscriptionExpiryMs("", now))
    }
}
