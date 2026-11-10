package alibarznji.qasa.theme

import alibarznji.qasa.prefs.AppLanguage
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.platform.LocalLayoutDirection
import android.view.View
import alibarznji.qasa.prefs.AppThemeMode
import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * Semantic palette: a warm cream base, plum for actions, and a peach wash for "all good"
 * states. Byte-identical to the iOS `ThemePalette` (`apps/ios/qasa_ios/Theme/Theme.swift`);
 * change both together.
 */
@Immutable
data class QasaColors(
    val background: Color,
    val chrome: Color,
    val cardBackground: Color,
    val cardBackgroundLight: Color,
    val cardMuted: Color,
    val stroke: Color,
    val strokeChrome: Color,
    /** Primary action colour: buttons, selected tabs, links. */
    val accent: Color,
    val accentDark: Color,
    /** Text and icons drawn on an [accent] fill. */
    val onAccent: Color,
    /** Text on a pale [cardMuted] accent chip. */
    val onAccentChip: Color,
    val gold: Color,
    val goldMuted: Color,
    val cyan: Color,
    val info: Color,
    val success: Color,
    val danger: Color,
    val dangerBackground: Color,
    val dangerText: Color,
    val dangerTextMuted: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val textPlaceholder: Color,
    val textOnChrome: Color,
    /** Warm wash behind the Home status when nothing needs attention. */
    val warm: Color,
    /** Cool wash behind the Home status when something needs attention. */
    val calm: Color,
    /** Drawer scrim: black at 0.45 in both schemes, matching iOS `SideMenuView.swift:14`. */
    val scrim: Color,
)

val LightQasaColors = QasaColors(
    background = Color(0xFFFBF6F0),
    chrome = Color(0xFFFBF6F0),
    cardBackground = Color(0xFFFFFFFF),
    cardBackgroundLight = Color(0xFFFFF3E3),
    cardMuted = Color(0xFFF3EAF7),
    stroke = Color(0xFFE6E1E1),
    strokeChrome = Color(0xFFDCD7D7),
    accent = Color(0xFF722F99),
    accentDark = Color(0xFF5C2580),
    onAccent = Color(0xFFFFFFFF),
    onAccentChip = Color(0xFF4A1A66),
    gold = Color(0xFFB7701E),
    goldMuted = Color(0xFF94591A),
    cyan = Color(0xFFE2707A),
    info = Color(0xFF8A4FB3),
    success = Color(0xFF2F9E6C),
    danger = Color(0xFFC8323C),
    dangerBackground = Color(0xFFFFE6E6),
    dangerText = Color(0xFF9E2F35),
    dangerTextMuted = Color(0xFFB83C44),
    textPrimary = Color(0xFF2A1F33),
    textSecondary = Color(0xFF6E6475),
    textTertiary = Color(0xFFA39DA3),
    textPlaceholder = Color(0xFFA39DA3),
    textOnChrome = Color(0xFF3E3346),
    warm = Color(0xFFFEE7C8),
    calm = Color(0xFFEDE9E9),
    scrim = Color(0xFF000000).copy(alpha = 0.45f),
)

val DarkQasaColors = QasaColors(
    background = Color(0xFF17111D),
    chrome = Color(0xFF17111D),
    cardBackground = Color(0xFF231A2B),
    cardBackgroundLight = Color(0xFF2B2134),
    cardMuted = Color(0xFF33243F),
    stroke = Color(0xFF352B3D),
    strokeChrome = Color(0xFF3F3448),
    accent = Color(0xFFB67FDB),
    accentDark = Color(0xFF722F99),
    onAccent = Color(0xFF22102F),
    onAccentChip = Color(0xFFF1E3FA),
    gold = Color(0xFFFEE7C8),
    goldMuted = Color(0xFFF0C48C),
    cyan = Color(0xFFFF9292),
    info = Color(0xFFC9A3E6),
    success = Color(0xFF5CCB97),
    danger = Color(0xFFFF6B6B),
    dangerBackground = Color(0xFF4A1F28),
    dangerText = Color(0xFFFFC0C0),
    dangerTextMuted = Color(0xFFFFD9D9),
    textPrimary = Color(0xFFF2EDF4),
    textSecondary = Color(0xFFC5C1C1),
    textTertiary = Color(0xFF8A8290),
    textPlaceholder = Color(0xFF8A8290),
    textOnChrome = Color(0xFFD9D3DD),
    warm = Color(0xFF3E2B2F),
    calm = Color(0xFF2A2430),
    scrim = Color(0xFF000000).copy(alpha = 0.45f),
)

val LocalQasaColors = staticCompositionLocalOf { LightQasaColors }

/**
 * The resolved color scheme. `FilterChipButton` used to branch on a boolean carried
 * inside `QasaColors`; §11 requires branching on the resolved scheme instead, so the
 * flag lives in its own local rather than on the palette.
 */
