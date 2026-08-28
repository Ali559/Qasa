import Foundation
import SwiftUI

/// Programmatic localization via String Catalog (`Localizable.xcstrings`).
/// Uses `LocalizedStringResource` so in-app language switches resolve `en` / `ckb` / `ar`
/// without requiring a restart or changing the system language.
enum L10n {
    static func tr(_ key: String.LocalizationValue, locale: Locale = preferredLocale) -> String {
        String(localized: LocalizedStringResource(key, locale: locale))
    }

    static func tr(_ key: String, locale: Locale = preferredLocale) -> String {
        String(
            localized: LocalizedStringResource(
                String.LocalizationValue(stringLiteral: key),
                locale: locale
            )
        )
    }

    /// Locale stored by Settings → Language (`qasa.language`).
    static var preferredLocale: Locale {
        AppLanguage.current.locale
    }
}

extension AppLanguage {
    /// Reads the persisted preference (safe for non-UI call sites).
    static var current: AppLanguage {
        let raw = UserDefaults.standard.string(forKey: "qasa.language") ?? "en"
        return AppLanguage(rawValue: raw) ?? .en
    }
}
