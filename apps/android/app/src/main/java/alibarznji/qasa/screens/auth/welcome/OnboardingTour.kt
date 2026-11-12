package alibarznji.qasa.screens.auth.welcome

import alibarznji.qasa.R
import alibarznji.qasa.screens.vault.detail.NoteColor
import alibarznji.qasa.theme.CardContainer
import alibarznji.qasa.theme.HalfCircleGauge
import alibarznji.qasa.theme.PrimaryButton
import alibarznji.qasa.theme.QasaButton
import alibarznji.qasa.theme.QasaButtonKind
import alibarznji.qasa.theme.QasaDimens
import alibarznji.qasa.theme.QasaIconSize
import alibarznji.qasa.theme.QasaIcons
import alibarznji.qasa.theme.QasaMotion
import alibarznji.qasa.theme.QasaTokens
import alibarznji.qasa.theme.RowDivider
import alibarznji.qasa.theme.RowIconBadge
import alibarznji.qasa.theme.StatusPill
import alibarznji.qasa.theme.StickyBottomBar
import alibarznji.qasa.theme.qasaColors
import alibarznji.qasa.theme.qasaReduceMotion
import alibarznji.qasa.theme.qasaText
import alibarznji.qasa.theme.spec
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Seconds to milliseconds, for the `motion.tour…` timings. */
private fun Float.millis(): Long = (this * 1000).toLong()

/**
 * The first-launch tour, direction B "Live demo" (`design/README.md` rule 6d, iOS
 * `OnboardingTourView`): four pages about free features, each playing a tiny version of
 * the app. Story bars at the top move on by themselves after `motion.tourDwell`; tapping
 * the leading or trailing half, or swiping, goes back or forward ([TourLogic]). With reduce
 * motion on, pages fade, the demos show their end state and nothing moves on by itself.
 */
@Composable
fun OnboardingTour(onSkip: () -> Unit, onDone: () -> Unit) {
    val c = qasaColors
    val reduceMotion = qasaReduceMotion.enabled
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    var page by rememberSaveable { mutableIntStateOf(0) }
    val progress = remember { Animatable(0f) }
    val currentOnDone by rememberUpdatedState(onDone)

    fun move(delta: Int) {
        when (val m = TourLogic.move(page, delta)) {
            is TourMove.Page -> page = m.index
            TourMove.Done -> currentOnDone()
            TourMove.Stay -> Unit
        }
    }

    // Fills the current bar over `tourDwell`, then moves on. Reduce motion: full bar, no move.
    LaunchedEffect(page, reduceMotion) {
        progress.snapTo(0f)
        if (reduceMotion) {
            progress.snapTo(1f)
            return@LaunchedEffect
        }
        delay(BAR_START_DELAY_MS)
        val dwell = QasaTokens.Motion.tourDwell.millis()
        // The bar and the move are timed apart, so a sped-up animator scale cannot cut the page short.
        launch { progress.animateTo(1f, tween(dwell.toInt(), easing = QasaTokens.Easings.linear)) }
        delay(dwell)
        move(1)
    }

    val nextLabel = stringResource(R.string.onboarding_next)
    Column(Modifier.fillMaxSize().background(c.background)) {
        Row(
            Modifier
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(start = QasaDimens.screenPadding, top = QasaDimens.contentGap),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(QasaDimens.contentGap),
        ) {
            StoryBars(page = page, progress = progress.value, modifier = Modifier.weight(1f))
            QasaButton(
                title = stringResource(R.string.common_skip),
                onClick = onSkip,
                kind = QasaButtonKind.Plain,
                fullWidth = false,
            )
        }

        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .pointerInput(rtl) {
                    val swipePx = TourLogic.SWIPE_DISTANCE_DP.dp.toPx()
                    val slopPx = TourLogic.TAP_VERTICAL_SLOP_DP.dp.toPx()
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        var last = down.position
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            last = change.position
                            if (!change.pressed) break
                        }
                        val delta = TourLogic.gestureDelta(
                            startX = down.position.x,
                            endX = last.x,
                            dy = last.y - down.position.y,
                            width = size.width.toFloat(),
                            rtl = rtl,
                            swipePx = swipePx,
                            tapSlopPx = slopPx,
                        )
                        if (delta != 0) move(delta)
                    }
                }
                .semantics {
                    customActions = listOf(CustomAccessibilityAction(nextLabel) { move(1); true })
                },
        ) {
            AnimatedContent(
                targetState = page,
                transitionSpec = { fadeIn(QasaMotion.fadeTween()) togetherWith fadeOut(QasaMotion.fadeTween()) },
                label = "tourPage",
            ) { index -> TourPage(index) }
        }

        StickyBottomBar {
            PrimaryButton(
                title = stringResource(if (TourLogic.isLast(page)) R.string.common_continue else R.string.onboarding_next),
                onClick = { move(1) },
            )
        }
    }
}

