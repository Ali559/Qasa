package alibarznji.qasa.theme

import alibarznji.qasa.R
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

/**
 * The 1dp stroke every outlined surface in the app renders. `Modifier.border` draws
 * centered on the bounds, so background must always be painted first (§2.3).
 */
private val StrokeWidth = QasaDimens.stroke

/** `StrengthBar` — `Components.swift:167`. 3 segments on create-vault, 5 in the generator. */
@Composable
fun StrengthBar(
    segments: Int,
    filled: Int,
    color: Color,
    modifier: Modifier = Modifier,
    height: androidx.compose.ui.unit.Dp = QasaDimens.gapTiny,
) {
    val c = qasaColors
    val description = stringResource(R.string.a11y_strengthbar, filled, segments)
    Row(
        modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = description
            },
        horizontalArrangement = Arrangement.spacedBy(QasaDimens.spaceMicro),
    ) {
        repeat(segments) { i ->
            Box(
                Modifier
                    .weight(1f)
                    .height(height)
                    .clip(RoundedCornerShape(50))
                    .background(if (i < filled) color else c.stroke),
            )
        }
    }
}

/**
 * `FilterChip` — `Components.swift:387`. 17/7 padding, capsule, 1pt border
 * (`accent` selected / `stroke` unselected), 14pt label semibold when selected.
 * Selected fill: `accent @ 16%` light, `accentDark` dark.
 *
 * Uses `Modifier.selectable` so TalkBack announces the selected state (§6.4).
 */
@Composable
fun FilterChipButton(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = qasaColors
    val isDark = qasaIsDarkScheme
    val shape = RoundedCornerShape(50)
    val bg = when {
        !selected -> c.cardBackground
        isDark -> c.accentDark
        else -> c.accent.copy(alpha = 0.16f)
    }
    val fg = when {
        !selected -> c.textSecondary
        isDark -> c.onAccentChip
        else -> c.accentDark
    }
    Box(
        modifier = modifier
            .defaultMinSize(minHeight = QasaDimens.touchTarget)
            .clip(shape)
            .background(bg)
            .border(StrokeWidth, if (selected) c.accent else c.stroke, shape)
            .selectable(
                selected = selected,
                role = Role.Tab,
                onClick = onClick,
            )
            .padding(horizontal = QasaDimens.cardPadding, vertical = QasaDimens.planDot),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            title,
            color = fg,
            style = if (selected) qasaText.bodyMediumSemibold else qasaText.bodyMedium,
        )
    }
}

@Composable
fun MintSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit, enabled: Boolean = true) {
    val c = qasaColors
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        enabled = enabled,
        colors = SwitchDefaults.colors(
            checkedThumbColor = c.onAccent,
            checkedTrackColor = c.accent,
            uncheckedThumbColor = c.cardBackground,
            uncheckedTrackColor = c.stroke,
        ),
    )
}

@Composable
fun QasaSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
) {
    val c = qasaColors
    Slider(
        value = value,
        onValueChange = onValueChange,
        valueRange = valueRange,
        colors = SliderDefaults.colors(
            thumbColor = c.accent,
            activeTrackColor = c.accent,
            inactiveTrackColor = c.stroke,
        ),
    )
}
