import SwiftData
import SwiftUI

/// What the big status on Home says. Only facts the app actually knows.
enum HomeStatus {
    case loading, empty, allGood, needsAttention

    init(report: PasswordHealthReport?) {
        guard let report else { self = .loading; return }
        if report.totalPasswords == 0 {
            self = .empty
        } else if report.reusedCount + report.weakCount + report.breachedCount == 0 {
            self = .allGood
        } else {
            self = .needsAttention
        }
    }
}

/// Home tab. The big status answers "are my passwords OK?" from the local health report.
/// It never claims the device is protected by the VPN: the VPN is still an API session
/// without a tunnel (`vpn.sessionOnly`), so it stays a separate, honest card below.
/// The page wash turns warm when nothing needs attention and cool when something does.
/// Mirrors Android `HomeScreen`.
struct HomeView: View {
    var onOpen: (AppPage) -> Void
    var onTab: (ShellTab) -> Void

    @Environment(\.themePalette) private var theme
    @Environment(AppSession.self) private var session
    @Environment(AppPreferences.self) private var prefs
    @Bindable private var cache = VaultRepository.shared.cache
    @State private var report: PasswordHealthReport?
    @Query(filter: #Predicate<AppNotification> { $0.isRead == false }) private var unreadLocalNotes: [AppNotification]

    /// Bell badge: the server's `unread_count` (whole inbox) plus unread "On this device"
    /// notifications. Same sum as Android `NotificationBadgeViewModel`.
    private var unreadBadge: Int { NotificationService.shared.unreadCount + unreadLocalNotes.count }

    private var status: HomeStatus { HomeStatus(report: report) }

    var body: some View {
        ScrollView {
            VStack(spacing: 16) {
                greeting
                HomeStatusHero(status: status, onAddFirst: { onTab(.passwords) })
                if status != .empty {
                    HomeHealthCard(report: report, onTap: { onOpen(.passwordHealth) })
                }
                HomeVPNCard(isPremium: session.isPremium, onTap: { onOpen(.vpn) })
                HomeBreachedCard(
                    isPremium: session.isPremium,
                    onTap: { session.isPremium ? onTab(.alerts) : onOpen(.premiumUpgrade) }
                )
            }
            .padding(.horizontal, Theme.screenPadding)
            .padding(.top, 16)
            .padding(.bottom, 40)
        }
        .background(
            LinearGradient(
                stops: [
                    .init(color: status == .allGood ? theme.warm : theme.calm, location: 0),
                    .init(color: theme.background, location: 0.55),
                ],
                startPoint: .top,
                endPoint: .bottom
            )
            .ignoresSafeArea()
            .animation(.easeInOut(duration: 0.4), value: status)
        )
        .onAppear(perform: refreshReport)
        .onChange(of: cache.rows) { _, _ in refreshReport() }
        .onChange(of: session.isPremium) { _, _ in refreshReport() }
        .task {
            await NotificationService.shared.refreshUnreadCount()
        }
        .task {
            if session.isPremium { await VPNService.shared.refreshStatus() }
        }
    }

    private var greeting: some View {
        let hour = Calendar.current.component(.hour, from: Date())
        let key = switch hour {
        case 5...11: "home.greetingMorning"
        case 12...17: "home.greetingAfternoon"
        default: "home.greetingEvening"
        }
        return HStack {
            VStack(alignment: .leading, spacing: 2) {
                Text(L10n.tr(key, locale: prefs.locale))
                    .font(.qasa(14))
                    .foregroundColor(theme.textSecondary)
                Text(session.displayName)
                    .font(.qasa(22, weight: .bold))
                    .foregroundColor(theme.textPrimary)
                    .lineLimit(1)
            }
            Spacer()
            Button { onOpen(.notifications) } label: {
                Image(systemName: "bell")
                    .font(.qasa(18, weight: .medium))
                    .foregroundColor(theme.textPrimary)
                    .frame(width: 44, height: 44)
                    .background(theme.cardBackground)
                    .clipShape(RoundedRectangle(cornerRadius: 14))
                    .overlay(alignment: .topTrailing) {
                        if unreadBadge > 0 {
                            Text(unreadBadge > 99 ? "99+" : "\(unreadBadge)")
                                .font(.qasa(11, weight: .semibold))
                                .foregroundColor(theme.onAccent)
                                .lineLimit(1)
                                .padding(.horizontal, 4)
                                .background(Capsule().fill(theme.accent))
                                .padding(4)
                        }
                    }
            }
            .accessibilityLabel(L10n.tr("nav.notifications", locale: prefs.locale))
        }
    }

    /// Local report straight away, then again once the Pwned Passwords check is back.
    /// Free accounts stop at the local report: the breach check is Premium-only.
    private func refreshReport() {
        let logins = PasswordHealthAnalyzer.vaultLogins()
        let isPremium = session.isPremium
        report = PasswordHealthAnalyzer.analyze(logins: logins, breachLocked: !isPremium)
        Task {
            let checked = await PasswordHealthAnalyzer.analyzeWithBreaches(logins: logins, isPremium: isPremium)
            // A check that finishes after the plan changed would bring back stale counts.
            if !checked.breachCheckFailed, session.isPremium == isPremium { report = checked }
        }
    }
}
