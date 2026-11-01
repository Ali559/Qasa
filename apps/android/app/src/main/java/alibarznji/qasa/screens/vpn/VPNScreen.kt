package alibarznji.qasa.screens.vpn

import alibarznji.qasa.R
import alibarznji.qasa.prefs.AppPreferences
import alibarznji.qasa.screens.AppSession
import alibarznji.qasa.theme.AppMenuHeader
import alibarznji.qasa.theme.PremiumGate
import alibarznji.qasa.theme.QasaDimens
import alibarznji.qasa.theme.ScreenScaffold
import alibarznji.qasa.theme.VpnPowerDial
import alibarznji.qasa.theme.qasaColors
import alibarznji.qasa.theme.qasaText
import alibarznji.qasa.vpn.VPNService
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch

@HiltViewModel
class VpnViewModel @Inject constructor(
    val vpn: VPNService,
    val appSession: AppSession,
    val prefs: AppPreferences,
) : ViewModel()

/** Mirrors iOS `VPNView.swift`: free-tier gate, else title → server pill → dial → metrics → toggles. */
@Composable
fun VPNScreen(
    onOpenMenu: (() -> Unit)? = null,
    onUpgrade: () -> Unit,
    vm: VpnViewModel = hiltViewModel(),
) {
    val c = qasaColors
    val isPremium by vm.appSession.isPremium.collectAsState()
    val connected by vm.vpn.connected.collectAsState()
    val isBusy by vm.vpn.isBusy.collectAsState()
    val nodeName by vm.vpn.nodeName.collectAsState()
    val city by vm.vpn.city.collectAsState()
    val assignedIP by vm.vpn.assignedIP.collectAsState()
    val lastError by vm.vpn.lastError.collectAsState()
    // Saved preferences (kill switch defaults on, like iOS). The whole page is premium-only,
    // so free users never reach these; acting on them needs a real tunnel (follow-up plan).
    val killSwitch by vm.prefs.vpnKillSwitch.collectAsState()
    val autoConnect by vm.prefs.vpnAutoConnect.collectAsState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) { if (isPremium) vm.vpn.refreshStatus() }

    ScreenScaffold(
        header = { AppMenuHeader(title = stringResource(R.string.nav_vpn), onMenu = onOpenMenu, applyStatusBars = false) },
    ) {
        if (!isPremium) {
            PremiumGate(hint = stringResource(R.string.settings_requirespremium), onUpgrade = onUpgrade)
            return@ScreenScaffold
        }

        // iOS VStack(spacing: 32) of: title + pill, dial + status, error, two metric cards, toggles.
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                stringResource(R.string.vpn_brand),
                color = c.textPrimary,
                style = qasaText.styleForSize(28, FontWeight.ExtraBold),
            )
            Spacer(Modifier.height(QasaDimens.gap))
            VpnServerPill(city = city, nodeName = nodeName)

            Spacer(Modifier.height(SectionSpacing + DialPadding))
            VpnPowerDial(
                connected = connected,
                busy = isBusy,
                onClick = {
                    // iOS `.disabled(vpn.isBusy)`.
                    if (isBusy) return@VpnPowerDial
                    scope.launch {
                        if (connected) vm.vpn.disconnect() else vm.vpn.connect()
                    }
                },
            )
            Spacer(Modifier.height(SectionSpacing))
            Text(
                if (connected) stringResource(R.string.vpn_connected) else stringResource(R.string.vpn_disconnected),
                color = if (connected) c.success else c.textSecondary,
                style = qasaText.statValue,
            )
            Spacer(Modifier.height(QasaDimens.contentGap))
            Text(
                stringResource(R.string.vpn_sessiononly),
                color = c.textSecondary,
                style = qasaText.sectionLabel,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(DialPadding))

            lastError?.let { err ->
                Spacer(Modifier.height(SectionSpacing))
                Text(err, color = c.danger, style = qasaText.bodySmall, textAlign = TextAlign.Center)
            }

            Spacer(Modifier.height(SectionSpacing))
            VpnMetricCard(
                icon = Icons.Default.Key,
                label = stringResource(R.string.vpn_virtualip),
                value = assignedIP ?: "—",
                highlighted = false,
            )
            Spacer(Modifier.height(SectionSpacing))
            VpnMetricCard(
                icon = Icons.Default.Security,
                label = stringResource(R.string.vpn_encryption),
                // Protocol name, not translated on iOS either.
                value = "WireGuard",
                highlighted = true,
            )
            Spacer(Modifier.height(SectionSpacing))
            VpnSecurityFeaturesCard(
                killSwitch = killSwitch,
                onKillSwitch = vm.prefs::setVpnKillSwitch,
                autoConnect = autoConnect,
                onAutoConnect = vm.prefs::setVpnAutoConnect,
            )
        }
    }
}

/** iOS `VStack(spacing: 32)` between the VPN page's groups. */
private val SectionSpacing = 32.dp

/** iOS `powerDial` `.padding(.vertical, 12)`. */
private val DialPadding = 12.dp
