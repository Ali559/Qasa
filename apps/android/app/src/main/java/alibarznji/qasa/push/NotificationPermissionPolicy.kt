package alibarznji.qasa.push

/** What to do about POST_NOTIFICATIONS after an unlock. */
enum class NotificationPermissionAction { None, Request, OfferSettings }

/** Android 13 (TIRAMISU), the first release with a runtime notification permission. */
const val NOTIFICATION_PERMISSION_MIN_SDK = 33

/** A declined permission is brought up again (as a link to system settings) at most this often. */
const val NOTIFICATION_SETTINGS_REOFFER_MS = 30L * 24 * 60 * 60 * 1000

/**
 * iOS asks for notification permission once, right after unlock, and never again (the OS
 * owns the switch from then on). Android does the same with the system prompt; once it
 * was declined, re-launching it only nags (and after two denials the OS drops it
 * silently), so the app instead points to system settings, no more than every 30 days.
 *
 * [lastPromptAtMs] is when either the system prompt or the settings offer was last shown.
 */
fun notificationPermissionAction(
    sdkInt: Int,
    granted: Boolean,
    alreadyRequested: Boolean,
    lastPromptAtMs: Long,
    nowMs: Long,
): NotificationPermissionAction = when {
    sdkInt < NOTIFICATION_PERMISSION_MIN_SDK || granted -> NotificationPermissionAction.None
    !alreadyRequested -> NotificationPermissionAction.Request
    nowMs - lastPromptAtMs >= NOTIFICATION_SETTINGS_REOFFER_MS -> NotificationPermissionAction.OfferSettings
    else -> NotificationPermissionAction.None
}

/**
 * Whether `notifications_enabled` needs a PATCH: only with a license token (the call needs
 * one) and only when the value differs from the last one the server accepted.
 */
fun shouldSyncNotificationsEnabled(hasLicenseToken: Boolean, current: Boolean, lastSynced: Boolean?): Boolean =
    hasLicenseToken && current != lastSynced
