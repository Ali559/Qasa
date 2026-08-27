import Foundation

enum AppLanguage: String, CaseIterable, Identifiable {
    case en, ckb, ar

    var id: String { rawValue }

    var displayName: String {
        switch self {
        case .en: return "English"
        case .ckb: return "کوردی"
        case .ar: return "العربية"
        }
    }
}

enum AppThemePreference: String, CaseIterable, Identifiable {
    case system, light, dark

    var id: String { rawValue }

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
            Task { await syncLocaleToServer() }
        }
    }

    var theme: AppThemePreference {
        didSet { defaults.set(theme.rawValue, forKey: themeKey) }
    }

    private init() {
        let lang = defaults.string(forKey: langKey).flatMap(AppLanguage.init(rawValue:)) ?? .en
        let themePref = defaults.string(forKey: themeKey).flatMap(AppThemePreference.init(rawValue:)) ?? .dark
        self.language = lang
        self.theme = themePref
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
