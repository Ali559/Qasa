package alibarznji.qasa.screens.shell

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import alibarznji.qasa.R
import alibarznji.qasa.screens.AppPage
import alibarznji.qasa.screens.AppSession
import alibarznji.qasa.screens.breach.BreachMonitorScreen
import alibarznji.qasa.screens.home.HomeScreen
import alibarznji.qasa.screens.premium.PremiumUpgradeScreen
import alibarznji.qasa.screens.settings.LegalPlaceholderScreen
import alibarznji.qasa.screens.settings.SettingsScreen
import alibarznji.qasa.screens.tools.CollectionsScreen
import alibarznji.qasa.screens.tools.GenerateScreen
import alibarznji.qasa.screens.tools.NotificationsScreen
import alibarznji.qasa.screens.tools.PasswordHealthScreen
import alibarznji.qasa.screens.vault.VaultListScreen
import alibarznji.qasa.screens.vpn.VPNScreen
import alibarznji.qasa.theme.qasaColors
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class ShellViewModel @Inject constructor(val appSession: AppSession) : ViewModel()

/**
 * Four bottom tabs (Home, Passwords, Alerts, You) replace the side drawer. Every other
 * page opens on top of the current tab with a back arrow and no tab bar, and Back
 * returns to the tab, then to Home.
 */
@Composable
fun AppShellScreen(
    onLock: () -> Unit,
    vm: ShellViewModel = hiltViewModel(),
) {
    val c = qasaColors
    var tab by rememberSaveable { mutableStateOf(ShellTab.Home) }
    var page by rememberSaveable { mutableStateOf<AppPage?>(null) }
    val isPremium by vm.appSession.isPremium.collectAsState()
    val openNotifications by vm.appSession.openNotifications.collectAsState()
    // Mirrors iOS `AppSession.displayName`: the placeholder name is shown localized.
    val displayName = vm.appSession.displayName
        .takeUnless { it.isBlank() || it == "Qasa User" }
        ?: stringResource(R.string.session_qasauser)

    LaunchedEffect(openNotifications) {
        if (openNotifications) {
            page = AppPage.Notifications
            vm.appSession.consumeOpenNotifications()
        }
    }

    fun open(target: AppPage) {
        when {
            target.requiresPremium && !isPremium -> page = AppPage.PremiumUpgrade
            // The two pages that became tabs switch tabs instead of stacking.
            target == AppPage.Vault -> { page = null; tab = ShellTab.Passwords }
            target == AppPage.BreachMonitoring -> { page = null; tab = ShellTab.Alerts }
            else -> page = target
        }
    }

    val back = { page = null }
    BackHandler(enabled = page != null || tab != ShellTab.Home) {
        if (page != null) page = null else tab = ShellTab.Home
    }

    Column(Modifier.fillMaxSize().background(c.background)) {
        // The tab bar owns the navigation-bar inset, so pages under it must not add it again.
        val bodyInsets = if (page == null) {
            Modifier.consumeWindowInsets(WindowInsets.navigationBars)
        } else {
            Modifier
        }
        Box(Modifier.weight(1f).fillMaxWidth().then(bodyInsets)) {
            val current = page
            if (current != null) {
                PageContent(current, back = back, onLock = onLock, onOpen = ::open)
            } else {
                when (tab) {
                    ShellTab.Home -> HomeScreen(
                        displayName = displayName,
                        isPremium = isPremium,
                        onOpen = ::open,
                        onTab = { tab = it },
                    )
                    ShellTab.Passwords -> VaultListScreen()
                    ShellTab.Alerts -> BreachMonitorScreen(onUpgrade = { open(AppPage.PremiumUpgrade) })
                    ShellTab.You -> YouScreen(displayName = displayName, isPremium = isPremium, onOpen = ::open)
                }
            }
        }
        if (page == null) ShellTabBar(selected = tab, onSelect = { tab = it })
    }
}

@Composable
private fun PageContent(
    page: AppPage,
    back: () -> Unit,
    onLock: () -> Unit,
    onOpen: (AppPage) -> Unit,
) {
    val upgrade = { onOpen(AppPage.PremiumUpgrade) }
    when (page) {
        AppPage.Notifications -> NotificationsScreen(onOpenMenu = back)
        AppPage.Generator -> GenerateScreen(onOpenMenu = back)
        AppPage.PasswordHealth -> PasswordHealthScreen(onOpenMenu = back)
        AppPage.Vpn -> VPNScreen(onOpenMenu = back, onUpgrade = upgrade)
        AppPage.Collections -> CollectionsScreen(onOpenMenu = back)
        AppPage.Settings -> SettingsScreen(onOpenMenu = back, onLock = onLock, onUpgrade = upgrade)
        AppPage.Terms -> LegalPlaceholderScreen(
            stringResource(R.string.nav_terms),
            bodyRes = R.string.legal_termsbody,
            onOpenMenu = back,
        )
        AppPage.Privacy -> LegalPlaceholderScreen(
            stringResource(R.string.nav_privacy),
            bodyRes = R.string.legal_privacybody,
            onOpenMenu = back,
        )
        AppPage.PremiumUpgrade -> PremiumUpgradeScreen(onOpenMenu = back, onDone = back)
        // Tabs, never stacked: `open` routes these to their tab.
        AppPage.Vault, AppPage.BreachMonitoring -> Unit
    }
}
