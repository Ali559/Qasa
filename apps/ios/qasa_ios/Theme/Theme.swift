import SwiftUI
import UIKit

/// Semantic color palette for light and dark appearance.
struct ThemePalette: Equatable {
    let background: Color
    let chrome: Color
    let cardBackground: Color
    let cardBackgroundLight: Color
    let cardMuted: Color
    let stroke: Color
    let strokeChrome: Color

    let mint: Color
    let mintDark: Color
    let mintOnPrimary: Color
    let mintOnChip: Color
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

    static let dark = ThemePalette(
        background: Color(hex: "0F172A"),
        chrome: Color(hex: "131315"),
        cardBackground: Color(hex: "1E293B"),
        cardBackgroundLight: Color(hex: "1F1F21"),
        cardMuted: Color(hex: "1B1B1D"),
        stroke: Color(hex: "334155"),
        strokeChrome: Color(hex: "45464D"),
        mint: Color(hex: "4EDEA3"),
        mintDark: Color(hex: "00A572"),
        mintOnPrimary: Color(hex: "003824"),
        mintOnChip: Color(hex: "00311F"),
        gold: Color(hex: "FBBF24"),
        goldMuted: Color(hex: "F59E0B"),
        cyan: Color(hex: "4CD7F6"),
        info: Color(hex: "60A5FA"),
        success: Color(hex: "10B981"),
        danger: Color(hex: "EF4444"),
        dangerBackground: Color(hex: "93000A"),
        dangerText: Color(hex: "FFB4AB"),
        dangerTextMuted: Color(hex: "FFDAD6"),
        textPrimary: Color(hex: "E4E2E4"),
        textSecondary: Color(hex: "C6C6CD"),
        textTertiary: Color(hex: "64748B"),
        textPlaceholder: Color(hex: "6B7280"),
        textOnChrome: Color(hex: "BEC6E0")
    )

    static let light = ThemePalette(
        background: Color(hex: "F4F6FA"),
        chrome: Color(hex: "FFFFFF"),
        cardBackground: Color(hex: "FFFFFF"),
        cardBackgroundLight: Color(hex: "EEF2F7"),
        cardMuted: Color(hex: "E8EEF6"),
        stroke: Color(hex: "D0D7E2"),
        strokeChrome: Color(hex: "CBD5E1"),
        mint: Color(hex: "00A572"),
        mintDark: Color(hex: "008F63"),
        mintOnPrimary: Color(hex: "FFFFFF"),
        mintOnChip: Color(hex: "00311F"),
        gold: Color(hex: "D97706"),
        goldMuted: Color(hex: "B45309"),
        cyan: Color(hex: "0891B2"),
        info: Color(hex: "2563EB"),
        success: Color(hex: "059669"),
        danger: Color(hex: "DC2626"),
        dangerBackground: Color(hex: "FEE2E2"),
        dangerText: Color(hex: "991B1B"),
        dangerTextMuted: Color(hex: "B91C1C"),
        textPrimary: Color(hex: "0F172A"),
        textSecondary: Color(hex: "475569"),
        textTertiary: Color(hex: "94A3B8"),
        textPlaceholder: Color(hex: "94A3B8"),
        textOnChrome: Color(hex: "334155")
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

    static let mint = Color(light: ThemePalette.light.mint, dark: ThemePalette.dark.mint)
    static let mintDark = Color(light: ThemePalette.light.mintDark, dark: ThemePalette.dark.mintDark)
    static let mintOnPrimary = Color(light: ThemePalette.light.mintOnPrimary, dark: ThemePalette.dark.mintOnPrimary)
    static let mintOnChip = Color(light: ThemePalette.light.mintOnChip, dark: ThemePalette.dark.mintOnChip)
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

    /// Legacy aliases (same adaptive tokens).
    static let lightBackground = background
    static let lightChrome = chrome
    static let lightTextPrimary = textPrimary
    static let lightTextSecondary = textSecondary

    static let cornerRadius: CGFloat = 12
    static let cornerRadiusLarge: CGFloat = 16
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

    /// Body / UI text — Rudaw for ckb/ar, system otherwise.
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
