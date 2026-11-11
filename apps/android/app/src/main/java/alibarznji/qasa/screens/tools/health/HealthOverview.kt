package alibarznji.qasa.screens.tools.health

import alibarznji.qasa.R
import alibarznji.qasa.screens.PasswordHealthReport
import alibarznji.qasa.screens.breach.BreachLockedCard
import alibarznji.qasa.theme.QasaIconSize
import alibarznji.qasa.theme.AppMenuHeader
import alibarznji.qasa.theme.CardContainer
import alibarznji.qasa.theme.HalfCircleGauge
import alibarznji.qasa.theme.QasaDimens
import alibarznji.qasa.theme.QasaIcons
import alibarznji.qasa.theme.QasaPressable
import alibarznji.qasa.theme.ScreenScaffold
import alibarznji.qasa.theme.qasaColors
import alibarznji.qasa.theme.qasaText
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** iOS `PasswordIssueTab`: the pill tabs of the issues screen. */
enum class HealthIssueTab { REUSED, WEAK, BREACHED }

/**
 * iOS `PasswordHealthView` body (`PasswordHealthView.swift:26`): gauge, a 2×2 grid of stat
 * cards that each push the issues screen, and the "How scoring works" card, 28pt apart.
 */
@Composable
internal fun HealthOverview(
    report: PasswordHealthReport?,
    onOpenMenu: (() -> Unit)?,
    onOpenIssues: (HealthIssueTab) -> Unit,
    onUpgrade: () -> Unit,
) {
    val c = qasaColors
    val shown = report ?: PasswordHealthReport.empty
    ScreenScaffold(
        header = {
            AppMenuHeader(
                title = stringResource(R.string.nav_passwordhealth),
                onMenu = onOpenMenu,
                applyStatusBars = false,
            )
        },
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = QasaDimens.contentGap),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Until the first report lands the gauge sits at 0, then counts up once (iOS
            // computes synchronously on appear, so it never shows the placeholder 100).
            HalfCircleGauge(score = report?.score ?: 0, modifier = Modifier.padding(top = QasaDimens.contentGap))
            Spacer(Modifier.height(HEALTH_GAUGE_LABEL_GAP))
            if (report != null) {
                Text(scoreLabel(report.score), color = c.textPrimary, style = qasaText.cardTitle)
            }
            // Free tier: say the score leaves breaches out, rather than implying none were found.
            if (report?.breachLocked == true) {
                Spacer(Modifier.height(QasaDimens.contentGap))
                Text(
                    stringResource(R.string.health_breachedlocked),
                    color = c.textSecondary,
                    style = qasaText.bodySmall,
                    textAlign = TextAlign.Center,
                )
            }
        }

        Spacer(Modifier.height(HEALTH_SECTION_GAP))
        // iOS order is Passwords / Reused, then Weak / Breached, and the Passwords card opens
        // the Reused tab (`PasswordHealthView.swift:34`) — there is no "all passwords" tab.
        Column(verticalArrangement = Arrangement.spacedBy(QasaDimens.gapSmall)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(QasaDimens.gapSmall)) {
                HealthStatCard(
                    title = stringResource(R.string.health_passwords),
                    value = shown.totalPasswords,
                    accent = c.accent,
                    onClick = { onOpenIssues(HealthIssueTab.REUSED) },
                    modifier = Modifier.weight(1f),
                )
                HealthStatCard(
                    title = stringResource(R.string.health_reused),
                    value = shown.reusedCount,
                    accent = c.gold,
                    onClick = { onOpenIssues(HealthIssueTab.REUSED) },
                    modifier = Modifier.weight(1f),
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(QasaDimens.gapSmall)) {
                HealthStatCard(
                    title = stringResource(R.string.health_weak),
                    value = shown.weakCount,
                    accent = c.danger,
                    onClick = { onOpenIssues(HealthIssueTab.WEAK) },
                    modifier = Modifier.weight(1f),
                )
                HealthStatCard(
                    title = stringResource(R.string.health_breached),
                    value = shown.breachedCount,
                    accent = c.danger,
                    onClick = { onOpenIssues(HealthIssueTab.BREACHED) },
                    modifier = Modifier.weight(1f),
                    locked = shown.breachLocked,
                )
            }
            if (report?.breachCheckFailed == true) {
                Text(stringResource(R.string.breached_offline), color = c.textSecondary, style = qasaText.bodySmall)
            }
            if (report?.breachLocked == true) {
                BreachLockedCard(onUpgrade = onUpgrade)
            }
        }

        Spacer(Modifier.height(HEALTH_SECTION_GAP))
        CardContainer {
            Text(stringResource(R.string.health_howscoring), color = c.textPrimary, style = qasaText.rowTitle)
            Spacer(Modifier.height(QasaDimens.gapMedium))
            Text(
                stringResource(R.string.health_howscoringbody),
                color = c.textSecondary,
                style = qasaText.bodyMedium,
            )
        }
    }
}

/** iOS `scoreLabel(for:)` (`PasswordHealthView.swift:129`). */
@Composable
private fun scoreLabel(score: Int): String = stringResource(
    when {
        score >= 90 -> R.string.health_excellent
        score >= 75 -> R.string.health_strong
        score >= 50 -> R.string.health_fair
        else -> R.string.health_needsattention
    },
)

/** iOS `statCard` (`PasswordHealthView.swift:148`): value 28 bold, title 13, 18pt vertical padding. */
@Composable
private fun HealthStatCard(
    title: String,
    value: Int,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    /** Free tier's Breached card: a lock instead of a count that was never checked. */
    locked: Boolean = false,
) {
    val c = qasaColors
    val shape = RoundedCornerShape(QasaDimens.cornerRadius)
    QasaPressable(onClick = onClick, modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(c.cardBackground, shape)
                .border(QasaDimens.stroke, c.stroke, shape)
                .padding(vertical = QasaDimens.gapXLarge),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(QasaDimens.contentGap),
        ) {
            if (locked) {
                Box(Modifier.height(HEALTH_STAT_VALUE_HEIGHT), contentAlignment = Alignment.Center) {
                    Icon(QasaIcons.Lock, null, tint = c.gold, modifier = Modifier.size(QasaIconSize.x4l))
                }
            } else {
                Text(java.text.NumberFormat.getIntegerInstance().format(value), color = accent, style = qasaText.hero)
            }
            Text(
                title,
                color = c.textSecondary,
                style = qasaText.bodySmall,
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
        }
    }
}

/** The 28pt bold value's line height, so a locked card is as tall as the others. */
private val HEALTH_STAT_VALUE_HEIGHT = 34.dp

/** iOS `VStack(spacing: 28)` between the gauge, stat grid and scoring card. */
private val HEALTH_SECTION_GAP = 28.dp

/** iOS `scoreGauge` `VStack(spacing: 20)` between the gauge and its label. */
private val HEALTH_GAUGE_LABEL_GAP = 20.dp
