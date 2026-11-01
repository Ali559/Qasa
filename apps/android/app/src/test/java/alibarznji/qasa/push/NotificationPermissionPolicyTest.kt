package alibarznji.qasa.push

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationPermissionPolicyTest {
    private val day = 24L * 60 * 60 * 1000
    private val now = 1_000 * day

    private fun action(
        sdk: Int = 34,
        granted: Boolean = false,
        requested: Boolean = false,
        lastPromptAt: Long = 0L,
    ) = notificationPermissionAction(sdk, granted, requested, lastPromptAt, now)

    @Test fun belowAndroid13NothingIsAsked() {
        assertEquals(NotificationPermissionAction.None, action(sdk = 32))
        assertEquals(NotificationPermissionAction.None, action(sdk = 26, requested = true))
    }

    @Test fun grantedNeedsNothing() {
        assertEquals(NotificationPermissionAction.None, action(granted = true))
        assertEquals(NotificationPermissionAction.None, action(granted = true, requested = true))
    }

    @Test fun firstUnlockOnAndroid13RequestsOnce() {
        assertEquals(NotificationPermissionAction.Request, action(sdk = 33))
    }

    @Test fun afterDenialSettingsAreOfferedEveryThirtyDaysAtMost() {
        assertEquals(NotificationPermissionAction.None, action(requested = true, lastPromptAt = now))
        assertEquals(NotificationPermissionAction.None, action(requested = true, lastPromptAt = now - 29 * day))
        assertEquals(NotificationPermissionAction.OfferSettings, action(requested = true, lastPromptAt = now - 30 * day))
    }

    @Test fun syncOnlyWithTokenAndOnChange() {
        assertFalse(shouldSyncNotificationsEnabled(hasLicenseToken = false, current = true, lastSynced = null))
        assertTrue(shouldSyncNotificationsEnabled(hasLicenseToken = true, current = true, lastSynced = null))
        assertTrue(shouldSyncNotificationsEnabled(hasLicenseToken = true, current = false, lastSynced = true))
        assertFalse(shouldSyncNotificationsEnabled(hasLicenseToken = true, current = true, lastSynced = true))
        assertFalse(shouldSyncNotificationsEnabled(hasLicenseToken = true, current = false, lastSynced = false))
    }
}
