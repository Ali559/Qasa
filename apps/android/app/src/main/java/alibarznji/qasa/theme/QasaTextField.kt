package alibarznji.qasa.theme

import alibarznji.qasa.R
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue

/**
 * The 1dp stroke every outlined surface in the app renders. `Modifier.border` draws
 * centered on the bounds, so background must always be painted first (§2.3).
 */
private val StrokeWidth = QasaDimens.stroke

/**
 * `ThemedTextField` — `Components.swift:131`. 16/18 padding, `cardBackground` fill,
 * 1pt `stroke` border, `cornerRadius`, optional 20pt leading icon at a 12pt gap,
 * mint cursor, 16pt text.
 *
 * `label` is not optional: Android's placeholder is a sibling `Text`, so without it
 * TalkBack announces typed characters and never the field name (§9.7).
 */
@Composable
fun ThemedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    label: String = placeholder,
    leadingIcon: ImageVector? = null,
    isSecure: Boolean = false,
    passwordVisible: Boolean = false,
    onToggleVisibility: (() -> Unit)? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    imeAction: ImeAction = ImeAction.Default,
) {
    val c = qasaColors
    val shape = RoundedCornerShape(QasaDimens.cornerRadius)
    val imeController = LocalSoftwareKeyboardController.current
    // Soft-keyboard typing comes through the IME connection, not as touches or key events.
    val activity = LocalUserActivity.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(c.cardBackground, shape)
            .border(StrokeWidth, c.stroke, shape)
            .padding(horizontal = QasaDimens.gap, vertical = QasaDimens.gapXLarge),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leadingIcon != null) {
            Icon(
                leadingIcon,
                contentDescription = null,
                tint = c.textSecondary,
                modifier = Modifier.size(QasaIconSize.xl),
            )
            Spacer(Modifier.width(QasaDimens.gapSmall))
        }
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) {
                Text(placeholder, color = c.textPlaceholder, style = qasaText.bodyLarge)
            }
            BasicTextField(
                value = value,
                onValueChange = {
                    activity.noteActivity()
                    onValueChange(it)
                },
                singleLine = true,
                textStyle = qasaText.bodyLarge.copy(color = c.textPrimary),
                cursorBrush = SolidColor(c.accent),
                visualTransformation = if (isSecure && !passwordVisible) {
                    androidx.compose.ui.text.input.PasswordVisualTransformation()
                } else {
                    androidx.compose.ui.text.input.VisualTransformation.None
                },
                keyboardOptions = keyboardOptions.copy(imeAction = imeAction),
                keyboardActions = keyboardActions,
                modifier = Modifier
                    .fillMaxWidth()
                    .semanticsFieldLabel(label),
            )
        }
        if (onToggleVisibility != null) {
            IconButton(
                onClick = {
                    imeController?.hide()
                    onToggleVisibility()
                },
                modifier = Modifier.size(QasaDimens.touchTarget),
            ) {
                Icon(
                    if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                    contentDescription = stringResource(
                        if (passwordVisible) R.string.a11y_hidepassword else R.string.a11y_showpassword,
                    ),
                    tint = c.textSecondary,
                    modifier = Modifier.size(QasaIconSize.xl),
                )
            }
        }
    }
}

/**
 * [ThemedTextField] over a [TextFieldValue], for fields that reformat what is typed (card
 * number, card expiry) and so must place the cursor themselves.
 */
@Composable
fun ThemedTextField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    label: String = placeholder,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
) {
    val c = qasaColors
    val shape = RoundedCornerShape(QasaDimens.cornerRadius)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(c.cardBackground, shape)
            .border(StrokeWidth, c.stroke, shape)
            .padding(horizontal = QasaDimens.gap, vertical = QasaDimens.gapXLarge),
    ) {
        if (value.text.isEmpty()) {
            Text(placeholder, color = c.textPlaceholder, style = qasaText.bodyLarge)
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = qasaText.bodyLarge.copy(color = c.textPrimary),
            cursorBrush = SolidColor(c.accent),
            keyboardOptions = keyboardOptions,
            modifier = Modifier
                .fillMaxWidth()
                .semanticsFieldLabel(label),
        )
    }
}

/** Attaches the field name as the accessibility label. Android has no native
 *  field label for `BasicTextField`, and the visible placeholder is a sibling node. */
private fun Modifier.semanticsFieldLabel(label: String): Modifier =
    this.then(semantics { contentDescription = label })
