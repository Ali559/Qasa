package alibarznji.qasa.theme

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

/*
 * Press feedback. iOS defines no `ButtonStyle`, so its rows, pickers and switches get the
 * plain system highlight and never shrink. These wrappers therefore default to the ripple
 * alone; [pressScale] opts a button-like surface into the 0.97 house scale (§8.3).
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
    val scale = rememberPressScale(interaction, enabled = pressScale)
    Box(
        modifier = modifier
            .scale(scale)
            .clickable(
                interactionSource = interaction,
                indication = LocalIndication.current,
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
    val scale = rememberPressScale(interaction, enabled = pressScale)
    Box(
        modifier = modifier
            .scale(scale)
            .selectable(
                selected = selected,
                interactionSource = interaction,
                indication = LocalIndication.current,
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
    val scale = rememberPressScale(interaction, enabled = pressScale)
    Box(
        modifier = modifier
            .scale(scale)
            .toggleable(
                value = checked,
                interactionSource = interaction,
                indication = LocalIndication.current,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = onCheckedChange,
            )
            .semanticsRoleButton(contentDescription),
        content = { content() },
    )
}

/** Marks the node as a button and applies an optional localized icon label. */
internal fun Modifier.semanticsRoleButton(contentDescription: String?): Modifier =
    this.then(
        semantics {
            if (contentDescription != null) this.contentDescription = contentDescription
        },
    )
