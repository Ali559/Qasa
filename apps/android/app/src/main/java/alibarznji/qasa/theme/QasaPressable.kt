package alibarznji.qasa.theme

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

/*
 * Press feedback, as iOS draws it (no Material ripple):
 * - rows, pickers, switches (`RowButtonStyle`): a `textPrimary @ 6%` wash, never a scale;
 * - [pressScale] surfaces (`PressableStyle`, card CTAs): opacity 0.85 and scale 0.98.
 * Both over `motion.press` (120ms ease-out).
 *
 * The interaction source is handed to `clickable`/`selectable`/`toggleable` itself.
 * Before, it was created but never wired in, so `rememberPressScale` never saw a press.
 */

/** A tappable surface; for list rows (no scale) and card CTAs (`pressScale = true`). */
@Composable
fun QasaPressable(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    role: Role? = Role.Button,
    contentDescription: String? = null,
    pressScale: Boolean = false,
    content: @Composable () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .qasaPressFeedback(interaction, pressScale, enabled)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = role,
                onClick = onClick,
            )
            .semanticsRoleButton(contentDescription),
        content = { content() },
    )
}

/** `selectable` surface; for list rows and pickers, which iOS never scales. */
@Composable
fun QasaSelectable(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    role: Role = Role.Tab,
    contentDescription: String? = null,
    pressScale: Boolean = false,
    content: @Composable () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .qasaPressFeedback(interaction, pressScale, enabled = true)
            .selectable(
                selected = selected,
                interactionSource = interaction,
                indication = null,
                role = role,
                onClick = onClick,
            )
            .semanticsRoleButton(contentDescription),
        content = { content() },
    )
}

/** `toggleable` surface; for switches. */
@Composable
fun QasaToggleable(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentDescription: String? = null,
    pressScale: Boolean = false,
    content: @Composable () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .qasaPressFeedback(interaction, pressScale, enabled)
            .toggleable(
                value = checked,
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = onCheckedChange,
            )
            .semanticsRoleButton(contentDescription),
        content = { content() },
    )
}

/**
 * The iOS press look for [interaction]: with [pressScale], `PressableStyle` (scale 0.98,
 * opacity 0.85, 0.45 when disabled); otherwise the `RowButtonStyle` wash.
 */
@Composable
private fun Modifier.qasaPressFeedback(
    interaction: MutableInteractionSource,
    pressScale: Boolean,
    enabled: Boolean,
): Modifier {
    if (pressScale) {
        val scale = rememberPressScale(interaction, enabled = enabled)
        val alpha = rememberPressOpacity(interaction, enabled = enabled)
        return this.graphicsLayer {
            scaleX = scale
            scaleY = scale
            this.alpha = alpha
        }
    }
    val c = qasaColors
    val pressed by interaction.collectIsPressedAsState()
    val wash by animateFloatAsState(
        if (pressed && enabled) QasaMotion.ROW_PRESSED_OPACITY else 0f,
        QasaMotion.pressTween(),
        label = "qasaPressWash",
    )
    return this.drawBehind {
        if (wash > 0f) {
            val r = QasaDimens.rowPressRadius.toPx()
            drawRoundRect(c.textPrimary.copy(alpha = wash), cornerRadius = CornerRadius(r, r))
        }
    }
}

/** Marks the node as a button and applies an optional localized icon label. */
internal fun Modifier.semanticsRoleButton(contentDescription: String?): Modifier =
    this.then(
        semantics {
            if (contentDescription != null) this.contentDescription = contentDescription
        },
    )
