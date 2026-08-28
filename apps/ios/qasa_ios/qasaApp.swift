import SwiftUI
import SwiftData

@main
struct qasaApp: App {
    @UIApplicationDelegateAdaptor(QasaAppDelegate.self) private var appDelegate
    private let container: ModelContainer
    @State private var prefs = AppPreferences.shared

    init() {
        let schema = Schema(VaultSchema.models)
        let config = ModelConfiguration(isStoredInMemoryOnly: false)
        do {
            container = try ModelContainer(for: schema, configurations: [config])
        } catch {
            let url = config.url
            try? FileManager.default.removeItem(at: url)
            do {
                container = try ModelContainer(for: schema, configurations: [config])
            } catch {
                fatalError("Failed to create ModelContainer: \(error)")
            }
        }
    }

    var body: some Scene {
        WindowGroup {
            RootView()
                .preferredColorScheme(colorScheme)
                .environment(\.locale, prefs.locale)
                .environment(\.layoutDirection, prefs.layoutDirection)
                .environment(prefs)
                .modelContainer(container)
        }
    }

    private var colorScheme: ColorScheme? {
        switch prefs.theme {
        case .system: return nil
        case .light: return .light
        case .dark: return .dark
        }
    }

    struct RootView: View {
        @Environment(\.modelContext) private var modelContext
        @Environment(\.colorScheme) private var systemScheme
        @Environment(AppPreferences.self) private var prefs
        @State private var unlocked = false
        @State private var showPairing = false
        @State private var session = AppSession()
        @State private var isBootstrapping = false

        private var showLogin: Bool {
            VaultAuthStore.vaultExists
        }

        private var effectiveScheme: ColorScheme {
            switch prefs.theme {
            case .system: return systemScheme
            case .light: return .light
            case .dark: return .dark
            }
        }

        var body: some View {
            Group {
                if unlocked {
                    AppShellView(onLogout: handleLogout)
                        .environment(session)
                } else if showLogin {
                    LoginView(
                        onUnlocked: { password in
                            Task { await handleUnlock(password: password) }
                        },
                        onCreateVault: {}
                    )
                } else {
                    CreateVaultView(
                        onVaultCreated: { password, biometrics in
                            Task { await handleCreate(password: password, biometrics: biometrics) }
                        },
                        onPairExisting: { showPairing = true },
                        onLogin: nil
                    )
                }
            }
            .environment(\.themePalette, ThemePalette.resolved(for: effectiveScheme))
            .environment(\.locale, prefs.locale)
            .environment(\.layoutDirection, prefs.layoutDirection)
            .font(.qasa(16))
            // Refresh localized strings without resetting unlock state (state lives on RootView).
            .id(prefs.language.rawValue)
            .sheet(isPresented: $showPairing) {
                PairExistingVaultView(
                    onPaired: { _ in
                        showPairing = false
                        // After adopting account_id, user still creates local vault keys
                        // then registers as additional device under same account.
                    },
                    onCancel: { showPairing = false }
                )
                .appLocalization()
            }
            .overlay {
                if isBootstrapping {
                    ProgressView(L10n.tr("auth.securingVault", locale: prefs.locale))
                        .padding(24)
                        .background(Theme.cardBackground)
                        .cornerRadius(12)
                }
            }
        }

        @MainActor
        private func handleCreate(password: String, biometrics: Bool) async {
            isBootstrapping = true
            defer { isBootstrapping = false }
            do {
                try VaultAuthStore.createVault(masterPassword: password, biometricsEnabled: biometrics)
                await AppBootstrap.onVaultUnlocked(context: modelContext, session: session)
                unlocked = true
            } catch {
                // createVault currently does not throw; keep hook for future validation.
            }
        }

        @MainActor
        private func handleUnlock(password: String) async {
            guard VaultAuthStore.verifyMasterPassword(password) else { return }
            isBootstrapping = true
            defer { isBootstrapping = false }
            await AppBootstrap.onVaultUnlocked(context: modelContext, session: session)
            unlocked = true
        }

        @MainActor
        private func handleLogout() {
            AppBootstrap.onLogout()
            session.syncFromLicense()
            unlocked = false
        }
    }
}
