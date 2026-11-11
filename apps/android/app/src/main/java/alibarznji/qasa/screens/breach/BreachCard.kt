package alibarznji.qasa.screens.breach

import alibarznji.qasa.R
import alibarznji.qasa.theme.PillBadge
import alibarznji.qasa.theme.QasaDimens
import alibarznji.qasa.theme.QasaIconSize
import alibarznji.qasa.theme.QasaIcons
import alibarznji.qasa.theme.qasaColors
import alibarznji.qasa.theme.qasaText
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

// iOS BreachCard.swift literals: card padding 21, chip h-padding 9, action v-padding 11.
private val CardPadding = 21.dp
private val ChipPaddingH = 9.dp
private val ActionPaddingV = 11.dp

/** iOS `BreachStatus` — the Unresolved / Resolved tab and card state. */
enum class BreachStatus { Unresolved, Resolved }

/** One compromised-data chip; iOS picks the icon from the field name in `BreachCard.fieldIcon`. */
data class CompromisedField(val label: String, val icon: ImageVector)

/** iOS `Breach` model as `BreachMonitorView.breachList` builds it. */
data class Breach(
    val title: String,
    val breachDate: String,
    val discoveredDate: String,
    val compromisedFields: List<CompromisedField>,
    val status: BreachStatus,
)

/**
 * Port of `I/Views/BreachCard.swift`: title + status badge, date line, wrapped compromised
 * chips, then "Rotate Password" (unresolved) or a static "Action Taken" row (resolved).
 * Danger/success 1dp border with a 2dp accent bar along the top; resolved cards at 80% opacity.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BreachCard(breach: Breach, modifier: Modifier = Modifier, onRotatePassword: () -> Unit = {}) {
    val c = qasaColors
    val unresolved = breach.status == BreachStatus.Unresolved
    val accent = if (unresolved) c.danger else c.success
    val shape = RoundedCornerShape(QasaDimens.cornerRadius)
    Box(
        modifier
            .fillMaxWidth()
            .alpha(if (unresolved) 1f else 0.8f)
            .clip(shape)
            .background(c.cardBackground)
            .border(QasaDimens.stroke, accent, shape),
    ) {
        Column(Modifier.padding(CardPadding), verticalArrangement = Arrangement.spacedBy(QasaDimens.gapSmall)) {
            Row(verticalAlignment = Alignment.Top) {
                Text(breach.title, color = c.textPrimary, style = qasaText.cardTitle, modifier = Modifier.weight(1f, fill = false))
                Spacer(Modifier.width(QasaDimens.gapSmall))
                PillBadge(
                    text = stringResource(if (unresolved) R.string.breach_unresolved else R.string.breach_resolved),
                    color = if (unresolved) c.dangerText else c.success,
                )
            }
            Text(
                stringResource(R.string.breach_breachdate, breach.breachDate, breach.discoveredDate),
                color = c.textSecondary,
                style = qasaText.bodyMedium,
            )
            FlowRow(
                modifier = Modifier.padding(top = QasaDimens.spaceSmall),
                horizontalArrangement = Arrangement.spacedBy(QasaDimens.contentGap),
                verticalArrangement = Arrangement.spacedBy(QasaDimens.contentGap),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.breach_compromised),
                    color = c.textTertiary,
                    style = qasaText.sectionLabel,
                    modifier = Modifier.padding(end = QasaDimens.spaceSmall),
                )
                breach.compromisedFields.forEach { CompromisedChip(it) }
            }
            if (unresolved) {
                ActionRow(
                    icon = QasaIcons.Login,
                    label = stringResource(R.string.breach_rotatepassword),
                    tint = c.danger,
                    background = c.chrome,
                    border = c.danger,
                    onClick = onRotatePassword,
                )
            } else {
                ActionRow(
                    icon = QasaIcons.Check,
                    label = stringResource(R.string.breach_actiontaken),
                    tint = c.textTertiary,
                    background = c.cardBackgroundLight,
                    border = c.strokeChrome,
                    onClick = null,
                )
            }
        }
        // iOS overlays a 2pt accent bar on the top edge, clipped to the card's top corners.
        Box(Modifier.fillMaxWidth().height(QasaDimens.spaceMicro).background(accent).align(Alignment.TopCenter))
    }
}

@Composable
private fun CompromisedChip(field: CompromisedField) {
    val c = qasaColors
    val shape = RoundedCornerShape(QasaDimens.radiusTiny)
    Row(
        Modifier
            .background(c.cardBackgroundLight, shape)
            .border(QasaDimens.stroke, c.stroke, shape)
            .padding(horizontal = ChipPaddingH, vertical = QasaDimens.spaceNano),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(field.icon, contentDescription = null, tint = c.textPrimary, modifier = Modifier.size(QasaIconSize.xxs))
        Spacer(Modifier.width(QasaDimens.spaceSmall))
        Text(field.label, color = c.textPrimary, style = qasaText.sectionLabel)
    }
}

@Composable
private fun ActionRow(
    icon: ImageVector,
    label: String,
    tint: Color,
    background: Color,
    border: Color,
    onClick: (() -> Unit)?,
) {
    val shape = RoundedCornerShape(QasaDimens.radiusSmall)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(background)
            .border(QasaDimens.stroke, border, shape)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(vertical = ActionPaddingV),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(QasaIconSize.sm))
        Spacer(Modifier.width(QasaDimens.contentGap))
        Text(label, color = tint, style = qasaText.bodyMedium)
    }
}

/** The single "Emails" field iOS attaches to every scan result (`BreachMonitorView.breachList`). */
val EmailsFieldIcon: ImageVector = QasaIcons.Email
