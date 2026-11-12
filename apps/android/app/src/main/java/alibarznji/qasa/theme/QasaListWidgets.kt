package alibarznji.qasa.theme

import alibarznji.qasa.theme.QasaDimens
import alibarznji.qasa.theme.QasaIconFrame
import alibarznji.qasa.theme.QasaIconSize
import alibarznji.qasa.theme.qasaText
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.remember
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow

/**
 * iOS `IconBadge`: a [QasaDimens.rowIconBadge] (36) square, [QasaDimens.rowIconBadgeRadius]
 * (10) corners, `tint @ 12%` fill (or [background]), 16dp glyph.
 */
@Composable
fun RowIconBadge(icon: ImageVector, tint: Color, background: Color? = null) {
    val shape = RoundedCornerShape(QasaDimens.rowIconBadgeRadius)
    Box(
        modifier = Modifier
            .size(QasaDimens.rowIconBadge)
            .background(background ?: tint.copy(alpha = ICON_BADGE_FILL_ALPHA), shape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(QasaDimens.rowIconBadgeGlyph))
    }
}

/** iOS `IconBadge` fill: the tint at 12%. */
private const val ICON_BADGE_FILL_ALPHA = 0.12f

/**
 * iOS `SettingsRow` — the one row layout behind [NavChevronRow] and [ToggleRow]: at least
 * [QasaDimens.rowMinHeight] (60) tall with 10dp vertical padding, optional [RowIconBadge],
 * 14dp gaps, title `type.rowTitle` (15 medium) and subtitle `type.rowSubtitle` (12) 2dp
 * apart, then the [trailing] slot centered on the same line. Start/end only, so it mirrors
 * in RTL.
 */
@Composable
fun QasaRow(
    title: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    iconColor: Color? = null,
    iconBackground: Color? = null,
    subtitle: String? = null,
    titleColor: Color? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val c = qasaColors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = QasaDimens.rowMinHeight)
            .padding(vertical = QasaDimens.rowVertical),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            RowIconBadge(icon, tint = iconColor ?: c.accent, background = iconBackground)
            Spacer(Modifier.width(QasaDimens.rowIconGap))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
            Text(title, color = titleColor ?: c.textPrimary, style = qasaText.settingsRowTitle)
            if (!subtitle.isNullOrEmpty()) {
                Spacer(Modifier.height(QasaDimens.rowTextGap))
                Text(subtitle, color = c.textSecondary, style = qasaText.rowSubtitle)
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(QasaDimens.rowIconGap))
            trailing()
        }
    }
}

/** iOS `RowChevron`: a 13dp `textTertiary` forward chevron (mirrored in RTL). */
@Composable
fun RowChevron() {
    // The SF Symbol's own 11x14 frame: no size modifier.
    Icon(QasaIcons.ChevronForward, contentDescription = null, tint = qasaColors.textTertiary)
}

/** iOS `RowValue`: trailing value text, `type.rowValue` (13), `textSecondary`, one line. */
@Composable
fun RowValue(text: String) {
    Text(
        text,
        color = qasaColors.textSecondary,
        style = qasaText.rowValue,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/** iOS `RowDivider`: a 1dp `stroke` line inset to the row text (badge 36 + gap 14). */
@Composable
fun RowDivider(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .padding(start = QasaDimens.rowIconBadge + QasaDimens.rowIconGap)
            .height(QasaDimens.stroke)
            .background(qasaColors.stroke),
    )
}

/**
 * iOS `SettingsSection`: an optional [SectionLabel] (inset 4) 8dp above a
 * [CardContainer] with no padding of its own, its rows inset 14 × 4.
 */
@Composable
fun SettingsSection(
    modifier: Modifier = Modifier,
    title: String? = null,
    trailingTitle: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier.fillMaxWidth()) {
        if (title != null || trailingTitle != null) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = QasaDimens.spaceSmall),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (title != null) SectionLabel(title)
                Spacer(Modifier.weight(1f))
                trailingTitle?.invoke()
            }
            Spacer(Modifier.height(QasaDimens.sectionLabelGap))
        }
        CardContainer(padding = 0.dp) {
            Column(
                Modifier.padding(
                    horizontal = QasaDimens.sectionInsetHorizontal,
                    vertical = QasaDimens.sectionInsetVertical,
                ),
                content = content,
            )
        }
    }
}

