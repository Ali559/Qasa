package alibarznji.qasa.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import alibarznji.qasa.R
import alibarznji.qasa.prefs.AppLanguage

/**
 * IBM Plex Sans Arabic (SIL OFL — `licenses/IBMPlexSansArabic-OFL.txt`) for every
 * language: its Latin and Arabic-script glyphs share one design, so en, ar and ckb read
 * as the same app. Four static files, one per weight the scale uses; ExtraBold and Black
 * requests resolve to Bold, the nearest bundled weight.
 */
private val PlexSansArabic = FontFamily(
    Font(R.font.ibm_plex_sans_arabic_regular, FontWeight.Normal),
    Font(R.font.ibm_plex_sans_arabic_medium, FontWeight.Medium),
    Font(R.font.ibm_plex_sans_arabic_semibold, FontWeight.SemiBold),
    Font(R.font.ibm_plex_sans_arabic_bold, FontWeight.Bold),
)

/** Letter-spacing tokens. `docs/design.md` §3.3. */
object QasaTracking {
    /** The house mono-label tracking. */
    val monoLabel = 0.6.sp

    /** Drawer section labels. */
    val drawerLabel = 0.8.sp

    /** "BEST VALUE" eyebrow. */
    val eyebrow = 1.2.sp

    /** Generated password, compact. */
    val passwordCompact = 1.5.sp

    /** Generated password, full. */
    val passwordFull = 2.8.sp
}

/**
 * The Android mirror of the iOS fixed type scale (`docs/design.md` §3.2) plus the
 * two families from §3.1.
 *
 * Every style is built through [style], so the ckb/ar rules — drop the mono family
 * for the body family, zero the letter spacing, never uppercase — are decided in
 * exactly one place instead of the 5 copy-pasted ternaries on iOS.
 */
