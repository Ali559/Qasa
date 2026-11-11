package alibarznji.qasa.screens.vault.editor

import alibarznji.qasa.R
import alibarznji.qasa.screens.vault.VaultDates
import alibarznji.qasa.theme.FieldIconButton
import alibarznji.qasa.theme.QasaDateRow
import alibarznji.qasa.theme.QasaNotesCard
import alibarznji.qasa.theme.QasaSecureRow
import alibarznji.qasa.theme.QasaTextRow
import alibarznji.qasa.theme.qasaColors
import androidx.annotation.StringRes
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import java.util.Locale
import uniffi.qasa_ffi.FfiField
import uniffi.qasa_ffi.formatCardExpiry
import uniffi.qasa_ffi.formatCardNumber

/*
 * The editor's rows (`design/README.md` rule 4). Each sits in a `QasaFieldGroup` the kind's
 * field list opens; [field] is the payload field whose rule message the row shows (danger
 * label and stripe here, the message under the card).
 */

/**
 * iOS `FormTextField` (FormHelpers.swift:19) as a group row; secure rows add the reveal
 * toggle. [placeholderRes] is an example hint (email, website), never the label.
 */
@Composable
fun FormTextField(
    @StringRes labelRes: Int,
    value: String,
    field: FfiField? = null,
    secure: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    modifier: Modifier = Modifier,
    @StringRes placeholderRes: Int? = null,
    onChange: (String) -> Unit,
) {
    val label = stringResource(labelRes)
    val placeholder = placeholderRes?.let { stringResource(it) }.orEmpty()
    val touch = rememberTouch(field)
    val error = fieldError(field)
    if (secure) {
        QasaSecureRow(
            label = label,
            value = value,
            onValueChange = { touch(); onChange(it) },
            modifier = modifier,
            placeholder = placeholder,
            error = error,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        )
    } else {
        QasaTextRow(
            label = label,
            value = value,
            onValueChange = { touch(); onChange(it) },
            modifier = modifier,
            placeholder = placeholder,
            error = error,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        )
    }
}

/** Notes / content: their own growing card under the group (min 112dp). */
@Composable
fun FormNotesField(@StringRes labelRes: Int, value: String, field: FfiField? = null, onChange: (String) -> Unit) {
    val touch = rememberTouch(field)
    QasaNotesCard(
        label = stringResource(labelRes),
        value = value,
        onValueChange = { touch(); onChange(it) },
        error = fieldError(field),
    )
}

/**
 * iOS `ValidatedDateField` as a group row: stores ISO `YYYY-MM-DD` from the calendar, shows
 * the date localized under the input, and the shared rule message for [field].
 */
@Composable
fun FormDateField(@StringRes labelRes: Int, value: String, field: FfiField, onChange: (String) -> Unit) {
    val locale = LocalConfiguration.current.locales[0] ?: Locale.getDefault()
    val date = remember(value) { VaultDates.parse(value) }
    val touch = rememberTouch(field)
    QasaDateRow(
        label = stringResource(labelRes),
        text = value,
        onTextChange = { touch(); onChange(it) },
        placeholder = stringResource(R.string.field_dateplaceholder),
        error = fieldError(field),
        date = date,
        localized = date?.let { VaultDates.format(it, locale) },
    )
}

/** Card number: grouped as you type (`formatCardNumber`), number keyboard. */
@Composable
fun CardNumberField(value: String, onChange: (String) -> Unit) {
    val touch = rememberTouch(FfiField.NUMBER)
    FormattedTextField(
        label = stringResource(R.string.field_number),
        value = value,
        error = fieldError(FfiField.NUMBER),
        format = ::formatCardNumber,
        keyboardType = KeyboardType.Number,
    ) { touch(); onChange(it) }
}

/** Card expiry: `MM/YY` as you type (`formatCardExpiry`), number keyboard. */
@Composable
fun CardExpiryField(value: String, onChange: (String) -> Unit) {
    val touch = rememberTouch(FfiField.EXPIRY_DATE)
    FormattedTextField(
        label = stringResource(R.string.field_expirydate),
        value = value,
        placeholder = stringResource(R.string.field_expiryplaceholder),
        error = fieldError(FfiField.EXPIRY_DATE),
        format = ::formatCardExpiry,
        keyboardType = KeyboardType.Number,
    ) { touch(); onChange(it) }
}

/**
 * iOS `passwordField` (VaultItemEditor.swift:135): masked by default; trailing, the wand
 * that opens `PasswordGeneratorModal`, then the reveal toggle.
 */
@Composable
fun PasswordFormField(value: String, onChange: (String) -> Unit, onGenerate: () -> Unit) {
    val c = qasaColors
    val touch = rememberTouch(FfiField.PASSWORD)
    QasaSecureRow(
        label = stringResource(R.string.field_password),
        value = value,
        onValueChange = { touch(); onChange(it) },
        error = fieldError(FfiField.PASSWORD),
        trailing = {
            FieldIconButton(
                icon = Icons.Outlined.AutoFixHigh,
                contentDescription = stringResource(R.string.vault_generatepassword),
                onClick = onGenerate,
                tint = c.accent,
            )
        },
    )
}

/**
 * iOS phone-number row (VaultItemEditor.swift:327): the selected country's dial code as a
 * fixed accent prefix on the value line, then the number with a phone keypad.
 */
@Composable
fun PhoneNumberField(dialCode: String, value: String, onChange: (String) -> Unit) {
    val touch = rememberTouch(FfiField.NUMBER)
    QasaTextRow(
        label = stringResource(R.string.field_phonenumber),
        value = value,
        onValueChange = { touch(); onChange(it) },
        prefix = dialCode,
        error = fieldError(FfiField.NUMBER),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
    )
}