/**
 * iOS `RowButtonStyle`: a tappable row whose press shows a soft `textPrimary @ 6%` wash
 * ([QasaDimens.rowPressRadius] corners, 8dp wider than the row on each side) over
 * `motion.press`, instead of a ripple. Never scales.
 */
@Composable
fun QasaRowPressable(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    role: Role? = Role.Button,
    content: @Composable () -> Unit,
) {
    val c = qasaColors
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val wash by animateFloatAsState(
        if (pressed && enabled) QasaMotion.ROW_PRESSED_OPACITY else 0f,
        QasaMotion.pressTween(),
        label = "qasaRowWash",
    )
    val shape = RoundedCornerShape(QasaDimens.rowPressRadius)
    Box(
        modifier = modifier
            .drawBehind {
                if (wash > 0f) {
                    val inset = ROW_WASH_OUTSET.toPx()
                    val radius = QasaDimens.rowPressRadius.toPx()
                    drawRoundRect(
                        color = c.textPrimary.copy(alpha = wash),
                        topLeft = androidx.compose.ui.geometry.Offset(-inset, 0f),
                        size = androidx.compose.ui.geometry.Size(size.width + inset * 2, size.height),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius, radius),
                    )
                }
            }
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = role,
                onClick = onClick,
            ),
    ) {
        content()
    }
}

/** iOS `RowButtonStyle` pads its wash by −8 on both sides. */
private val ROW_WASH_OUTSET = 8.dp

/**
 * `NavChevronRow` — `Components.swift`, on the shared [QasaRow]: badge, title, subtitle,
 * optional [RowValue], then [RowChevron] 8dp after it (hidden when destructive; the title
 * and badge take `danger` instead). Pressed: the [QasaRowPressable] wash.
 */
@Composable
fun NavChevronRow(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    subtitle: String? = null,
    trailingText: String? = null,
    destructive: Boolean = false,
    iconColor: Color? = null,
) {
    val c = qasaColors
    QasaRowPressable(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
    ) {
        QasaRow(
            title = title,
            icon = icon,
            iconColor = if (destructive) c.danger else iconColor,
            subtitle = subtitle,
            titleColor = if (destructive) c.danger else null,
            trailing = if (trailingText == null && destructive) {
                null
            } else {
                {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(QasaDimens.contentGap),
                    ) {
                        if (trailingText != null) RowValue(trailingText)
                        if (!destructive) RowChevron()
                    }
                }
            },
        )
    }
}

/**
 * `ToggleRow` — `Components.swift:219`, on the shared [QasaRow] with the switch as the
 * trailing slot. The whole row toggles (`Role.Switch`), so the target is the full row.
 */
@Composable
fun ToggleRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String = "",
    icon: ImageVector? = null,
    iconColor: Color? = null,
    iconBackground: Color? = null,
    enabled: Boolean = true,
) {
    QasaRow(
        title = title,
        modifier = modifier.toggleable(
            value = checked,
            enabled = enabled,
            role = Role.Switch,
            onValueChange = onCheckedChange,
        ),
        icon = icon,
        iconColor = iconColor,
        iconBackground = iconBackground,
        subtitle = subtitle,
        trailing = { MintSwitch(checked = checked, onCheckedChange = null, enabled = enabled) },
    )
}

