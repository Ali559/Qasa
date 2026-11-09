package alibarznji.qasa.services

import alibarznji.qasa.device.DeviceRegistrationService
import alibarznji.qasa.license.LicenseManager
import alibarznji.qasa.push.DevicePrefsSync
import alibarznji.qasa.push.PushTokenRegistrar
import alibarznji.qasa.screens.AppSession
import alibarznji.qasa.vault.VaultRepository
import alibarznji.qasa.vault.VaultSession
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppBootstrap @Inject constructor(
    private val session: VaultSession,
    private val devices: DeviceRegistrationService,
    private val license: LicenseManager,
    private val devicePrefs: DevicePrefsSync,
    private val pushTokenRegistrar: PushTokenRegistrar,
    private val appSession: AppSession,
    private val vault: VaultRepository,
) {

    suspend fun onVaultUnlocked() {
        // Folder names saved before they were encrypted (plan decision 5) are encrypted
        // with the vault key now and the plaintext blanked. Never blocks the unlock: the
        // screens show a legacy name as is, and the next unlock tries again.
        try {
            vault.encryptLegacyCollectionNames()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (_: Exception) {
        }
        // Rows synced in while locked (a vault adopted over Wi‑Fi) need search tokens,
        // which only the unlocked search key can build. Never blocks the unlock.
        try {
            vault.reindexSearchIfPending()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (_: Exception) {
            // Search falls back to no tokens for those items; the flag stays set to retry.
        }
        pushTokenRegistrar.refreshTokenIfAvailable()
        // The verifying key must exist before any token is trusted, and before the UI
        // reads `isPremium` — verification fails closed without one.
        license.bootstrapVerifyingKey()
        devices.registerIfNeeded()
        if (license.refresh()) {
            // refreshed
        } else {
            license.reloadFromStore()
        }
        appSession.syncFromLicense(license)
        // Locale, FCM token and the real notifications-enabled state (no longer hardcoded true).
        devicePrefs.syncAll()
    }

    /**
     * iOS PATCHes the locale whenever the language changes, so push copy follows it.
     * Locale only: prefs is a partial update. Runs on [DevicePrefsSync]'s own scope, since
     * the activity is recreated right after this is called.
     */
    fun onLanguageChanged() {
        if (license.hasToken) devicePrefs.syncLocale()
    }

    /**
     * App resume or a POST_NOTIFICATIONS result: the user may have flipped the OS switch
     * (in settings, the prompt, or the shade). PATCHes `notifications_enabled` only if it
     * changed since the last sync and a license token exists.
     */
    fun onNotificationStateMayHaveChanged() {
        devicePrefs.syncNotificationsEnabledIfChanged()
    }

    fun onLogout() {
        session.lock()
    }
}
