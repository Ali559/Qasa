package alibarznji.qasa.screens.shell

import alibarznji.qasa.R
import alibarznji.qasa.screens.AppPage
import alibarznji.qasa.screens.settings.main.SettingsProfileCard
import alibarznji.qasa.theme.AppMenuHeader
import alibarznji.qasa.theme.CardContainer
import alibarznji.qasa.theme.NavChevronRow
import alibarznji.qasa.theme.QasaDimens
import alibarznji.qasa.theme.ScreenScaffold
import alibarznji.qasa.theme.SectionLabel
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.MonitorHeart
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.PanTool
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.VpnLock
import androidx.compose.material.icons.outlined.WorkspacePremium
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource

/**
 * The "You" tab: what the side drawer used to hold, grouped into short cards so nothing
 * is more than one tap away.
 */
@Composable
fun YouScreen(
    displayName: String,
    isPremium: Boolean,
    onOpen: (AppPage) -> Unit,
) {
    ScreenScaffold(header = { AppMenuHeader(title = stringResource(R.string.tab_you), onMenu = null, applyStatusBars = false) }) {
        Column(verticalArrangement = Arrangement.spacedBy(QasaDimens.sectionGap)) {
            SettingsProfileCard(
                displayName = displayName,
                isPremium = isPremium,
                onCta = { onOpen(if (isPremium) AppPage.Settings else AppPage.PremiumUpgrade) },
            )
            PageGroup(
                label = stringResource(R.string.nav_tools),
                pages = listOf(
                    AppPage.Notifications,
                    AppPage.PasswordHealth,
                    AppPage.Generator,
                    AppPage.Collections,
                    AppPage.Vpn,
                ),
                onOpen = onOpen,
            )
            PageGroup(
                label = stringResource(R.string.you_account),
                pages = listOf(AppPage.Settings),
                onOpen = onOpen,
            )
            PageGroup(
                label = stringResource(R.string.you_about),
                pages = listOf(AppPage.Terms, AppPage.Privacy),
                onOpen = onOpen,
            )
        }
    }
}

@Composable
private fun PageGroup(label: String, pages: List<AppPage>, onOpen: (AppPage) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(QasaDimens.contentGap)) {
        SectionLabel(label)
        CardContainer {
            pages.forEach { page ->
                NavChevronRow(
                    title = stringResource(page.labelRes),
                    icon = page.youIcon,
                    onClick = { onOpen(page) },
                )
            }
        }
    }
}

/** Outlined Material stand-ins for iOS `AppPage.systemImage` (`AppSession.swift:90-104`). */
private val AppPage.youIcon: ImageVector
    get() = when (this) {
        AppPage.Vault -> Icons.Outlined.Folder
        AppPage.Notifications -> Icons.Outlined.Notifications
        AppPage.Generator -> Icons.Outlined.AutoFixHigh
        AppPage.PasswordHealth -> Icons.Outlined.MonitorHeart
        AppPage.BreachedPasswords -> Icons.Outlined.Shield
        AppPage.Vpn -> Icons.Outlined.VpnLock
        AppPage.Collections -> Icons.Outlined.Folder
        AppPage.Settings -> Icons.Outlined.Settings
        AppPage.Backups -> Icons.Outlined.Backup
        AppPage.Terms -> Icons.Outlined.Description
        AppPage.Privacy -> Icons.Outlined.PanTool
        AppPage.PremiumUpgrade -> Icons.Outlined.WorkspacePremium
    }
