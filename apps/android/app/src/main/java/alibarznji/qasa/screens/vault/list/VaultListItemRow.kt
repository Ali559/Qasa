package alibarznji.qasa.screens.vault.list

import alibarznji.qasa.R
import alibarznji.qasa.screens.vault.RowCopy
import alibarznji.qasa.screens.vault.icon
import alibarznji.qasa.screens.vault.normalizedWebsiteUrl
import alibarznji.qasa.screens.vault.rowCopyActions
import alibarznji.qasa.screens.vault.rowPrimaryCopy
import alibarznji.qasa.screens.vault.subtypeIcon
import alibarznji.qasa.screens.vault.websiteUrl
import alibarznji.qasa.theme.QasaConfirmDialog
import alibarznji.qasa.theme.QasaDimens
import alibarznji.qasa.theme.QasaIcons
import alibarznji.qasa.theme.QasaRowIconButton
import alibarznji.qasa.theme.qasaText
import alibarznji.qasa.theme.QasaIconSize
import alibarznji.qasa.theme.SiteIcons
import alibarznji.qasa.theme.VaultItemRow
import alibarznji.qasa.theme.siteLogoFor
import alibarznji.qasa.vault.VaultItemKind
import alibarznji.qasa.theme.qasaColors
import alibarznji.qasa.vault.VaultListRow
import alibarznji.qasa.vault.VaultPayload
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.launch
import alibarznji.qasa.screens.vault.detail.organizationColor
import alibarznji.qasa.theme.SheetAction
import alibarznji.qasa.theme.QasaActionSheet

/**
 * Port of iOS `VaultItemRow` (`Views/Vault/VaultItemRow.swift`): subtype icon, primary copy
 * button and an overflow menu with per-field copies, Open Website, Edit and Delete.
 *
 * The row list only carries the kind, so — like iOS `loadPayload()` on appear — each row
 * decrypts its own payload, keyed on id + `updatedAt` so it re-reads only after an edit
 * or a `markUsed`. Until then it shows the kind icon and no copy button.
 */
@Composable
internal fun VaultListItemRow(
    row: VaultListRow,
    subtitleFallback: String,
    loadPayload: suspend (String) -> VaultPayload?,
    onOpen: () -> Unit,
    /** `gated` = a protected note's value; the caller answers the biometric gate first. */
    onCopy: (copy: RowCopy, gated: Boolean) -> Unit,
    onEdit: (VaultPayload) -> Unit,
    onDelete: () -> Unit,
) {
    val c = qasaColors
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val payload by produceState<VaultPayload?>(null, row.id, row.updatedAt) {
        value = runCatching { loadPayload(row.id) }.getOrNull()
    }
    var menuOpen by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val primary = payload?.rowPrimaryCopy()

    VaultItemRow(
        title = row.displayName,
        subtitle = row.subtitle.ifBlank { subtitleFallback },
        icon = payload?.subtypeIcon ?: row.kind.icon,
        logo = if (row.kind == VaultItemKind.Login) siteLogoFor(row.displayName) else null,
        siteIcon = (payload as? VaultPayload.Login)?.let { SiteIcons.forWebsite(it.website) },
        itemColor = payload?.organizationColor()?.let { it.swatch() to it.tile() },
        onClick = onOpen,
        // iOS gates even the title copy of a protected note (`VaultItemRow.swift:145`).
        onCopy = primary?.let { p -> { onCopy(p, payload?.isProtectedNote == true) } },
        copyContentDescription = stringResource(R.string.a11y_copy),
        trailing = {
            Box {
                // iOS: `more-vertical` in a 36pt frame.
                QasaRowIconButton(
                    icon = QasaIcons.MoreVertical,
                    contentDescription = stringResource(R.string.a11y_moreactions),
                    onClick = { menuOpen = true },
                )
            }
        },
    )

    // iOS: the item's ⋯ is the app's own action sheet, titled with the item's name.
    if (menuOpen) {
        val actions = buildList {
            payload?.rowCopyActions().orEmpty().forEach { action ->
                add(SheetAction(QasaIcons.Copy, stringResource(action.menuLabelRes)) { onCopy(action.copy, action.gated) })
            }
            payload?.websiteUrl?.let { url ->
                add(
                    SheetAction(QasaIcons.Website, stringResource(R.string.vault_openwebsite)) {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(normalizedWebsiteUrl(url)))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        try {
                            context.startActivity(intent)
                        } catch (_: ActivityNotFoundException) {
                            // No browser: nothing to open, same as iOS `openURL` failing.
                        }
                    },
                )
            }
            add(
                SheetAction(QasaIcons.Edit, stringResource(R.string.vault_edititem)) {
                    scope.launch {
                        val p = payload ?: runCatching { loadPayload(row.id) }.getOrNull() ?: return@launch
                        onEdit(p)
                    }
                },
            )
            add(SheetAction(QasaIcons.Delete, stringResource(R.string.vault_deleteitem), destructive = true) { confirmDelete = true })
        }
        QasaActionSheet(title = row.displayName, actions = actions, onDismiss = { menuOpen = false })
    }

    if (confirmDelete) {
        QasaConfirmDialog(
            title = stringResource(R.string.vault_deleteitemtitle),
            message = stringResource(R.string.vault_deleteitemmessage, row.displayName),
            confirmLabel = stringResource(R.string.common_delete),
            onConfirm = {
                confirmDelete = false
                onDelete()
            },
            onDismiss = { confirmDelete = false },
        )
    }
}
