import Foundation
import SwiftUI
import Observation

@Observable
final class AppSession {
    var username: String
    var email: String
    var isPremium: Bool

    init(
        username: String = "Jane Doe",
        email: String = "jane.doe@example.com",
        isPremium: Bool = false
    ) {
        self.username = username
        self.email = email
        self.isPremium = UserDefaults.standard.object(forKey: "kaso.isPremium") as? Bool ?? isPremium
    }

    var planSubtitle: String {
        isPremium ? "Premium Plan" : "Free Plan"
    }

    func setPremium(_ value: Bool) {
        isPremium = value
        UserDefaults.standard.set(value, forKey: "kaso.isPremium")
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
        case .breachMonitoring, .vpn: return true
        default: return false
        }
    }
}
