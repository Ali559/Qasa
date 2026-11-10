package alibarznji.qasa.theme

import alibarznji.qasa.theme.QasaDimens
import alibarznji.qasa.theme.QasaIconFrame
import alibarznji.qasa.theme.QasaIconSize
import alibarznji.qasa.theme.qasaText
import androidx.compose.animation.core.animateDpAsState
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
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
 * The rounded-square icon badge every settings / list row leads with: a fixed
 * [QasaDimens.rowIconBadge] square, `tint @ 12%` fill (or [background]), 20dp glyph.
 */
@Composable
fun RowIconBadge(icon: ImageVector, tint: Color, background: Color? = null) {
    val shape = RoundedCornerShape(QasaDimens.rowIconBadgeRadius)
    Box(
        modifier = Modifier
            .size(QasaDimens.rowIconBadge)
            .clip(shape)
            .background(background ?: tint.copy(alpha = 0.12f), shape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(QasaIconSize.xl))
    }
}

/**
 * The one row layout behind [NavChevronRow] and [ToggleRow]: at least
 * [QasaDimens.rowMinHeight] tall, optional [RowIconBadge], title 16 medium and subtitle
 * 13 stacked and vertically centered against the badge, then the [trailing] slot
 * (chevron, value, switch) centered on the same line. Start/end only, so it mirrors in RTL.
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
            .padding(vertical = QasaDimens.contentGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            RowIconBadge(icon, tint = iconColor ?: c.accent, background = iconBackground)
            Spacer(Modifier.width(QasaDimens.drawerGap))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
            Text(title, color = titleColor ?: c.textPrimary, style = qasaText.rowTitleMedium)
            if (!subtitle.isNullOrEmpty()) {
                Spacer(Modifier.height(QasaDimens.spaceMicro))
                Text(subtitle, color = c.textSecondary, style = qasaText.bodySmall)
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(QasaDimens.gapSmall))
            trailing()
        }
    }
}

/**
 * `NavChevronRow` — `Components.swift:257`, on the shared [QasaRow]: badge, title,
 * subtitle, optional trailing value, then a 16dp `textTertiary` chevron (hidden when
 * destructive; the title and badge take `danger` instead).
 *
 * The chevron is `AutoMirrored` so it points the right way in RTL (§5).
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
    QasaPressable(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
    ) {
        QasaRow(
            title = title,
            icon = icon,
            iconColor = iconColor ?: if (destructive) c.danger else null,
            subtitle = subtitle,
            titleColor = if (destructive) c.danger else null,
            trailing = if (trailingText == null && destructive) {
                null
            } else {
                {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (trailingText != null) {
                            Text(
                                trailingText,
                                color = c.textSecondary,
                                style = qasaText.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (!destructive) {
                            if (trailingText != null) Spacer(Modifier.width(QasaDimens.spaceSmall))
                            Icon(
                                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null,
                                tint = c.textTertiary,
                                modifier = Modifier.size(QasaIconSize.xl),
                            )
                        }
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
            Icon(icon, null, tint = iconColor, modifier = Modifier.size(QasaIconSize.lg))
            Spacer(Modifier.height(QasaDimens.contentGap))
            Text(
                qasaText.label(label),
                color = c.textSecondary,
                style = qasaText.sectionLabel,
            )
            Spacer(Modifier.height(QasaDimens.spaceMicro))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(value, color = c.textPrimary, style = qasaText.statValue)
                if (unit.isNotEmpty()) {
                    Spacer(Modifier.width(QasaDimens.spaceSmall))
                    Text(unit, color = c.textSecondary, style = qasaText.bodyMedium)
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
                    .background(if (siteIcon == null) logo?.color ?: c.cardMuted else c.cardMuted, tileShape),
                contentAlignment = Alignment.Center,
            ) {
                if (siteIcon != null) {
                    Icon(siteIcon, null, tint = c.textPrimary, modifier = Modifier.size(QasaIconSize.x3l))
                } else if (logo != null) {
                    Text(logo.letter, color = Color.White, style = qasaText.cardTitleBold)
                } else {
                    Icon(icon, null, tint = c.accent, modifier = Modifier.size(QasaIconSize.lg))
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
                Text(
                    subtitle,
                    color = c.textSecondary,
                    style = qasaText.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (onCopy != null) {
                IconButton(
                    onClick = onCopy,
                    modifier = Modifier.size(QasaDimens.touchTarget),
                ) {
                    Icon(
                        Icons.Default.ContentCopy,
                        contentDescription = copyContentDescription,
                        tint = c.textSecondary,
                        modifier = Modifier.size(QasaIconSize.md),
                    )
                }
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
        animationSpec = if (qasaReduceMotion.enabled) {
            androidx.compose.animation.core.tween(QasaMotion.FAST)
        } else {
            androidx.compose.animation.core.spring(
                dampingRatio = QasaMotion.TOGGLE_DAMPING,
                stiffness = androidx.compose.animation.core.Spring.StiffnessMedium,
            )
        },
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
                    .background(if (checked) c.info else c.stroke),
                contentAlignment = Alignment.Center,
            ) {
                if (checked) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(QasaIconSize.xxxs),
                    )
                }
            }
        }
    }
}

/** Android-only. Keep it — iOS reaches for `ContentUnavailableView` at 4 sites. */
@Composable
fun EmptyState(
    title: String,
    hint: String,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Default.Add,
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
