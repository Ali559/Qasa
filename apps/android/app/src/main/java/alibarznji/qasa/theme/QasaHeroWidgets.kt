package alibarznji.qasa.theme

import alibarznji.qasa.R
import alibarznji.qasa.theme.QasaDimens
import alibarznji.qasa.theme.QasaIconSize
import alibarznji.qasa.theme.qasaText
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * `HalfCircleGauge` — `PasswordHealthView.swift:91` (`scoreGauge`) + `:267`. An
 * `AngularGradient` (`danger -> gold -> accent`) over the trimmed 180-360 arc.
 *
 * iOS `animateScore()` resets the arc and the numeral to 0 and eases both out to the
 * score over 1100ms, so the number counts up; the numeral is coloured by the score it is
 * showing (>=80 accent, >=50 gold, else danger) with "out of 100" under it. Android
 * matches that, and also announces the final score as one node for TalkBack.
 */
@Composable
fun HalfCircleGauge(
    score: Int,
    modifier: Modifier = Modifier,
) {
    val c = qasaColors
    val clamped = score.coerceIn(0, 100)
    val scoreDescription = gaugeScoreDescription(clamped)
    val reduceMotion = qasaReduceMotion.enabled
    val progress = remember { Animatable(0f) }
    LaunchedEffect(clamped, reduceMotion) {
        progress.snapTo(0f)
        progress.animateTo(
            targetValue = clamped.toFloat(),
            animationSpec = tween(
                durationMillis = if (reduceMotion) QasaMotion.FAST else QasaMotion.GAUGE,
                easing = EaseOut,
            ),
        )
    }
    val animatedScore = progress.value
    val shownScore = animatedScore.roundToInt()
    val sweepFraction = animatedScore / 100f
    val gradient = Brush.sweepGradient(
        // 180deg of a 360deg sweep starts at the left edge, matching the arc origin.
        colorStops = arrayOf(
            0.00f to c.danger,
            0.25f to c.gold,
            0.50f to c.accent,
            0.75f to c.accent,
            1.00f to c.danger,
        ),
        center = Offset.Unspecified,
    )
    // iOS `scoreColor(for:)` (`PasswordHealthView.swift:121`).
    val numberColor = when {
        shownScore >= 80 -> c.accent
        shownScore >= 50 -> c.gold
        else -> c.danger
    }
    Box(
        modifier = modifier
            .size(width = QasaDimens.gaugeWidth, height = QasaDimens.gaugeHeight)
            .clearAndSetSemantics { contentDescription = scoreDescription },
        contentAlignment = Alignment.BottomCenter,
    ) {
        Canvas(Modifier.matchParentSize()) {
            val strokePx = QasaDimens.gaugeStroke.toPx()
            val diameter = min(size.width, size.height * 2f) - strokePx
            val topLeft = Offset((size.width - diameter) / 2f, size.height - diameter / 2f)
            val arcSize = Size(diameter, diameter)
            drawArc(
                color = c.stroke,
                startAngle = 180f,
                sweepAngle = 180f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokePx, cap = StrokeCap.Round),
            )
            if (sweepFraction > 0f) {
                drawArc(
                    brush = gradient,
                    startAngle = 180f,
                    sweepAngle = 180f * sweepFraction,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokePx, cap = StrokeCap.Round),
                )
            }
        }
        // iOS: VStack(spacing: 2) { score 44 bold; "out of 100" mono 12 } .padding(.bottom, 8)
        Column(
            modifier = Modifier.padding(bottom = QasaDimens.contentGap),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(QasaDimens.spaceMicro),
        ) {
            Text(
                shownScore.toString(),
                color = numberColor,
                style = qasaText.healthScore,
                textAlign = TextAlign.Center,
            )
            Text(
                stringResource(R.string.health_outof100),
                color = c.textSecondary,
                style = qasaText.sectionLabel,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun gaugeScoreDescription(score: Int): String =
    stringResource(R.string.a11y_healthscore, score)

/**
 * `VpnPowerDial` — `VPNView.swift:115-190`. Outer ring `cyan @ 30%` 1pt at 160,
 * inner ring `cyan @ 55%` 1pt at 140, **opaque** `cardBackground` core at 128 with
 * a `accent @ 40%` radius-20 shadow when connected, `power` glyph at 36 medium.
 *
 * Android used 2dp/3dp rings, a translucent core, no shadow, and printed ON/OFF text.
 */
@Composable
fun VpnPowerDial(
    connected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    busy: Boolean = false,
) {
    val c = qasaColors
    val ringColor = if (connected) c.accent else c.stroke
    val stateLabel = stringResource(
        if (connected) R.string.vpn_connected else R.string.vpn_disconnected,
    )
    QasaPressable(
        onClick = onClick,
        contentDescription = stringResource(R.string.a11y_vpndial, stateLabel),
        modifier = modifier.size(QasaDimens.dialOuter),
    ) {
        // Centred like the iOS ZStack; without it the rings stacked in the top-left corner.
        Box(Modifier.size(QasaDimens.dialOuter), contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .size(QasaDimens.dialOuter)
                    .border(QasaDimens.stroke, c.cyan.copy(alpha = 0.3f), CircleShape),
            )
            Box(
                Modifier
                    .size(QasaDimens.dialMid)
                    .border(QasaDimens.stroke, ringColor.copy(alpha = 0.5f), CircleShape),
            )
            Box(
                modifier = Modifier
                    .size(QasaDimens.dialInner)
                    .then(
                        if (connected) {
                            Modifier.shadow(
                                elevation = QasaDimens.dialCoreElevation,
                                shape = CircleShape,
                                ambientColor = c.accent.copy(alpha = 0.4f),
                                spotColor = c.accent.copy(alpha = 0.4f),
                            )
                        } else {
                            Modifier
                        },
                    )
                    .clip(CircleShape)
                    .background(c.cardBackground)
                    .border(QasaDimens.ringStroke, ringColor, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                if (busy) {
                    CircularProgressIndicator(color = c.accent)
                } else {
                    Icon(
                        QasaIcons.Power,
                        contentDescription = null,
                        tint = if (connected) c.accent else c.textSecondary,
                        modifier = Modifier.size(QasaIconSize.x5l),
                    )
                }
            }
        }
    }
}
