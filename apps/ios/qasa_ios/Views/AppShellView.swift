import SwiftUI
import SwiftData

/// The four destinations that replaced the side menu in the calm + personal redesign.
enum ShellTab: Hashable {
    case home, passwords, alerts, you
}

/// Four bottom tabs (Home, Passwords, Alerts, You) replace the side menu. Every other page
/// opens on top of the current tab with a back chevron and no tab bar; back returns to the tab.
/// Mirrors Android `AppShellScreen`.
struct AppShellView: View {
    var onLogout: (() -> Void)? = nil

    @Environment(\.openURL) private var openURL
    @Environment(AppSession.self) private var session
    @Environment(AppPreferences.self) private var prefs
    @State private var tab: ShellTab = .home
    @State private var page: AppPage?

    var body: some View {
        ZStack {
            TabView(selection: $tab) {
                Tab(L10n.tr("tab.home", locale: prefs.locale), systemImage: "house", value: ShellTab.home) {
                    HomeView(onOpen: open, onTab: { tab = $0 })
                }
                Tab(L10n.tr("tab.passwords", locale: prefs.locale), systemImage: "key", value: ShellTab.passwords) {
                    NavigationStack {
                        VaultListView()
                    }
                }
                Tab(L10n.tr("tab.alerts", locale: prefs.locale), systemImage: "bell.badge", value: ShellTab.alerts) {
                    NavigationStack {
                        BreachedPasswordsView(onUpgrade: { open(.premiumUpgrade) })
                    }
                    // Email leak alerts are parked (todo email-leak-alerts in docs/server-followups-plan.md).
                    // BreachMonitorView(onUpgrade: { open(.premiumUpgrade) })
                }
                Tab(L10n.tr("tab.you", locale: prefs.locale), systemImage: "person.crop.circle", value: ShellTab.you) {
                    YouView(onOpen: open)
                }
            }
            .environment(session)

            if let page {
                pageContent(page)
                    .environment(session)
                    .themedBackground()
                    .transition(.move(edge: .trailing))
                    .zIndex(10)
            }
        }
        .tint(Theme.accent)
        .onReceive(NotificationCenter.default.publisher(for: .qasaOpenNotifications)) { _ in
            open(.notifications)
        }
    }

    private func back() {
        withAnimation(.easeOut(duration: 0.22)) { page = nil }
    }

    private func open(_ next: AppPage) {
        // Privacy and Terms live on the website, in the app's language.
        if next == .terms || next == .privacy {
            openURL(LegalLinks.url(locale: prefs.locale, terms: next == .terms))
            return
        }
        withAnimation(.easeOut(duration: 0.22)) {
            if next.requiresPremium && !session.isPremium {
                page = .premiumUpgrade
            } else if next == .vault {
                // The two pages that became tabs switch tabs instead of stacking.
                page = nil
                tab = .passwords
            } else if next == .breachedPasswords {
                page = nil
                tab = .alerts
            } else {
                page = next
            }
        }
    }

    @ViewBuilder
    private func pageContent(_ page: AppPage) -> some View {
        switch page {
        case .notifications:
            NotificationsView(onOpenMenu: back)
        case .generator:
            GeneratorView(onOpenMenu: back)
        case .passwordHealth:
            NavigationStack {
                PasswordHealthView(onOpenMenu: back, onUpgrade: { open(.premiumUpgrade) })
            }
        case .vpn:
            VPNView(onOpenMenu: back, onUpgrade: { open(.premiumUpgrade) })
        case .collections:
            NavigationStack {
                CollectionsView(onOpenMenu: back)
            }
        case .settings:
            SettingsView(onOpenMenu: back, onLogout: onLogout)
        case .premiumUpgrade:
            PremiumUpgradeView(onOpenMenu: back, embedded: true)
        case .vault, .breachedPasswords, .terms, .privacy:
            // Tabs and website links, never stacked: `open` routes these elsewhere.
            EmptyView()
        }
    }
}

#Preview {
    AppShellView()
        .environment(AppSession())
        .environment(AppPreferences.shared)
        .modelContainer(for: VaultSchema.models, inMemory: true)
}
