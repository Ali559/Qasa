package alibarznji.qasa.screens.vault.editor

import alibarznji.qasa.R
import alibarznji.qasa.theme.QasaDimens
import alibarznji.qasa.theme.QasaIconSize
import alibarznji.qasa.theme.ThemedTextField
import alibarznji.qasa.theme.qasaColors
import alibarznji.qasa.theme.qasaText
import alibarznji.qasa.vault.Country
import alibarznji.qasa.vault.db.VaultCollectionEntity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** A vault Phone item offered by iOS `AddressPhonePicker` (VaultItemEditor.swift:730). */
data class PhoneOption(val id: String, val displayName: String)

/**
 * The Android stand-in for iOS' `.pickerStyle(.menu)` pickers: a field-styled anchor that
 * opens a Material `DropdownMenu`. `trailing` sits inside the box (the collection picker's
 * "new collection" button, as on iOS).
 */
@Composable
fun <T> MenuPicker(
    selectedLabel: String,
    options: List<T>,
    optionLabel: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    val c = qasaColors
    val shape = RoundedCornerShape(QasaDimens.cornerRadius)
    var expanded by remember { mutableStateOf(false) }
    Box(modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 50.dp)
                .background(c.cardBackground, shape)
                .border(QasaDimens.stroke, c.stroke, shape)
                .clickable(role = Role.DropdownList) { expanded = true }
                .padding(start = QasaDimens.gapSmall, end = QasaDimens.spaceSmall),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                selectedLabel,
                color = c.accent,
                style = qasaText.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Icon(Icons.Default.ArrowDropDown, null, tint = c.accent, modifier = Modifier.size(QasaIconSize.x3l))
            trailing?.invoke()
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            containerColor = c.cardBackground,
            modifier = Modifier.heightIn(max = 360.dp),
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(optionLabel(option), color = c.textPrimary, style = qasaText.bodyLarge) },
                    onClick = {
                        expanded = false
                        onSelect(option)
                    },
                )
            }
        }
    }
}

/** iOS `CountryPickerField` / `CountryMenuPicker` (FormHelpers.swift:49-85). */
@Composable
fun CountryMenuPicker(countryCode: String, onChange: (String) -> Unit, showsDialCode: Boolean = false, modifier: Modifier = Modifier) {
    val selected = Country.all.firstOrNull { it.code == countryCode }
    MenuPicker(
        selectedLabel = selected?.pickerLabel(showsDialCode) ?: countryCode,
        options = Country.all,
        optionLabel = { it.pickerLabel(showsDialCode) },
        onSelect = { onChange(it.code) },
        modifier = modifier,
    )
}

@Composable
fun CountryPickerField(countryCode: String, onChange: (String) -> Unit) {
    FieldBlock(stringResource(R.string.vault_country)) {
        CountryMenuPicker(countryCode, onChange)
    }
}

/**
 * iOS `CollectionPickerField` (FormHelpers.swift:87): "None" + collections sorted by name,
 * and a folder button that creates a collection and selects it.
 */
@Composable
fun CollectionPickerField(
    collectionId: String?,
    collections: List<VaultCollectionEntity>,
    onChange: (String?) -> Unit,
    onCreate: (name: String, onCreated: (String) -> Unit) -> Unit,
) {
    val c = qasaColors
    val none = stringResource(R.string.common_none)
    val sorted = remember(collections) { collections.sortedBy { it.name.lowercase() } }
    var showNew by remember { mutableStateOf(false) }
    FieldBlock(stringResource(R.string.vault_collection)) {
        MenuPicker(
            selectedLabel = sorted.firstOrNull { it.id == collectionId }?.name ?: none,
            options = listOf<VaultCollectionEntity?>(null) + sorted,
            optionLabel = { it?.name ?: none },
            onSelect = { onChange(it?.id) },
            trailing = {
                IconButton(onClick = { showNew = true }) {
                    Icon(
                        Icons.Default.CreateNewFolder,
                        contentDescription = stringResource(R.string.vault_newcollection),
                        tint = c.accent,
                        modifier = Modifier.size(QasaIconSize.xl),
                    )
                }
            },
        )
    }
    if (showNew) {
        NewCollectionDialog(
            onDismiss = { showNew = false },
            onCreate = { name ->
                showNew = false
                onCreate(name) { id -> onChange(id) }
            },
        )
    }
}

@Composable
private fun NewCollectionDialog(onDismiss: () -> Unit, onCreate: (String) -> Unit) {
    val c = qasaColors
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.cardBackground,
        titleContentColor = c.textPrimary,
        title = { Text(stringResource(R.string.vault_newcollection)) },
        text = {
            ThemedTextField(value = name, onValueChange = { name = it }, placeholder = stringResource(R.string.common_name))
        },
        confirmButton = {
            // iOS ignores Create with a blank name (FormHelpers.swift:124).
            TextButton(onClick = { name.trim().takeIf { it.isNotEmpty() }?.let(onCreate) }) {
                Text(stringResource(R.string.common_create), color = c.accent)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel), color = c.accent) }
        },
    )
}

/** iOS `NoteColorPicker` (FormHelpers.swift:135): six 28pt swatches, white ring on the pick. */
@Composable
fun NoteColorPicker(color: NoteColor, onChange: (NoteColor) -> Unit) {
    val c = qasaColors
    FieldBlock(stringResource(R.string.vault_color)) {
        Row(horizontalArrangement = Arrangement.spacedBy(QasaDimens.gapSmall)) {
            NoteColor.entries.forEach { option ->
                val label = stringResource(option.label)
                val isSelected = option == color
                Box(
                    Modifier
                        .size(QasaIconSize.x4l)
                        .background(option.color(c), CircleShape)
                        .border(if (isSelected) 2.dp else 0.dp, if (isSelected) Color.White else Color.Transparent, CircleShape)
                        .clickable(role = Role.RadioButton) { onChange(option) }
                        .semantics {
                            contentDescription = label
                            selected = isSelected
                        },
                )
            }
        }
    }
}

/** Shared "Type" picker used by name honorific, email kind and phone kind. */
@Composable
fun <T> KindPicker(
    selected: T,
    options: List<T>,
    label: @Composable (T) -> String,
    onChange: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    FieldBlock(stringResource(R.string.field_type), modifier) {
        MenuPicker(selectedLabel = label(selected), options = options, optionLabel = label, onSelect = onChange)
    }
}

/**
 * iOS `AddressPhonePicker`: "None" plus the vault's Phone items. `phones == null` while
 * loading; once loaded, a link to a deleted phone is cleared, as iOS `onAppear` does.
 */
@Composable
fun AddressPhonePicker(phoneItemId: String?, phones: List<PhoneOption>?, onChange: (String?) -> Unit) {
    val none = stringResource(R.string.common_none)
    LaunchedEffect(phones, phoneItemId) {
        if (phones != null && phoneItemId != null && phones.none { it.id == phoneItemId }) onChange(null)
    }
    val list = phones.orEmpty()
    FieldBlock(stringResource(R.string.field_phonenumber)) {
        MenuPicker(
            selectedLabel = list.firstOrNull { it.id == phoneItemId }?.displayName ?: none,
            options = listOf<PhoneOption?>(null) + list,
            optionLabel = { it?.displayName ?: none },
            onSelect = { onChange(it?.id) },
        )
    }
}