@Composable
private fun StoryBars(page: Int, progress: Float, modifier: Modifier = Modifier) {
    val c = qasaColors
    val shape = RoundedCornerShape(50)
    Row(
        modifier.clearAndSetSemantics {},
        horizontalArrangement = Arrangement.spacedBy(QasaDimens.spaceSmall),
    ) {
        repeat(TourLogic.PAGE_COUNT) { i ->
            val fill = when {
                i < page -> 1f
                i > page -> 0f
                else -> progress
            }
            Box(
                Modifier
                    .weight(1f)
                    .height(QasaDimens.spaceTight)
                    .clip(shape)
                    .background(c.strokeChrome),
            ) {
                Box(Modifier.fillMaxHeight().fillMaxWidth(fill).background(c.accent, shape))
            }
        }
    }
}

// One page

@Composable
private fun TourPage(index: Int) {
    val (title, body) = when (index) {
        0 -> R.string.onboarding_tour1title to R.string.onboarding_tour1body
        1 -> R.string.onboarding_tour2title to R.string.onboarding_tour2body
        2 -> R.string.onboarding_tour3title to R.string.onboarding_tour3body
        else -> R.string.onboarding_tour4title to R.string.onboarding_tour4body
    }
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = QasaDimens.screenPadding)
            .padding(bottom = QasaDimens.gap),
    ) {
        Spacer(Modifier.weight(1f).heightIn(min = QasaDimens.gapSmall))
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            when (index) {
                0 -> VaultDemo()
                1 -> SignUpDemo()
                2 -> CodeDemo()
                else -> HealthDemo()
            }
        }
        Spacer(Modifier.weight(1f).heightIn(min = QasaDimens.gapXLarge))
        TourCopy(stringResource(title), stringResource(body))
    }
}

@Composable
private fun TourCopy(title: String, text: String) {
    val c = qasaColors
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(QasaDimens.contentGap)) {
        Text(
            title,
            color = c.textPrimary,
            style = qasaText.styleForSize(24, FontWeight.Bold),
            modifier = Modifier.rise(shown),
        )
        Text(
            text,
            color = c.textSecondary,
            style = qasaText.bodyLegal,
            modifier = Modifier.rise(shown, delayMillis = COPY_BODY_DELAY_MS),
        )
    }
}

/** Fades and rises into place on `motion.standard`; still under reduce motion. */
@Composable
private fun Modifier.rise(shown: Boolean, delayMillis: Int = 0): Modifier {
    val reduceMotion = qasaReduceMotion.enabled
    val visible = shown || reduceMotion
    val t by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(QasaMotion.STANDARD, delayMillis, QasaTokens.Easings.easeOut),
        label = "tourRise",
    )
    val lift = RISE_OFFSET
    return graphicsLayer {
        alpha = if (reduceMotion) 1f else t
        translationY = if (reduceMotion) 0f else (1f - t) * lift.toPx()
    }
}

