import Foundation
import SwiftUI

enum AppLanguage: String, CaseIterable, Identifiable, Sendable {
    case en, ckb, ar

    var id: String { rawValue }

    var displayName: String {
        switch self {
        case .en: return "English"
        case .ckb: return "کوردی"
        case .ar: return "العربية"
        }
    }

    var locale: Locale {
        switch self {
        case .en: return Locale(identifier: "en")
        case .ckb: return Locale(identifier: "ckb")
        case .ar: return Locale(identifier: "ar")
        }
    }

    var layoutDirection: LayoutDirection {
        switch self {
        case .en: return .leftToRight
        case .ckb, .ar: return .rightToLeft
        }
    }
}

enum AppThemePreference: String, CaseIterable, Identifiable {
    case system, light, dark

    var id: String { rawValue }

    var displayNameKey: String.LocalizationValue {
        switch self {
        case .system: return "settings.theme.system"
        case .light: return "settings.theme.light"
        case .dark: return "settings.theme.dark"
        }
    }

    var displayName: String {
        switch self {
        case .system: return "System"
        case .light: return "Light"
        case .dark: return "Dark"
        }
    }
}

@Observable
final class AppPreferences {
    static let shared = AppPreferences()

    private let defaults = UserDefaults.standard
    private let langKey = "qasa.language"
    private let themeKey = "qasa.theme"

    var language: AppLanguage {
        didSet {
            defaults.set(language.rawValue, forKey: langKey)
            // Helps Bundle / catalog lookups that still consult preferred languages.
            defaults.set([language.rawValue], forKey: "AppleLanguages")
            AppTypography.applyChromeFonts(for: language)
            Task { await syncLocaleToServer() }
        }
    }

    var theme: AppThemePreference {
        didSet { defaults.set(theme.rawValue, forKey: themeKey) }
    }

    var locale: Locale { language.locale }
    var layoutDirection: LayoutDirection { language.layoutDirection }

    private init() {
        let lang = defaults.string(forKey: langKey).flatMap(AppLanguage.init(rawValue:)) ?? .en
        let themePref = defaults.string(forKey: themeKey).flatMap(AppThemePreference.init(rawValue:)) ?? .system
        self.language = lang
        self.theme = themePref
        defaults.set([lang.rawValue], forKey: "AppleLanguages")
        AppTypography.applyChromeFonts(for: lang)
    }

    func syncLocaleToServer() async {
        guard VaultKeychainStore.licenseToken != nil else { return }
        struct Ok: Decodable { let ok: Bool? }
        let body = DevicePrefsRequest(
            locale: language.rawValue,
            push_token: nil,
            notifications_enabled: nil
        )
        _ = try? await QasaAPIClient.shared.request(
            QasaConfig.devicePrefsURL,
            method: "PATCH",
            body: body
        ) as Ok
    }
}
