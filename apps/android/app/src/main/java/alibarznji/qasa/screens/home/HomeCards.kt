package alibarznji.qasa.screens.home

import alibarznji.qasa.R
import alibarznji.qasa.screens.PasswordHealthReport
import alibarznji.qasa.screens.vpn.VpnViewModel
import alibarznji.qasa.theme.PrimaryButton
import alibarznji.qasa.theme.QasaDimens
import alibarznji.qasa.theme.QasaIconFrame
import alibarznji.qasa.theme.QasaIconSize
import alibarznji.qasa.theme.QasaPressable
import alibarznji.qasa.theme.qasaColors
import alibarznji.qasa.theme.qasaText
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.MailOutline
import androidx.compose.material.icons.outlined.TipsAndUpdates
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material.icons.outlined.VpnLock
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

private val HeroDisc = 168.dp
private val HeroCore = 104.dp
private val MeterHeight = 8.dp

/** White disc on the page wash, with the status glyph in a raised inner circle. */
@Composable
internal fun StatusHero(status: HomeStatus, onAddFirst: () -> Unit) {
    val c = qasaColors
    val (icon, tint) = when (status) {
        HomeStatus.AllGood -> Icons.Outlined.TaskAlt to c.success
        HomeStatus.NeedsAttention -> Icons.Outlined.TipsAndUpdates to c.accent
        HomeStatus.Empty, HomeStatus.Loading -> Icons.Outlined.Key to c.accent
    }
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(HeroDisc).background(c.cardBackground.copy(alpha = 0.55f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier.size(HeroCore).background(c.cardBackground, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                if (status == HomeStatus.Loading) {
                    CircularProgressIndicator(color = c.accent, modifier = Modifier.size(QasaIconSize.x6l))
                } else {
                    Icon(icon, null, tint = tint, modifier = Modifier.size(QasaIconSize.x7l))
                }
            }
        }
        Spacer(Modifier.height(QasaDimens.gapSmall))
        val (title, body) = when (status) {
            HomeStatus.AllGood -> stringResource(R.string.home_allgood_title) to stringResource(R.string.home_allgood_body)
            HomeStatus.NeedsAttention -> stringResource(R.string.home_attention_title) to stringResource(R.string.home_attention_hint)
            HomeStatus.Empty -> stringResource(R.string.home_empty_title) to stringResource(R.string.home_empty_body)
            HomeStatus.Loading -> stringResource(R.string.home_checking) to ""
        }
        Text(title, color = c.textPrimary, style = qasaText.sectionHeadline, textAlign = TextAlign.Center)
        if (body.isNotEmpty()) {
            Spacer(Modifier.height(QasaDimens.spaceSmall))
            Text(body, color = c.textSecondary, style = qasaText.bodyMedium, textAlign = TextAlign.Center)
        }
        if (status == HomeStatus.Empty) {
            Spacer(Modifier.height(QasaDimens.gap))
            PrimaryButton(title = stringResource(R.string.home_addfirst), onClick = onAddFirst)
        }
    }
}

/** Three-colour bar: fine, reused and easy-to-guess passwords as shares of the total. */
@Composable
internal fun HealthCard(report: PasswordHealthReport?, onClick: () -> Unit) {
    val c = qasaColors
    val total = report?.totalPasswords ?: 0
    val reused = report?.reusedCount ?: 0
    val weak = report?.weakCount ?: 0
    // A login can be both reused and weak; count it once.
    val toFix = report?.let { r -> (r.reusedItems + r.weakItems).map { it.id }.toSet().size } ?: 0
    HomeCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.home_health_title), color = c.textPrimary, style = qasaText.rowTitle, modifier = Modifier.weight(1f))
            if (toFix > 0) {
                Pill(stringResource(R.string.home_tofix, toFix), c.dangerBackground, c.dangerText)
            } else {
                Pill(stringResource(R.string.home_allfine), c.success.copy(alpha = 0.14f), c.success)
            }
        }
        Spacer(Modifier.height(QasaDimens.gapSmall))
        Row(
            Modifier.fillMaxWidth().height(MeterHeight).clip(RoundedCornerShape(50)).background(c.stroke),
        ) {
            val fine = (total - toFix).coerceAtLeast(0)
            Segment(fine, total, c.success)
            Segment(reused, total, c.gold)
            Segment(weak, total, c.danger)
        }
        Spacer(Modifier.height(QasaDimens.contentGap))
        Text(
            if (toFix > 0) {
                stringResource(R.string.home_attention_body, reused, weak)
            } else {
                stringResource(R.string.home_health_allgood, total)
            },
            color = c.textSecondary,
            style = qasaText.bodySmall,
        )
    }
}

