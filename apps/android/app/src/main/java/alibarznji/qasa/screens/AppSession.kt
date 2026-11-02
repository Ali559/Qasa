package alibarznji.qasa.screens

import alibarznji.qasa.R
import alibarznji.qasa.license.LicenseManager
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
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
    /** Free Pwned Passwords check; the Alerts tab. */
    BreachedPasswords(R.string.nav_breached_passwords),
    Vpn(R.string.nav_vpn),
    Collections(R.string.nav_collections),
    Settings(R.string.nav_settings),
    Terms(R.string.nav_terms),
    Privacy(R.string.nav_privacy),
    PremiumUpgrade(R.string.nav_upgrade);

    val requiresPremium: Boolean
        // Email leak alerts are parked (todo email-leak-alerts in docs/server-followups-plan.md);
        // the breached-password check that replaced them is free.
        get() = this == Vpn
}

@Singleton
class AppSession @Inject constructor() {
    private val _isPremium = MutableStateFlow(false)
    val isPremium = _isPremium.asStateFlow()
    private val _tierLabel = MutableStateFlow("Free")
    val tierLabel = _tierLabel.asStateFlow()
    private val _openNotifications = MutableStateFlow(false)
    val openNotifications = _openNotifications.asStateFlow()
    var displayName: String = "Qasa User"

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
}
