import SwiftUI

enum Theme {
    // Surfaces — adapt lightly for light mode via preferredColorScheme on the root;
    // tokens remain dark-first for the vault chrome (product default).
    static let background = Color(hex: "0F172A")
    static let chrome = Color(hex: "131315")
    static let cardBackground = Color(hex: "1E293B")
    static let cardBackgroundLight = Color(hex: "1F1F21")
    static let cardMuted = Color(hex: "1B1B1D")
    static let stroke = Color(hex: "334155")
    static let strokeChrome = Color(hex: "45464D")

    // Brand
    static let mint = Color(hex: "4EDEA3")
    static let mintDark = Color(hex: "00A572")
    static let mintOnPrimary = Color(hex: "003824")
    static let mintOnChip = Color(hex: "00311F")
    static let gold = Color(hex: "FBBF24")
    static let goldMuted = Color(hex: "F59E0B")
    static let cyan = Color(hex: "4CD7F6")
    static let info = Color(hex: "60A5FA")

    // Status
    static let success = Color(hex: "10B981")
    static let danger = Color(hex: "EF4444")
    static let dangerBackground = Color(hex: "93000A")
    static let dangerText = Color(hex: "FFB4AB")
    static let dangerTextMuted = Color(hex: "FFDAD6")

    // Text
    static let textPrimary = Color(hex: "E4E2E4")
    static let textSecondary = Color(hex: "C6C6CD")
    static let textTertiary = Color(hex: "45464D")
    static let textPlaceholder = Color(hex: "6B7280")
    static let textOnChrome = Color(hex: "BEC6E0")

    // Light-mode companions (used where views opt in)
    static let lightBackground = Color(hex: "F4F6FA")
    static let lightChrome = Color(hex: "FFFFFF")
    static let lightTextPrimary = Color(hex: "0F172A")
    static let lightTextSecondary = Color(hex: "475569")

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
}

extension Font {
    static func kasoMono(_ size: CGFloat, weight: Font.Weight = .medium) -> Font {
        .system(size: size, weight: weight, design: .monospaced)
    }
}
