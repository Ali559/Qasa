package alibarznji.qasa.screens.vault.list

import alibarznji.qasa.R
import alibarznji.qasa.screens.vault.RowCopy
import alibarznji.qasa.screens.vault.VaultListViewModel
import alibarznji.qasa.theme.AppMenuHeader
import alibarznji.qasa.theme.EmptyState
import alibarznji.qasa.theme.FilterChipButton
import alibarznji.qasa.theme.MintFab
import alibarznji.qasa.theme.QasaDimens
import alibarznji.qasa.theme.SectionLabel
import alibarznji.qasa.theme.ThemedTextField
import alibarznji.qasa.theme.qasaColors
import alibarznji.qasa.vault.BiometricGateEffect
import alibarznji.qasa.vault.VaultItemKind
import alibarznji.qasa.vault.VaultListRow
import alibarznji.qasa.vault.VaultPayload
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import alibarznji.qasa.theme.LocalQasaToast
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource

/** iOS `VaultFilterTab`: "All" (`null`) followed by every kind. */
private val tabs: List<VaultItemKind?> = listOf(null) + VaultItemKind.entries

/**
 * The vault list — port of iOS `VaultListView` body: search, kind chips, then the
 * Suggested / Most Recent / A–Z sections and the tab-aware add FAB.
 */
@Composable
internal fun VaultListContent(
    onOpenMenu: (() -> Unit)?,
    onOpenDetail: (String) -> Unit,
    /** FAB tap with the current tab (`null` = All). */
    onAdd: (VaultItemKind?) -> Unit,
    onEdit: (String, VaultPayload) -> Unit,
    vm: VaultListViewModel,
) {
    val c = qasaColors
    val context = LocalContext.current
    // The app's own toast (iOS `ToastCenter` look), not the system one.
    val qasaToast = LocalQasaToast.current
    val haptics = LocalHapticFeedback.current
    val rows by vm.rows.collectAsState()
    val index by vm.autofillEntries.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val provider = remember { MockSuggestedContextProvider() }
    val sections = remember(rows, index, tab, query) {
        buildVaultListSections(rows, tabs[tab], query, provider.context, index)
    }

    // One gate for the whole list: `BiometricPrompt` callbacks are activity-wide, so a
    // prompt per row would let the last-composed row receive every result.
    var pendingGated by remember { mutableStateOf<(() -> Unit)?>(null) }
    BiometricGateEffect(
        enabled = pendingGated != null,
        title = stringResource(R.string.vault_unlockprotectednote),
        onAuthenticated = {
            val action = pendingGated
            pendingGated = null
            action?.invoke()
        },
        // Fails closed: cancel, lockout or no hardware copies / opens nothing.
        onDismissed = { pendingGated = null },
    )

    fun copy(rowId: String, target: RowCopy) {
        if (target.ephemeral) {
            // Card and bank values expire from the clipboard (iOS `PaymentClipboard`).
            vm.clipboard.copy(target.label, target.value)
        } else {
            val clip = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clip.setPrimaryClip(ClipData.newPlainText(target.label, target.value))
        }
        vm.markUsed(rowId)
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        // iOS flashes "Copied <label>" on the row.
        qasaToast.show(context.getString(R.string.vault_copiedfmt, context.getString(target.labelRes)))
    }

    val rowContent: LazyListScope.(String, List<VaultListRow>) -> Unit = { keyPrefix, sectionRows ->
        items(sectionRows, key = { keyPrefix + it.id }) { row ->
            VaultListItemRow(
                row = row,
                subtitleFallback = stringResource(row.kind.chipLabel),
                loadPayload = vm::loadPayload,
                onOpen = { onOpenDetail(row.id) },
                onCopy = { target, gated ->
                    if (gated) pendingGated = { copy(row.id, target) } else copy(row.id, target)
                },
                onEdit = { payload ->
                    // The editor shows a protected note's content, so it answers the same
                    // gate the detail screen does unless this unlock already revealed it.
                    if (payload.isProtectedNote && !vm.session.isProtectedNoteRevealed(row.id)) {
                        pendingGated = {
                            vm.session.markProtectedNoteRevealed(row.id)
                            onEdit(row.id, payload)
                        }
                    } else {
                        onEdit(row.id, payload)
                    }
                },
                onDelete = { vm.delete(row.id) },
            )
        }
    }

    Box(Modifier.fillMaxSize().background(c.background)) {
        Column(Modifier.fillMaxSize()) {
            AppMenuHeader(title = stringResource(R.string.vault_title_yours), onMenu = onOpenMenu)
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = QasaDimens.screenPadding),
                verticalArrangement = Arrangement.spacedBy(QasaDimens.gapMedium),
            ) {
                item { Spacer(Modifier.height(QasaDimens.gap)) }
                item {
                    ThemedTextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = stringResource(R.string.vault_searchplaceholder),
                        leadingIcon = Icons.Default.Search,
                    )
                }
                item {
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(QasaDimens.contentGap),
                    ) {
                        tabs.forEachIndexed { i, k ->
                            FilterChipButton(
                                title = stringResource(k?.chipLabel ?: R.string.vault_tab_all),
                                selected = tab == i,
                                onClick = { tab = i },
                            )
                        }
                    }
                }
                val suggestedTitle = provider.context.titleRes
                if (sections.suggested.isNotEmpty() && suggestedTitle != null) {
                    item(key = "h-suggested") { SectionLabel(stringResource(suggestedTitle)) }
                    rowContent("s-", sections.suggested)
                }
                if (sections.recent.isNotEmpty()) {
                    item(key = "h-recent") { SectionLabel(stringResource(R.string.vault_mostrecent)) }
                    rowContent("r-", sections.recent)
                }
                sections.letters.forEach { (letter, sectionRows) ->
                    item(key = "h-$letter") { SectionLabel(letter) }
                    rowContent("", sectionRows)
                }
                if (sections.filtered.isEmpty()) {
                    item {
                        EmptyState(
                            title = stringResource(R.string.vault_emptytitle),
                            // iOS has only the empty-vault hint; Android also explains an
                            // empty search (iOS will gain it later).
                            hint = if (query.isBlank()) {
                                stringResource(R.string.vault_emptyhint)
                            } else {
                                stringResource(R.string.vault_nosearchmatches)
                            },
                            icon = Icons.Default.Inbox,
                        )
                    }
                }
                item { Spacer(Modifier.height(QasaDimens.fabClearance)) }
            }
        }
        Box(Modifier.align(Alignment.BottomEnd).padding(QasaDimens.screenPadding)) {
            MintFab(onClick = { onAdd(tabs[tab]) })
        }
    }
}

@get:StringRes
private val VaultItemKind.chipLabel: Int
    get() = when (this) {
        VaultItemKind.Login -> R.string.vault_kind_login_tab
        VaultItemKind.SecureNote -> R.string.vault_kind_securenote_tab
        VaultItemKind.Payment -> R.string.vault_kind_payment_tab
        VaultItemKind.PersonalInfo -> R.string.vault_kind_personalinfo_tab
        VaultItemKind.IdDocument -> R.string.vault_kind_iddocument_tab
    }
