import SwiftUI
import UIKit

/// Semantic color palette for light and dark appearance, built from the brand palette
/// #722F99 (purple: actions), #C5C1C1 (grey: strokes, secondary text), #FEE7C8 (beige:
/// canvas, warm wash, gold) and #FF9292 (peach: VPN accent, danger in dark).
/// Byte-identical to Android `QasaColors` and the generated web `tokens.css`.
struct ThemePalette: Equatable {
    let background: Color
    let chrome: Color
    let cardBackground: Color
    let cardBackgroundLight: Color
    let cardMuted: Color
    let stroke: Color
    let strokeChrome: Color

    /// Primary action colour: buttons, selected tabs, links.
    let accent: Color
    let accentDark: Color
    /// Text and icons drawn on an `accent` fill.
    let onAccent: Color
    /// Text on a pale `cardMuted` accent chip.
    let onAccentChip: Color
    let gold: Color
    let goldMuted: Color
    let cyan: Color
    let info: Color

    let success: Color
    let danger: Color
    let dangerBackground: Color
    let dangerText: Color
    let dangerTextMuted: Color

    let textPrimary: Color
    let textSecondary: Color
    let textTertiary: Color
    let textPlaceholder: Color
    let textOnChrome: Color

    /// Warm wash behind the Home status when nothing needs attention.
    let warm: Color
    /// Cool wash behind the Home status when something does.
    let calm: Color

    static let dark = ThemePalette(
        background: Color(hex: "17111D"),
        chrome: Color(hex: "17111D"),
        cardBackground: Color(hex: "231A2B"),
        cardBackgroundLight: Color(hex: "2B2134"),
        cardMuted: Color(hex: "33243F"),
        stroke: Color(hex: "352B3D"),
        strokeChrome: Color(hex: "3F3448"),
        accent: Color(hex: "B67FDB"),
        accentDark: Color(hex: "722F99"),
        onAccent: Color(hex: "22102F"),
        onAccentChip: Color(hex: "F1E3FA"),
        gold: Color(hex: "FEE7C8"),
        goldMuted: Color(hex: "F0C48C"),
        cyan: Color(hex: "FF9292"),
        info: Color(hex: "C9A3E6"),
        success: Color(hex: "5CCB97"),
        danger: Color(hex: "FF6B6B"),
        dangerBackground: Color(hex: "4A1F28"),
        dangerText: Color(hex: "FFC0C0"),
        dangerTextMuted: Color(hex: "FFD9D9"),
        textPrimary: Color(hex: "F2EDF4"),
        textSecondary: Color(hex: "C5C1C1"),
        textTertiary: Color(hex: "8A8290"),
        textPlaceholder: Color(hex: "8A8290"),
        textOnChrome: Color(hex: "D9D3DD"),
        warm: Color(hex: "3E2B2F"),
        calm: Color(hex: "2A2430")
    )

    static let light = ThemePalette(
        background: Color(hex: "FBF6F0"),
        chrome: Color(hex: "FBF6F0"),
        cardBackground: Color(hex: "FFFFFF"),
        cardBackgroundLight: Color(hex: "FFF3E3"),
        cardMuted: Color(hex: "F3EAF7"),
        stroke: Color(hex: "E6E1E1"),
        strokeChrome: Color(hex: "DCD7D7"),
        accent: Color(hex: "722F99"),
        accentDark: Color(hex: "5C2580"),
        onAccent: Color(hex: "FFFFFF"),
        onAccentChip: Color(hex: "4A1A66"),
        gold: Color(hex: "B7701E"),
        goldMuted: Color(hex: "94591A"),
        cyan: Color(hex: "E2707A"),
        info: Color(hex: "8A4FB3"),
        success: Color(hex: "2F9E6C"),
        danger: Color(hex: "C8323C"),
        dangerBackground: Color(hex: "FFE6E6"),
        dangerText: Color(hex: "9E2F35"),
        dangerTextMuted: Color(hex: "B83C44"),
        textPrimary: Color(hex: "2A1F33"),
        textSecondary: Color(hex: "6E6475"),
        textTertiary: Color(hex: "A39DA3"),
        textPlaceholder: Color(hex: "A39DA3"),
        textOnChrome: Color(hex: "3E3346"),
        warm: Color(hex: "FEE7C8"),
        calm: Color(hex: "EDE9E9")
    )

    static func resolved(for scheme: ColorScheme) -> ThemePalette {
        scheme == .dark ? .dark : .light
    }
}

private struct ThemePaletteKey: EnvironmentKey {
    static let defaultValue = ThemePalette.dark
}

extension EnvironmentValues {
    var themePalette: ThemePalette {
        get { self[ThemePaletteKey.self] }
        set { self[ThemePaletteKey.self] = newValue }
    }
}

/// Adaptive tokens that follow the effective color scheme (system / light / dark).
/// Prefer `@Environment(\.themePalette)` in new code; these remain for gradual migration.
enum Theme {
    static let background = Color(light: ThemePalette.light.background, dark: ThemePalette.dark.background)
    static let chrome = Color(light: ThemePalette.light.chrome, dark: ThemePalette.dark.chrome)
    static let cardBackground = Color(light: ThemePalette.light.cardBackground, dark: ThemePalette.dark.cardBackground)
    static let cardBackgroundLight = Color(
        light: ThemePalette.light.cardBackgroundLight,
        dark: ThemePalette.dark.cardBackgroundLight
    )
    static let cardMuted = Color(light: ThemePalette.light.cardMuted, dark: ThemePalette.dark.cardMuted)
    static let stroke = Color(light: ThemePalette.light.stroke, dark: ThemePalette.dark.stroke)
    static let strokeChrome = Color(light: ThemePalette.light.strokeChrome, dark: ThemePalette.dark.strokeChrome)

