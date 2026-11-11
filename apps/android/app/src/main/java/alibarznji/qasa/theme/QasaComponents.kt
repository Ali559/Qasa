package alibarznji.qasa.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp

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

/** iOS `QasaButtonKind`. */
enum class QasaButtonKind {
    /** Accent fill: the one main action of a page. */
    Primary,

    /** Card fill with a stroke: the alternative action. */
    Secondary,

    /** Danger fill: an irreversible action. */
    Destructive,

    /** Quiet accent text, still [QasaDimens.buttonHeight] tall. */
    Plain,
}

/**
 * iOS `QasaButtonStyle` (`Controls.swift`): exactly [QasaDimens.buttonHeight] tall,
 * [QasaDimens.controlRadius] corners, [QasaDimens.buttonHorizontal] side padding, label
 * `type.button` (16 semibold) on one line, optional 15dp leading icon 8dp before it.
 * Pressed: opacity 0.85 and scale 0.98 over `motion.press`; disabled: opacity 0.45. No
 * ripple, no shadow, no M3 minimum size: the same pixels as iOS.
 *
 * [labelColor] overrides the kind's label color (the Android-only red secondary action).
 */
@Composable
fun QasaButton(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    kind: QasaButtonKind = QasaButtonKind.Primary,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    fullWidth: Boolean = true,
    labelColor: Color? = null,
) {
    val c = qasaColors
    val shape = RoundedCornerShape(QasaDimens.controlRadius)
    val interaction = remember { MutableInteractionSource() }
    val scale = rememberPressScale(interaction, enabled = enabled)
    val alpha = rememberPressOpacity(interaction, enabled = enabled)
    val fill = when (kind) {
        QasaButtonKind.Primary -> c.accent
        QasaButtonKind.Secondary -> c.cardBackground
        QasaButtonKind.Destructive -> c.danger
        QasaButtonKind.Plain -> Color.Transparent
    }
    val label = labelColor ?: when (kind) {
        QasaButtonKind.Primary -> c.onAccent
        QasaButtonKind.Secondary -> c.textPrimary
        // iOS draws the destructive label in plain white in both schemes.
        QasaButtonKind.Destructive -> Color.White
        QasaButtonKind.Plain -> c.accent
    }
    Row(
        modifier = modifier
            .then(if (fullWidth) Modifier.fillMaxWidth() else Modifier)
            .height(QasaDimens.buttonHeight)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                this.alpha = alpha
            }
            .background(fill, shape)
            .then(if (kind == QasaButtonKind.Secondary) Modifier.border(StrokeWidth, c.stroke, shape) else Modifier)
            .clip(shape)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = QasaDimens.buttonHorizontal),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = label, modifier = Modifier.size(QasaDimens.buttonIconGlyph))
            Spacer(Modifier.width(QasaDimens.buttonIconGap))
        }
        Text(title, color = label, style = qasaText.button, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * `PrimaryButton` — `Components.swift`: a full-width [QasaButton], `danger` fill when
 * [destructive]. Disabled callers pass `enabled = false`, never hand-roll an opacity.
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
    QasaButton(
        title = title,
        onClick = onClick,
        modifier = modifier,
        kind = if (destructive) QasaButtonKind.Destructive else QasaButtonKind.Primary,
        enabled = enabled,
        icon = icon,
    )
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
    padding: Dp = QasaDimens.cardPadding,
    cornerRadius: Dp = QasaDimens.cornerRadius,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = qasaColors
    val shape = RoundedCornerShape(cornerRadius)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(background ?: c.cardBackground, shape)
            .border(StrokeWidth, borderColor ?: c.stroke, shape)
            .padding(padding),
        content = content,
    )
}

/** `SectionLabel` — `Components.swift`: `type.sectionLabel` (12 semibold), `textSecondary`, every language. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    val c = qasaColors
    Text(
        text = qasaText.label(text),
        color = c.textSecondary,
        style = qasaText.tokenSectionLabel,
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
    icon: ImageVector = QasaIcons.Add,
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

/**
 * `.qasaSecondary` — a full-width [QasaButton] of kind Secondary: `cardBackground` fill,
 * 1dp `stroke`, `textPrimary` label; `danger` label when [destructive] (Android-only).
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
    QasaButton(
        title = title,
        onClick = onClick,
        modifier = modifier,
        kind = QasaButtonKind.Secondary,
        enabled = enabled,
        icon = icon,
        labelColor = if (destructive) qasaColors.danger else null,
    )
}

/**
 * A quiet red text action (not full width): the plain kind's press and disabled states,
 * `danger` label, at least a 48dp target.
 */
@Composable
fun DestructiveTextButton(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    QasaButton(
        title = title,
        onClick = onClick,
        modifier = modifier,
        kind = QasaButtonKind.Plain,
        enabled = enabled,
        fullWidth = false,
        labelColor = qasaColors.danger,
    )
}

// ---------------------------------------------------------------------------
// Small helpers
// ---------------------------------------------------------------------------

/** Animates a color so state changes read as transitions rather than cuts. */
@Composable
fun animateQasaColor(target: Color): Color =
    animateColorAsState(target, QasaMotion.fastTween(), label = "qasaColor").value
