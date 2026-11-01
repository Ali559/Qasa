import Foundation
import SwiftUI
import Observation

@Observable
final class AppSession {
    var username: String
    var email: String
    var isPremium: Bool
    var tierLabel: String
    var deviceLimit: Int

    /// Set when the build cannot reach a valid API host, so every network-backed screen
    /// fails the same way with an explanation instead of silently timing out.
    var configurationError: String?

    init(
        username: String = "Qasa User",
        email: String = "",
        isPremium: Bool = false
    ) {
        self.username = username
        self.email = email
        self.isPremium = isPremium
        self.tierLabel = LicenseManager.shared.tierLabel
        self.deviceLimit = LicenseManager.shared.deviceLimit
        self.configurationError = QasaConfig.isBaseURLValid ? nil : QasaConfig.apiBaseURL.absoluteString
        syncFromLicense()
    }

    var displayName: String {
        if username.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
            || username == "Qasa User" {
            return L10n.tr("session.qasaUser", locale: AppPreferences.shared.locale)
        }
        return username
    }

    var planSubtitle: String {
        let locale = AppPreferences.shared.locale
        if isPremium {
            return L10n.tr("settings.premiumPlan", locale: locale)
        }
        return String(format: L10n.tr("settings.freePlan", locale: locale), tierLabel)
    }

    func syncFromLicense() {
        let license = LicenseManager.shared
        isPremium = license.isPremium
        tierLabel = license.tierLabel
        deviceLimit = license.deviceLimit
    }
}

enum AppPage: String, CaseIterable, Identifiable, Hashable {
    case vault
    case notifications
    case generator
    case passwordHealth
    case breachMonitoring
    case vpn
    case collections
    case settings
    case terms
    case privacy
    case premiumUpgrade

    var id: String { rawValue }

    var localizationKey: String {
        switch self {
        case .vault: return "nav.vault"
        case .notifications: return "nav.notifications"
        case .generator: return "nav.generator"
        case .passwordHealth: return "nav.passwordHealth"
        case .breachMonitoring: return "nav.breachMonitoring"
        case .vpn: return "nav.vpn"
        case .collections: return "nav.collections"
        case .settings: return "nav.settings"
        case .terms: return "nav.terms"
        case .privacy: return "nav.privacy"
        case .premiumUpgrade: return "nav.upgrade"
        }
    }

    var title: String {
        L10n.tr(localizationKey, locale: AppLanguage.current.locale)
    }

    var systemImage: String {
        switch self {
        case .vault: return "lock.shield.fill"
        case .notifications: return "bell.fill"
        case .generator: return "wand.and.stars"
        case .passwordHealth: return "heart.text.square.fill"
        case .breachMonitoring: return "exclamationmark.shield.fill"
        case .vpn: return "network.badge.shield.half.filled"
        case .collections: return "folder.fill"
        case .settings: return "gearshape.fill"
        case .terms: return "doc.text"
        case .privacy: return "hand.raised.fill"
        case .premiumUpgrade: return "crown.fill"
        }
    }

    var requiresPremium: Bool {
        switch self {
        case .vpn, .breachMonitoring: return true
        default: return false
        }
    }
}