/** `PillBadge` — `Components.swift:298`. 4pt radius, 8/2 padding, `@ 10%` fill + `@ 20%` border or solid. */
@Composable
fun PillBadge(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
    solid: Boolean = false,
) {
    val c = qasaColors
    val shape = RoundedCornerShape(QasaDimens.radiusTiny)
    Text(
        text,
        color = if (solid) c.onAccentChip else color,
        style = qasaText.sectionLabel,
        modifier = modifier
            .background(if (solid) color else color.copy(alpha = 0.1f), shape)
            .border(
                QasaDimens.stroke,
                if (solid) Color.Transparent else color.copy(alpha = 0.2f),
                shape,
            )
            .padding(horizontal = QasaDimens.contentGap, vertical = QasaDimens.spaceMicro),
    )
}

/**
 * `StatBox` — `Components.swift:186`. Icon 18, 8pt gap, uppercased mono-12 label
 * + 0.6 tracking, value 20 semibold with the unit 14 on the same baseline.
 */
@Composable
fun StatBox(
    label: String,
    value: String,
    unit: String = "",
    icon: ImageVector,
    iconColor: Color,
    modifier: Modifier = Modifier,
) {
    val c = qasaColors
    CardContainer(modifier = modifier) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            // iOS: icon 16 semibold, then label and value, all 8 apart.
            Icon(icon, null, tint = iconColor, modifier = Modifier.size(QasaIconSize.md))
            Spacer(Modifier.height(QasaDimens.contentGap))
            Text(
                qasaText.label(label),
                color = c.textSecondary,
                style = qasaText.sectionLabel,
            )
            Spacer(Modifier.height(QasaDimens.contentGap))
            // iOS `HStack(alignment: .firstTextBaseline, spacing: 4)`.
            Row {
                Text(value, color = c.textPrimary, style = qasaText.statValue, modifier = Modifier.alignByBaseline())
                if (unit.isNotEmpty()) {
                    Spacer(Modifier.width(QasaDimens.spaceSmall))
                    Text(unit, color = c.textSecondary, style = qasaText.bodyMedium, modifier = Modifier.alignByBaseline())
                }
            }
        }
    }
}

/**
 * `VaultItemRow` — `VaultItemRow.swift`. 17pt padding, 12pt radius, 1pt `stroke`
 * border, `cardBackground`; 48x48 tile with an 8pt radius, `chrome` fill and
 * `strokeChrome` border, 18pt icon; 16pt gap; title 16 semibold and subtitle 14,
 * both single-line with ellipsis.
 */
@Composable
fun VaultItemRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onCopy: (() -> Unit)? = null,
    copyContentDescription: String? = null,
    /** When set, the tile shows this site logo instead of [icon] (logins). */
    logo: SiteLogo? = null,
    /** A bundled [SiteIcons] icon for the login's site; shown instead of [logo] when set. */
    siteIcon: ImageVector? = null,
    /** A note or card's own colour ([NoteColor]): the icon's tint and its tile fill. */
    itemColor: Pair<Color, Color>? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val c = qasaColors
    val shape = RoundedCornerShape(QasaDimens.cornerRadius)
    QasaPressable(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(c.cardBackground, shape)
                .border(QasaDimens.stroke, c.stroke, shape)
                .padding(QasaDimens.cardPadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val tileShape = RoundedCornerShape(QasaDimens.radiusSmall)
            Box(
                modifier = Modifier
                    .size(QasaIconFrame.tile)
                    .background(if (siteIcon == null) logo?.color ?: itemColor?.second ?: c.cardMuted else c.cardMuted, tileShape),
                contentAlignment = Alignment.Center,
            ) {
                if (siteIcon != null) {
                    Icon(siteIcon, null, tint = c.textPrimary, modifier = Modifier.size(QasaIconSize.x3l))
                } else if (logo != null) {
                    Text(logo.letter, color = Color.White, style = qasaText.cardTitleBold)
                } else {
                    Icon(icon, null, tint = itemColor?.first ?: c.accent, modifier = Modifier.size(QasaIconSize.lg))
                }
            }
            Spacer(Modifier.width(QasaDimens.gap))
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    color = c.textPrimary,
                    style = qasaText.rowTitle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(QasaDimens.rowTextGap))
                Text(
                    subtitle,
                    color = c.textSecondary,
                    style = qasaText.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            // iOS `Spacer(minLength: 8)` before the row's buttons.
            Spacer(Modifier.width(QasaDimens.contentGap))
            if (onCopy != null) {
                QasaRowIconButton(
                    icon = QasaIcons.Copy,
                    contentDescription = copyContentDescription,
                    onClick = onCopy,
                )
            }
            trailing?.invoke()
        }
    }
}

