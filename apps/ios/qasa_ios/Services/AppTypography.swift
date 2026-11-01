import SwiftUI
import UIKit

/// App-wide type for English (system) vs Kurdish/Arabic (`RudawRegular` from `rudawregular2.ttf`).
enum AppTypography {
    static let rudawPostScriptName = "RudawRegular"
    /// Rubik (variable, SIL OFL — `Resources/Rubik-OFL.txt`) for English: rounded and friendly,
    /// matching Android. One variable file serves every weight.
    static let rubikFamilyName = "Rubik"

    static var usesRudaw: Bool {
        switch AppLanguage.current {
        case .ckb, .ar: return true
        case .en: return false
        }
    }

    static func font(size: CGFloat, weight: Font.Weight = .regular) -> Font {
        if usesRudaw {
            return .custom(rudawPostScriptName, size: size)
        }
        return .custom(rubikFamilyName, size: size).weight(weight)
    }

    static func uiFont(size: CGFloat, weight: UIFont.Weight = .regular) -> UIFont {
        if usesRudaw, let custom = UIFont(name: rudawPostScriptName, size: size) {
            return custom
        }
        return rubikUIFont(size: size, weight: weight)
    }

    static func rubikUIFont(size: CGFloat, weight: UIFont.Weight) -> UIFont {
        let descriptor = UIFontDescriptor(fontAttributes: [.family: rubikFamilyName])
            .addingAttributes([.traits: [UIFontDescriptor.TraitKey.weight: weight]])
        let font = UIFont(descriptor: descriptor, size: size)
        return font.familyName == rubikFamilyName ? font : .systemFont(ofSize: size, weight: weight)
    }

    /// Apply navigation / bar button fonts when language changes.
    static func applyChromeFonts(for language: AppLanguage) {
        let useRudaw = language == .ckb || language == .ar
        let titleFont = useRudaw
            ? (UIFont(name: rudawPostScriptName, size: 17) ?? .systemFont(ofSize: 17, weight: .semibold))
            : rubikUIFont(size: 17, weight: .semibold)
        let barFont = useRudaw
            ? (UIFont(name: rudawPostScriptName, size: 17) ?? .systemFont(ofSize: 17, weight: .regular))
            : rubikUIFont(size: 17, weight: .regular)

        let nav = UINavigationBarAppearance()
        nav.configureWithDefaultBackground()
        nav.titleTextAttributes = [.font: titleFont]
        nav.largeTitleTextAttributes = [.font: titleFont.withSize(34)]
        UINavigationBar.appearance().standardAppearance = nav
        UINavigationBar.appearance().compactAppearance = nav
        UINavigationBar.appearance().scrollEdgeAppearance = nav

        UIBarButtonItem.appearance().setTitleTextAttributes([.font: barFont], for: .normal)
        UIBarButtonItem.appearance().setTitleTextAttributes([.font: barFont], for: .highlighted)
        UIBarButtonItem.appearance().setTitleTextAttributes([.font: barFont], for: .disabled)
    }
}

extension View {
    /// Sheets often drop in-app locale / RTL — re-apply from preferences.
    func appLocalization() -> some View {
        let prefs = AppPreferences.shared
        return self
            .environment(\.locale, prefs.locale)
            .environment(\.layoutDirection, prefs.layoutDirection)
    }
}
