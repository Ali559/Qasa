import SwiftUI
import UIKit

/// App-wide type: IBM Plex Sans Arabic for English, Arabic and Kurdish alike (`QasaFontFace`).
enum AppTypography {
    static func font(size: CGFloat, weight: Font.Weight = .regular) -> Font {
        QasaFontFace.font(size: size, weight: weight)
    }

    static func uiFont(size: CGFloat, weight: UIFont.Weight = .regular) -> UIFont {
        QasaFontFace.uiFont(size: size, weight: weight)
    }

    /// Apply navigation / bar button / tab bar fonts. Called again when the language changes.
    static func applyChromeFonts(for language: AppLanguage) {
        let titleFont = uiFont(size: 17, weight: .semibold)
        let barFont = uiFont(size: 17, weight: .regular)

        let nav = UINavigationBarAppearance()
        nav.configureWithDefaultBackground()
        nav.titleTextAttributes = [.font: titleFont]
        nav.largeTitleTextAttributes = [.font: uiFont(size: 34, weight: .bold)]
        UINavigationBar.appearance().standardAppearance = nav
        UINavigationBar.appearance().compactAppearance = nav
        UINavigationBar.appearance().scrollEdgeAppearance = nav

        UIBarButtonItem.appearance().setTitleTextAttributes([.font: barFont], for: .normal)
        UIBarButtonItem.appearance().setTitleTextAttributes([.font: barFont], for: .highlighted)
        UIBarButtonItem.appearance().setTitleTextAttributes([.font: barFont], for: .disabled)

        let tabFont = uiFont(size: 10, weight: .medium)
        UITabBarItem.appearance().setTitleTextAttributes([.font: tabFont], for: .normal)
        UITabBarItem.appearance().setTitleTextAttributes([.font: tabFont], for: .selected)
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