/**
 * `CheckToggleStyle` — `Components.swift:321`. 48x24 track (`accent` on, `stroke`
 * off), 24pt thumb, 11pt bold check glyph.
 *
 * The thumb colour was a hardcoded `#2563EB` — a port of an iOS defect (§2.2
 * rule 6). The snap had no animation and `.clickable` gave TalkBack no on/off
 * state, so it now animates on the house spring and uses `Modifier.toggleable`.
 */
@Composable
fun CheckToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentDescription: String? = null,
) {
    val c = qasaColors
    val track by animateDpAsState(
        // Thumb rides ±12 from the centre like iOS (`Components.swift` CheckToggle); the
        // off state used +1, which parked the thumb in the middle of the track.
        targetValue = if (checked) QasaDimens.gapSmall else -QasaDimens.gapSmall,
        // iOS `.spring(response: 0.25, dampingFraction: 0.8)` (`motion.toggle`).
        animationSpec = if (qasaReduceMotion.enabled) QasaMotion.fadeTween() else QasaMotion.toggleSpring(),
        label = "qasaCheckToggle",
    )
    val trackColor = animateQasaColor(if (checked) c.accent else c.stroke)
    QasaToggleable(
        checked = checked,
        onCheckedChange = onCheckedChange,
        enabled = enabled,
        contentDescription = contentDescription,
        modifier = modifier
            .width(QasaDimens.touchTarget)
            .height(QasaDimens.sectionGap)
            .clip(RoundedCornerShape(50))
            .background(trackColor),
    ) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .offset(x = track)
                    .size(QasaIconSize.x3l)
                    .clip(CircleShape)
                    // iOS: a card-colored thumb with an accent check when on, textTertiary off.
                    .background(if (checked) c.cardBackground else c.textTertiary),
                contentAlignment = Alignment.Center,
            ) {
                if (checked) {
                    Icon(
                        QasaIcons.Check,
                        contentDescription = null,
                        tint = c.accent,
                        modifier = Modifier.size(QasaIconSize.xxxs),
                    )
                }
            }
        }
    }
}

/**
 * iOS `VaultItemRow`'s plain 36dp icon buttons (copy, ⋯): a 17dp `textSecondary` glyph,
 * dimmed while pressed, no ripple and no 48dp layout inflation.
 */
@Composable
fun QasaRowIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = qasaColors.textSecondary,
) {
    val interaction = remember { MutableInteractionSource() }
    val alpha = rememberPressOpacity(interaction)
    Box(
        modifier
            .size(QasaDimens.rowIconBadge)
            .graphicsLayer { this.alpha = alpha }
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
            .semanticsRoleButton(contentDescription),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(QasaIconSize.menu))
    }
}

/** Android-only. Keep it — iOS reaches for `ContentUnavailableView` at 4 sites. */
@Composable
fun EmptyState(
    title: String,
    hint: String,
    modifier: Modifier = Modifier,
    icon: ImageVector = QasaIcons.Add,
) {
    val c = qasaColors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = QasaDimens.scrollTail),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, null, tint = c.textTertiary, modifier = Modifier.size(QasaIconSize.x6l))
        Spacer(Modifier.height(QasaDimens.gapSmall))
        Text(title, color = c.textPrimary, style = qasaText.cardTitle)
        Spacer(Modifier.height(QasaDimens.gapTiny))
        Text(hint, color = c.textSecondary, style = qasaText.bodyMedium)
    }
}
