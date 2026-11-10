import SwiftUI
import SwiftData

/// The four destinations that replaced the side menu in the calm + personal redesign.
enum ShellTab: Hashable {
    case home, passwords, alerts, you
}

extension ShellTab {
    /// Base outline SF Symbol; every one has a `.fill` variant.
    var symbol: String {
        switch self {
        case .home: return "house"
        case .passwords: return "key"
        case .alerts: return "bell"
        case .you: return "person.crop.circle"
        }
    }

    var titleKey: String {
        switch self {
        case .home: return "tab.home"
        case .passwords: return "tab.passwords"
        case .alerts: return "tab.alerts"
        case .you: return "tab.you"
        }
    }
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
    /// A generated password waiting for the Passwords tab to open the new-login editor.
    @State private var pendingPassword: String?

    var body: some View {
        ZStack {
            // `.tabItem` + `.tag` rather than iOS 18's `Tab`, so the shell runs on iOS 17.
            TabView(selection: $tab) {
                HomeView(onOpen: open, onTab: { tab = $0 })
                    .tabItem { tabLabel(.home) }
                    .tag(ShellTab.home)
                NavigationStack {
                    VaultListView(pendingPassword: $pendingPassword)
                }
                .tabItem { tabLabel(.passwords) }
                .tag(ShellTab.passwords)
                NavigationStack {
                    BreachedPasswordsView(onUpgrade: { open(.premiumUpgrade) })
                }
                // Email leak alerts are parked (todo email-leak-alerts in docs/server-followups-plan.md).
                // BreachMonitorView(onUpgrade: { open(.premiumUpgrade) })
                .tabItem { tabLabel(.alerts) }
                .tag(ShellTab.alerts)
                YouView(onOpen: open)
                    .tabItem { tabLabel(.you) }
                    .tag(ShellTab.you)
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

    /// Outline symbol when inactive, its `.fill` twin when selected.
    private func tabLabel(_ item: ShellTab) -> some View {
        let selected = tab == item
        return Label {
            Text(L10n.tr(item.titleKey, locale: prefs.locale))
        } icon: {
            Image(systemName: selected ? item.symbol + ".fill" : item.symbol)
        }
        // The tab bar fills every symbol by default; keep the outline for inactive tabs.
        .environment(\.symbolVariants, .none)
    }

    /// Generator "Save to my passwords": close the page, show Passwords, and let the list
    /// open the new-login editor with the password filled in.
    private func saveGeneratedPassword(_ password: String) {
        guard !password.isEmpty else { return }
        withAnimation(.easeOut(duration: 0.22)) {
            page = nil
            tab = .passwords
        }
        pendingPassword = password
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
            GeneratorView(onOpenMenu: back, onSaveToVault: saveGeneratedPassword)
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