/** Rows of a demo rise in on the `tourEnter` spring once [shown] passes their [order]. */
@Composable
private fun Modifier.staggered(order: Int, shown: Int): Modifier {
    val reduceMotion = qasaReduceMotion.enabled
    val visible = reduceMotion || shown > order
    val t by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = QasaTokens.Motion.tourEnter.spec(),
        label = "tourStagger",
    )
    val v = if (reduceMotion) 1f else t
    return graphicsLayer {
        alpha = v.coerceIn(0f, 1f)
        translationY = (1f - v) * STAGGER_OFFSET.toPx()
        val s = 1f - (1f - v) * (1f - STAGGER_SCALE)
        scaleX = s
        scaleY = s
    }
}

/** Counts [shown] up to [count], `tourStagger` apart. */
@Composable
private fun rememberStagger(count: Int): Int {
    var shown by remember { mutableIntStateOf(0) }
    LaunchedEffect(count) {
        for (i in 0 until count) {
            delay(if (i == 0) STAGGER_FIRST_MS else QasaTokens.Motion.tourStagger.millis())
            shown = i + 1
        }
    }
    return shown
}

/** The card every demo sits in: the app's card surface, rows inset like a settings card. */
@Composable
private fun DemoCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    CardContainer(modifier = modifier, padding = 0.dp, content = content)
}

@Composable
private fun DemoRow(icon: ImageVector, color: NoteColor, title: String, subtitle: String, modifier: Modifier = Modifier) {
    val c = qasaColors
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = QasaDimens.sectionInsetHorizontal, vertical = QasaDimens.gapMedium),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(QasaDimens.gapSmall),
    ) {
        RowIconBadge(icon, tint = color.swatch(), background = color.tile())
        Column(Modifier.weight(1f)) {
            Text(title, color = c.textPrimary, style = qasaText.bodySemibold15, maxLines = 1)
            Text(subtitle, color = c.textSecondary, style = qasaText.bodySmall, maxLines = 1)
        }
    }
}

/** Fixed demo text (an address, digits) reads left to right in every language. */
@Composable
private fun Ltr(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr, content = content)
}

// Page 1, the vault

@Composable
private fun VaultDemo() {
    val c = qasaColors
    val shown = rememberStagger(4)
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(QasaDimens.gapSmall)) {
        DemoCard {
            DemoRow(QasaIcons.Login, NoteColor.Purple, "Gmail", DEMO_EMAIL, Modifier.staggered(0, shown))
            RowDivider()
            DemoRow(QasaIcons.Card, NoteColor.Gold, "Visa", "•••• 4821", Modifier.staggered(1, shown))
            RowDivider()
            DemoRow(
                QasaIcons.Note,
                NoteColor.Mint,
                stringResource(R.string.onboarding_mockwifi),
                stringResource(R.string.field_note),
                Modifier.staggered(2, shown),
            )
        }
        StatusPill(
            text = stringResource(R.string.brand_localoffline),
            dotColor = c.success,
            textColor = c.success,
            modifier = Modifier.staggered(3, shown),
        )
    }
}

// Page 2, sign-up with a strong password

