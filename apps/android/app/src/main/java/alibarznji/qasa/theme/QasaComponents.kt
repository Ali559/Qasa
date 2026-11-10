package alibarznji.qasa.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow

/*
 * Buttons, surfaces and labels from `Components.swift`. Inputs and toggles live in
 * QasaControls.kt / QasaTextField.kt, tappable wrappers in QasaPressable.kt (split to stay
 * under the 400-line cap; names and package unchanged).
 */

/**
 * The 1dp stroke every outlined surface in the app renders. `Modifier.border` draws
 * centered on the bounds, so background must always be painted first (§2.3).
 */
private val StrokeWidth = QasaDimens.stroke

/**
 * Status / offline pill — `Components.swift:34`. 13/5 padding, capsule,
 * 1pt `strokeChrome` border, `cardBackgroundLight` fill, 12 mono + 0.6 tracking,
 * `textOnChrome`, optional 8x8 leading dot.
 */
@Composable
fun StatusPill(
    text: String,
    modifier: Modifier = Modifier,
    dotColor: Color? = null,
    textColor: Color? = null,
    background: Color? = null,
) {
    val c = qasaColors
    val shape = RoundedCornerShape(50)
    Row(
        modifier = modifier
            .background(background ?: c.cardBackgroundLight, shape)
            .border(StrokeWidth, c.strokeChrome, shape)
            .padding(horizontal = QasaDimens.padTight, vertical = QasaDimens.spaceNano),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (dotColor != null) {
            Box(
                Modifier
                    .size(QasaIconSize.xs)
                    .clip(CircleShape)
                    .background(dotColor),
            )
            Spacer(Modifier.width(QasaDimens.contentGap))
        }
        Text(
            text,
            color = textColor ?: c.textOnChrome,
            style = qasaText.sectionLabel,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
    }
}

/**
 * `PrimaryButton` — `Components.swift:64`. Full width, exactly [QasaDimens.buttonHeight]
 * tall, `cornerRadius`, `accent` fill (`danger` when [destructive]), `onAccent` label at
 * 17 semibold, optional 16 leading icon, a soft accent glow while enabled.
 *
 * Disabled callers must pass `enabled = false`, never hand-roll an opacity (§6.2).
 */
@Composable
fun PrimaryButton(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    destructive: Boolean = false,
) {
    val c = qasaColors
    val fill = if (destructive) c.danger else c.accent
    val shape = RoundedCornerShape(QasaDimens.cornerRadius)
    val interaction = remember { MutableInteractionSource() }
    val scale = rememberPressScale(interaction)
    Button(
        onClick = onClick,
        enabled = enabled,
        interactionSource = interaction,
        modifier = modifier
            .fillMaxWidth()
            .height(QasaDimens.buttonHeight)
            .scale(scale)
            .then(
                if (enabled) {
                    Modifier.shadow(
                        QasaDimens.contentGap,
                        shape,
                        ambientColor = fill.copy(alpha = 0.3f),
                        spotColor = fill.copy(alpha = 0.3f),
                    )
                } else {
                    Modifier
                },
            ),
        shape = shape,
        colors = ButtonDefaults.buttonColors(
            containerColor = fill,
            contentColor = c.onAccent,
            disabledContainerColor = fill.copy(alpha = 0.4f),
            disabledContentColor = c.onAccent.copy(alpha = 0.75f),
        ),
        contentPadding = ButtonContentPadding,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(QasaIconSize.md))
            Spacer(Modifier.width(QasaDimens.contentGap))
        }
        Text(title, style = qasaText.ctaLabel, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * `CardContainer` — `Components.swift:96`. Padding 17, `cardBackground` fill,
 * 1pt `stroke` border, `cornerRadius`, `fillMaxWidth`.
 *
 * Background is painted before the border so the full 1dp is visible; the previous
 * order rendered every card at ~0.5dp app-wide (§2.3).
 */
@Composable
fun CardContainer(
    modifier: Modifier = Modifier,
    background: Color? = null,
    borderColor: Color? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = qasaColors
    val shape = RoundedCornerShape(QasaDimens.cornerRadius)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(background ?: c.cardBackground, shape)
            .border(StrokeWidth, borderColor ?: c.stroke, shape)
            .padding(QasaDimens.cardPadding),
        content = content,
    )
}

/**
 * `SectionLabel` — `Components.swift:119`. Uppercased for `en` only; mono + 0.6
 * tracking for `en`, semibold + 0 tracking for ckb/ar; `textSecondary`.
 *
 * The mono/uppercase decision lives in [QasaText], not at 5 copy-pasted ternaries.
 */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    val c = qasaColors
    Text(
        text = qasaText.label(text),
        color = c.textSecondary,
        style = if (qasaText.arabicScript) qasaText.sectionLabelSemibold else qasaText.sectionLabel,
        modifier = modifier,
    )
}

