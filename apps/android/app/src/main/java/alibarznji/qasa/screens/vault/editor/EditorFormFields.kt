package alibarznji.qasa.screens.vault.editor

import alibarznji.qasa.R
import alibarznji.qasa.screens.vault.VaultDates
import alibarznji.qasa.theme.FieldIconButton
import alibarznji.qasa.theme.QasaDimens
import alibarznji.qasa.theme.SectionLabel
import alibarznji.qasa.theme.ThemedTextField
import alibarznji.qasa.theme.ValidatedDateField
import alibarznji.qasa.theme.qasaColors
import alibarznji.qasa.theme.qasaText
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalConfiguration
import java.util.Locale
import uniffi.qasa_ffi.FfiField
import uniffi.qasa_ffi.formatCardExpiry
import uniffi.qasa_ffi.formatCardNumber

/** iOS `FormFieldLabel` + content + the 16pt `VStack` spacing of `VaultFormChrome`. */
@Composable
fun FieldBlock(
    label: String,
    modifier: Modifier = Modifier,
    error: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier.fillMaxWidth()) {
        SectionLabel(label)
        Spacer(Modifier.height(QasaDimens.contentGap))
        content()
        FieldErrorText(error)
        Spacer(Modifier.height(QasaDimens.gap))
    }
}

/**
 * iOS `FormTextField` (FormHelpers.swift:19); secure rows add an eye toggle. [field] is the
 * payload field whose rule message shows under it.
 */
@Composable
fun FormTextField(
    @StringRes labelRes: Int,
    value: String,
    field: FfiField? = null,
    secure: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    modifier: Modifier = Modifier,
    onChange: (String) -> Unit,
) {
    val label = stringResource(labelRes)
    var visible by remember { mutableStateOf(false) }
    val touch = rememberTouch(field)
    FieldBlock(label, modifier, error = fieldError(field)) {
        ThemedTextField(
            value = value,
            onValueChange = { touch(); onChange(it) },
            placeholder = label,
            isSecure = secure,
            passwordVisible = visible,
            onToggleVisibility = if (secure) ({ visible = !visible }) else null,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        )
    }
}

/**
 * iOS `ValidatedDateField` in the editor's field rhythm: stores ISO `YYYY-MM-DD` from the
 * picker, shows the date localized beside it, and the shared rule message for [field].
 */
@Composable
fun FormDateField(@StringRes labelRes: Int, value: String, field: FfiField, onChange: (String) -> Unit) {
    val locale = LocalConfiguration.current.locales[0] ?: Locale.getDefault()
    val date = remember(value) { VaultDates.parse(value) }
    val touch = rememberTouch(field)
    ValidatedDateField(
        title = stringResource(labelRes),
        text = value,
        onTextChange = { touch(); onChange(it) },
        placeholder = stringResource(R.string.field_dateplaceholder),
        error = fieldError(field),
        date = date,
        localized = date?.let { VaultDates.format(it, locale) },
    )
    Spacer(Modifier.height(QasaDimens.gap))
}

/** Card number: grouped as you type (`formatCardNumber`), number keyboard. */
@Composable
fun CardNumberField(value: String, onChange: (String) -> Unit) {
    val label = stringResource(R.string.field_number)
    val touch = rememberTouch(FfiField.NUMBER)
    FieldBlock(label, error = fieldError(FfiField.NUMBER)) {
        FormattedTextField(
            value = value,
            placeholder = label,
            label = label,
            format = ::formatCardNumber,
            keyboardType = KeyboardType.Number,
        ) { touch(); onChange(it) }
    }
}

/** Card expiry: `MM/YY` as you type (`formatCardExpiry`), number keyboard. */
@Composable
fun CardExpiryField(value: String, modifier: Modifier = Modifier, onChange: (String) -> Unit) {
    val label = stringResource(R.string.field_expirydate)
    val touch = rememberTouch(FfiField.EXPIRY_DATE)
    FieldBlock(label, modifier, error = fieldError(FfiField.EXPIRY_DATE)) {
        FormattedTextField(
            value = value,
            placeholder = stringResource(R.string.field_expiryplaceholder),
            label = label,
            format = ::formatCardExpiry,
            keyboardType = KeyboardType.Number,
        ) { touch(); onChange(it) }
    }
}

/**
 * iOS `passwordField` (VaultItemEditor.swift:135): masked by default with an eye toggle,
 * plus the wand button that opens `PasswordGeneratorModal`.
 */
@Composable
fun PasswordFormField(value: String, onChange: (String) -> Unit, onGenerate: () -> Unit) {
    val c = qasaColors
    val label = stringResource(R.string.field_password)
    var visible by remember { mutableStateOf(false) }
    val touch = rememberTouch(FfiField.PASSWORD)
    FieldBlock(label, error = fieldError(FfiField.PASSWORD)) {
        ThemedTextField(
            value = value,
            onValueChange = { touch(); onChange(it) },
            placeholder = label,
            isSecure = true,
            passwordVisible = visible,
            onToggleVisibility = { visible = !visible },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
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
}

/**
 * iOS phone-number row (VaultItemEditor.swift:327): the selected country's dial code in a
 * mint chip, then the number field with a phone keypad.
 */
@Composable
fun PhoneNumberField(dialCode: String, value: String, onChange: (String) -> Unit) {
    val c = qasaColors
    val label = stringResource(R.string.field_phonenumber)
    val shape = RoundedCornerShape(QasaDimens.cornerRadius)
    val touch = rememberTouch(FfiField.NUMBER)
    FieldBlock(label, error = fieldError(FfiField.NUMBER)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .widthIn(min = 64.dp)
                    .height(QasaDimens.fieldHeight)
                    .background(c.cardBackground, shape)
                    .border(QasaDimens.stroke, c.stroke, shape)
                    .padding(horizontal = QasaDimens.gapSmall),
                contentAlignment = Alignment.Center,
            ) {
                Text(dialCode, color = c.accent, style = qasaText.rowTitle, textAlign = TextAlign.Center)
            }
            Spacer(Modifier.width(QasaDimens.contentGap))
            ThemedTextField(
                value = value,
                onValueChange = { touch(); onChange(it) },
                placeholder = label,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                modifier = Modifier.weight(1f),
            )
        }
    }
}
