import SwiftUI
import SwiftData

/// The four destinations that replaced the side menu in the calm + personal redesign.
enum ShellTab: Hashable {
    case home, passwords, alerts, you
}

extension ShellTab {
    /// The tab's linear glyph, shown while it is not the active tab.
    var icon: QasaIcon {
        switch self {
        case .home: return .home
        case .passwords: return .passwords
        case .alerts: return .alerts
        case .you: return .you
        }
    }

    /// The filled glyph (Solar Bold) the tab shows while it is active.
    var activeIcon: QasaIcon {
        switch self {
        case .home: return .homeFill
        case .passwords: return .passwordsFill
        case .alerts: return .alertsFill
        case .you: return .youFill
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

    /// Linear glyph when inactive, its filled twin when selected.
    private func tabLabel(_ item: ShellTab) -> some View {
        let selected = tab == item
        return Label {
            Text(L10n.tr(item.titleKey, locale: prefs.locale))
        } icon: {
            Self.tabIcon(selected ? item.activeIcon : item.icon)
        }
    }

    /// The tab bar draws a tab item's image at its own size and ignores SwiftUI frames, so
    /// the 24-unit glyph is rendered at the tab-bar size (22 pt) up front, as a template.
    private static func tabIcon(_ icon: QasaIcon) -> Image {
        let side: CGFloat = 22
        guard let source = UIImage(named: icon.rawValue) else { return Image(icon.rawValue) }
        let size = CGSize(width: side, height: side)
        let rendered = UIGraphicsImageRenderer(size: size).image { _ in
            source.draw(in: CGRect(origin: .zero, size: size))
        }
        return Image(uiImage: rendered.withRenderingMode(.alwaysTemplate))
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