@Composable
private fun SignUpDemo() {
    val c = qasaColors
    val reduceMotion = qasaReduceMotion.enabled
    var typed by remember { mutableStateOf(if (reduceMotion) DEMO_EMAIL else "") }
    var passwordFocused by remember { mutableStateOf(reduceMotion) }
    var suggesting by remember { mutableStateOf(false) }
    var tapped by remember { mutableStateOf(false) }
    var filled by remember { mutableStateOf(reduceMotion) }

    LaunchedEffect(reduceMotion) {
        if (reduceMotion) return@LaunchedEffect
        delay(SIGNUP_START_MS)
        for (ch in DEMO_EMAIL) {
            typed += ch
            delay(QasaTokens.Motion.tourTypeStep.millis())
        }
        delay(SIGNUP_BEFORE_SUGGEST_MS)
        passwordFocused = true
        suggesting = true
        delay(SIGNUP_BEFORE_TAP_MS)
        tapped = true
        delay(SIGNUP_TAP_HOLD_MS)
        tapped = false
        suggesting = false
        filled = true
    }

    val suggestion by animateFloatAsState(
        if (suggesting) 1f else 0f,
        if (suggesting) QasaTokens.Motion.tourEnter.spec() else QasaMotion.standardTween(),
        label = "signUpSuggestion",
    )
    val press by animateFloatAsState(if (tapped) QasaMotion.PRESS_SCALE else 1f, QasaMotion.pressTween(), label = "signUpPress")

    DemoCard {
        Text(
            stringResource(R.string.onboarding_mocksignup),
            color = c.textPrimary,
            style = qasaText.bodySemibold15,
            modifier = Modifier.padding(
                start = QasaDimens.sectionInsetHorizontal,
                end = QasaDimens.sectionInsetHorizontal,
                top = QasaDimens.sectionInsetHorizontal,
                bottom = QasaDimens.contentGap,
            ),
        )
        DemoField(stringResource(R.string.field_email), typed, focused = !passwordFocused && typed.isNotEmpty())
        DemoField(
            stringResource(R.string.field_password),
            if (filled) "•".repeat(DEMO_PASSWORD.length) else "",
            focused = passwordFocused,
        )
        Row(
            Modifier
                .padding(horizontal = QasaDimens.sectionInsetHorizontal)
                .padding(bottom = QasaDimens.gapSmall)
                .fillMaxWidth()
                .graphicsLayer {
                    alpha = suggestion.coerceIn(0f, 1f)
                    translationY = (1f - suggestion) * QasaDimens.contentGap.toPx()
                    scaleX = press
                    scaleY = press
                }
                .clip(RoundedCornerShape(QasaDimens.radiusSmall))
                .background(if (tapped) c.notePurpleTile else c.cardMuted)
                .padding(QasaDimens.gapMedium),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(QasaDimens.gapMedium),
        ) {
            RowIconBadge(QasaIcons.Generate, tint = c.accent, background = c.cardBackground)
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.fill_strongpassword), color = c.textPrimary, style = qasaText.bodySmallSemibold)
                Ltr { Text(DEMO_PASSWORD, color = c.textSecondary, style = qasaText.detailMono, maxLines = 1) }
            }
        }
    }
}

