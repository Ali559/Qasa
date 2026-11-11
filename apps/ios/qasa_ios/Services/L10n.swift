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

    /// A message for `error` in the app's language. App errors (`LocalizedError`s built on
    /// `L10n`) speak for themselves; system errors (no network, cancelled…) would come back
    /// in the device's language, so they get the app's own wording instead.
    static func message(for error: Error) -> String {
        if let api = error as? QasaAPIError, case .network(let underlying) = api {
            return message(for: underlying)
        }
        if error is URLError { return tr("error.network") }
        let ns = error as NSError
        if ns.domain == NSURLErrorDomain { return tr("error.network") }
        if error is LocalizedError, let text = (error as? LocalizedError)?.errorDescription, !text.isEmpty {
            return text
        }
        return tr("error.generic")
    }

    /// The language picked in Settings → Language. Read through `AppPreferences` (observable),
    /// so a view that calls `tr` re-renders the moment the language changes.
    static var preferredLocale: Locale {
        AppLanguage.current.locale
    }
}

extension AppLanguage {
    /// The app's language: `AppPreferences.shared.language`, the one source every screen reads.
    /// It used to read `UserDefaults` directly, which SwiftUI can't observe, so screens built
    /// on it (password health, breach monitor, the item editor…) kept an old language.
    static var current: AppLanguage {
        AppPreferences.shared.language
    }
}
