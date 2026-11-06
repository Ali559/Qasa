package alibarznji.qasa.screens.breach

import alibarznji.qasa.R
import alibarznji.qasa.screens.PasswordHealthReport
import alibarznji.qasa.screens.premium.appLocale
import alibarznji.qasa.screens.tools.health.HealthViewModel
import alibarznji.qasa.screens.vault.VaultItemEditorSheetContent
import alibarznji.qasa.theme.AppMenuHeader
import alibarznji.qasa.theme.CardContainer
import alibarznji.qasa.theme.EmptyState
import alibarznji.qasa.theme.QasaDimens
import alibarznji.qasa.theme.QasaIconSize
import alibarznji.qasa.theme.QasaModalBottomSheet
import alibarznji.qasa.theme.ScreenScaffold
import alibarznji.qasa.theme.SecondaryButton
import alibarznji.qasa.theme.VaultItemRow
import alibarznji.qasa.theme.qasaColors
import alibarznji.qasa.theme.qasaText
import alibarznji.qasa.theme.siteLogoFor
import alibarznji.qasa.vault.VaultPayload
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.WifiOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import java.text.NumberFormat
import kotlinx.coroutines.launch

/** The item being edited from the breached list. */
private data class BreachedEdit(val itemId: String, val payload: VaultPayload)

/**
 * The Alerts tab: which saved login passwords appear in HIBP Pwned Passwords. Premium only;
 * a Free account sees [BreachLockedCard] and the check never runs. Shares
 * [HealthViewModel]'s report, so the check, the cache and the counts match Password Health.
 * Replaced the Premium email leak alerts ([BreachMonitorScreen]), which are parked
 * (todo email-leak-alerts in docs/server-followups-plan.md).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BreachedPasswordsScreen(
    onOpenMenu: (() -> Unit)? = null,
    onUpgrade: () -> Unit = {},
    vm: HealthViewModel = hiltViewModel(),
) {
    val report by vm.report.collectAsState()
    val checking by vm.breachChecking.collectAsState()
    val scope = rememberCoroutineScope()
    var editing by remember { mutableStateOf<BreachedEdit?>(null) }

    ScreenScaffold(
        header = { AppMenuHeader(title = stringResource(R.string.nav_breached_passwords), onMenu = onOpenMenu, applyStatusBars = false) },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(QasaDimens.sectionGap)) {
            val current = report
            when {
                current?.breachLocked == true -> BreachLockedCard(onUpgrade = onUpgrade)
                current == null || checking -> CheckingRow()
                current.breachCheckFailed -> OfflineState(onRetry = vm::recheckBreaches)
                current.breachedItems.isEmpty() -> {
                    EmptyState(
                        title = stringResource(R.string.breached_none_title),
                        hint = stringResource(R.string.breached_none_body),
                        icon = Icons.Outlined.Shield,
                    )
                    SecondaryButton(title = stringResource(R.string.breached_check_again), onClick = vm::recheckBreaches)
                }
                else -> {
                    BreachedList(
                        report = current,
                        onOpen = { id ->
                            scope.launch {
                                val payload = runCatching { vm.loadPayload(id) }.getOrNull() ?: return@launch
                                editing = BreachedEdit(id, payload)
                            }
                        },
                    )
                    SecondaryButton(title = stringResource(R.string.breached_check_again), onClick = vm::recheckBreaches)
                }
            }
            PrivacyCard()
        }
    }

    editing?.let { edit ->
        QasaModalBottomSheet(onDismissRequest = { editing = null }) {
            VaultItemEditorSheetContent(
                initial = edit.payload,
                existingId = edit.itemId,
                onDone = { editing = null },
            )
        }
    }
}

@Composable
private fun CheckingRow() {
    val c = qasaColors
    Row(Modifier.fillMaxWidth().padding(top = QasaDimens.gap), verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(color = c.accent, strokeWidth = QasaDimens.stroke * 2, modifier = Modifier.size(QasaDimens.gap))
        Spacer(Modifier.width(QasaDimens.gapSmall))
        Text(stringResource(R.string.breached_checking), color = c.textSecondary, style = qasaText.bodyMedium)
    }
}

@Composable
private fun OfflineState(onRetry: () -> Unit) {
    val c = qasaColors
    Column(verticalArrangement = Arrangement.spacedBy(QasaDimens.gap)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.WifiOff, null, tint = c.textSecondary, modifier = Modifier.size(QasaIconSize.lg))
            Spacer(Modifier.width(QasaDimens.gapSmall))
            Text(stringResource(R.string.breached_offline), color = c.textSecondary, style = qasaText.bodyMedium)
        }
        SecondaryButton(title = stringResource(R.string.breached_check_again), onClick = onRetry)
    }
}

/** Summary, the "change it on the site first" hint, then one row per affected login. */
@Composable
private fun BreachedList(report: PasswordHealthReport, onOpen: (String) -> Unit) {
    val c = qasaColors
    val format = NumberFormat.getIntegerInstance(appLocale())
    Column(verticalArrangement = Arrangement.spacedBy(QasaDimens.gapSmall)) {
        Text(
            stringResource(R.string.breached_found, report.breachedCount),
            color = c.textPrimary,
            style = qasaText.sectionHeadline,
        )
        Text(stringResource(R.string.breached_change_hint), color = c.textSecondary, style = qasaText.bodyMedium)
        Spacer(Modifier.height(QasaDimens.spaceSmall))
        report.breachedItems.forEach { row ->
            VaultItemRow(
                title = row.displayName,
                subtitle = stringResource(R.string.breached_seen_count, format.format(report.breachCounts[row.id] ?: 0)),
                icon = Icons.Outlined.Key,
                logo = siteLogoFor(row.displayName),
                onClick = { onOpen(row.id) },
            )
        }
    }
}

@Composable
private fun PrivacyCard() {
    val c = qasaColors
    CardContainer {
        Text(stringResource(R.string.breached_privacy_title), color = c.textPrimary, style = qasaText.rowTitle)
        Spacer(Modifier.height(QasaDimens.gapMedium))
        Text(stringResource(R.string.breached_privacy_body), color = c.textSecondary, style = qasaText.bodyMedium)
    }
}
