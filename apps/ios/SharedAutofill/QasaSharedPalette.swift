import SwiftUI
import UIKit

/// The brand palette for the extensions (AutoFill, payment notification), which do not link
/// the app's `Theme`. Values come from `QasaTokens` (design/tokens.json), like the app's `ThemePalette`.
enum QasaSharedPalette {
    static let accent = adaptive(light: QasaTokens.Light.accent, dark: QasaTokens.Dark.accent)
    static let onAccent = adaptive(light: QasaTokens.Light.onAccent, dark: QasaTokens.Dark.onAccent)
    static let danger = adaptive(light: QasaTokens.Light.danger, dark: QasaTokens.Dark.danger)
    static let background = adaptive(light: QasaTokens.Light.background, dark: QasaTokens.Dark.background)
    static let card = adaptive(light: QasaTokens.Light.cardBackground, dark: QasaTokens.Dark.cardBackground)
    static let stroke = adaptive(light: QasaTokens.Light.stroke, dark: QasaTokens.Dark.stroke)
    static let textPrimary = adaptive(light: QasaTokens.Light.textPrimary, dark: QasaTokens.Dark.textPrimary)
    static let textSecondary = adaptive(light: QasaTokens.Light.textSecondary, dark: QasaTokens.Dark.textSecondary)

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
    static let fieldHeight: CGFloat = QasaTokens.Size.fieldHeight
    static let buttonHeight: CGFloat = QasaTokens.Size.buttonHeight
    static let controlCornerRadius: CGFloat = QasaTokens.Radius.control
}

extension View {
    /// Extension-side field box: `QasaMetrics.fieldHeight`, card fill, stroke.
    func qasaExtensionField() -> some View {
        self
            .font(QasaFontFace.font(size: 16))
            .foregroundStyle(QasaSharedPalette.textPrimary)
            .tint(QasaSharedPalette.accent)
            .padding(.horizontal, QasaTokens.Space.fieldHorizontal)
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
