package alibarznji.qasa.screens.vpn

import alibarznji.qasa.R
import alibarznji.qasa.network.VPNNode
import alibarznji.qasa.network.VPNUsage
import alibarznji.qasa.theme.CardContainer
import alibarznji.qasa.theme.QasaDimens
import alibarznji.qasa.theme.QasaIcons
import alibarznji.qasa.theme.QasaModalBottomSheet
import alibarznji.qasa.theme.QasaPressable
import alibarznji.qasa.theme.SecondaryButton
import alibarznji.qasa.theme.qasaColors
import alibarznji.qasa.theme.qasaText
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import java.util.Locale

/** Region picker: Auto-select, then every node the API offers with its load. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun VpnServerSheet(
    nodes: List<VPNNode>,
    selectedId: String?,
    onSelect: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    val c = qasaColors
    QasaModalBottomSheet(onDismissRequest = onDismiss, title = stringResource(R.string.vpn_chooseserver)) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(QasaDimens.screenPadding),
        ) {
            CardContainer {
                ServerRow(
                    title = stringResource(R.string.vpn_autoselect),
                    subtitle = stringResource(R.string.vpn_autoselectsub),
                    selected = selectedId == null,
                    onClick = { onSelect(null) },
                )
                nodes.forEach { node ->
                    HorizontalDivider(color = c.stroke, thickness = QasaDimens.stroke)
                    ServerRow(
                        title = "${node.city} — ${node.name}",
                        // Digits and the percent sign in the app language (Locale.setDefault follows it).
                        subtitle = "${stringResource(R.string.vpn_serverload)} ${java.text.NumberFormat.getPercentInstance().format(node.load_pct / 100.0)}",
                        selected = node.id == selectedId,
                        onClick = { onSelect(node.id) },
                    )
                }
            }
            if (nodes.isEmpty()) {
                Spacer(Modifier.height(QasaDimens.contentGap))
                Text(stringResource(R.string.vpn_noservers), color = c.textSecondary, style = qasaText.bodySmall)
            }
        }
    }
}

@Composable
private fun ServerRow(title: String, subtitle: String, selected: Boolean, onClick: () -> Unit) {
    val c = qasaColors
    QasaPressable(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(vertical = QasaDimens.gapSmall),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, color = c.textPrimary, style = qasaText.bodyLarge)
                Text(subtitle, color = c.textSecondary, style = qasaText.bodySmall)
            }
            if (selected) Icon(QasaIcons.Check, null, tint = c.accent)
        }
    }
}

/** Monthly data against the plan's quota, and the throttle notice once it is used up. */
@Composable
internal fun VpnDataCard(usage: VPNUsage) {
    val c = qasaColors
    val fraction = if (usage.quota_bytes > 0) (usage.bytes_used.toFloat() / usage.quota_bytes).coerceIn(0f, 1f) else 0f
    CardContainer {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(QasaDimens.contentGap)) {
            Text(qasaText.label(stringResource(R.string.vpn_datathismonth)), color = c.textSecondary, style = qasaText.sectionLabel)
            Text(
                stringResource(R.string.vpn_datausedfmt, formatBytes(usage.bytes_used), formatBytes(usage.quota_bytes)),
                color = c.textPrimary,
                style = qasaText.bodyLarge.copy(fontFamily = FontFamily.Monospace),
            )
            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier.fillMaxWidth().height(6.dp),
                color = if (usage.throttled || fraction >= 0.8f) c.danger else c.accent,
                trackColor = c.stroke,
                drawStopIndicator = {},
            )
            if (usage.throttled) {
                Text(
                    stringResource(R.string.vpn_throttledfmt, usage.throttle_mbps ?: 5),
                    color = c.danger,
                    style = qasaText.bodySmall,
                )
            }
        }
    }
}

/**
 * Android apps cannot block traffic themselves when their VPN drops; only the system's
 * Always-on VPN with "Block connections without VPN" does. Shown under the kill switch.
 */
@Composable
internal fun VpnKillSwitchHint(systemKillSwitch: Boolean?) {
    val c = qasaColors
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(QasaDimens.contentGap)) {
        if (systemKillSwitch == true) {
            Text(stringResource(R.string.vpn_killswitch_android_on), color = c.success, style = qasaText.bodySmall)
        } else {
            Text(stringResource(R.string.vpn_killswitch_android_hint), color = c.textSecondary, style = qasaText.bodySmall)
            SecondaryButton(
                title = stringResource(R.string.vpn_open_vpn_settings),
                onClick = {
                    runCatching {
                        context.startActivity(Intent(Settings.ACTION_VPN_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** Binary units, matching the server's `gb_to_bytes` (1 GB = 1024³ bytes). */
internal fun formatBytes(bytes: Long): String {
    val gb = bytes / GIB
    return if (gb >= 1.0) {
        String.format(Locale.getDefault(), "%.1f GB", gb)
    } else {
        String.format(Locale.getDefault(), "%.0f MB", bytes / MIB)
    }
}

private const val MIB = 1024.0 * 1024.0
private const val GIB = MIB * 1024.0
