package alibarznji.qasa.theme

import alibarznji.qasa.R
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The 1dp stroke every outlined surface in the app renders. `Modifier.border` draws
 * centered on the bounds, so background must always be painted first (§2.3).
 */
private val StrokeWidth = QasaDimens.stroke

/**
 * iOS `QasaFieldChrome` (`Controls.swift`): the one frame every single-line input draws.
 * Exactly [QasaDimens.fieldHeight] tall, `cardBackground` fill, 1dp `stroke` border
 * (`danger` when [isError]; focus does not recolor it, as on iOS), [QasaDimens.controlRadius]
 * corners, [QasaDimens.fieldHorizontal] padding on both sides, an optional 20dp leading icon
 * 12dp before the text, and a trailing slot for [FieldIconButton]s. Content is centered
 * vertically, so the 48dp height never clips the text.
 *
 * Also used by fields that are not text inputs (a dropdown shown as a field, a dial-code
 * chip) so they line up with the inputs beside them.
 */
@Composable
fun QasaFieldFrame(
    modifier: Modifier = Modifier,
    @Suppress("UNUSED_PARAMETER") focused: Boolean = false,
    leadingIcon: ImageVector? = null,
    isError: Boolean = false,
    horizontalPadding: Dp = QasaDimens.fieldHorizontal,
    trailing: (@Composable RowScope.() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val c = qasaColors
    val shape = RoundedCornerShape(QasaDimens.controlRadius)
    val border = animateQasaColor(if (isError) c.danger else c.stroke)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(QasaDimens.fieldHeight)
            // Fill first, then the stroke on top (iOS `.background` + `.overlay(stroke)`).
            .background(c.cardBackground, shape)
            .border(StrokeWidth, border, shape)
            .padding(horizontal = horizontalPadding),
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

/**
 * iOS `FieldIconButton`: a [QasaDimens.fieldIconButton] (32) square with a 16dp glyph,
 * `textSecondary` unless tinted, plain press (dims, no ripple, no 48dp layout inflation,
 * so it fits the 48dp field like on iOS).
 */
@Composable
fun FieldIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    tint: androidx.compose.ui.graphics.Color = qasaColors.textSecondary,
) {
    val interaction = remember { MutableInteractionSource() }
    val alpha = rememberPressOpacity(interaction)
    Box(
        modifier = Modifier
            .size(QasaDimens.fieldIconButton)
            .alpha(alpha)
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(QasaDimens.fieldIconGlyph))
    }
}

/** Placeholder text, single line, in the field's text style. */
@Composable
private fun FieldPlaceholder(text: String) {
    Text(
        text,
        color = qasaColors.textPlaceholder,
        style = qasaText.field,
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
    isError: Boolean = false,
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
    QasaFieldFrame(modifier, focused = focused, leadingIcon = leadingIcon, isError = isError, trailing = trailingSlot) {
        BasicTextField(
            value = value,
            onValueChange = {
                activity.noteActivity()
                onValueChange(it)
            },
            singleLine = true,
            textStyle = qasaText.field.copy(color = c.textPrimary),
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
    isError: Boolean = false,
) {
    val c = qasaColors
    val activity = LocalUserActivity.current
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    QasaFieldFrame(modifier, focused = focused, isError = isError) {
        BasicTextField(
            value = value,
            onValueChange = {
                activity.noteActivity()
                onValueChange(it)
            },
            singleLine = true,
            textStyle = qasaText.field.copy(color = c.textPrimary),
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

/**
 * iOS `FormTextField(multiline: true)` in `QasaNotesChrome` (`Controls.swift`): a multi-line
 * notes box at least [QasaDimens.notesMinHeight] (112) tall that grows with its text. Same
 * fill, 1dp stroke (`danger` when [isError]) and [QasaDimens.controlRadius] as a field;
 * padding [QasaDimens.notesHorizontal] × [QasaDimens.notesVertical] plus the text view's own
 * [NotesTextInset] above and below; 4 to 12 lines (`lineLimit(4...12)`), then it scrolls.
 */
@Composable
fun QasaNotesField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    label: String = placeholder,
    isError: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
) {
    val c = qasaColors
    val activity = LocalUserActivity.current
    val shape = RoundedCornerShape(QasaDimens.controlRadius)
    val border = animateQasaColor(if (isError) c.danger else c.stroke)
    BasicTextField(
        value = value,
        onValueChange = {
            activity.noteActivity()
            onValueChange(it)
        },
        singleLine = false,
        minLines = NOTES_MIN_LINES,
        maxLines = NOTES_MAX_LINES,
        textStyle = qasaText.field.copy(color = c.textPrimary),
        cursorBrush = SolidColor(c.accent),
        keyboardOptions = keyboardOptions,
        modifier = modifier
            .fillMaxWidth()
            .semanticsFieldLabel(label),
        decorationBox = { inner ->
            Box(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = QasaDimens.notesMinHeight)
                    .background(c.cardBackground, shape)
                    .border(StrokeWidth, border, shape)
                    .padding(horizontal = QasaDimens.notesHorizontal, vertical = QasaDimens.notesVertical)
                    .padding(vertical = NotesTextInset),
                contentAlignment = Alignment.TopStart,
            ) {
                if (value.isEmpty()) {
                    Text(placeholder, color = c.textPlaceholder, style = qasaText.field)
                }
                inner()
            }
        },
    )
}

/** iOS `FormTextField` pads its vertical `TextField` by this inside the notes chrome. */
private val NotesTextInset = QasaTokens.Space.notesTextInset

/** iOS `lineLimit(4...12)`. */
private const val NOTES_MIN_LINES = 4
private const val NOTES_MAX_LINES = 12

/** Attaches the field name as the accessibility label. Android has no native
 *  field label for `BasicTextField`, and the visible placeholder is a sibling node. */
private fun Modifier.semanticsFieldLabel(label: String): Modifier =
    this.then(semantics { contentDescription = label })
