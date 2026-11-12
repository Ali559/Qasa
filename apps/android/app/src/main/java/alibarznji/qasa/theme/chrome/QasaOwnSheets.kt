package alibarznji.qasa.theme

import alibarznji.qasa.R
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.border
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign

// The app's own menus (`design/README.md`, rule 6a; iOS `OwnSheets.swift`): every ⋯ menu is
// an action sheet and every dropdown a picker sheet, never a `DropdownMenu`. Both use the
// shared sheet header and close by dragging down, the scrim or back; neither has a Cancel.

/** One row of a [QasaActionSheet]. */
data class SheetAction(
    val icon: ImageVector,
    val title: String,
    val destructive: Boolean = false,
    val action: () -> Unit,
)

/**
 * iOS `qasaActionSheet`: the shared header ([title]), then one [SettingsSection] of rows
 * (icon badge, label, [RowDivider] between, no chevron), the destructive one in `danger`.
 * A tap slides the sheet down first, then runs the action, so a follow-up sheet (a delete
 * confirmation) opens after it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QasaActionSheet(title: String, actions: List<SheetAction>, onDismiss: () -> Unit) {
    var pending by remember { mutableStateOf<(() -> Unit)?>(null) }
    QasaModalBottomSheet(
        onDismissRequest = {
            val run = pending
            pending = null
            onDismiss()
            run?.invoke()
        },
        title = title,
    ) {
        val close = rememberSheetDismiss(onDismiss)
        Column(
            Modifier
                .padding(horizontal = QasaDimens.screenPadding)
                .padding(bottom = QasaDimens.sheetContentBottom),
        ) {
            SettingsSection {
                actions.forEachIndexed { index, item ->
                    if (index > 0) RowDivider()
                    QasaRowPressable(onClick = {
                        pending = item.action
                        close()
                    }) {
                        QasaRow(
                            title = item.title,
                            icon = item.icon,
                            iconColor = if (item.destructive) qasaColors.danger else null,
                            titleColor = if (item.destructive) qasaColors.danger else null,
                        )
                    }
                }
            }
        }
    }
}

/** One choice in a [QasaPickerSheet]; [search] matches without being shown (a dial code). */
data class PickerOption<T>(val value: T, val label: String, val search: String = "")

/** More options than this get a search field (iOS `pickerSheetSearchThreshold`). */
const val PICKER_SEARCH_THRESHOLD = 8

/**
 * iOS `qasaPickerSheet`: the field's label as header, then the options in one card: label
 * (`type.rowTitle`), at least 60 tall, a `textPrimary` check on [selected], a full-width
 * stroke divider inset 14 between. Long lists add a search field and scroll. A tap picks and
 * slides the sheet down.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> QasaPickerSheet(
    title: String,
    options: List<PickerOption<T>>,
    /** The current choice (may itself be the null "None" option). */
    selected: T?,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    val c = qasaColors
    var query by rememberSaveable { mutableStateOf("") }
    val long = options.size > PICKER_SEARCH_THRESHOLD
    val shown = remember(options, query) {
        val q = query.trim()
        if (q.isEmpty()) options else options.filter { it.label.contains(q, ignoreCase = true) || it.search.contains(q, ignoreCase = true) }
    }
    QasaModalBottomSheet(onDismissRequest = onDismiss, title = title) {
        val close = rememberSheetDismiss(onDismiss)
        if (long) {
            Box(Modifier.padding(horizontal = QasaDimens.screenPadding).padding(bottom = QasaDimens.formGap)) {
                ThemedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = stringResource(R.string.common_search),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false),
                    imeAction = ImeAction.Search,
                )
            }
        }
        if (shown.isEmpty()) {
            Text(
                stringResource(R.string.common_nomatches),
                color = c.textSecondary,
                style = qasaText.dialogMessage,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(vertical = QasaDimens.sectionGap),
            )
            Spacer(Modifier.height(QasaDimens.sheetContentBottom))
            return@QasaModalBottomSheet
        }
        val shape = RoundedCornerShape(QasaDimens.cornerRadius)
        LazyColumn(
            Modifier
                .weight(1f, fill = false)
                .padding(horizontal = QasaDimens.screenPadding)
                .padding(bottom = QasaDimens.sheetContentBottom)
                .background(c.cardBackground, shape)
                .border(QasaDimens.stroke, c.stroke, shape),
        ) {
            itemsIndexed(shown) { index, option ->
                if (index > 0) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(start = PickerRowInset)
                            .height(QasaDimens.stroke)
                            .background(c.stroke),
                    )
                }
                val isSelected = option.value == selected
                QasaRowPressable(
                    onClick = {
                        onSelect(option.value)
                        close()
                    },
                    role = Role.RadioButton,
                    modifier = Modifier.semantics { this.selected = isSelected },
                ) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = QasaDimens.rowMinHeight)
                            .padding(horizontal = PickerRowInset),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(option.label, color = c.textPrimary, style = qasaText.settingsRowTitle, modifier = Modifier.weight(1f))
                        if (isSelected) {
                            Icon(QasaIcons.Check, contentDescription = null, tint = c.textPrimary, modifier = Modifier.size(QasaIconSize.md))
                        }
                    }
                }
            }
        }
    }
}

/** iOS: rows and dividers inset 14 inside the card. */
private val PickerRowInset = QasaDimens.sectionInsetHorizontal
