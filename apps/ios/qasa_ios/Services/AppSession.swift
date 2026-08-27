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
        syncFromLicense()
    }

    var planSubtitle: String {
        isPremium ? "Premium Plan" : "\(tierLabel) Plan"
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

    var title: String {
        switch self {
        case .vault: return "Vault"
        case .notifications: return "Notifications"
        case .generator: return "Generator"
        case .passwordHealth: return "Password Health"
        case .breachMonitoring: return "Breach Monitoring"
        case .vpn: return "VPN"
        case .collections: return "Collections"
        case .settings: return "Settings"
        case .terms: return "Terms of Use"
        case .privacy: return "Privacy Policy"
        case .premiumUpgrade: return "Upgrade"
        }
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
        case .vpn: return true
        default: return false
        }
    }
}
