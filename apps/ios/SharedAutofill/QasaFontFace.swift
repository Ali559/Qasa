import SwiftUI
import UIKit

/// IBM Plex Sans Arabic (SIL OFL, `qasa_ios/Resources/IBMPlexSansArabic-OFL.txt`) for every
/// language: one face covers Latin, Arabic and Kurdish (Sorani). Static files, one per weight;
/// the app and the AutoFill extension both register them in `UIAppFonts`.
enum QasaFontFace {
    static let regular = "IBMPlexSansArabic-Regular"
    static let medium = "IBMPlexSansArabic-Medium"
    static let semibold = "IBMPlexSansArabic-SemiBold"
    static let bold = "IBMPlexSansArabic-Bold"

    /// The static file for a weight (no light/thin files ship, so lighter weights use Regular).
    static func postScriptName(for weight: Font.Weight) -> String {
        switch weight {
        case .medium: return medium
        case .semibold: return semibold
        case .bold, .heavy, .black: return bold
        default: return regular
        }
    }

    static func postScriptName(for weight: UIFont.Weight) -> String {
        if weight >= .bold { return bold }
        if weight >= .semibold { return semibold }
        if weight >= .medium { return medium }
        return regular
    }

    /// Scales with Dynamic Type relative to `.body` (SwiftUI's default for custom fonts).
    static func font(size: CGFloat, weight: Font.Weight = .regular) -> Font {
        .custom(postScriptName(for: weight), size: size)
    }

    /// Unscaled UIKit font (bar appearances), falling back to the system font.
    static func uiFont(size: CGFloat, weight: UIFont.Weight = .regular) -> UIFont {
        UIFont(name: postScriptName(for: weight), size: size) ?? .systemFont(ofSize: size, weight: weight)
    }
}