/**
 * `MintFab` — `Components.swift:428`. 56x56, `cornerRadiusLarge`, `accent` fill,
 * `onAccent` glyph at 18 bold, 1pt `accent @ 50%` border, black-20% shadow at
 * radius 8 / y 4.
 *
 * Android used M3's `elevation(QasaDimens.contentGap)`, which tonal-tints instead of casting a black
 * shadow, and omitted the border entirely (§4.3).
 */
@Composable
fun MintFab(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Default.Add,
    contentDescription: String? = null,
) {
    val c = qasaColors
    val shape = RoundedCornerShape(QasaDimens.cornerRadiusLarge)
    val interaction = remember { MutableInteractionSource() }
    val scale = rememberPressScale(interaction)
    Box(
        modifier = modifier
            .size(QasaIconFrame.fab)
            .scale(scale)
            .shadow(
                elevation = QasaDimens.contentGap,
                shape = shape,
                ambientColor = Color.Black.copy(alpha = 0.2f),
                spotColor = Color.Black.copy(alpha = 0.2f),
            )
            .background(c.accent, shape)
            .border(StrokeWidth, c.accent.copy(alpha = 0.5f), shape)
            .clip(shape)
            .clickable(
                interactionSource = interaction,
                indication = LocalIndication.current,
                onClick = onClick,
            )
            .semanticsRoleButton(contentDescription),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
            tint = c.onAccent,
            modifier = Modifier.size(QasaIconSize.lg),
        )
    }
}

/** No vertical padding: the fixed [QasaDimens.buttonHeight] centers the label. */
private val ButtonContentPadding = PaddingValues(horizontal = QasaDimens.gap)

/**
 * Android-only. Same [QasaDimens.buttonHeight] as [PrimaryButton], `cardBackgroundLight`,
 * 1pt `stroke` border, 16 semibold; `danger` label when [destructive].
 */
@Composable
fun SecondaryButton(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    destructive: Boolean = false,
    icon: ImageVector? = null,
) {
    val c = qasaColors
    val interaction = remember { MutableInteractionSource() }
    val scale = rememberPressScale(interaction)
    val content = if (destructive) c.danger else c.textPrimary
    Button(
        onClick = onClick,
        enabled = enabled,
        interactionSource = interaction,
        modifier = modifier
            .fillMaxWidth()
            .height(QasaDimens.buttonHeight)
            .scale(scale),
        shape = RoundedCornerShape(QasaDimens.cornerRadius),
        colors = ButtonDefaults.buttonColors(
            containerColor = c.cardBackgroundLight,
            contentColor = content,
            disabledContainerColor = c.cardBackgroundLight.copy(alpha = 0.6f),
            disabledContentColor = content.copy(alpha = 0.45f),
        ),
        border = BorderStroke(StrokeWidth, c.stroke),
        contentPadding = ButtonContentPadding,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(QasaIconSize.md))
            Spacer(Modifier.width(QasaDimens.contentGap))
        }
        Text(title, style = qasaText.rowTitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * Destructive action. Android-only on iOS; there it was a bare `Text` with no
 * indication at all. Now a `Button` with a role, a ripple, and a 48dp target.
 */
@Composable
fun DestructiveTextButton(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val c = qasaColors
    val interaction = remember { MutableInteractionSource() }
    val scale = rememberPressScale(interaction)
    Button(
        onClick = onClick,
        enabled = enabled,
        interactionSource = interaction,
        contentPadding = PaddingValues(horizontal = QasaDimens.contentGap),
        shape = RoundedCornerShape(QasaDimens.radiusSmall),
        colors = ButtonDefaults.textButtonColors(
            contentColor = c.danger,
            disabledContentColor = c.danger.copy(alpha = 0.45f),
        ),
        modifier = modifier
            .heightIn(min = QasaDimens.touchTarget)
            .scale(scale),
    ) {
        Text(title, style = qasaText.bodySemibold15)
    }
}

// ---------------------------------------------------------------------------
// Small helpers
// ---------------------------------------------------------------------------

/** Animates a color so state changes read as transitions rather than cuts. */
@Composable
fun animateQasaColor(target: Color): Color =
    animateColorAsState(target, tween(QasaMotion.FAST), label = "qasaColor").value
