import SwiftUI

enum Theme {
    static let background = Color(hex: "0A0E1A")
    static let cardBackground = Color(hex: "121A2C")
    static let cardBackgroundLight = Color(hex: "182238")
    static let stroke = Color.white.opacity(0.08)

    static let mint = Color(hex: "2FE6A3")
    static let mintDark = Color(hex: "1CA97A")
    static let gold = Color(hex: "F5B429")
    static let danger = Color(hex: "EF4444")
    static let dangerBackground = Color(hex: "3A1418")

    static let textPrimary = Color.white
    static let textSecondary = Color.white.opacity(0.55)
    static let textTertiary = Color.white.opacity(0.35)

    static let cornerRadius: CGFloat = 14
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
