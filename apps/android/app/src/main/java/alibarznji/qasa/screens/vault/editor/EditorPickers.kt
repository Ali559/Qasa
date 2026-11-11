package alibarznji.qasa.screens.vault.editor

import uniffi.qasa_ffi.FfiField
import alibarznji.qasa.R
import alibarznji.qasa.theme.FieldIconButton
import alibarznji.qasa.theme.QasaDimens
import alibarznji.qasa.theme.QasaIconSize
import alibarznji.qasa.theme.QasaMenuRow
import alibarznji.qasa.theme.QasaTextInputSheet
import alibarznji.qasa.theme.SectionLabel
import alibarznji.qasa.theme.qasaColors
import alibarznji.qasa.vault.Country
import alibarznji.qasa.vault.VaultCollection
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/** A vault Phone item offered by iOS `AddressPhonePicker` (VaultItemEditor.swift:730). */
data class PhoneOption(val id: String, val displayName: String)

/** iOS `CountryPickerField` / `CountryMenuPicker` (FormHelpers.swift:49-85) as a group row. */
@Composable
fun CountryPickerField(countryCode: String, showsDialCode: Boolean = false, onChange: (String) -> Unit) {
    val touch = rememberTouch(FfiField.COUNTRY_CODE)
    val selected = Country.all.firstOrNull { it.code == countryCode }
    QasaMenuRow(
        label = stringResource(R.string.vault_country),
        selectedLabel = selected?.pickerLabel(showsDialCode) ?: countryCode,
        options = Country.all,
        optionLabel = { it.pickerLabel(showsDialCode) },
        onSelect = { touch(); onChange(it.code) },
        error = fieldError(FfiField.COUNTRY_CODE),
    )
}

/**
 * iOS `CollectionPickerField` (FormHelpers.swift:87): "None" + collections sorted by name,
 * and a folder button beside the chevron that creates a collection and selects it.
 */
@Composable
fun CollectionPickerField(
    collectionId: String?,
    collections: List<VaultCollection>,
    onChange: (String?) -> Unit,
    onCreate: (name: String, onCreated: (String) -> Unit) -> Unit,
) {
    val c = qasaColors
    val none = stringResource(R.string.common_none)
    val sorted = remember(collections) { collections.sortedBy { it.name.lowercase() } }
    var showNew by remember { mutableStateOf(false) }
    QasaMenuRow(
        label = stringResource(R.string.vault_collection),
        selectedLabel = sorted.firstOrNull { it.id.equals(collectionId, ignoreCase = true) }?.name ?: none,
        options = listOf<VaultCollection?>(null) + sorted,
        optionLabel = { it?.name ?: none },
        onSelect = { onChange(it?.id) },
        trailing = {
            FieldIconButton(
                icon = Icons.Default.CreateNewFolder,
                contentDescription = stringResource(R.string.vault_newcollection),
                onClick = { showNew = true },
                tint = c.accent,
            )
        },
    )
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
    // iOS ignores Create with a blank name (FormHelpers.swift:124): the button stays disabled.
    QasaTextInputSheet(
        title = stringResource(R.string.vault_newcollection),
        initial = "",
        placeholder = stringResource(R.string.common_name),
        confirmLabel = stringResource(R.string.common_create),
        onConfirm = onCreate,
        onDismiss = onDismiss,
    )
}

/** iOS `NoteColorPicker` (FormHelpers.swift:135): six 28pt swatches, white ring on the pick. */
@Composable
fun NoteColorPicker(color: NoteColor, onChange: (NoteColor) -> Unit) {
    val c = qasaColors
    // Not a field: the swatches keep their label above them.
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(QasaDimens.fieldLabelGap)) {
        SectionLabel(stringResource(R.string.vault_color))
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

/** Shared "Type" picker row used by name honorific, email kind and phone kind. */
@Composable
fun <T> KindPicker(
    selected: T,
    options: List<T>,
    label: @Composable (T) -> String,
    onChange: (T) -> Unit,
) {
    QasaMenuRow(
        label = stringResource(R.string.field_type),
        selectedLabel = label(selected),
        options = options,
        optionLabel = label,
        onSelect = onChange,
    )
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
    QasaMenuRow(
        label = stringResource(R.string.field_phonenumber),
        selectedLabel = list.firstOrNull { it.id == phoneItemId }?.displayName ?: none,
        options = listOf<PhoneOption?>(null) + list,
        optionLabel = { it?.displayName ?: none },
        onSelect = { onChange(it?.id) },
    )
}
