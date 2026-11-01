package alibarznji.qasa.screens.vault.editor

import alibarznji.qasa.R
import alibarznji.qasa.theme.QasaDimens
import alibarznji.qasa.theme.QasaIconSize
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
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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

/** iOS `FormFieldLabel` + content + the 16pt `VStack` spacing of `VaultFormChrome`. */
@Composable
fun FieldBlock(label: String, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth()) {
        SectionLabel(label)
        Spacer(Modifier.height(QasaDimens.contentGap))
        content()
        Spacer(Modifier.height(QasaDimens.gap))
    }
}

/** iOS `FormTextField` (FormHelpers.swift:19); secure rows add an eye toggle. */
@Composable
fun FormTextField(
    @StringRes labelRes: Int,
    value: String,
    secure: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    onChange: (String) -> Unit,
) {
    val label = stringResource(labelRes)
    var visible by remember { mutableStateOf(false) }
    FieldBlock(label) {
        ThemedTextField(
            value = value,
            onValueChange = onChange,
            placeholder = label,
            isSecure = secure,
            passwordVisible = visible,
            onToggleVisibility = if (secure) ({ visible = !visible }) else null,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        )
    }
}

/** iOS `ValidatedDateField` in the editor's field rhythm. */
@Composable
fun FormDateField(@StringRes labelRes: Int, value: String, onChange: (String) -> Unit) {
    ValidatedDateField(title = stringResource(labelRes), text = value, onTextChange = onChange)
    Spacer(Modifier.height(QasaDimens.gap))
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
    FieldBlock(label) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ThemedTextField(
                value = value,
                onValueChange = onChange,
                placeholder = label,
                isSecure = true,
                passwordVisible = visible,
                onToggleVisibility = { visible = !visible },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(QasaDimens.spaceSmall))
            IconButton(onClick = onGenerate, modifier = Modifier.size(QasaDimens.touchTarget)) {
                Icon(
                    Icons.Default.AutoFixHigh,
                    contentDescription = stringResource(R.string.vault_generatepassword),
                    tint = c.accent,
                    modifier = Modifier.size(QasaIconSize.xl),
                )
            }
        }
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
    FieldBlock(label) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .widthIn(min = 64.dp)
                    .background(c.cardBackground, shape)
                    .border(QasaDimens.stroke, c.stroke, shape)
                    .padding(horizontal = QasaDimens.gapSmall, vertical = QasaDimens.gapXLarge),
                contentAlignment = Alignment.Center,
            ) {
                Text(dialCode, color = c.accent, style = qasaText.rowTitle, textAlign = TextAlign.Center)
            }
            Spacer(Modifier.width(QasaDimens.contentGap))
            ThemedTextField(
                value = value,
                onValueChange = onChange,
                placeholder = label,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                modifier = Modifier.weight(1f),
            )
        }
    }
}
