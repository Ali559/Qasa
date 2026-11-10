package alibarznji.qasa.theme

import alibarznji.qasa.R
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow

/**
 * The 1dp stroke every outlined surface in the app renders. `Modifier.border` draws
 * centered on the bounds, so background must always be painted first (§2.3).
 */
private val StrokeWidth = QasaDimens.stroke

/**
 * The one frame every single-line input draws: exactly [QasaDimens.fieldHeight] tall,
 * `cardBackground` fill, 1dp border (`accent` while focused), `cornerRadius`, an optional
 * 20dp leading icon and a trailing slot for [FieldIconButton]s. Content is centered
 * vertically, so the 48dp height never clips the text.
 *
 * Also used by fields that are not text inputs (a dropdown shown as a field, a dial-code
 * chip) so they line up with the inputs beside them.
 */
@Composable
fun QasaFieldFrame(
    modifier: Modifier = Modifier,
    focused: Boolean = false,
    leadingIcon: ImageVector? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val c = qasaColors
    val shape = RoundedCornerShape(QasaDimens.cornerRadius)
    val border = animateQasaColor(if (focused) c.accent else c.stroke)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(QasaDimens.fieldHeight)
            .background(c.cardBackground, shape)
            .border(StrokeWidth, border, shape)
            .padding(
                start = QasaDimens.gap,
                // Trailing icon buttons bring their own 10dp of inner space.
                end = if (trailing != null) QasaDimens.spaceSmall else QasaDimens.gap,
            ),
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
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            content()
        }
        trailing?.invoke(this)
    }
}

/** A 40dp icon button sized to sit inside a [QasaFieldFrame] trailing slot. */
@Composable
fun FieldIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    tint: androidx.compose.ui.graphics.Color = qasaColors.textSecondary,
) {
    IconButton(onClick = onClick, modifier = Modifier.size(QasaDimens.fieldIconButton)) {
        Icon(icon, contentDescription = contentDescription, tint = tint, modifier = Modifier.size(QasaIconSize.xl))
    }
}

/** Placeholder text, single line, in the field's text style. */
@Composable
private fun FieldPlaceholder(text: String) {
    Text(
        text,
        color = qasaColors.textPlaceholder,
        style = qasaText.bodyLarge,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/**
 * `ThemedTextField` — `Components.swift:131`, drawn in a [QasaFieldFrame]: one fixed 48dp
 * height for plain, secure and search fields alike. Secure fields get the eye toggle; extra
 * trailing actions (the editor's generate wand) go in [trailing].
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
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    val c = qasaColors
    val imeController = LocalSoftwareKeyboardController.current
    // Soft-keyboard typing comes through the IME connection, not as touches or key events.
    val activity = LocalUserActivity.current
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val trailingSlot: (@Composable RowScope.() -> Unit)? =
        if (onToggleVisibility == null && trailing == null) {
            null
        } else {
            {
                if (onToggleVisibility != null) {
                    FieldIconButton(
                        icon = if (passwordVisible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                        contentDescription = stringResource(
                            if (passwordVisible) R.string.a11y_hidepassword else R.string.a11y_showpassword,
                        ),
                        onClick = {
                            imeController?.hide()
                            onToggleVisibility()
                        },
                    )
                }
                trailing?.invoke(this)
            }
        }
    QasaFieldFrame(modifier, focused = focused, leadingIcon = leadingIcon, trailing = trailingSlot) {
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
                PasswordVisualTransformation()
            } else {
                VisualTransformation.None
            },
            keyboardOptions = keyboardOptions.copy(imeAction = imeAction),
            keyboardActions = keyboardActions,
            interactionSource = interaction,
            modifier = Modifier
                .fillMaxWidth()
                .semanticsFieldLabel(label),
            decorationBox = { inner ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty()) FieldPlaceholder(placeholder)
                    inner()
                }
            },
        )
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
    val activity = LocalUserActivity.current
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    QasaFieldFrame(modifier, focused = focused) {
        BasicTextField(
            value = value,
            onValueChange = {
                activity.noteActivity()
                onValueChange(it)
            },
            singleLine = true,
            textStyle = qasaText.bodyLarge.copy(color = c.textPrimary),
            cursorBrush = SolidColor(c.accent),
            keyboardOptions = keyboardOptions,
            interactionSource = interaction,
            modifier = Modifier
                .fillMaxWidth()
                .semanticsFieldLabel(label),
            decorationBox = { inner ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (value.text.isEmpty()) FieldPlaceholder(placeholder)
                    inner()
                }
            },
        )
    }
}

/** Attaches the field name as the accessibility label. Android has no native
 *  field label for `BasicTextField`, and the visible placeholder is a sibling node. */
private fun Modifier.semanticsFieldLabel(label: String): Modifier =
    this.then(semantics { contentDescription = label })
