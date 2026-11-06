package alibarznji.qasa.screens.settings

import alibarznji.qasa.vpn.VPNService
import alibarznji.qasa.license.LicenseManager
import alibarznji.qasa.prefs.AppPreferences
import alibarznji.qasa.device.DeviceRegistrationService
import alibarznji.qasa.screens.AppSession
import alibarznji.qasa.screens.settings.main.SettingsHomeContent
import alibarznji.qasa.screens.nav.SettingsRoutes
import alibarznji.qasa.theme.QasaModalBottomSheet
import alibarznji.qasa.vault.VaultRepository
import alibarznji.qasa.vault.VaultSecureStore
import alibarznji.qasa.vault.VaultSession
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    val prefs: AppPreferences,
    val store: VaultSecureStore,
    val session: VaultSession,
    val repo: VaultRepository,
    val appSession: AppSession,
    val devices: DeviceRegistrationService,
    val license: LicenseManager,
    val vpn: VPNService,
) : ViewModel()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onOpenMenu: (() -> Unit)? = null,
    onLock: () -> Unit,
    onUpgrade: () -> Unit,
    vm: SettingsViewModel = hiltViewModel(),
) {
    val navController = rememberNavController()

    Box(Modifier.fillMaxSize()) {
        NavHost(
            navController = navController,
            startDestination = SettingsRoutes.HOME,
            modifier = Modifier.fillMaxSize(),
        ) {
            composable(SettingsRoutes.HOME) {
                SettingsHomeContent(
                    onOpenMenu = onOpenMenu,
                    onLock = onLock,
                    onUpgrade = onUpgrade,
                    onChangePassword = { navController.navigate(SettingsRoutes.CHANGE_PASSWORD) },
                    onDevices = { navController.navigate(SettingsRoutes.DEVICES) },
                    onLan = { navController.navigate(SettingsRoutes.LAN) },
                    onSubscription = { navController.navigate(SettingsRoutes.SUBSCRIPTION) },
                    vm = vm,
                )
            }
            composable(SettingsRoutes.CHANGE_PASSWORD) {
                SettingsSheet(onDismiss = { navController.popBackStack() }) {
                    ChangeMasterPasswordScreen(onDone = { navController.popBackStack() })
                }
            }
            composable(SettingsRoutes.DEVICES) {
                SettingsSheet(onDismiss = { navController.popBackStack() }) {
                    LinkedDevicesScreen(
                        onDone = { navController.popBackStack() },
                        onUpgrade = {
                            navController.popBackStack()
                            onUpgrade()
                        },
                    )
                }
            }
            composable(SettingsRoutes.LAN) {
                SettingsSheet(onDismiss = { navController.popBackStack() }) {
                    LANSyncScreen(
                        onDone = { navController.popBackStack() },
                        onUpgrade = {
                            navController.popBackStack()
                            onUpgrade()
                        },
                    )
                }
            }
            composable(SettingsRoutes.SUBSCRIPTION) {
                SettingsSheet(onDismiss = { navController.popBackStack() }) {
                    ManageSubscriptionScreen(onDone = { navController.popBackStack() })
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsSheet(
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) {
    BackHandler(onBack = onDismiss)
    val maxHeight = LocalConfiguration.current.screenHeightDp.dp * 0.92f
    QasaModalBottomSheet(onDismissRequest = onDismiss) {
        Box(Modifier.fillMaxWidth().heightIn(max = maxHeight)) {
            content()
        }
    }
}