@Composable
private fun RowScope.Segment(count: Int, total: Int, color: Color) {
    if (count <= 0 || total <= 0) return
    Box(Modifier.weight(count.toFloat()).fillMaxHeight().background(color))
}

/**
 * VPN state as it really is. Premium users can connect from here; the "tunnel coming
 * soon" note stays until the WireGuard tunnel ships, so Home never overstates protection.
 */
@Composable
internal fun VpnCard(isPremium: Boolean, vm: VpnViewModel, onOpenVpn: () -> Unit) {
    val c = qasaColors
    val connected by vm.vpn.connected.collectAsState()
    val busy by vm.vpn.isBusy.collectAsState()
    val city by vm.vpn.city.collectAsState()
    val scope = rememberCoroutineScope()
    HomeCard(onClick = onOpenVpn) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(Icons.Outlined.VpnLock)
            Spacer(Modifier.size(QasaDimens.gapSmall))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.nav_vpn), color = c.textPrimary, style = qasaText.rowTitle)
                Text(
                    when {
                        !isPremium -> stringResource(R.string.home_vpn_premium)
                        connected -> stringResource(R.string.home_vpn_on, city ?: "—")
                        else -> stringResource(R.string.home_vpn_off)
                    },
                    color = c.textSecondary,
                    style = qasaText.bodySmall,
                )
            }
            if (isPremium) {
                QasaPressable(
                    onClick = {
                        if (!busy) scope.launch { if (connected) vm.vpn.disconnect() else vm.vpn.connect() }
                    },
                    enabled = !busy,
                ) {
                    Pill(
                        stringResource(if (connected) R.string.home_vpn_disconnect else R.string.home_vpn_connect),
                        c.cardMuted,
                        c.onAccentChip,
                    )
                }
            }
        }
        if (isPremium) {
            Spacer(Modifier.height(QasaDimens.contentGap))
            Text(stringResource(R.string.vpn_sessiononly), color = c.textTertiary, style = qasaText.labelRegular)
        }
    }
}

@Composable
internal fun LeaksCard(onClick: () -> Unit) {
    val c = qasaColors
    HomeCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(Icons.Outlined.MailOutline)
            Spacer(Modifier.size(QasaDimens.gapSmall))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.nav_breachmonitoring), color = c.textPrimary, style = qasaText.rowTitle)
                Text(stringResource(R.string.home_leaks_body), color = c.textSecondary, style = qasaText.bodySmall)
            }
        }
    }
}

@Composable
private fun HomeCard(onClick: () -> Unit, content: @Composable () -> Unit) {
    val c = qasaColors
    val shape = RoundedCornerShape(QasaDimens.cornerRadius)
    QasaPressable(onClick = onClick, modifier = Modifier.fillMaxWidth(), pressScale = true) {
        Column(
            Modifier.fillMaxWidth().clip(shape).background(c.cardBackground, shape).padding(QasaDimens.cardPadding),
            verticalArrangement = Arrangement.Top,
        ) { content() }
    }
}

@Composable
private fun IconTile(icon: ImageVector) {
    val c = qasaColors
    Box(
        Modifier.size(QasaIconFrame.toggleDisc).background(c.cardMuted, RoundedCornerShape(QasaDimens.radiusSmall)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = c.accent, modifier = Modifier.size(QasaIconSize.xl))
    }
}

@Composable
private fun Pill(text: String, background: Color, color: Color) {
    Text(
        text,
        color = color,
        style = qasaText.bodySmallMedium,
        modifier = Modifier
            .background(background, RoundedCornerShape(50))
            .padding(horizontal = QasaDimens.gapSmall, vertical = QasaDimens.spaceSmall),
    )
}
