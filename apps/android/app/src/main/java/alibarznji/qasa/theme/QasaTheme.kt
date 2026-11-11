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
 * states. Every value comes from [QasaTokens.Light] / [QasaTokens.Dark] (generated from
 * `design/tokens.json`, like the iOS `ThemePalette`); only [scrim] is Android's own.
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
    background = QasaTokens.Light.background,
    chrome = QasaTokens.Light.chrome,
    cardBackground = QasaTokens.Light.cardBackground,
    cardBackgroundLight = QasaTokens.Light.cardBackgroundLight,
    cardMuted = QasaTokens.Light.cardMuted,
    stroke = QasaTokens.Light.stroke,
    strokeChrome = QasaTokens.Light.strokeChrome,
    accent = QasaTokens.Light.accent,
    accentDark = QasaTokens.Light.accentDark,
    onAccent = QasaTokens.Light.onAccent,
    onAccentChip = QasaTokens.Light.onAccentChip,
    gold = QasaTokens.Light.gold,
    goldMuted = QasaTokens.Light.goldMuted,
    cyan = QasaTokens.Light.cyan,
    info = QasaTokens.Light.info,
    success = QasaTokens.Light.success,
    danger = QasaTokens.Light.danger,
    dangerBackground = QasaTokens.Light.dangerBackground,
    dangerText = QasaTokens.Light.dangerText,
    dangerTextMuted = QasaTokens.Light.dangerTextMuted,
    textPrimary = QasaTokens.Light.textPrimary,
    textSecondary = QasaTokens.Light.textSecondary,
    textTertiary = QasaTokens.Light.textTertiary,
    textPlaceholder = QasaTokens.Light.textPlaceholder,
    textOnChrome = QasaTokens.Light.textOnChrome,
    warm = QasaTokens.Light.warm,
    calm = QasaTokens.Light.calm,
    scrim = Color(0xFF000000).copy(alpha = 0.45f),
)

val DarkQasaColors = QasaColors(
    background = QasaTokens.Dark.background,
    chrome = QasaTokens.Dark.chrome,
    cardBackground = QasaTokens.Dark.cardBackground,
    cardBackgroundLight = QasaTokens.Dark.cardBackgroundLight,
    cardMuted = QasaTokens.Dark.cardMuted,
    stroke = QasaTokens.Dark.stroke,
    strokeChrome = QasaTokens.Dark.strokeChrome,
    accent = QasaTokens.Dark.accent,
    accentDark = QasaTokens.Dark.accentDark,
    onAccent = QasaTokens.Dark.onAccent,
    onAccentChip = QasaTokens.Dark.onAccentChip,
    gold = QasaTokens.Dark.gold,
    goldMuted = QasaTokens.Dark.goldMuted,
    cyan = QasaTokens.Dark.cyan,
    info = QasaTokens.Dark.info,
    success = QasaTokens.Dark.success,
    danger = QasaTokens.Dark.danger,
    dangerBackground = QasaTokens.Dark.dangerBackground,
    dangerText = QasaTokens.Dark.dangerText,
    dangerTextMuted = QasaTokens.Dark.dangerTextMuted,
    textPrimary = QasaTokens.Dark.textPrimary,
    textSecondary = QasaTokens.Dark.textSecondary,
    textTertiary = QasaTokens.Dark.textTertiary,
    textPlaceholder = QasaTokens.Dark.textPlaceholder,
    textOnChrome = QasaTokens.Dark.textOnChrome,
    warm = QasaTokens.Dark.warm,
    calm = QasaTokens.Dark.calm,
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
        // M3 buttons (TextButton, dialog actions) take labelLarge: the 16 semibold button style.
        labelLarge = t.button,
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
            // Every remaining Material role from the palette too: an unset role falls back
            // to Material's stock lavender, and `surfaceTint` (default: primary) washes
            // raised surfaces — menus, dialogs, sheets — in purple that iOS never shows.
            surfaceTint = colors.cardBackground,
            surfaceBright = colors.cardBackground,
            surfaceDim = colors.background,
            onError = Color.White, // iOS: white on a danger fill in both themes
            onTertiary = colors.onAccent,
            tertiaryContainer = colors.cardMuted,
            onTertiaryContainer = colors.onAccentChip,
            inversePrimary = colors.accentDark,
            inverseSurface = colors.textPrimary,
            inverseOnSurface = colors.background,
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
            // Every remaining Material role from the palette too: an unset role falls back
            // to Material's stock lavender, and `surfaceTint` (default: primary) washes
            // raised surfaces — menus, dialogs, sheets — in purple that iOS never shows.
            surfaceTint = colors.cardBackground,
            surfaceBright = colors.cardBackground,
            surfaceDim = colors.background,
            onError = Color.White, // iOS: white on a danger fill in both themes
            onTertiary = colors.onAccent,
            tertiaryContainer = colors.cardMuted,
            onTertiaryContainer = colors.onAccentChip,
            inversePrimary = colors.accentDark,
            inverseSurface = colors.textPrimary,
            inverseOnSurface = colors.background,
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
