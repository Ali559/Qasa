package alibarznji.qasa.screens.vpn

import alibarznji.qasa.R
import alibarznji.qasa.theme.CardContainer
import alibarznji.qasa.theme.QasaDimens
import alibarznji.qasa.theme.ToggleRow
import alibarznji.qasa.theme.qasaColors
import alibarznji.qasa.theme.qasaText
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PanTool
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** iOS `VPNView.swift:58` — capsule under the title: globe + "city — node" (or Auto-select). */
@Composable
internal fun VpnServerPill(city: String?, nodeName: String?) {
    val c = qasaColors
    // iOS `serverLabel` (VPNView.swift:196): both parts must be known, else "Auto-select".
    val label = if (city != null && nodeName != null) "$city — $nodeName" else stringResource(R.string.vpn_autoselect)
    val shape = RoundedCornerShape(50)
    Row(
        modifier = Modifier
            .background(c.cardBackground, shape)
            .border(QasaDimens.stroke, c.stroke, shape)
            .padding(horizontal = 25.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(QasaDimens.gapSmall),
    ) {
        Icon(Icons.Default.Public, contentDescription = null, tint = c.cyan)
        Text(label, color = c.textPrimary, style = qasaText.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * iOS `metricCard` (VPNView.swift:203): accent icon, mono uppercase label, monospaced value;
 * `highlighted` swaps the stroke for a 1.5pt accent border (the WireGuard card).
 */
@Composable
internal fun VpnMetricCard(icon: ImageVector, label: String, value: String, highlighted: Boolean) {
    val c = qasaColors
    val shape = RoundedCornerShape(QasaDimens.cornerRadius)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(c.cardBackground, shape)
            .border(if (highlighted) 1.5.dp else QasaDimens.stroke, if (highlighted) c.accent else c.stroke, shape)
            .padding(QasaDimens.cardPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = null, tint = c.accent)
        Spacer(Modifier.height(QasaDimens.contentGap))
        Text(qasaText.label(label), color = c.textSecondary, style = qasaText.sectionLabel)
        Spacer(Modifier.height(QasaDimens.contentGap))
        Text(value, color = c.textPrimary, style = qasaText.bodyLarge.copy(fontFamily = FontFamily.Monospace))
    }
}

/**
 * iOS `securityFeaturesCard` (VPNView.swift:158): muted card, "Security Features" heading with a
 * bottom rule, then the kill-switch and auto-connect toggles.
 */
@Composable
internal fun VpnSecurityFeaturesCard(
    killSwitch: Boolean,
    onKillSwitch: (Boolean) -> Unit,
    autoConnect: Boolean,
    onAutoConnect: (Boolean) -> Unit,
) {
    val c = qasaColors
    CardContainer(background = c.cardMuted) {
        Column(verticalArrangement = Arrangement.spacedBy(QasaDimens.gap)) {
            Column(Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.vpn_securityfeatures), color = c.textPrimary, style = qasaText.pageTitle)
                Spacer(Modifier.height(QasaDimens.contentGap))
                Box(Modifier.fillMaxWidth().height(QasaDimens.stroke).background(c.stroke))
            }
            ToggleRow(
                title = stringResource(R.string.vpn_killswitch),
                subtitle = stringResource(R.string.vpn_killswitchsub),
                checked = killSwitch,
                onCheckedChange = onKillSwitch,
                icon = Icons.Default.PanTool,
                iconColor = c.danger,
                iconBackground = c.dangerBackground.copy(alpha = 0.35f),
            )
            ToggleRow(
                title = stringResource(R.string.vpn_autoconnect),
                subtitle = stringResource(R.string.vpn_autoconnectsub),
                checked = autoConnect,
                onCheckedChange = onAutoConnect,
                icon = Icons.Default.Sync,
                iconColor = c.cyan,
                // iOS hard-codes Color(hex: "001B21") here.
                iconBackground = Color(0xFF001B21),
            )
        }
    }
}