@Immutable
class QasaText internal constructor(
    private val body: FontFamily,
    private val mono: FontFamily,
    /** ar/ckb: Arabic script, which must not be letter-spaced or set in Latin monospace. */
    val arabicScript: Boolean,
) {
    private fun style(
        size: Int,
        weight: FontWeight = FontWeight.Normal,
        tracking: androidx.compose.ui.unit.TextUnit = 0.sp,
        monospaced: Boolean = false,
    ): TextStyle = TextStyle(
        fontFamily = if (monospaced) mono else body,
        fontWeight = weight,
        fontSize = size.sp,
        // The font's own line height (ascent + descent = 1.5 × size), which is what iOS
        // lays text out with: centered, untrimmed, no extra font padding.
        lineHeight = (size * QasaTokens.Typography.LINE_HEIGHT_MULTIPLE).sp,
        lineHeightStyle = QasaLineHeightStyle,
        platformStyle = QasaPlatformTextStyle,
        // Letter spacing breaks cursive joining in Arabic script, so ckb/ar gets zero.
        letterSpacing = if (arabicScript) 0.sp else tracking,
    )

    /** A named style from `design/tokens.json` `type` ([QasaTokens.Typography]). */
    private fun token(t: QasaTokenText): TextStyle = TextStyle(
        fontFamily = body,
        fontWeight = t.weight,
        fontSize = t.size,
        lineHeight = t.lineHeight,
        lineHeightStyle = QasaLineHeightStyle,
        platformStyle = QasaPlatformTextStyle,
        letterSpacing = 0.sp,
    )

    // ---- Named styles (`design/tokens.json` `type`, the iOS values) -------

    /** 34 bold — `type.largeTitle`. */
    val tokenLargeTitle get() = token(QasaTokens.Typography.largeTitle)

    /** 20 bold — `type.pageTitle`: the [AppMenuHeader] title. */
    val headerTitle get() = token(QasaTokens.Typography.pageTitle)

    /** 17 semibold — `type.sheetTitle`: every bottom sheet's centered title. */
    val sheetTitle get() = token(QasaTokens.Typography.sheetTitle)

    /** 17 semibold — `type.navTitle`. */
    val navTitle get() = token(QasaTokens.Typography.navTitle)

    /** 17 regular — `type.barButton`. */
    val barButton get() = token(QasaTokens.Typography.barButton)

    /** 19 semibold — `type.dialogTitle`: confirmation sheet title. */
    val dialogTitle get() = token(QasaTokens.Typography.dialogTitle)

    /** 15 regular — `type.dialogMessage`: confirmation sheet message. */
    val dialogMessage get() = token(QasaTokens.Typography.dialogMessage)

    /** 16 regular — `type.field`: text typed into fields and notes, and their placeholders. */
    val field get() = token(QasaTokens.Typography.field)

    /** 12 semibold — `type.fieldLabel`. */
    val fieldLabel get() = token(QasaTokens.Typography.fieldLabel)

    /** 11 regular — `type.fieldError`. */
    val fieldError get() = token(QasaTokens.Typography.fieldError)

    /** 16 semibold — `type.button`: every [PrimaryButton] / [SecondaryButton] label. */
    val button get() = token(QasaTokens.Typography.button)

    /** 15 medium — `type.rowTitle`: settings / navigation row title. */
    val settingsRowTitle get() = token(QasaTokens.Typography.rowTitle)

    /** 12 regular — `type.rowSubtitle`. */
    val rowSubtitle get() = token(QasaTokens.Typography.rowSubtitle)

    /** 13 regular — `type.rowValue`: trailing row value. */
    val rowValue get() = token(QasaTokens.Typography.rowValue)

    /** 12 semibold — `type.sectionLabel`: `SectionLabel`, every language. */
    val tokenSectionLabel get() = token(QasaTokens.Typography.sectionLabel)

    /** 13 medium — `type.pill`. */
    val pill get() = token(QasaTokens.Typography.pill)

    /** 14 semibold — `type.toast`. */
    val toast get() = token(QasaTokens.Typography.toast)

    /** 10 medium — `type.tabLabel`. */
    val tabLabel get() = token(QasaTokens.Typography.tabLabel)

    /** 15 regular — `type.body`. */
    val body15 get() = token(QasaTokens.Typography.body)

    /** 12 regular — `type.caption`. */
    val caption get() = token(QasaTokens.Typography.caption)

    /**
     * Labels keep the case they were written in. The redesign dropped the all-caps
     * monospace labels because they read as technical; kept as a hook so callers need not change.
     */
    fun label(text: String): String = text

    /**
     * Escape hatch for the two brand-lockup headers, whose `titleSize` is a caller
     * parameter on iOS (24 centered / 20 menu). Still routed through [style], so the
     * ckb/ar family and tracking rules hold.
     */
    fun styleForSize(size: Int, weight: FontWeight): TextStyle = style(size, weight)

    // ---- Display ----------------------------------------------------------

    /** 34 semibold — nav large title. */
    val navLargeTitle get() = style(34, FontWeight.SemiBold)

    /** 28 bold — auth headers, plan price. */
    val displayLarge get() = style(28, FontWeight.Bold)

    /** 28 bold — screen hero: VPN status, health stats. */
    val hero get() = style(28, FontWeight.Bold)

    /** 26 bold — store-error title. */
    val errorTitle get() = style(26, FontWeight.Bold)

    /** 24 bold — standalone brand header, legal title. */
    val brandStandalone get() = style(24, FontWeight.Bold)

    /** 22 regular — plain section headline. */
    val sectionRegular get() = style(22)

    /** 22 bold — section headlines, drawer brand, plan price. */
    val sectionHeadline get() = style(22, FontWeight.Bold)

    /** 22 heavy — drawer brand. */
    val brandHeavy get() = style(22, FontWeight.Black)

    /** 20 semibold — page titles, stat values, premium sheet title. */
    val pageTitle get() = style(20, FontWeight.SemiBold)

    /** 20 semibold — stat values. */
    val statValue get() = style(20, FontWeight.SemiBold)

    /** 20 regular — plain stat value. */
    val statRegular get() = style(20)

    /** 20 bold — heavy stat value. */
    val statBold get() = style(20, FontWeight.Bold)

    // ---- Body -------------------------------------------------------------

    /** 18 semibold — primary button label, breach card title. */
    val buttonLabel get() = style(18, FontWeight.SemiBold)

    /** 18 semibold — card titles. */
    val cardTitle get() = style(18, FontWeight.SemiBold)

    /** 18 bold — bold variant of the 18pt slot. */
    val cardTitleBold get() = style(18, FontWeight.Bold)

    /** 18 regular — plain card body. */
    val cardBody get() = style(18)

    /** 17 semibold — modal CTAs, drawer display name, chrome bar buttons. */
    val ctaLabel get() = style(17, FontWeight.SemiBold)

    /** 17 regular — plain chrome bar button. */
    val barLabel get() = style(17, FontWeight.Normal)

    /** 16 regular — the default body size: text fields, subtitles. */
    val bodyLarge get() = style(16)

    /** 16 semibold — row titles, toggle titles. */
    val rowTitle get() = style(16, FontWeight.SemiBold)

    /** 16 medium — settings / navigation row titles ([QasaRow]). */
    val rowTitleMedium get() = style(16, FontWeight.Medium)

    /** 15 regular — legal body, shield text. */
    val bodyLegal get() = style(15)

    /** 15 medium — secondary shield text. */
    val bodyMedium15 get() = style(15, FontWeight.Medium)

    /** 15 semibold — emphasised 15pt. */
    val bodySemibold15 get() = style(15, FontWeight.SemiBold)

    /** 14 regular — the standard body size: row subtitles, feature rows. */
    val bodyMedium get() = style(14)

    /** 14 medium — chip-adjacent emphasis. */
    val bodyMediumMedium get() = style(14, FontWeight.Medium)

    /** 14 semibold — detail labels, selected chip labels. */
    val bodyMediumSemibold get() = style(14, FontWeight.SemiBold)

    /** 13 regular — secondary and error body. */
    val bodySmall get() = style(13)

    /** 13 medium. */
    val bodySmallMedium get() = style(13, FontWeight.Medium)

    /** 13 semibold. */
    val bodySmallSemibold get() = style(13, FontWeight.SemiBold)

    // ---- Labels -----------------------------------------------------------

    /** 13 medium — section labels, status pills, badges, stat labels (sentence case, body face). */
    val sectionLabel get() = style(13, FontWeight.Medium)

    /** 12 semibold + 0 tracking — the ckb/ar half of `SectionLabel`. */
    val sectionLabelSemibold get() = style(12, FontWeight.SemiBold)

    /** 13 medium — group labels on the You tab. */
    val drawerSectionLabel get() = style(13, FontWeight.Medium)

    /** 12 bold + 1.2 tracking — the "BEST VALUE" eyebrow. */
    val eyebrow get() = style(12, FontWeight.Bold, QasaTracking.eyebrow)

    /** 12 regular — plain 12pt label. */
    val labelRegular get() = style(12)

    /** 11 bold — toggle checkmark. */
    val checkGlyph get() = style(11, FontWeight.Bold)

    /** 11 regular — date parse error. */
    val errorDetail get() = style(11, FontWeight.Normal)

    /** 11 semibold — emphasised 11pt. */
    val detailSemibold get() = style(11, FontWeight.SemiBold)

    /** 11 mono medium — compact diagnostics and store paths. */
    val detailMono get() = style(11, FontWeight.Medium, monospaced = true)

    /** 10 regular — inline chip icon, account-tier icon. */
    val iconInline get() = style(10)

    /** 9 bold — payment gateway wordmark. */
    val wordmark get() = style(9, FontWeight.Bold)

    // ---- Monospace --------------------------------------------------------

    /** 15 mono — secret values. */
    val bodyMono get() = style(15, FontWeight.Normal, monospaced = true)

    /** 22 mono bold + 1.5 tracking — generated password, compact. */
    val passwordCompact get() = style(22, FontWeight.Bold, QasaTracking.passwordCompact, monospaced = true)

    /** 28 mono bold + 2.8 tracking — generated password, full. */
    val passwordFull get() = style(28, FontWeight.Bold, QasaTracking.passwordFull, monospaced = true)

    // ---- Glyph slots ------------------------------------------------------
    // These exist so icon sizes stay in the scale and no caller invents a size.

    /** 88 bold — create-vault shield watermark. */
    val watermark get() = style(88, FontWeight.Bold)

    /** 44 semibold — health score numeral, shield icon. */
    val shield get() = style(44, FontWeight.SemiBold)

    /** 44 bold — bold variant of the 44pt slot. */
    val healthScore get() = style(44, FontWeight.Bold)

    /** 40 semibold — store-error icon. */
    val errorIcon get() = style(40, FontWeight.SemiBold)

    /** 36 medium — detail hero icon, VPN power glyph. */
    val glyphLarge get() = style(36, FontWeight.Medium)

    companion object {
        fun forLanguage(language: AppLanguage): QasaText {
            val arabicScript = language == AppLanguage.CKB || language == AppLanguage.AR
            // Latin monospace has no Arabic glyphs worth showing, so ckb/ar keep the body face.
            val mono = if (arabicScript) PlexSansArabic else FontFamily.Monospace
            return QasaText(body = PlexSansArabic, mono = mono, arabicScript = arabicScript)
        }
    }
}

/** Text centered in its 1.5× line box, nothing trimmed — iOS's own placement. */
private val QasaLineHeightStyle = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.None,
)

/** No Android font padding on top of the line height, so metrics match iOS. */
private val QasaPlatformTextStyle = PlatformTextStyle(includeFontPadding = false)

val LocalQasaText = staticCompositionLocalOf { QasaText.forLanguage(AppLanguage.EN) }

/** The type scale. Read through this; never build a `TextStyle` by hand. */
val qasaText: QasaText
    @Composable @ReadOnlyComposable get() = LocalQasaText.current

/** 1sp tracking reads as visible noise; keep zero-tracking styles honest. */
val TextStyle.zeroTracking: TextStyle
    @ReadOnlyComposable get() = copy(letterSpacing = 0.em)
