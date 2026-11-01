import SwiftUI
import UIKit

/// Semantic color palette for light and dark appearance. The calm + personal redesign
/// (`docs/design/mobile-redesign-mockup.html`): misty lilac base, lavender for actions,
/// a warm peach wash for "all good". Byte-identical to Android `QasaColors`.
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
        background: Color(hex: "17152A"),
        chrome: Color(hex: "17152A"),
        cardBackground: Color(hex: "221F3A"),
        cardBackgroundLight: Color(hex: "2A2745"),
        cardMuted: Color(hex: "2F2B52"),
        stroke: Color(hex: "332F4F"),
        strokeChrome: Color(hex: "3A3658"),
        accent: Color(hex: "A79BFF"),
        accentDark: Color(hex: "7C6CF2"),
        onAccent: Color(hex: "1B1640"),
        onAccentChip: Color(hex: "E8E4FF"),
        gold: Color(hex: "F5B84A"),
        goldMuted: Color(hex: "E09A2B"),
        cyan: Color(hex: "6CC6E3"),
        info: Color(hex: "8FA6FF"),
        success: Color(hex: "5CCB97"),
        danger: Color(hex: "F27F7F"),
        dangerBackground: Color(hex: "4A2030"),
        dangerText: Color(hex: "FFB8B8"),
        dangerTextMuted: Color(hex: "FFD3D3"),
        textPrimary: Color(hex: "EEECF8"),
        textSecondary: Color(hex: "B5B1CC"),
        textTertiary: Color(hex: "7D7998"),
        textPlaceholder: Color(hex: "7D7998"),
        textOnChrome: Color(hex: "CFCBE6"),
        warm: Color(hex: "3D2B3B"),
        calm: Color(hex: "26233F")
    )

    static let light = ThemePalette(
        background: Color(hex: "F6F4FB"),
        chrome: Color(hex: "F6F4FB"),
        cardBackground: Color(hex: "FFFFFF"),
        cardBackgroundLight: Color(hex: "F1EEFA"),
        cardMuted: Color(hex: "ECE8FD"),
        stroke: Color(hex: "EBE8F4"),
        strokeChrome: Color(hex: "E4E0F0"),
        accent: Color(hex: "7C6CF2"),
        accentDark: Color(hex: "6656E0"),
        onAccent: Color(hex: "FFFFFF"),
        onAccentChip: Color(hex: "3A2FA0"),
        gold: Color(hex: "E09A2B"),
        goldMuted: Color(hex: "B7791F"),
        cyan: Color(hex: "3FA7C9"),
        info: Color(hex: "5B7CF0"),
        success: Color(hex: "2F9E6C"),
        danger: Color(hex: "E45D5D"),
        dangerBackground: Color(hex: "FDEAEA"),
        dangerText: Color(hex: "9E2F2F"),
        dangerTextMuted: Color(hex: "B23C3C"),
        textPrimary: Color(hex: "24213A"),
        textSecondary: Color(hex: "6B6785"),
        textTertiary: Color(hex: "A19DB8"),
        textPlaceholder: Color(hex: "A19DB8"),
        textOnChrome: Color(hex: "3A3654"),
        warm: Color(hex: "FFD9C7"),
        calm: Color(hex: "E6E3F2")
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

    /// Body / UI text — Rudaw for ckb/ar, Rubik otherwise.
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
