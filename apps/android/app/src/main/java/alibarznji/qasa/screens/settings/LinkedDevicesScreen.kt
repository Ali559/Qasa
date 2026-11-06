package alibarznji.qasa.screens.settings

import alibarznji.qasa.vpn.VPNService
import alibarznji.qasa.R
import alibarznji.qasa.device.DeviceRegistrationService
import alibarznji.qasa.license.LicenseManager
import alibarznji.qasa.network.QasaApiException
import alibarznji.qasa.network.RemoteDevice
import alibarznji.qasa.screens.AppSession
import alibarznji.qasa.screens.settings.devices.DeviceCard
import alibarznji.qasa.screens.settings.devices.DevicesInfoNote
import alibarznji.qasa.screens.settings.devices.LimitExceededBanner
import alibarznji.qasa.screens.settings.devices.UnlinkAllButton
import alibarznji.qasa.screens.settings.devices.UpgradeHintBanner
import alibarznji.qasa.screens.settings.subscription.appLocale
import alibarznji.qasa.services.AutoLockMonitor
import alibarznji.qasa.theme.BackChromeHeader
import alibarznji.qasa.theme.QasaConfirmDialog
import alibarznji.qasa.theme.QasaDimens
import alibarznji.qasa.theme.QasaIconSize
import alibarznji.qasa.theme.ScreenScaffold
import alibarznji.qasa.theme.StatusPill
import alibarznji.qasa.theme.qasaColors
import alibarznji.qasa.theme.qasaText
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch

@HiltViewModel
class LinkedDevicesViewModel @Inject constructor(
    val devices: DeviceRegistrationService,
    val license: LicenseManager,
    val appSession: AppSession,
    private val autoLock: AutoLockMonitor,
    private val vpn: VPNService,
) : ViewModel() {
    /** iOS `LicenseManager.shared.clear()` when this device loses its slot; the session follows. */
    fun clearLicense() {
        // The server already ended this device's VPN session; drop the tunnel and key too.
        viewModelScope.launch { vpn.forget() }
        license.clear()
        appSession.syncFromLicense(license)
    }

    /** iOS `AutoLockMonitor.shared.requestLock()`: this device locks locally, stays linked. */
    fun lockNow() = autoLock.requestLock()
}

