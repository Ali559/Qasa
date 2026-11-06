package alibarznji.qasa.screens.tools.health

import alibarznji.qasa.R
import alibarznji.qasa.screens.PasswordHealthReport
import alibarznji.qasa.screens.breach.BreachLockedCard
import alibarznji.qasa.screens.vault.RowCopy
import alibarznji.qasa.screens.vault.list.VaultListItemRow
import alibarznji.qasa.theme.BackChromeHeader
import alibarznji.qasa.theme.EmptyState
import alibarznji.qasa.theme.QasaDimens
import alibarznji.qasa.theme.qasaColors
import alibarznji.qasa.theme.qasaText
import alibarznji.qasa.vault.VaultListRow
import alibarznji.qasa.vault.VaultPayload
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

/**
 * iOS `PasswordHealthIssuesView` (`PasswordHealthView.swift:165`): Reused / Weak / Breached pill tabs
 * with counts, then the matching logins as vault rows (tap opens the item, overflow has
 * copy / edit / delete), or the iOS empty state for that tab.
 */
@Composable
internal fun HealthIssuesScreen(
    initialTab: HealthIssueTab,
    report: PasswordHealthReport,
    onBack: () -> Unit,
    onOpenItem: (String) -> Unit,
    onEdit: (String, VaultPayload) -> Unit,
    onCopy: (String, RowCopy) -> Unit,
    onDelete: (String) -> Unit,
    loadPayload: suspend (String) -> VaultPayload?,
    onUpgrade: () -> Unit,
) {
    val c = qasaColors
    var selected by rememberSaveable { mutableStateOf(initialTab) }
    val items = when (selected) {
        HealthIssueTab.REUSED -> report.reusedItems
        HealthIssueTab.WEAK -> report.weakItems
        HealthIssueTab.BREACHED -> report.breachedItems
    }
    val loginLabel = stringResource(R.string.vault_kind_login_tab)

    Column(Modifier.fillMaxSize().background(c.background)) {
        BackChromeHeader(title = stringResource(R.string.health_issuestitle), onBack = onBack)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = QasaDimens.screenPadding, vertical = QasaDimens.drawerGap),
            horizontalArrangement = Arrangement.spacedBy(QasaDimens.contentGap),
        ) {
            HealthIssueTab.entries.forEach { tab ->
                val count = when (tab) {
                    HealthIssueTab.REUSED -> report.reusedItems.size
                    HealthIssueTab.WEAK -> report.weakItems.size
                    HealthIssueTab.BREACHED -> report.breachedItems.size
                }
                // A Free account's Breached count was never checked, so it shows no number.
                val locked = tab == HealthIssueTab.BREACHED && report.breachLocked
                IssuePill(
                    title = if (locked) stringResource(tab.titleRes) else "${stringResource(tab.titleRes)} ($count)",
                    selected = tab == selected,
                    onClick = { selected = tab },
                )
            }
        }
        if (selected == HealthIssueTab.BREACHED && report.breachLocked) {
            BreachLockedCard(onUpgrade = onUpgrade, modifier = Modifier.padding(horizontal = QasaDimens.screenPadding))
        } else if (items.isEmpty()) {
            val (title, hint, icon) = when (selected) {
                HealthIssueTab.REUSED -> Triple(R.string.health_noreusedtitle, R.string.health_noreusedbody, Icons.Default.Sync)
                HealthIssueTab.WEAK -> Triple(R.string.health_noweaktitle, R.string.health_noweakbody, Icons.Default.Warning)
                HealthIssueTab.BREACHED -> Triple(R.string.breached_none_title, R.string.breached_none_body, Icons.Default.Shield)
            }
            EmptyState(
                title = stringResource(title),
                hint = stringResource(hint),
                icon = icon,
                modifier = Modifier.padding(horizontal = QasaDimens.screenPadding),
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().navigationBarsPadding(),
                contentPadding = PaddingValues(
                    start = QasaDimens.screenPadding,
                    end = QasaDimens.screenPadding,
                    bottom = QasaDimens.scrollTail,
                ),
                verticalArrangement = Arrangement.spacedBy(QasaDimens.gapSmall),
            ) {
                items(items, key = { it.id }) { row: VaultListRow ->
                    VaultListItemRow(
                        row = row,
                        subtitleFallback = loginLabel,
                        loadPayload = loadPayload,
                        onOpen = { onOpenItem(row.id) },
                        // Only logins reach this list, so the protected-note gate never applies.
                        onCopy = { target, _ -> onCopy(row.id, target) },
                        onEdit = { payload -> onEdit(row.id, payload) },
                        onDelete = { onDelete(row.id) },
                    )
                }
            }
        }
    }
}

private val HealthIssueTab.titleRes: Int
    get() = when (this) {
        HealthIssueTab.REUSED -> R.string.health_reused
        HealthIssueTab.WEAK -> R.string.health_weak
        HealthIssueTab.BREACHED -> R.string.health_breached
    }

/** iOS pill: 14 semibold, 14/9 padding, capsule; selected = accent fill + `onAccent`. */
@Composable
private fun IssuePill(title: String, selected: Boolean, onClick: () -> Unit) {
    val c = qasaColors
    val shape = RoundedCornerShape(50)
    Text(
        title,
        color = if (selected) c.onAccent else c.textSecondary,
        style = qasaText.bodyMediumSemibold,
        modifier = Modifier
            .clip(shape)
            .background(if (selected) c.accent else c.cardBackground, shape)
            .border(QasaDimens.stroke, if (selected) c.accent else c.stroke, shape)
            .selectable(selected = selected, role = Role.Tab, onClick = onClick)
            .padding(horizontal = QasaDimens.drawerGap, vertical = 9.dp),
    )
}
