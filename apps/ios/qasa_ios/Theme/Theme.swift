import SwiftUI
import UIKit

/// Semantic color palette for light and dark appearance, built from the brand palette
/// #722F99 (purple: actions), #C5C1C1 (grey: strokes, secondary text), #FEE7C8 (beige:
/// canvas, warm wash, gold) and #FF9292 (peach: VPN accent, danger in dark).
/// Values come from `design/tokens.json` (`ThemePalette+Tokens.swift`), like Android `QasaColors`
/// and the desktop `tokens.css`.
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

    // `dark` and `light` are generated from design/tokens.json: ThemePalette+Tokens.swift.

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

    // Sizes come from design/tokens.json (`QasaTokens`), shared with Android and the desktop.
    static let cornerRadius: CGFloat = QasaTokens.Radius.card
    static let cornerRadiusLarge: CGFloat = QasaTokens.Radius.cardLarge
    static let screenPadding: CGFloat = QasaTokens.Space.screenPadding

    // MARK: Control sizes (one height for every field and every full-width action button).
    /// Every single-line input: text, secure, search, date and picker-styled fields.
    static let fieldHeight: CGFloat = QasaTokens.Size.fieldHeight
    /// Every full-width primary / secondary / destructive action button.
    static let buttonHeight: CGFloat = QasaTokens.Size.buttonHeight
    /// Corner radius shared by fields and action buttons.
    static let controlCornerRadius: CGFloat = QasaTokens.Radius.control
    /// Horizontal inset of text inside a field.
    static let fieldHorizontalPadding: CGFloat = QasaTokens.Space.fieldHorizontal
    /// Minimum height of a multi-line notes area (exempt from `fieldHeight`).
    static let notesMinHeight: CGFloat = QasaTokens.Size.notesMinHeight
    /// Gap between a field's label and its box, and between stacked form fields.
    static let fieldLabelSpacing: CGFloat = QasaTokens.Space.fieldLabelGap
    static let formSpacing: CGFloat = QasaTokens.Space.formGap

    // MARK: Settings / list rows.
    static let rowMinHeight: CGFloat = QasaTokens.Size.rowMinHeight
    static let iconBadgeSize: CGFloat = QasaTokens.Size.iconBadge
    static let iconBadgeCornerRadius: CGFloat = QasaTokens.Radius.iconBadge
    static let iconBadgeGlyphSize: CGFloat = QasaTokens.Size.iconBadgeGlyph
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
