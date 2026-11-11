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
    // ---- Shape (`design/tokens.json` radius.*) ----
    /** 20 — cards (`radius.card`). */
    val cornerRadius: Dp = QasaTokens.Radius.card
    /** 24 — large cards, the FAB (`radius.cardLarge`). */
    val cornerRadiusLarge: Dp = QasaTokens.Radius.cardLarge
    /** 10 — every field, notes box and full-width button (`radius.control`). */
    val controlRadius: Dp = QasaTokens.Radius.control
    /** 10 — the soft wash behind a pressed row (`radius.rowPress`). */
    val rowPressRadius: Dp = QasaTokens.Radius.rowPress
    /** 28 — bottom sheet top corners (`radius.sheet`). */
    val sheetRadius: Dp = QasaTokens.Radius.sheet
    val screenPadding: Dp = QasaTokens.Space.screenPadding

    /** 4 — `PillBadge`. */
    val radiusTiny: Dp = 4.dp

    /** 12 — chips, icon tiles. Not for fields or buttons: those take [controlRadius]. */
    val radiusSmall: Dp = 12.dp

    /** 14 — drawer rows, folder tiles. */
    val radiusMedium: Dp = 14.dp

    // ---- Layout ----
    val headerHeight: Dp = QasaTokens.Size.headerHeight

    /** 16 — header side padding (`space.headerHorizontal`). */
    val headerHorizontal: Dp = QasaTokens.Space.headerHorizontal

    /**
     * 48 — the one height of every single-line input: text, password, search, date,
     * dropdown-as-field. Multi-line fields grow from it.
     */
    val fieldHeight: Dp = QasaTokens.Size.fieldHeight

    /** 112 — minimum height of a multi-line notes box, which grows from it. */
    val notesMinHeight: Dp = QasaTokens.Size.notesMinHeight

    /** 14 — a field's side padding (`space.fieldHorizontal`). */
    val fieldHorizontal: Dp = QasaTokens.Space.fieldHorizontal

    /** 10 × 8 — the notes box padding (`space.notesHorizontal` / `notesVertical`). */
    val notesHorizontal: Dp = QasaTokens.Space.notesHorizontal
    val notesVertical: Dp = QasaTokens.Space.notesVertical

    /** 8 — label above a field (`space.fieldLabelGap`). */
    val fieldLabelGap: Dp = QasaTokens.Space.fieldLabelGap

    /** 16 — between form fields (`space.formGap`). */
    val formGap: Dp = QasaTokens.Space.formGap

    /** 48 — the one height of every full-width action button (primary and secondary). */
    val buttonHeight: Dp = QasaTokens.Size.buttonHeight

    /** 20 — a button's side padding (`space.buttonHorizontal`). */
    val buttonHorizontal: Dp = QasaTokens.Space.buttonHorizontal

    /** 8 — leading icon to label (`space.buttonIconGap`). */
    val buttonIconGap: Dp = QasaTokens.Space.buttonIconGap

    /** 12 / 10 — bottom action bar vertical padding and button gap. */
    val bottomBarVertical: Dp = QasaTokens.Space.bottomBarVertical
    val bottomBarGap: Dp = QasaTokens.Space.bottomBarGap

    /** 32 — trailing icon buttons inside a [fieldHeight] field (reveal, generate, clear). */
    val fieldIconButton: Dp = QasaTokens.Size.fieldIconButton

    /** 16 — the glyph inside a [fieldIconButton]. */
    val fieldIconGlyph: Dp = QasaTokens.Size.fieldIconGlyph

    /** 15 — a button's leading glyph. */
    val buttonIconGlyph: Dp = QasaTokens.Size.buttonIconGlyph

    /** 36 — the rounded-square icon badge on settings and list rows. */
    val rowIconBadge: Dp = QasaTokens.Size.iconBadge

    /** 16 — the glyph inside [rowIconBadge]. */
    val rowIconBadgeGlyph: Dp = QasaTokens.Size.iconBadgeGlyph

    /** 13 — the trailing row chevron. */
    val rowChevronGlyph: Dp = QasaTokens.Size.rowChevronGlyph

    /** 10 — corner radius of [rowIconBadge]. */
    val rowIconBadgeRadius: Dp = QasaTokens.Radius.iconBadge

    /** 60 — minimum height of a settings / navigation row. */
    val rowMinHeight: Dp = QasaTokens.Size.rowMinHeight

    /** Row padding: 10 vertical, 14 badge-to-text, 2 title-to-subtitle. */
    val rowVertical: Dp = QasaTokens.Space.rowVertical
    val rowIconGap: Dp = QasaTokens.Space.rowIconGap
    val rowTextGap: Dp = QasaTokens.Space.rowTextGap

    /** `SettingsSection`: 8 label-to-card, card inset 14 × 4. */
    val sectionLabelGap: Dp = QasaTokens.Space.sectionLabelGap
    val sectionInsetHorizontal: Dp = QasaTokens.Space.sectionInsetHorizontal
    val sectionInsetVertical: Dp = QasaTokens.Space.sectionInsetVertical

    // ---- Bottom sheets (`design/README.md` rule 4) ----
    val sheetGrabberWidth: Dp = QasaTokens.Size.sheetGrabberWidth
    val sheetGrabberHeight: Dp = QasaTokens.Size.sheetGrabberHeight
    val sheetGrabberTop: Dp = QasaTokens.Space.sheetGrabberTop
    val sheetTitleTop: Dp = QasaTokens.Space.sheetTitleTop
    val sheetTitleBottom: Dp = QasaTokens.Space.sheetTitleBottom
    val sheetContentTop: Dp = QasaTokens.Space.sheetContentTop
    val sheetContentBottom: Dp = QasaTokens.Space.sheetContentBottom
    val sheetTextGap: Dp = QasaTokens.Space.sheetTextGap
    val sheetActionsGap: Dp = QasaTokens.Space.sheetActionsGap
    val sheetButtonGap: Dp = QasaTokens.Space.sheetButtonGap

    // ---- Toast ----
    val toastHorizontal: Dp = QasaTokens.Space.toastHorizontal
    val toastVertical: Dp = QasaTokens.Space.toastVertical

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
    val stroke: Dp = QasaTokens.Size.stroke

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
