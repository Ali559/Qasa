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
    private let autoLockKey = "qasa.autoLockMinutes"
    private let welcomeKey = "qasa.hasCompletedWelcome"

    var language: AppLanguage {
        didSet {
            defaults.set(language.rawValue, forKey: langKey)
            // Helps Bundle / catalog lookups that still consult preferred languages.
            defaults.set([language.rawValue], forKey: "AppleLanguages")
            UserDefaults(suiteName: AppGroup.identifier)?.set(language.rawValue, forKey: langKey)
            AppTypography.applyChromeFonts(for: language)
            Task { await syncLocaleToServer() }
        }
    }

    var theme: AppThemePreference {
        didSet { defaults.set(theme.rawValue, forKey: themeKey) }
    }

    /// Minutes of inactivity before the vault locks (1…60).
    var autoLockMinutes: Int {
        didSet {
            let clamped = min(60, max(1, autoLockMinutes))
            if clamped != autoLockMinutes {
                autoLockMinutes = clamped
                return
            }
            defaults.set(clamped, forKey: autoLockKey)
            AutoLockMonitor.shared.noteActivity()
        }
    }

    var hasCompletedWelcome: Bool {
        didSet { defaults.set(hasCompletedWelcome, forKey: welcomeKey) }
    }

    var locale: Locale { language.locale }
    var layoutDirection: LayoutDirection { language.layoutDirection }

    private init() {
        let lang = defaults.string(forKey: langKey).flatMap(AppLanguage.init(rawValue:))
            ?? Self.inferredLanguage()
        let themePref = defaults.string(forKey: themeKey).flatMap(AppThemePreference.init(rawValue:)) ?? .system
        let storedLock = defaults.object(forKey: autoLockKey) as? Int ?? 5
        self.language = lang
        self.theme = themePref
        self.autoLockMinutes = min(60, max(1, storedLock))
        self.hasCompletedWelcome = defaults.bool(forKey: welcomeKey)
        // Persist an inferred first pick too, so every reader sees the same language.
        defaults.set(lang.rawValue, forKey: langKey)
        defaults.set([lang.rawValue], forKey: "AppleLanguages")
        UserDefaults(suiteName: AppGroup.identifier)?.set(lang.rawValue, forKey: langKey)
        AppTypography.applyChromeFonts(for: lang)
    }

    func markWelcomeCompleted() {
        hasCompletedWelcome = true
    }

    func resetWelcome() {
        hasCompletedWelcome = false
    }

    /// Best-effort first pick from the device language list (Kurdish / Arabic / English).
    static func inferredLanguage() -> AppLanguage {
        for id in Locale.preferredLanguages {
            let lower = id.lowercased()
            if lower.hasPrefix("ckb") || lower.hasPrefix("ku") { return .ckb }
            if lower.hasPrefix("ar") { return .ar }
            if lower.hasPrefix("en") { return .en }
        }
        return .en
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
