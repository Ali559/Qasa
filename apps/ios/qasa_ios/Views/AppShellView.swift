import SwiftUI
import SwiftData

struct AppShellView: View {
    var onLogout: (() -> Void)? = nil

    @Environment(AppSession.self) private var session
    @State private var showMenu = false
    @State private var page: AppPage = .vault

    var body: some View {
        ZStack {
            Group {
                switch page {
                case .vault:
                    NavigationStack {
                        VaultListView(onOpenMenu: openMenu)
                    }
                case .notifications:
                    NotificationsView(onOpenMenu: openMenu)
                case .generator:
                    GeneratorView(onOpenMenu: openMenu)
                case .passwordHealth:
                    NavigationStack {
                        PasswordHealthView(onOpenMenu: openMenu)
                    }
                case .breachMonitoring:
                    BreachMonitorView(onOpenMenu: openMenu)
                case .vpn:
                    VPNView(onOpenMenu: openMenu)
                case .collections:
                    NavigationStack {
                        CollectionsView(onOpenMenu: openMenu)
                    }
                case .settings:
                    SettingsView(onOpenMenu: openMenu, onLogout: onLogout)
                case .terms:
                    LegalPlaceholderView(
                        title: "Terms of Use",
                        onOpenMenu: openMenu,
                        bodyText: """
                        These Terms of Use are a placeholder for Qasa’s legal agreement with you.

                        By using the app you agree to follow applicable laws and not misuse the service. Full terms will be published here before public release.
                        """
                    )
                case .privacy:
                    LegalPlaceholderView(
                        title: "Privacy Policy",
                        onOpenMenu: openMenu,
                        bodyText: """
                        This Privacy Policy is a placeholder describing how Qasa will handle your data.

                        Vault contents are designed to stay encrypted and under your control. Full policy details will be published here before public release.
                        """
                    )
                case .premiumUpgrade:
                    PremiumUpgradeView(onOpenMenu: openMenu, embedded: true)
                }
            }
            .environment(session)

            if showMenu {
                SideMenuView(
                    isPresented: $showMenu,
                    currentPage: page,
                    onSelect: selectPage,
                    onUpgrade: {
                        selectPage(.premiumUpgrade)
                    }
                )
                .environment(session)
                .zIndex(10)
            }
        }
        .preferredColorScheme(.dark)
        .tint(Theme.mint)
        .onReceive(NotificationCenter.default.publisher(for: .qasaOpenNotifications)) { _ in
            selectPage(.notifications)
        }
    }

    private func openMenu() {
        withAnimation(.easeOut(duration: 0.22)) {
            showMenu = true
        }
    }

    private func selectPage(_ next: AppPage) {
        if next.requiresPremium && !session.isPremium {
            page = .premiumUpgrade
        } else {
            page = next
        }
        withAnimation(.easeOut(duration: 0.2)) {
            showMenu = false
        }
    }
}

#Preview {
    AppShellView()
        .environment(AppSession())
        .modelContainer(for: VaultSchema.models, inMemory: true)
}
