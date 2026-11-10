package alibarznji.qasa.screens.premium

import alibarznji.qasa.R
import alibarznji.qasa.network.PricingPlanDTO
import alibarznji.qasa.theme.QasaDimens
import alibarznji.qasa.theme.QasaIconSize
import alibarznji.qasa.theme.QasaTracking
import alibarznji.qasa.theme.qasaColors
import alibarznji.qasa.theme.qasaText
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import java.text.NumberFormat
import java.util.Locale

/** iOS `interval == "yearly"`; also accepts the older Android "year" spelling. */
internal val PricingPlanDTO.isYearly: Boolean
    get() = interval.contains("year", ignoreCase = true)

/**
 * `PricingPlanDTO.displayAmount` (PricingService.swift:67): locale-aware grouping in the app
 * language, so "10,000" in en and the locale's own digits/separators in ar/ckb.
 */
internal fun PricingPlanDTO.displayAmount(locale: Locale): String =
    NumberFormat.getIntegerInstance(locale).apply { isGroupingUsed = true }.format(amount_iqd)

/** The app-language locale (LocaleContext wraps the activity configuration). */
@Composable
internal fun appLocale(): Locale = LocalConfiguration.current.locales[0] ?: Locale.getDefault()

/** `planCarousel` (PremiumUpgradeView.swift:173): paged cards + 7pt dots, 12pt apart. */
@Composable
internal fun PremiumPlanCarousel(plans: List<PricingPlanDTO>, pagerState: PagerState) {
    val c = qasaColors
    val density = LocalDensity.current
    // iOS fixes the TabView at 240pt. Feature rows in ckb/ar can run taller, so every page is
    // composed (beyondViewportPageCount) and all cards grow to the tallest one; swiping then
    // never changes the carousel height.
    var tallest by remember { mutableIntStateOf(0) }
    val minHeight = maxOf(QasaDimens.planCardHeight, with(density) { tallest.toDp() })
    Column(verticalArrangement = Arrangement.spacedBy(QasaDimens.gapSmall)) {
        HorizontalPager(
            state = pagerState,
            beyondViewportPageCount = plans.size,
            verticalAlignment = Alignment.Top,
            modifier = Modifier.fillMaxWidth(),
        ) { page ->
            PlanCard(
                plan = plans[page],
                selected = page == pagerState.currentPage,
                modifier = Modifier
                    // Vertical room too, so the pager never clips the card's border.
                    .padding(horizontal = QasaDimens.spaceMicro, vertical = QasaDimens.spaceMicro)
                    .heightIn(min = minHeight)
                    .onSizeChanged { if (it.height > tallest) tallest = it.height },
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(QasaDimens.contentGap, Alignment.CenterHorizontally),
        ) {
            plans.indices.forEach { index ->
                Box(
                    Modifier
                        .size(QasaDimens.planDot)
                        .clip(CircleShape)
                        .background(if (index == pagerState.currentPage) c.accent else c.stroke),
                )
            }
        }
    }
}

/** `planCard` (PremiumUpgradeView.swift:195). */
@Composable
private fun PlanCard(plan: PricingPlanDTO, selected: Boolean, modifier: Modifier = Modifier) {
    val c = qasaColors
    val shape = RoundedCornerShape(QasaDimens.cornerRadius)
    val features = buildList {
        add(stringResource(R.string.premium_featuredevices))
        // "Breached password check" (the email leak alerts this key once named are parked).
        add(stringResource(R.string.premium_featurebreach))
        add(stringResource(R.string.premium_featurevpn))
        if (plan.isYearly) add(stringResource(R.string.premium_featuresave))
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(c.cardBackgroundLight, shape)
            // Mint outline on the selected plan, stroke otherwise (iOS :233).
            .border(QasaDimens.stroke, if (selected) c.accent else c.stroke, shape),
    ) {
        // iOS: 128pt gold @10% circle, blurred 20, offset (40, -40) at the top trailing corner.
        // A radial gradient gives the same glow without Modifier.blur (API 31+ only).
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .offset(x = QasaIconSize.x6l, y = -QasaIconSize.x6l)
                .size(QasaDimens.dialInner)
                .background(Brush.radialGradient(listOf(c.gold.copy(alpha = 0.1f), Color.Transparent))),
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(QasaDimens.cardPadding + QasaDimens.contentGap),
            verticalArrangement = Arrangement.spacedBy(QasaDimens.screenPadding),
        ) {
            PlanHeader(plan)
            Column(verticalArrangement = Arrangement.spacedBy(QasaDimens.gapSmall)) {
                features.forEach { FeatureRow(it) }
            }
        }
    }
}

@Composable
private fun PlanHeader(plan: PricingPlanDTO) {
    val c = qasaColors
    val text = qasaText
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(QasaDimens.contentGap),
    ) {
        Text(
            stringResource(if (plan.isYearly) R.string.premium_bestvalue else R.string.premium_upgradetopro),
            color = c.gold,
            style = text.sectionLabel.copy(letterSpacing = QasaTracking.eyebrow),
        )
        Row {
            Text(
                plan.displayAmount(appLocale()),
                color = c.textPrimary,
                style = text.displayLarge,
                modifier = Modifier.alignByBaseline(),
            )
            Spacer(Modifier.width(QasaDimens.spaceSmall))
            Text(
                stringResource(if (plan.isYearly) R.string.premium_periodyear else R.string.premium_periodmonth),
                color = c.textSecondary,
                style = text.bodyMedium,
                modifier = Modifier.alignByBaseline(),
            )
        }
        Text(
            stringResource(if (plan.id == "premium_yearly") R.string.sub_planyearly else R.string.sub_planmonthly),
            color = c.accent,
            // 11 semibold for ckb/ar, mono 11 otherwise.
            style = if (text.arabicScript) text.detailSemibold else text.detailMono,
        )
    }
}

/** `FeatureRow` (PremiumUpgradeView.swift:400): accent check 18, 12pt gap, 14pt text. */
@Composable
private fun FeatureRow(label: String) {
    val c = qasaColors
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = c.accent, modifier = Modifier.size(QasaIconSize.lg))
        Spacer(Modifier.width(QasaDimens.gapSmall))
        Text(label, color = c.textPrimary, style = qasaText.bodyMedium)
    }
}
