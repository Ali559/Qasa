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

    /// The pending expiry check (see `PremiumRefreshPolicy`); re-armed on every sync.
    @ObservationIgnored private var premiumCheckTask: Task<Void, Never>?

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
        schedulePremiumCheck()
    }

    // MARK: - Premium re-sync

    private var nextPremiumCheck: Date? {
        let license = LicenseManager.shared
        return PremiumRefreshPolicy.nextCheck(
            isPremium: isPremium,
            licenseExpiresAt: license.parsed?.expiresAt,
            subscriptionExpiresAt: PremiumRefreshPolicy.parseDate(SubscriptionService.shared.current?.expires_at),
            lastAttempt: license.lastRefreshAttempt,
            now: Date()
        )
    }

    /// While unlocked and Premium, re-syncs at the next known expiry so the locked
    /// screens appear when Premium ends, not at the next unlock. Locked: cancels.
    private func schedulePremiumCheck() {
        premiumCheckTask?.cancel()
        premiumCheckTask = nil
        guard VaultSession.shared.isUnlocked, let at = nextPremiumCheck else { return }
        let delay = max(0, at.timeIntervalSinceNow)
        premiumCheckTask = Task { [weak self] in
            try? await Task.sleep(for: .seconds(delay))
            guard !Task.isCancelled else { return }
            await self?.refreshPremium()
        }
    }

    /// One license refresh, then a sync. Offline-safe: a failed refresh keeps the stored
    /// token, so a paid user stays Premium for as long as that token is valid.
    func refreshPremium() async {
        guard VaultSession.shared.isUnlocked else { return }
        if VaultKeychainStore.licenseToken != nil {
            _ = await LicenseManager.shared.refresh()
        }
        guard VaultSession.shared.isUnlocked else { return }
        syncFromLicense()
    }

    /// Scene became active: refresh if a known expiry has passed or the last refresh is
    /// older than the throttle; otherwise just re-read the local license (cheap) and
    /// re-arm the timer, which does not fire while the app is suspended.
    func handleForeground() {
        guard VaultSession.shared.isUnlocked else { return }
        if PremiumRefreshPolicy.shouldRefreshOnForeground(
            now: Date(),
            lastAttempt: LicenseManager.shared.lastRefreshAttempt,
            nextCheck: nextPremiumCheck
        ) {
            Task { await refreshPremium() }
        } else {
            syncFromLicense()
        }
    }
}

enum AppPage: String, CaseIterable, Identifiable, Hashable {
    case vault
    case notifications
    case generator
    case passwordHealth
    case breachedPasswords
    // Email leak alerts are parked (todo email-leak-alerts in docs/server-followups-plan.md).
    // case breachMonitoring
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
        case .breachedPasswords: return "nav.breachedPasswords"
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

    var icon: QasaIcon {
        switch self {
        case .vault: return .shieldLock
        case .notifications: return .alerts
        case .generator: return .generate
        case .passwordHealth: return .health
        case .breachedPasswords: return .breach
        case .vpn: return .vpn
        case .collections: return .folder
        case .settings: return .settings
        case .terms: return .note
        case .privacy: return .privacy
        case .premiumUpgrade: return .premium
        }
    }

    var requiresPremium: Bool {
        switch self {
        // Email leak alerts are parked (todo email-leak-alerts in docs/server-followups-plan.md).
        // case .vpn, .breachMonitoring: return true
        case .vpn: return true
        default: return false
        }
    }
}
