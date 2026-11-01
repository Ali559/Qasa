package alibarznji.qasa.theme

import alibarznji.qasa.prefs.AppLanguage
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
 * Semantic palette. The calm + personal redesign (`docs/design/mobile-redesign-mockup.html`):
 * a misty lilac base, lavender for actions, and a warm peach wash for "all good" states.
 * Android leads this redesign; iOS `ThemePalette` still carries the previous mint palette
 * until it is ported.
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
    background = Color(0xFFF6F4FB),
    chrome = Color(0xFFF6F4FB),
    cardBackground = Color(0xFFFFFFFF),
    cardBackgroundLight = Color(0xFFF1EEFA),
    cardMuted = Color(0xFFECE8FD),
    stroke = Color(0xFFEBE8F4),
    strokeChrome = Color(0xFFE4E0F0),
    accent = Color(0xFF7C6CF2),
    accentDark = Color(0xFF6656E0),
    onAccent = Color(0xFFFFFFFF),
    onAccentChip = Color(0xFF3A2FA0),
    gold = Color(0xFFE09A2B),
    goldMuted = Color(0xFFB7791F),
    cyan = Color(0xFF3FA7C9),
    info = Color(0xFF5B7CF0),
    success = Color(0xFF2F9E6C),
    danger = Color(0xFFE45D5D),
    dangerBackground = Color(0xFFFDEAEA),
    dangerText = Color(0xFF9E2F2F),
    dangerTextMuted = Color(0xFFB23C3C),
    textPrimary = Color(0xFF24213A),
    textSecondary = Color(0xFF6B6785),
    textTertiary = Color(0xFFA19DB8),
    textPlaceholder = Color(0xFFA19DB8),
    textOnChrome = Color(0xFF3A3654),
    warm = Color(0xFFFFD9C7),
    calm = Color(0xFFE6E3F2),
    scrim = Color(0xFF000000).copy(alpha = 0.45f),
)

val DarkQasaColors = QasaColors(
    background = Color(0xFF17152A),
    chrome = Color(0xFF17152A),
    cardBackground = Color(0xFF221F3A),
    cardBackgroundLight = Color(0xFF2A2745),
    cardMuted = Color(0xFF2F2B52),
    stroke = Color(0xFF332F4F),
    strokeChrome = Color(0xFF3A3658),
    accent = Color(0xFFA79BFF),
    accentDark = Color(0xFF7C6CF2),
    onAccent = Color(0xFF1B1640),
    onAccentChip = Color(0xFFE8E4FF),
    gold = Color(0xFFF5B84A),
    goldMuted = Color(0xFFE09A2B),
    cyan = Color(0xFF6CC6E3),
    info = Color(0xFF8FA6FF),
    success = Color(0xFF5CCB97),
    danger = Color(0xFFF27F7F),
    dangerBackground = Color(0xFF4A2030),
    dangerText = Color(0xFFFFB8B8),
    dangerTextMuted = Color(0xFFFFD3D3),
    textPrimary = Color(0xFFEEECF8),
    textSecondary = Color(0xFFB5B1CC),
    textTertiary = Color(0xFF7D7998),
    textPlaceholder = Color(0xFF7D7998),
    textOnChrome = Color(0xFFCFCBE6),
    warm = Color(0xFF3D2B3B),
    calm = Color(0xFF26233F),
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
        )
    }

    // Status bar and navigation bar icons track the resolved theme. Without this,
    // light theme renders light-on-light icons that are effectively invisible (§11).
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
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
