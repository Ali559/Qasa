import SwiftUI
import UIKit

/// The brand palette for the extensions (AutoFill, payment notification), which do not link
/// the app's `Theme`. Values are byte-identical to `ThemePalette` in `qasa_ios/Theme/Theme.swift`.
enum QasaSharedPalette {
    static let accent = adaptive(light: 0x722F99, dark: 0xB67FDB)
    static let onAccent = adaptive(light: 0xFFFFFF, dark: 0x22102F)
    static let danger = adaptive(light: 0xC8323C, dark: 0xFF6B6B)
    static let background = adaptive(light: 0xFBF6F0, dark: 0x17111D)
    static let card = adaptive(light: 0xFFFFFF, dark: 0x231A2B)
    static let stroke = adaptive(light: 0xE6E1E1, dark: 0x352B3D)
    static let textPrimary = adaptive(light: 0x2A1F33, dark: 0xF2EDF4)
    static let textSecondary = adaptive(light: 0x6E6475, dark: 0xC5C1C1)

    private static func adaptive(light: UInt32, dark: UInt32) -> Color {
        Color(uiColor: UIColor { traits in
            uiColor(traits.userInterfaceStyle == .dark ? dark : light)
        })
    }

    private static func uiColor(_ rgb: UInt32) -> UIColor {
        UIColor(
            red: CGFloat((rgb >> 16) & 0xFF) / 255,
            green: CGFloat((rgb >> 8) & 0xFF) / 255,
            blue: CGFloat(rgb & 0xFF) / 255,
            alpha: 1
        )
    }
}

/// Control sizes shared by the app (`Theme.fieldHeight`, `Theme.buttonHeight`) and the
/// extensions, so every field and every action button is the same height everywhere.
enum QasaMetrics {
    static let fieldHeight: CGFloat = 48
    static let buttonHeight: CGFloat = 48
    static let controlCornerRadius: CGFloat = 14
}

extension View {
    /// Extension-side field box: `QasaMetrics.fieldHeight`, card fill, stroke.
    func qasaExtensionField() -> some View {
        self
            .font(QasaFontFace.font(size: 16))
            .foregroundStyle(QasaSharedPalette.textPrimary)
            .tint(QasaSharedPalette.accent)
            .padding(.horizontal, 14)
            .frame(maxWidth: .infinity, minHeight: QasaMetrics.fieldHeight, maxHeight: QasaMetrics.fieldHeight)
            .background(QasaSharedPalette.card)
            .clipShape(RoundedRectangle(cornerRadius: QasaMetrics.controlCornerRadius, style: .continuous))
            .overlay(
                RoundedRectangle(cornerRadius: QasaMetrics.controlCornerRadius, style: .continuous)
                    .stroke(QasaSharedPalette.stroke, lineWidth: 1)
            )
    }
}

/// Extension-side primary action: accent fill, `QasaMetrics.buttonHeight`.
struct QasaExtensionPrimaryButtonStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        StyledLabel(configuration: configuration)
    }

    private struct StyledLabel: View {
        @Environment(\.isEnabled) private var isEnabled
        let configuration: ButtonStyle.Configuration

        var body: some View {
            configuration.label
                .font(QasaFontFace.font(size: 16, weight: .semibold))
                .foregroundStyle(QasaSharedPalette.onAccent)
                .frame(maxWidth: .infinity)
                .frame(height: QasaMetrics.buttonHeight)
                .background(
                    RoundedRectangle(cornerRadius: QasaMetrics.controlCornerRadius, style: .continuous)
                        .fill(QasaSharedPalette.accent)
                )
                .opacity(isEnabled ? (configuration.isPressed ? 0.85 : 1) : 0.45)
        }
    }
}
