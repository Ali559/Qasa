package alibarznji.qasa.screens

import alibarznji.qasa.R
import alibarznji.qasa.license.LicenseManager
import alibarznji.qasa.prefs.AppPreferences
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class AppPage(
    /** Catalog key for the drawer label, resolved with `stringResource` like iOS (`AppSession.swift:70-84`). */
    @androidx.annotation.StringRes val labelRes: Int,
) {
    Vault(R.string.nav_vault),
    Notifications(R.string.nav_notifications),
    Generator(R.string.nav_generator),
    PasswordHealth(R.string.nav_passwordhealth),
    // Email leak alerts are parked (todo email-leak-alerts in docs/server-followups-plan.md).
    // BreachMonitoring(R.string.nav_breachmonitoring),
    /** Pwned Passwords check; the Alerts tab. Premium, but the tab stays and shows the locked state. */
    BreachedPasswords(R.string.nav_breached_passwords),
    Vpn(R.string.nav_vpn),
    Collections(R.string.nav_collections),
    Settings(R.string.nav_settings),
    /** Opened from the Home backup reminder; in Settings it is a sheet of its own. */
    Backups(R.string.backup_title),
    Terms(R.string.nav_terms),
    Privacy(R.string.nav_privacy),
    PremiumUpgrade(R.string.nav_upgrade);

    val requiresPremium: Boolean
        // Email leak alerts are parked (todo email-leak-alerts in docs/server-followups-plan.md).
        // The breached-password check is Premium, but its tab opens for everyone and shows
        // the upsell itself, so it is not redirected here.
        get() = this == Vpn
}

@Singleton
class AppSession @Inject constructor(private val prefs: AppPreferences) {
    private val _isPremium = MutableStateFlow(false)
    val isPremium = _isPremium.asStateFlow()
    private val _tierLabel = MutableStateFlow("Free")
    val tierLabel = _tierLabel.asStateFlow()
    private val _openNotifications = MutableStateFlow(false)
    val openNotifications = _openNotifications.asStateFlow()
    /** The account's name; [PLACEHOLDER_USERNAME] until there is one. */
    var displayName: String = PLACEHOLDER_USERNAME

    /** The name picked at first launch or in Settings; empty when none. */
    val userName: StateFlow<String> get() = prefs.userName

    fun syncFromLicense(license: LicenseManager) {
        _isPremium.value = license.isPremium
        _tierLabel.value = license.tierLabel
    }

    fun requestOpenNotifications() {
        _openNotifications.value = true
    }

    fun consumeOpenNotifications() {
        _openNotifications.value = false
    }

    /**
     * A login another app or site just submitted (autofill save): the Passwords tab opens
     * the new-login form with it, after the unlock if the vault is locked. Kept in memory
     * only, never written anywhere.
     */
    private val _pendingLoginSave = MutableStateFlow<alibarznji.qasa.vault.VaultPayload.Login?>(null)
    val pendingLoginSave = _pendingLoginSave.asStateFlow()

    fun requestSaveLogin(login: alibarznji.qasa.vault.VaultPayload.Login) {
        _pendingLoginSave.value = login
    }

    fun consumeLoginSave(): alibarznji.qasa.vault.VaultPayload.Login? =
        _pendingLoginSave.value.also { _pendingLoginSave.value = null }

    companion object {
        /** iOS placeholder username: shown localized (`session_qasauser`), never verbatim. */
        const val PLACEHOLDER_USERNAME = "Qasa User"

        /**
         * iOS `AppSession.displayName`: the picked [userName] first, then the account's
         * [username], or null when only the placeholder is left (the caller shows
         * `session_qasauser`).
         */
        fun chosenDisplayName(userName: String, username: String): String? {
            val chosen = AppPreferences.normalizedUserName(userName)
            if (chosen.isNotEmpty()) return chosen
            if (username.isBlank() || username == PLACEHOLDER_USERNAME) return null
            return username
        }
    }
}
