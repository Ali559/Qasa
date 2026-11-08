package alibarznji.qasa.screens.tools.collections

import alibarznji.qasa.R
import alibarznji.qasa.theme.AppMenuHeader
import alibarznji.qasa.theme.EmptyState
import alibarznji.qasa.theme.MintFab
import alibarznji.qasa.theme.QasaConfirmDialog
import alibarznji.qasa.theme.QasaDimens
import alibarznji.qasa.theme.QasaIconSize
import alibarznji.qasa.theme.ScreenScaffold
import alibarznji.qasa.theme.ThemedTextField
import alibarznji.qasa.theme.qasaColors
import alibarznji.qasa.theme.qasaText
import alibarznji.qasa.vault.VaultListRow
import alibarznji.qasa.vault.db.VaultCollectionEntity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource

/**
 * iOS `CollectionsView` body: collection cards (tap → detail, ⋮ → Edit / Delete), the
 * "No Collections" empty state, and the + FAB with the create alert.
 */
@Composable
internal fun CollectionsListContent(
    collections: List<VaultCollectionEntity>,
    rows: List<VaultListRow>,
    onOpenMenu: (() -> Unit)?,
    onOpen: (VaultCollectionEntity) -> Unit,
    onCreate: (String) -> Unit,
    onRename: (String, String) -> Unit,
    onDelete: (String) -> Unit,
) {
    var showCreate by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<VaultCollectionEntity?>(null) }
    var pendingDelete by remember { mutableStateOf<String?>(null) }

    Box(Modifier.fillMaxSize()) {
        ScreenScaffold(
            header = { AppMenuHeader(title = stringResource(R.string.nav_collections), onMenu = onOpenMenu, applyStatusBars = false) },
        ) {
            if (collections.isEmpty()) {
                EmptyState(
                    title = stringResource(R.string.collections_nonetitle),
                    hint = stringResource(R.string.collections_empty),
                    icon = Icons.Default.Folder,
                )
            } else {
                collections.forEach { col ->
                    CollectionCard(
                        collection = col,
                        count = rows.count { it.collectionId.equals(col.id, ignoreCase = true) },
                        onOpen = { onOpen(col) },
                        onEdit = { renaming = col },
                        onDelete = { pendingDelete = col.id },
                    )
                }
            }
            Spacer(Modifier.height(QasaDimens.emptyStateMin))
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = QasaDimens.screenPadding, bottom = QasaDimens.sectionGap),
        ) {
            MintFab(onClick = { showCreate = true })
        }
    }

    if (showCreate) {
        CollectionNameDialog(
            title = stringResource(R.string.collections_createtitle),
            initial = "",
            confirmLabel = stringResource(R.string.common_create),
            onConfirm = { name ->
                onCreate(name)
                showCreate = false
            },
            onDismiss = { showCreate = false },
        )
    }

    renaming?.let { col ->
        CollectionNameDialog(
            title = stringResource(R.string.collections_rename),
            initial = col.name,
            confirmLabel = stringResource(R.string.common_save),
            onConfirm = { name ->
                onRename(col.id, name)
                renaming = null
            },
            onDismiss = { renaming = null },
        )
    }

    pendingDelete?.let { id ->
        QasaConfirmDialog(
            title = stringResource(R.string.collections_deletetitle),
            message = stringResource(R.string.collections_deletemessage),
            confirmLabel = stringResource(R.string.common_delete),
            onConfirm = {
                pendingDelete = null
                onDelete(id)
            },
            onDismiss = { pendingDelete = null },
        )
    }
}

/** iOS `collectionCard`: 48pt folder well, name, item count, rotated-ellipsis menu. */
@Composable
private fun CollectionCard(
    collection: VaultCollectionEntity,
    count: Int,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val c = qasaColors
    val shape = RoundedCornerShape(QasaDimens.cornerRadius)
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .padding(bottom = QasaDimens.gapSmall)
            .fillMaxWidth()
            .clip(shape)
            .background(c.cardBackground)
            .border(QasaDimens.stroke, c.stroke, shape)
            .clickable(onClick = onOpen)
            .padding(QasaDimens.gap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(QasaDimens.touchTarget)
                .background(c.chrome, RoundedCornerShape(QasaDimens.radiusMedium))
                .border(QasaDimens.stroke, c.strokeChrome, RoundedCornerShape(QasaDimens.radiusMedium)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Default.Folder, contentDescription = null, tint = c.accent, modifier = Modifier.size(QasaIconSize.lg))
        }
        Spacer(Modifier.width(QasaDimens.drawerGap))
        Column(modifier = Modifier.weight(1f)) {
            Text(collection.name, color = c.textPrimary, style = qasaText.rowTitle)
            Spacer(Modifier.height(QasaDimens.spaceSmall))
            Text(
                stringResource(
                    if (count == 1) R.string.collections_itemcountone else R.string.collections_itemcount,
                    count,
                ),
                color = c.textSecondary,
                style = qasaText.bodySmall,
            )
        }
        Box {
            IconButton(onClick = { menuOpen = true }) {
                Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.a11y_moreactions), tint = c.textSecondary)
            }
            DropdownMenu(
                expanded = menuOpen,
                onDismissRequest = { menuOpen = false },
                containerColor = c.cardBackground,
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.common_edit), color = c.textPrimary) },
                    leadingIcon = { Icon(Icons.Default.Edit, null, tint = c.textPrimary) },
                    onClick = {
                        menuOpen = false
                        onEdit()
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.common_delete), color = c.danger) },
                    leadingIcon = { Icon(Icons.Default.Delete, null, tint = c.danger) },
                    onClick = {
                        menuOpen = false
                        onDelete()
                    },
                )
            }
        }
    }
}

/** iOS `.alert` with a `TextField`: shared by "New Collection" and "Rename Collection". */
@Composable
private fun CollectionNameDialog(
    title: String,
    initial: String,
    confirmLabel: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val c = qasaColors
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, color = c.textPrimary) },
        text = {
            ThemedTextField(
                value = name,
                onValueChange = { name = it },
                placeholder = stringResource(R.string.common_name),
            )
        },
        confirmButton = {
            // iOS alert buttons always dismiss; a blank name is simply not saved (`guard !trimmed.isEmpty`).
            TextButton(onClick = { if (name.isNotBlank()) onConfirm(name.trim()) else onDismiss() }) {
                Text(confirmLabel, color = c.accent)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel), color = c.textSecondary)
            }
        },
        containerColor = c.cardBackground,
    )
}
