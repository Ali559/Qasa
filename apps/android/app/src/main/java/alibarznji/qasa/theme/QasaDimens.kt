package alibarznji.qasa.theme

import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Spacing, shape and the de-facto constants iOS hardcodes at its call sites.
 * `docs/design.md` §4.1 — every raw `N.dp` outside this object is a defect.
 */
object QasaDimens {
    // ---- Shape: rounder than iOS Theme.swift:157-159 for the calm + personal redesign ----
    val cornerRadius: Dp = 20.dp
    val cornerRadiusLarge: Dp = 24.dp
    val screenPadding: Dp = 20.dp

    /** 4 — `PillBadge`. */
    val radiusTiny: Dp = 4.dp

    /** 8 — chips, text-field-adjacent surfaces, icon tiles. */
    val radiusSmall: Dp = 12.dp

    /** 10 — drawer rows, folder tiles. */
    val radiusMedium: Dp = 14.dp

    // ---- Layout ----
    val headerHeight: Dp = 64.dp

    /**
     * 48 — the one height of every single-line input: text, password, search, date,
     * dropdown-as-field. Multi-line fields grow from it.
     */
    val fieldHeight: Dp = 48.dp

    /** 48 — the one height of every full-width action button (primary and secondary). */
    val buttonHeight: Dp = 48.dp

    /** 40 — trailing icon buttons inside a [fieldHeight] field (reveal, generate, clear). */
    val fieldIconButton: Dp = 40.dp

    /** 36 — the rounded-square icon badge on settings and list rows. */
    val rowIconBadge: Dp = 36.dp

    /** 10 — corner radius of [rowIconBadge]. */
    val rowIconBadgeRadius: Dp = 10.dp

    /** 56 — minimum height of a settings / navigation row. */
    val rowMinHeight: Dp = 56.dp

    /** 17 — the single most-repeated magic number in the codebase. */
    val cardPadding: Dp = 17.dp

    /** 24 — gap between sections. */
    val sectionGap: Dp = 24.dp

    /** 8 — gap inside a section. */
    val contentGap: Dp = 8.dp

    /** 40 — trailing space below the last element in a scroll. */
    val scrollTail: Dp = 40.dp

    /** 96 — vertical clearance so a FAB never covers the last row. */
    val fabClearance: Dp = 96.dp

    /**
     * 88 — the toast's distance above the navigation bar: clears the shell tab bar (~67)
     * and a [StickyBottomBar] holding one button (~73).
     */
    val toastBottomClearance: Dp = 88.dp

    /** 16 — header horizontal padding and the standard inter-element gap. */
    val gap: Dp = 16.dp

    /** 12 — gap between an icon slot and its label. */
    val gapSmall: Dp = 12.dp

    /** 20 — drawer width. */
    val drawerWidth: Dp = 320.dp

    /** 18 — drawer header padding. */
    val drawerPadding: Dp = 18.dp

    /** 18 — gap inside a section group. */
    val gapXLarge: Dp = 18.dp

    /** 14 — gap between a drawer row icon and its label. */
    val drawerGap: Dp = 14.dp

    /** 13 — vertical padding of the compact choice rows. */
    val padTight: Dp = 13.dp

    /** 10 — inline gap inside a dense row. */
    val gapMedium: Dp = 10.dp

    /** 6 — gap between a value and its unit. */
    val gapTiny: Dp = 6.dp

    /** 5 — vertical padding inside the monospace value field. */
    val spaceNano: Dp = 5.dp

    /** 3 — inset around the plan-value dot. */
    val spaceTight: Dp = 3.dp

    /** 4 — gap between a stacked value and its label. */
    val spaceSmall: Dp = 4.dp

    /** 4 — the "this device" accent bar width. */
    val accentBarWidth: Dp = 4.dp

    /** 2 — inner padding of a 7dp inline dot. */
    val spaceMicro: Dp = 2.dp

    /** 2 — selection ring on a plan-value dot or the settings avatar. */
    val ringStroke: Dp = 2.dp

    /** 1 — hairline borders and dividers. */
    val stroke: Dp = 1.dp

    /** 7 — the plan-value dot diameter. */
    val planDot: Dp = 7.dp

    /** 72 — minimum height of an empty-state block. */
    val emptyStateMin: Dp = 72.dp

    /** 80 — fixed hero slot on the auth screens. */
    val heroArtHeight: Dp = 80.dp

    /** 220 — onboarding illustration height. */
    val onboardingArt: Dp = 220.dp

    /** 240 — minimum plan-card height. */
    val planCardHeight: Dp = 240.dp

    /** 118 — half-circle gauge height. */
    val gaugeHeight: Dp = 118.dp

    /** 220 — half-circle gauge width. */
    val gaugeWidth: Dp = 220.dp

    /** 14 — gauge arc thickness. */
    val gaugeStroke: Dp = 14.dp

    /** 20 — connected-state shadow on the VPN dial core. */
    val dialCoreElevation: Dp = 20.dp

    /** 128 — VPN dial inner ring. */
    val dialInner: Dp = 128.dp

    /** 140 — VPN dial mid ring. */
    val dialMid: Dp = 140.dp

    /** 160 — VPN dial outer ring. */
    val dialOuter: Dp = 160.dp

    /** 48dp minimum interactive target (§9.1). */
    val touchTarget: Dp = 48.dp

    /** 520 — the store-error column cap (`VaultStoreErrorView`, iOS `maxWidth: 520`). */
    val vaultErrorMaxWidth: Dp = 520.dp
}

/** Icon sizes. §5 — the full permitted set, so no caller invents one. */
object QasaIconSize {
    val xs: Dp = 8.dp
    val xxs: Dp = 10.dp
    val xxxs: Dp = 11.dp
    val tiny: Dp = 12.dp
    val sm: Dp = 14.dp
    val md: Dp = 16.dp
    val menu: Dp = 17.dp
    val lg: Dp = 18.dp
    val xl: Dp = 20.dp
    val xxl: Dp = 22.dp
    val x3l: Dp = 24.dp
    val x4l: Dp = 28.dp
    val x5l: Dp = 36.dp
    val x6l: Dp = 40.dp
    val x7l: Dp = 44.dp
    val x8l: Dp = 56.dp
    val watermark: Dp = 88.dp
}

/** Icon container frames. §5. */
object QasaIconFrame {
    val chip: Dp = 14.dp
    val brandShield: Dp = 20.dp
    val toggleDisc: Dp = 40.dp
    val tile: Dp = 48.dp
    val fab: Dp = 56.dp
    val shield: Dp = 88.dp
}

/**
 * The sheet maximum height: 92% of the window, and always below the status bar (on
 * phones where `screenHeightDp` leaves the system bars out, 92% of it could reach it).
 */
@Composable
fun qasaSheetMaxHeight(): Dp {
    val density = LocalDensity.current
    val window = with(density) { LocalWindowInfo.current.containerSize.height.toDp() }
    val statusBar = with(density) { WindowInsets.statusBars.getTop(this).toDp() }
    val screen = if (window > 0.dp) window else LocalConfiguration.current.screenHeightDp.dp
    return minOf(screen * 0.92f, screen - statusBar - QasaDimens.gap)
}
