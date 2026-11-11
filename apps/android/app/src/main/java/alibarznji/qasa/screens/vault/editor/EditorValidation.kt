package alibarznji.qasa.screens.vault.editor

import alibarznji.qasa.theme.QasaDimens
import alibarznji.qasa.theme.ThemedTextField
import alibarznji.qasa.theme.qasaColors
import alibarznji.qasa.theme.qasaText
import alibarznji.qasa.vault.VaultValidation
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import uniffi.qasa_ffi.FfiField
import uniffi.qasa_ffi.FfiProblem

/**
 * The editor's live field-rule state: the issues of the payload as it would be saved,
 * the fields the user has edited, and whether a Save was attempted (then every issue
 * shows). Field composables read it through [LocalEditorValidation].
 */
class EditorValidation(
    val issues: Map<FfiField, FfiProblem>,
    val touched: Set<FfiField>,
    val showAll: Boolean,
    val onTouch: (FfiField) -> Unit,
) {
    fun visible(field: FfiField): FfiProblem? = issues[field]?.takeIf { showAll || field in touched }
}

val LocalEditorValidation = staticCompositionLocalOf<EditorValidation?> { null }

/** The localized message for [field] when it should show, else null. */
@Composable
fun fieldError(field: FfiField?): String? {
    val problem = field?.let { LocalEditorValidation.current?.visible(it) } ?: return null
    return VaultValidation.message(problem, LocalContext.current.resources)
}

/** Records that the user edited [field], so its issue starts showing. */
@Composable
fun rememberTouch(field: FfiField?): () -> Unit {
    val validation = LocalEditorValidation.current
    return { if (field != null) validation?.onTouch?.invoke(field) }
}

/** The rule message under a field: danger colour, the date field's error style. */
@Composable
fun FieldErrorText(message: String?) {
    if (message == null) return
    // iOS `FormTextField`: label, field and message share one 8pt-spaced stack.
    Spacer(Modifier.height(QasaDimens.fieldLabelGap))
    Text(message, color = qasaColors.danger, style = qasaText.fieldError)
}

/**
 * A text field that reformats as you type ([format]: `formatCardNumber`,
 * `formatCardExpiry`) and keeps the cursor after the same digit it followed.
 */
@Composable
fun FormattedTextField(
    value: String,
    placeholder: String,
    label: String,
    format: (String) -> String,
    keyboardType: KeyboardType,
    isError: Boolean = false,
    onChange: (String) -> Unit,
) {
    var field by remember { mutableStateOf(TextFieldValue(value, TextRange(value.length))) }
    // The payload changed from outside (normalize on open, a reset): follow it.
    if (field.text != value) field = TextFieldValue(value, TextRange(value.length))
    ThemedTextField(
        value = field,
        onValueChange = { typed ->
            val next = reformat(field, typed, format)
            field = next
            if (next.text != value) onChange(next.text)
        },
        placeholder = placeholder,
        label = label,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        isError = isError,
    )
}

/**
 * [typed] formatted, with the cursor after as many digits as preceded it in [typed]. A
 * backspace over a separator (which formatting would put straight back) removes the digit
 * before it instead.
 */
internal fun reformat(previous: TextFieldValue, typed: TextFieldValue, format: (String) -> String): TextFieldValue {
    if (typed.text == previous.text) return typed // the cursor or selection moved
    val atEnd = typed.selection.end >= typed.text.length
    var digitsBefore = typed.text.take(typed.selection.end).count(Char::isDigit)
    var formatted = format(typed.text)
    if (formatted == previous.text && typed.text.length < previous.text.length && digitsBefore > 0) {
        val digits = typed.text.filter(Char::isDigit)
        formatted = format(digits.removeRange(digitsBefore - 1, digitsBefore))
        digitsBefore--
    }
    val cursor = if (atEnd) formatted.length else cursorAfterDigits(formatted, digitsBefore)
    return TextFieldValue(formatted, TextRange(cursor))
}

private fun cursorAfterDigits(text: String, digits: Int): Int {
    if (digits <= 0) return 0
    var seen = 0
    for ((i, ch) in text.withIndex()) {
        if (ch.isDigit() && ++seen == digits) return i + 1
    }
    return text.length
}
