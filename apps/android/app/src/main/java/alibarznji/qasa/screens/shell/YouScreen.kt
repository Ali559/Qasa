package alibarznji.qasa.screens.shell

import alibarznji.qasa.R
import alibarznji.qasa.screens.AppPage
import alibarznji.qasa.screens.settings.main.SettingsProfileCard
import alibarznji.qasa.theme.AppMenuHeader
import alibarznji.qasa.theme.CardContainer
import alibarznji.qasa.theme.NavChevronRow
import alibarznji.qasa.theme.QasaDimens
import alibarznji.qasa.theme.QasaIcons
import alibarznji.qasa.theme.ScreenScaffold
import alibarznji.qasa.theme.SectionLabel
import alibarznji.qasa.theme.qasaColors
import alibarznji.qasa.theme.qasaText
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
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
                // Solar is CC BY 4.0: the credit it asks for (assets/icons/LICENSE.md).
                footer = stringResource(R.string.you_iconscredit),
            )
        }
    }
}

@Composable
private fun PageGroup(label: String, pages: List<AppPage>, onOpen: (AppPage) -> Unit, footer: String? = null) {
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
        if (footer != null) {
            Text(footer, color = qasaColors.textSecondary, style = qasaText.bodySmall)
        }
    }
}

/** The library glyphs for iOS `AppPage.youIcon` (`YouView.swift`). */
private val AppPage.youIcon: ImageVector
    get() = when (this) {
        AppPage.Vault -> QasaIcons.Folder
        AppPage.Notifications -> QasaIcons.Alerts
        AppPage.Generator -> QasaIcons.Generate
        AppPage.PasswordHealth -> QasaIcons.Health
        AppPage.BreachedPasswords -> QasaIcons.Breach
        AppPage.Vpn -> QasaIcons.Vpn
        AppPage.Collections -> QasaIcons.Folder
        AppPage.Settings -> QasaIcons.Settings
        AppPage.Backups -> QasaIcons.Backup
        AppPage.Terms -> QasaIcons.Note
        AppPage.Privacy -> QasaIcons.Privacy
        AppPage.PremiumUpgrade -> QasaIcons.Premium
    }
