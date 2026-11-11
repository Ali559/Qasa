package alibarznji.qasa.theme

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp

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
 * Used by [ThemedTextField] (search fields, label-less prompt sheets); labelled fields are
 * [QasaFieldGroup] rows.
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
 * so it fits a 48dp field or a group row like on iOS).
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
 * `ThemedTextField` — `Components.swift:131`, drawn in a [QasaFieldFrame]: the plain 48dp
 * box with a placeholder. Only for fields rule 4 leaves ungrouped: search fields and
 * one-field prompt sheets with no label. Every labelled field is a row in a
 * [QasaFieldGroup] instead ([QasaTextRow], [QasaSecureRow], …).
 *
 * `label` defaults to the placeholder: Android's placeholder is a sibling `Text`, so
 * without it TalkBack announces typed characters and never the field name (§9.7).
 */
@Composable
fun ThemedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    label: String = placeholder,
    leadingIcon: ImageVector? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    imeAction: ImeAction = ImeAction.Default,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    val c = qasaColors
    // Soft-keyboard typing comes through the IME connection, not as touches or key events.
    val activity = LocalUserActivity.current
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    QasaFieldFrame(modifier, focused = focused, leadingIcon = leadingIcon, trailing = trailing) {
        BasicTextField(
            value = value,
            onValueChange = {
                activity.noteActivity()
                onValueChange(it)
            },
            singleLine = true,
            textStyle = qasaText.field.copy(color = c.textPrimary),
            cursorBrush = SolidColor(c.accent),
            keyboardOptions = keyboardOptions.copy(imeAction = imeAction),
            keyboardActions = keyboardActions,
            interactionSource = interaction,
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = label },
            decorationBox = { inner ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty()) FieldPlaceholder(placeholder)
                    inner()
                }
            },
        )
    }
}