    static let accent = Color(light: ThemePalette.light.accent, dark: ThemePalette.dark.accent)
    static let accentDark = Color(light: ThemePalette.light.accentDark, dark: ThemePalette.dark.accentDark)
    static let onAccent = Color(light: ThemePalette.light.onAccent, dark: ThemePalette.dark.onAccent)
    static let onAccentChip = Color(light: ThemePalette.light.onAccentChip, dark: ThemePalette.dark.onAccentChip)
    static let gold = Color(light: ThemePalette.light.gold, dark: ThemePalette.dark.gold)
    static let goldMuted = Color(light: ThemePalette.light.goldMuted, dark: ThemePalette.dark.goldMuted)
    static let cyan = Color(light: ThemePalette.light.cyan, dark: ThemePalette.dark.cyan)
    static let info = Color(light: ThemePalette.light.info, dark: ThemePalette.dark.info)

    static let success = Color(light: ThemePalette.light.success, dark: ThemePalette.dark.success)
    static let danger = Color(light: ThemePalette.light.danger, dark: ThemePalette.dark.danger)
    static let dangerBackground = Color(
        light: ThemePalette.light.dangerBackground,
        dark: ThemePalette.dark.dangerBackground
    )
    static let dangerText = Color(light: ThemePalette.light.dangerText, dark: ThemePalette.dark.dangerText)
    static let dangerTextMuted = Color(
        light: ThemePalette.light.dangerTextMuted,
        dark: ThemePalette.dark.dangerTextMuted
    )

    static let textPrimary = Color(light: ThemePalette.light.textPrimary, dark: ThemePalette.dark.textPrimary)
    static let textSecondary = Color(light: ThemePalette.light.textSecondary, dark: ThemePalette.dark.textSecondary)
    static let textTertiary = Color(light: ThemePalette.light.textTertiary, dark: ThemePalette.dark.textTertiary)
    static let textPlaceholder = Color(
        light: ThemePalette.light.textPlaceholder,
        dark: ThemePalette.dark.textPlaceholder
    )
    static let textOnChrome = Color(light: ThemePalette.light.textOnChrome, dark: ThemePalette.dark.textOnChrome)
    static let warm = Color(light: ThemePalette.light.warm, dark: ThemePalette.dark.warm)
    static let calm = Color(light: ThemePalette.light.calm, dark: ThemePalette.dark.calm)

    /// Legacy aliases (same adaptive tokens).
    static let lightBackground = background
    static let lightChrome = chrome
    static let lightTextPrimary = textPrimary
    static let lightTextSecondary = textSecondary

    // Rounder than before for the calm + personal redesign (matches Android QasaDimens).
    static let cornerRadius: CGFloat = 20
    static let cornerRadiusLarge: CGFloat = 24
    static let screenPadding: CGFloat = 20

    // MARK: Control sizes (one height for every field and every full-width action button).
    /// Every single-line input: text, secure, search, date and picker-styled fields.
    static let fieldHeight: CGFloat = QasaMetrics.fieldHeight
    /// Every full-width primary / secondary / destructive action button.
    static let buttonHeight: CGFloat = QasaMetrics.buttonHeight
    /// Corner radius shared by fields and action buttons.
    static let controlCornerRadius: CGFloat = QasaMetrics.controlCornerRadius
    /// Horizontal inset of text inside a field.
    static let fieldHorizontalPadding: CGFloat = 14
    /// Minimum height of a multi-line notes area (exempt from `fieldHeight`).
    static let notesMinHeight: CGFloat = 112
    /// Gap between a field's label and its box, and between stacked form fields.
    static let fieldLabelSpacing: CGFloat = 8
    static let formSpacing: CGFloat = 16

    // MARK: Settings / list rows.
    static let rowMinHeight: CGFloat = 60
    static let iconBadgeSize: CGFloat = 36
    static let iconBadgeCornerRadius: CGFloat = 10
    static let iconBadgeGlyphSize: CGFloat = 16
}

extension Color {
    init(hex: String) {
        let scanner = Scanner(string: hex)
        var rgb: UInt64 = 0
        scanner.scanHexInt64(&rgb)
        let r = Double((rgb & 0xFF0000) >> 16) / 255
        let g = Double((rgb & 0x00FF00) >> 8) / 255
        let b = Double(rgb & 0x0000FF) / 255
        self.init(red: r, green: g, blue: b)
    }

    init(light: Color, dark: Color) {
        self.init(
            uiColor: UIColor { traits in
                traits.userInterfaceStyle == .dark ? UIColor(dark) : UIColor(light)
            }
        )
    }
}

extension Font {
    static func kasoMono(_ size: CGFloat, weight: Font.Weight = .medium) -> Font {
        .system(size: size, weight: weight, design: .monospaced)
    }

    /// Small labels, pills and badges: sentence case in the body face. The redesign dropped the
    /// all-caps monospace labels because they read as technical; `kasoMono` is for secrets only.
    static func qasaLabel(_ size: CGFloat) -> Font {
        AppTypography.font(size: size + 1, weight: .medium)
    }

    /// Body / UI text — IBM Plex Sans Arabic for every language.
    static func qasa(_ size: CGFloat, weight: Font.Weight = .regular) -> Font {
        AppTypography.font(size: size, weight: weight)
    }
}

extension View {
    func themedBackground() -> some View {
        modifier(ThemedBackgroundModifier())
    }
}

private struct ThemedBackgroundModifier: ViewModifier {
    @Environment(\.themePalette) private var theme

    func body(content: Content) -> some View {
        content.background(theme.background.ignoresSafeArea())
    }
}
