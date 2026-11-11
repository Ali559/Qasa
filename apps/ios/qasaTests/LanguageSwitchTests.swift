import Foundation
import Testing
@testable import qasa

/// Every screen's text follows Settings → Language at once (design/README.md, "Language").
/// Password health once stayed Arabic: its strings read the language from `UserDefaults`
/// instead of `AppPreferences`, so they missed changes made in the app.
@MainActor
@Suite(.serialized)
struct LanguageSwitchTests {
    private func withLanguage(_ language: AppLanguage, _ body: () -> Void) {
        let prefs = AppPreferences.shared
        let saved = prefs.language
        prefs.language = language
        defer { prefs.language = saved }
        body()
    }

    @Test func defaultLookupFollowsTheAppSetting() {
        withLanguage(.en) {
            #expect(AppLanguage.current == .en)
            #expect(L10n.tr("health.reused") == "Reused")
            #expect(PasswordIssueTab.weak.title == "Weak")
        }
        withLanguage(.ar) {
            #expect(L10n.tr("health.reused") == "معاد استخدامها")
            #expect(PasswordIssueTab.weak.title == "ضعيفة")
        }
        withLanguage(.ckb) {
            #expect(L10n.tr("health.reused") == "دووبارەکراو")
        }
    }

    @Test func languageIsReadFromPreferencesNotDefaults() {
        withLanguage(.en) {
            // A stale value in UserDefaults must not win over the live preference.
            UserDefaults.standard.set("ar", forKey: "qasa.language")
            defer { UserDefaults.standard.set("en", forKey: "qasa.language") }
            #expect(L10n.tr("nav.passwordHealth") == "Password health")
        }
    }

    @Test func systemErrorsUseTheAppLanguage() {
        withLanguage(.ar) {
            let offline = URLError(.notConnectedToInternet)
            #expect(L10n.message(for: offline) == L10n.tr("error.network"))
            #expect(L10n.message(for: QasaAPIError.network(offline)) == L10n.tr("error.network"))
            #expect(L10n.message(for: VaultCryptoError.vaultLocked) == L10n.tr("error.generic"))
            #expect(L10n.message(for: VaultCryptoError.wrongPassword) == L10n.tr("auth.unlock.wrongPassword"))
            #expect(L10n.tr("error.generic") != "Something went wrong. Try again.")
        }
    }
}
