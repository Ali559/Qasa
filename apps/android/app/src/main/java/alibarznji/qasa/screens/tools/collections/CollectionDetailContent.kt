package alibarznji.qasa.screens.tools.collections

import alibarznji.qasa.R
import alibarznji.qasa.screens.vault.RowCopy
import alibarznji.qasa.screens.vault.VaultListViewModel
import alibarznji.qasa.screens.vault.list.VaultListItemRow
import alibarznji.qasa.theme.BackChromeHeader
import alibarznji.qasa.theme.EmptyState
import alibarznji.qasa.theme.QasaDimens
import alibarznji.qasa.theme.qasaColors
import alibarznji.qasa.vault.BiometricGateEffect
import alibarznji.qasa.vault.VaultItemKind
import alibarznji.qasa.vault.VaultPayload
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import alibarznji.qasa.theme.LocalQasaToast
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource

/**
 * Port of iOS `CollectionDetailView` (`Views/CollectionsView.swift`): the collection's items
 * sorted by name, each a full vault row (copy, overflow, edit, delete) that opens the item
 * detail; an "Empty Collection" state otherwise.
 */
@Composable
internal fun CollectionDetailContent(
    collectionId: String,
    collectionName: String,
    onBack: () -> Unit,
    onOpenItem: (String) -> Unit,
    onEdit: (String, VaultPayload) -> Unit,
    vm: VaultListViewModel,
) {
    val c = qasaColors
    val context = LocalContext.current
    // The app's own toast (iOS `ToastCenter` look), not the system one.
    val qasaToast = LocalQasaToast.current
    val haptics = LocalHapticFeedback.current
    val rows by vm.rows.collectAsState()
    // iOS `collectionItems`: filter by collection, `localizedCaseInsensitiveCompare` on name.
    val items = remember(rows, collectionId) {
        rows.filter { it.collectionId.equals(collectionId, ignoreCase = true) }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.displayName })
    }

    // Same single gate as `VaultListContent`: protected-note copies and edits answer it first.
    var pendingGated by remember { mutableStateOf<(() -> Unit)?>(null) }
    BiometricGateEffect(
        enabled = pendingGated != null,
        title = stringResource(R.string.vault_unlockprotectednote),
        onAuthenticated = {
            val action = pendingGated
            pendingGated = null
            action?.invoke()
        },
        onDismissed = { pendingGated = null },
    )

    fun copy(rowId: String, target: RowCopy) {
        if (target.ephemeral) {
            vm.clipboard.copy(target.label, target.value)
        } else {
            val clip = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clip.setPrimaryClip(ClipData.newPlainText(target.label, target.value))
        }
        vm.markUsed(rowId)
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        qasaToast.show(context.getString(R.string.vault_copiedfmt, context.getString(target.labelRes)))
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(c.background)
            .windowInsetsPadding(WindowInsets.statusBars),
    ) {
        BackChromeHeader(title = collectionName, onBack = onBack, applyStatusBars = false)
        if (items.isEmpty()) {
            EmptyState(
                title = stringResource(R.string.collections_emptycollectiontitle),
                hint = stringResource(R.string.collections_assignhint, collectionName),
                icon = Icons.Default.Inbox,
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .windowInsetsPadding(WindowInsets.navigationBars),
                contentPadding = PaddingValues(
                    start = QasaDimens.screenPadding,
                    end = QasaDimens.screenPadding,
                    top = QasaDimens.screenPadding,
                    bottom = QasaDimens.scrollTail,
                ),
                verticalArrangement = Arrangement.spacedBy(QasaDimens.gapSmall),
            ) {
                items(items, key = { it.id }) { row ->
                    VaultListItemRow(
                        row = row,
                        subtitleFallback = stringResource(row.kind.chipLabel),
                        loadPayload = vm::loadPayload,
                        onOpen = { onOpenItem(row.id) },
                        onCopy = { target, gated ->
                            if (gated) pendingGated = { copy(row.id, target) } else copy(row.id, target)
                        },
                        onEdit = { payload ->
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
        }
    }
}

/** Same fallback subtitle as the vault list (`VaultListContent.chipLabel`, private there). */
@get:StringRes
private val VaultItemKind.chipLabel: Int
    get() = when (this) {
        VaultItemKind.Login -> R.string.vault_kind_login_tab
        VaultItemKind.SecureNote -> R.string.vault_kind_securenote_tab
        VaultItemKind.Payment -> R.string.vault_kind_payment_tab
        VaultItemKind.PersonalInfo -> R.string.vault_kind_personalinfo_tab
        VaultItemKind.IdDocument -> R.string.vault_kind_iddocument_tab
    }
