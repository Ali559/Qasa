package alibarznji.qasa.screens.home

import alibarznji.qasa.R
import alibarznji.qasa.notifications.NotificationBadgeViewModel
import alibarznji.qasa.screens.AppPage
import alibarznji.qasa.screens.PasswordHealthReport
import alibarznji.qasa.screens.shell.ShellTab
import alibarznji.qasa.screens.tools.health.HealthViewModel
import alibarznji.qasa.screens.vpn.VpnViewModel
import alibarznji.qasa.theme.QasaDimens
import alibarznji.qasa.theme.QasaIconSize
import alibarznji.qasa.theme.QasaPressable
import alibarznji.qasa.theme.qasaColors
import alibarznji.qasa.theme.qasaText
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import java.util.Calendar

/** What the big status on Home says. Only facts the app actually knows. */
enum class HomeStatus { Loading, Empty, AllGood, NeedsAttention }

internal fun homeStatus(report: PasswordHealthReport?): HomeStatus = when {
    report == null -> HomeStatus.Loading
    report.totalPasswords == 0 -> HomeStatus.Empty
    report.reusedCount + report.weakCount + report.breachedCount == 0 -> HomeStatus.AllGood
    else -> HomeStatus.NeedsAttention
}

/**
 * Home tab. The big status answers "are my passwords OK?" from the local health report.
 * It never claims the device is protected by the VPN: the VPN is still an API session
 * without a tunnel (`vpn_sessiononly`), so it stays a separate, honest card below.
 * The page wash turns warm when nothing needs attention and cool when something does.
 */
@Composable
fun HomeScreen(
    displayName: String,
    isPremium: Boolean,
    onOpen: (AppPage) -> Unit,
    onTab: (ShellTab) -> Unit,
    health: HealthViewModel = hiltViewModel(),
    vpnVm: VpnViewModel = hiltViewModel(),
    badge: NotificationBadgeViewModel = hiltViewModel(),
) {
    val c = qasaColors
    val report by health.report.collectAsState()
    val status = homeStatus(report)
    val wash by animateColorAsState(
        if (status == HomeStatus.AllGood) c.warm else c.calm,
        label = "homeWash",
    )

    val unread by badge.count.collectAsState()

    LaunchedEffect(isPremium) { if (isPremium) vpnVm.vpn.refreshStatus() }
    LaunchedEffect(Unit) { badge.refresh() }

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(0f to wash, 0.55f to c.background)),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .padding(horizontal = QasaDimens.screenPadding)
                .padding(top = QasaDimens.gap, bottom = QasaDimens.scrollTail),
            verticalArrangement = Arrangement.spacedBy(QasaDimens.gap),
        ) {
            GreetingRow(displayName = displayName, unread = unread, onNotifications = { onOpen(AppPage.Notifications) })
            StatusHero(status = status, onAddFirst = { onTab(ShellTab.Passwords) })
            if (status != HomeStatus.Empty) {
                HealthCard(report = report, onClick = { onOpen(AppPage.PasswordHealth) })
            }
            VpnCard(isPremium = isPremium, vm = vpnVm, onOpenVpn = { onOpen(AppPage.Vpn) })
            BreachedCard(onClick = { onTab(ShellTab.Alerts) })
        }
    }
}

@Composable
private fun GreetingRow(displayName: String, unread: Int, onNotifications: () -> Unit) {
    val c = qasaColors
    val hour = remember { Calendar.getInstance().get(Calendar.HOUR_OF_DAY) }
    val greeting = stringResource(
        when (hour) {
            in 5..11 -> R.string.home_greeting_morning
            in 12..17 -> R.string.home_greeting_afternoon
            else -> R.string.home_greeting_evening
        },
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(greeting, color = c.textSecondary, style = qasaText.bodyMedium)
            Text(displayName, color = c.textPrimary, style = qasaText.sectionHeadline, maxLines = 1)
        }
        QasaPressable(
            onClick = onNotifications,
            contentDescription = stringResource(R.string.nav_notifications),
            modifier = Modifier.size(QasaIconSize.x7l),
        ) {
            Box(
                Modifier.fillMaxSize().background(c.cardBackground, RoundedCornerShape(QasaDimens.radiusMedium)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.Notifications, null, tint = c.textPrimary, modifier = Modifier.size(QasaIconSize.xl))
                // Unread badge: server inbox + "On this device" (NotificationBadgeViewModel).
                if (unread > 0) {
                    Text(
                        if (unread > 99) "99+" else unread.toString(),
                        color = c.onAccent,
                        style = qasaText.detailSemibold,
                        maxLines = 1,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = QasaDimens.spaceSmall, end = QasaDimens.spaceSmall)
                            .background(c.accent, CircleShape)
                            .padding(horizontal = QasaDimens.spaceSmall),
                    )
                }
            }
        }
    }
}
