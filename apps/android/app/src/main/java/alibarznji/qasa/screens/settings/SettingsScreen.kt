package alibarznji.qasa.screens.settings

import alibarznji.qasa.theme.qasaSheetMaxHeight
import alibarznji.qasa.account.AccountDeletionResult
import alibarznji.qasa.account.AccountDeletionService
import alibarznji.qasa.services.LocalDataEraser
import alibarznji.qasa.vpn.VPNService
import alibarznji.qasa.license.LicenseManager
import alibarznji.qasa.prefs.AppPreferences
import alibarznji.qasa.device.DeviceRegistrationService
import alibarznji.qasa.screens.AppSession
import alibarznji.qasa.screens.settings.backup.BackupsScreen
import alibarznji.qasa.screens.settings.main.SettingsHomeContent
import alibarznji.qasa.screens.nav.SettingsRoutes
import alibarznji.qasa.screens.premium.PremiumUpgradeScreen
import alibarznji.qasa.theme.QasaModalBottomSheet
import alibarznji.qasa.theme.rememberSheetDismiss
import alibarznji.qasa.vault.VaultRepository
import alibarznji.qasa.vault.VaultSecureStore
import alibarznji.qasa.vault.VaultSession
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
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
    val eraser: LocalDataEraser,
    val accountDeletion: AccountDeletionService,
) : ViewModel() {
    /**
     * Runs on the view model's scope, not the screen's: leaving Settings mid-request must
     * not cancel between the server deleting the account and this phone erasing it.
     */
    fun deleteAccount(onResult: (AccountDeletionResult) -> Unit) {
        viewModelScope.launch { onResult(accountDeletion.delete()) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onOpenMenu: (() -> Unit)? = null,
    onLock: () -> Unit,
    onUpgrade: () -> Unit,
    vm: SettingsViewModel = hiltViewModel(),
) {
    // Sub-pages are sheets over Settings, not navigation destinations: as destinations the
    // Settings page faded out behind an opening sheet (the top sat empty for a beat), and
    // popping one removed the sheet in a single frame instead of sliding it down.
    var sheet by remember { mutableStateOf<String?>(null) }
    val close = { sheet = null }

    Box(Modifier.fillMaxSize()) {
        SettingsHomeContent(
            onOpenMenu = onOpenMenu,
            onLock = onLock,
            // iOS opens the upgrade as a sheet from Settings (SettingsView.swift:288).
            onUpgrade = { sheet = SettingsRoutes.PREMIUM },
            onChangePassword = { sheet = SettingsRoutes.CHANGE_PASSWORD },
            onDevices = { sheet = SettingsRoutes.DEVICES },
            onLan = { sheet = SettingsRoutes.LAN },
            onBackups = { sheet = SettingsRoutes.BACKUPS },
            onSubscription = { sheet = SettingsRoutes.SUBSCRIPTION },
            vm = vm,
        )
        when (sheet) {
            SettingsRoutes.CHANGE_PASSWORD -> SettingsSheet(onDismiss = close) {
                ChangeMasterPasswordScreen(onDone = rememberSheetDismiss(close))
            }
            SettingsRoutes.DEVICES -> SettingsSheet(onDismiss = close) {
                val done = rememberSheetDismiss(close)
                LinkedDevicesScreen(
                    onDone = done,
                    onUpgrade = { sheet = SettingsRoutes.PREMIUM },
                )
            }
            SettingsRoutes.LAN -> SettingsSheet(onDismiss = close) {
                LANSyncScreen(
                    onDone = rememberSheetDismiss(close),
                    onUpgrade = {
                        close()
                        onUpgrade()
                    },
                )
            }
            SettingsRoutes.BACKUPS -> SettingsSheet(onDismiss = close) {
                BackupsScreen(onDone = rememberSheetDismiss(close))
            }
            SettingsRoutes.SUBSCRIPTION -> SettingsSheet(onDismiss = close) {
                ManageSubscriptionScreen(onDone = rememberSheetDismiss(close))
            }
            SettingsRoutes.PREMIUM -> SettingsSheet(onDismiss = close) {
                PremiumUpgradeScreen(onDone = rememberSheetDismiss(close), asSheet = true)
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
    val maxHeight = qasaSheetMaxHeight()
    QasaModalBottomSheet(onDismissRequest = onDismiss) {
        Box(Modifier.fillMaxWidth().heightIn(max = maxHeight)) {
            content()
        }
    }
}
