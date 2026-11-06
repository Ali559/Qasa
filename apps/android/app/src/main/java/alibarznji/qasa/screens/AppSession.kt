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
    /** Pwned Passwords check; the Alerts tab. Premium, but the tab stays and shows the locked state. */
    BreachedPasswords(R.string.nav_breached_passwords),
    Vpn(R.string.nav_vpn),
    Collections(R.string.nav_collections),
    Settings(R.string.nav_settings),
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