val LocalIsDarkScheme = staticCompositionLocalOf { false }

val qasaIsDarkScheme: Boolean
    @Composable get() = LocalIsDarkScheme.current

/**
 * M3 slots are populated from the fixed scale so any M3 component that falls back to
 * `MaterialTheme.typography` still lands on a sanctioned size with an explicit
 * line height. Screens read `qasaText.<role>`, not these.
 */
private fun qasaMaterialTypography(t: QasaText): Typography {
    return Typography(
        displayLarge = t.displayLarge,
        displayMedium = t.hero,
        displaySmall = t.errorTitle,
        headlineLarge = t.hero,
        headlineMedium = t.pageTitle,
        headlineSmall = t.ctaLabel,
        titleLarge = t.cardTitle,
        titleMedium = t.rowTitle,
        titleSmall = t.bodyMediumSemibold,
        bodyLarge = t.bodyLarge,
        bodyMedium = t.bodyMedium,
        bodySmall = t.bodySmall,
        labelLarge = t.bodyMediumSemibold,
        labelMedium = t.sectionLabel,
        labelSmall = t.errorDetail,
    )
}

@Composable
fun QasaTheme(
    themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    language: AppLanguage = AppLanguage.EN,
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode) {
        AppThemeMode.SYSTEM -> isSystemInDarkTheme()
        AppThemeMode.LIGHT -> false
        AppThemeMode.DARK -> true
    }
    val colors = if (dark) DarkQasaColors else LightQasaColors
    val text = QasaText.forLanguage(language)
    val scheme = if (dark) {
        darkColorScheme(
            primary = colors.accent,
            onPrimary = colors.onAccent,
            secondary = colors.gold,
            background = colors.background,
            surface = colors.cardBackground,
            onBackground = colors.textPrimary,
            onSurface = colors.textPrimary,
            outline = colors.stroke,
            error = colors.danger,
            primaryContainer = colors.cardMuted,
            onPrimaryContainer = colors.onAccentChip,
            onSecondary = colors.onAccent,
            secondaryContainer = colors.cardMuted,
            onSecondaryContainer = colors.onAccentChip,
            tertiary = colors.info,
            surfaceVariant = colors.cardBackgroundLight,
            onSurfaceVariant = colors.textSecondary,
            surfaceContainerLowest = colors.cardBackground,
            surfaceContainerLow = colors.cardBackground,
            surfaceContainer = colors.cardBackground,
            surfaceContainerHigh = colors.cardBackground,
            surfaceContainerHighest = colors.cardBackgroundLight,
            outlineVariant = colors.strokeChrome,
            errorContainer = colors.dangerBackground,
            onErrorContainer = colors.dangerText,
            scrim = colors.scrim,
        )
    } else {
        lightColorScheme(
            primary = colors.accent,
            onPrimary = colors.onAccent,
            secondary = colors.gold,
            background = colors.background,
            onBackground = colors.textPrimary,
            surface = colors.cardBackground,
            onSurface = colors.textPrimary,
            outline = colors.stroke,
            error = colors.danger,
            primaryContainer = colors.cardMuted,
            onPrimaryContainer = colors.onAccentChip,
            onSecondary = colors.onAccent,
            secondaryContainer = colors.cardMuted,
            onSecondaryContainer = colors.onAccentChip,
            tertiary = colors.info,
            surfaceVariant = colors.cardBackgroundLight,
            onSurfaceVariant = colors.textSecondary,
            surfaceContainerLowest = colors.cardBackground,
            surfaceContainerLow = colors.cardBackground,
            surfaceContainer = colors.cardBackground,
            surfaceContainerHigh = colors.cardBackground,
            surfaceContainerHighest = colors.cardBackgroundLight,
            outlineVariant = colors.strokeChrome,
            errorContainer = colors.dangerBackground,
            onErrorContainer = colors.dangerText,
            scrim = colors.scrim,
        )
    }

    // Status bar and navigation bar icons track the resolved theme. Without this,
    // light theme renders light-on-light icons that are effectively invisible (§11).
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            // Views outside Compose (decor, IME, autofill popups) follow the app language too.
            window.decorView.layoutDirection =
                if (language.isRtl) View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }

    CompositionLocalProvider(
        LocalQasaColors provides colors,
        LocalIsDarkScheme provides dark,
        LocalQasaText provides text,
        // Explicit, not derived from the configuration: on API 31-32 the wrapped
        // configuration can be lost on recreate, and ICU may not know `ckb` is RTL.
        // Sheets and popups read this local, so they follow it too.
        LocalLayoutDirection provides if (language.isRtl) LayoutDirection.Rtl else LayoutDirection.Ltr,
    ) {
        ProvideQasaMotion {
            MaterialTheme(
                colorScheme = scheme,
                typography = qasaMaterialTypography(text),
                content = content,
            )
        }
    }
}

val qasaColors: QasaColors
    @Composable get() = LocalQasaColors.current