@Composable
private fun DemoField(label: String, value: String, focused: Boolean) {
    val c = qasaColors
    val shape = RoundedCornerShape(QasaDimens.controlRadius)
    Column(
        Modifier
            .padding(horizontal = QasaDimens.sectionInsetHorizontal)
            .padding(bottom = QasaDimens.contentGap)
            .fillMaxWidth()
            .border(QasaDimens.stroke, if (focused) c.accent else c.stroke, shape)
            .padding(horizontal = QasaDimens.gapMedium, vertical = QasaDimens.gapTiny),
    ) {
        Text(label, color = c.textTertiary, style = qasaText.detailSemibold)
        Ltr {
            Text(
                value.ifEmpty { " " },
                color = c.textPrimary,
                style = qasaText.bodyMedium,
                maxLines = 1,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

// Page 3, a two-step code

@Composable
private fun CodeDemo() {
    val c = qasaColors
    val reduceMotion = qasaReduceMotion.enabled
    var codeIndex by remember { mutableIntStateOf(0) }
    val left = remember { Animatable(CODE_START_LEFT) }
    val shown = rememberStagger(1)

    LaunchedEffect(reduceMotion) {
        if (reduceMotion) return@LaunchedEffect
        delay(CODE_START_DELAY_MS)
        while (true) {
            // The ring runs down at one steady pace; when it empties the code changes.
            val remaining = (left.value * CODE_PERIOD_MS).toInt()
            left.animateTo(0f, tween(remaining, easing = QasaTokens.Easings.linear))
            codeIndex = (codeIndex + 1) % DEMO_CODES.size
            left.snapTo(1f)
        }
    }

    DemoCard(Modifier.staggered(0, shown)) {
        DemoRow(QasaIcons.Login, NoteColor.Blue, "GitHub", DEMO_EMAIL, Modifier.padding(top = QasaDimens.spaceSmall))
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = QasaDimens.sectionInsetHorizontal)
                .padding(bottom = QasaDimens.gap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Ltr {
                AnimatedContent(
                    targetState = codeIndex,
                    transitionSpec = { fadeIn(QasaMotion.standardTween()) togetherWith fadeOut(QasaMotion.standardTween()) },
                    label = "tourCode",
                ) { i -> Text(DEMO_CODES[i], color = c.textPrimary, style = qasaText.passwordCompact) }
            }
            Spacer(Modifier.weight(1f))
            val track = c.stroke
            val ring = c.accent
            Canvas(Modifier.size(QasaIconSize.x4l).clearAndSetSemantics {}) {
                val stroke = QasaDimens.spaceSmall.toPx()
                val inset = stroke / 2f
                val arcSize = Size(size.width - stroke, size.height - stroke)
                drawArc(track, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
                drawArc(
                    ring,
                    -90f,
                    360f * left.value,
                    false,
                    Offset(inset, inset),
                    arcSize,
                    style = Stroke(stroke, cap = StrokeCap.Round),
                )
            }
        }
    }
}

// Page 4, password health

@Composable
private fun HealthDemo() {
    val c = qasaColors
    val shown = rememberStagger(1)
    DemoCard(Modifier.staggered(0, shown)) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(vertical = QasaDimens.sectionInsetHorizontal),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(QasaDimens.gapTiny),
        ) {
            Text(stringResource(R.string.nav_passwordhealth), color = c.textPrimary, style = qasaText.bodyMediumSemibold)
            // The app's own gauge: it sweeps to the score over `motion.gauge`.
            HalfCircleGauge(score = DEMO_HEALTH_SCORE)
            Row(horizontalArrangement = Arrangement.spacedBy(QasaDimens.contentGap)) {
                HealthChip(stringResource(R.string.onboarding_mockweak, 2), NoteColor.Gold)
                HealthChip(stringResource(R.string.onboarding_mockreused, 1), NoteColor.Purple)
            }
        }
    }
}

@Composable
private fun HealthChip(text: String, color: NoteColor) {
    Text(
        text,
        color = color.swatch(),
        style = qasaText.sectionLabelSemibold,
        modifier = Modifier
            .background(color.tile(), RoundedCornerShape(50))
            .padding(horizontal = QasaDimens.gapMedium, vertical = QasaDimens.spaceTight),
    )
}

private const val DEMO_EMAIL = "you@example.com"
private const val DEMO_PASSWORD = "Kx9#mT2v!qL7&wR4"
private val DEMO_CODES = listOf("482 913", "731 046", "205 588", "964 120")
private const val DEMO_HEALTH_SCORE = 82

// The demos' own choreography, as on iOS.
private const val BAR_START_DELAY_MS = 30L
private const val STAGGER_FIRST_MS = 50L
private const val COPY_BODY_DELAY_MS = 80
private const val SIGNUP_START_MS = 500L
private const val SIGNUP_BEFORE_SUGGEST_MS = 250L
private const val SIGNUP_BEFORE_TAP_MS = 900L
private const val SIGNUP_TAP_HOLD_MS = 200L
private const val CODE_START_DELAY_MS = 400L
private const val CODE_START_LEFT = 0.65f

/** iOS: the ring loses 5% every 0.3 s, so a full code lasts 6 s. */
private const val CODE_PERIOD_MS = 6000

private val RISE_OFFSET = 14.dp
private val STAGGER_OFFSET = 18.dp
private const val STAGGER_SCALE = 0.96f