/** Mirrors `I/Views/LinkedDevicesView.swift`. */
@Composable
fun LinkedDevicesScreen(
    onDone: () -> Unit,
    onUpgrade: () -> Unit,
    vm: LinkedDevicesViewModel = hiltViewModel(),
) {
    val c = qasaColors
    val locale = appLocale()
    var list by remember { mutableStateOf<List<RemoteDevice>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var isActing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val isPremium by vm.appSession.isPremium.collectAsState()
    val parsed by vm.license.parsed.collectAsState()
    // iOS reads the device id from the licence; registration's id covers a missing token.
    val currentId = parsed?.deviceId ?: vm.devices.currentDeviceId
    val used = maxOf(list.size, vm.devices.devicesUsed)
    val limit = parsed?.deviceLimit ?: vm.license.deviceLimit
    // iOS `canUnlinkOthers`: only meaningful while a non-primary device exists.
    val canUnlinkOthers = isPremium && list.any { it.is_primary != true }
    var pendingUnlink by remember { mutableStateOf<RemoteDevice?>(null) }
    var pendingLogout by remember { mutableStateOf<RemoteDevice?>(null) }
    var showUnlinkAllConfirm by remember { mutableStateOf(false) }
    val cannotUnlinkPrimary = stringResource(R.string.devices_cannotunlinkprimary)
    val sessionExpired = stringResource(R.string.devices_sessionexpired)
    // iOS `tierLabel`: "Free: 1/1 Used" / "Premium: 2/5 Used" (tier name localized here).
    val tierName = stringResource(if (isPremium) R.string.breach_premium else R.string.breach_free)
    val tierUsed = stringResource(R.string.devices_tierused, tierName, used, limit)

    // iOS `loadDevices`: on failure fall back to the devices the limit error listed.
    suspend fun loadDevices() {
        isLoading = true
        try {
            list = vm.devices.fetchDevices()
            error = null
        } catch (e: Exception) {
            vm.devices.lastLimitError?.devices?.let { list = it }
            error = if (e is QasaApiException.Unauthorized) sessionExpired else e.message
        } finally {
            isLoading = false
        }
    }

    fun act(block: suspend () -> Unit) {
        scope.launch {
            isActing = true
            try {
                block()
            } catch (e: Exception) {
                error = e.message
            } finally {
                isActing = false
            }
        }
    }

    LaunchedEffect(Unit) { loadDevices() }

    ScreenScaffold(
        header = {
            BackChromeHeader(
                applyStatusBars = false,
                title = stringResource(R.string.devices_title),
                onBack = onDone,
                trailing = {
                    StatusPill(
                        tierUsed,
                        dotColor = if (isPremium) c.success else c.goldMuted,
                        textColor = c.textSecondary,
                    )
                },
            )
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(QasaDimens.sectionGap)) {
            if (!isPremium) UpgradeHintBanner(onUpgrade)
            vm.devices.lastLimitError?.let { LimitExceededBanner(it.message) }

            Column(verticalArrangement = Arrangement.spacedBy(QasaDimens.gap)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.devices_activedevices),
                        color = c.textPrimary,
                        style = qasaText.cardTitle,
                        modifier = Modifier.weight(1f),
                    )
                    if (isLoading || isActing) {
                        CircularProgressIndicator(
                            color = c.accent,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(QasaIconSize.xl),
                        )
                    }
                }
                error?.let { Text(it, color = c.danger, style = qasaText.bodyMedium) }
                if (list.isEmpty() && !isLoading) {
                    Text(stringResource(R.string.devices_nonelinked), color = c.textSecondary, style = qasaText.bodyMedium)
                }
                list.forEach { d ->
                    // The primary anchors the account while others remain (iOS guard).
                    val primaryLocked = d.is_primary == true && list.size > 1
                    DeviceCard(
                        device = d,
                        isCurrent = currentId != null && d.id == currentId,
                        isPremium = isPremium,
                        locale = locale,
                        actionsEnabled = !isActing,
                        unlinkDisabled = isPremium && primaryLocked,
                        onUpgrade = onUpgrade,
                        onLogout = { pendingLogout = d },
                        onUnlink = {
                            if (primaryLocked) error = cannotUnlinkPrimary else pendingUnlink = d
                        },
                    )
                }
                if (canUnlinkOthers) {
                    UnlinkAllButton(enabled = !isActing, onClick = { showUnlinkAllConfirm = true })
                }
            }

            DevicesInfoNote()
        }
    }

    pendingUnlink?.let { d ->
        QasaConfirmDialog(
            title = stringResource(R.string.devices_unlinktitle),
            message = stringResource(R.string.devices_unlinkmessage, d.device_name),
            confirmLabel = stringResource(R.string.devices_unlink),
            onConfirm = {
                pendingUnlink = null
                act {
                    vm.devices.unlinkDevice(d.id)
                    // iOS: unlinking this device drops its licence (LinkedDevicesView.swift:223).
                    if (d.id == currentId) vm.clearLicense()
                    loadDevices()
                }
            },
            onDismiss = { pendingUnlink = null },
        )
    }

    pendingLogout?.let { d ->
        QasaConfirmDialog(
            title = stringResource(R.string.devices_logouttitle),
            message = stringResource(R.string.devices_logoutmessage, d.device_name),
            confirmLabel = stringResource(R.string.devices_logout),
            onConfirm = {
                pendingLogout = null
                if (d.id == currentId) {
                    // iOS `logoutDevice`: this device locks locally without unlinking.
                    onDone()
                    vm.lockNow()
                } else {
                    // Remote logout = revoke the licence, the same server call as unlink.
                    act {
                        vm.devices.unlinkDevice(d.id)
                        loadDevices()
                    }
                }
            },
            onDismiss = { pendingLogout = null },
        )
    }

    if (showUnlinkAllConfirm) {
        QasaConfirmDialog(
            title = stringResource(R.string.devices_unlinkalltitle),
            message = stringResource(R.string.devices_unlinkallmessage),
            confirmLabel = stringResource(R.string.devices_unlinkallothers),
            onConfirm = {
                showUnlinkAllConfirm = false
                act {
                    val remaining = vm.devices.unlinkOtherDevices()
                    list = remaining
                    if (currentId != null && remaining.none { it.id == currentId }) vm.clearLicense()
                    error = null
                }
            },
            onDismiss = { showUnlinkAllConfirm = false },
        )
    }
}
