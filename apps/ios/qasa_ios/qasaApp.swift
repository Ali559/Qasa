import SwiftUI
import SwiftData

@main
struct qasaApp: App {
    private let container: ModelContainer

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
                .preferredColorScheme(.dark)
                .modelContainer(container)
        }
    }

    struct RootView: View {
        @Environment(\.modelContext) private var modelContext
        @State private var unlocked = false
        @State private var forceCreate = false
        @State private var session = AppSession()
        @State private var isBootstrapping = false

        private var showLogin: Bool {
            VaultAuthStore.vaultExists && !forceCreate
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
                        onCreateVault: { forceCreate = true }
                    )
                } else {
                    CreateVaultView(
                        onVaultCreated: { password, biometrics in
                            Task { await handleCreate(password: password, biometrics: biometrics) }
                        },
                        onLogin: VaultAuthStore.vaultExists ? { forceCreate = false } : nil
                    )
                }
            }
            .overlay {
                if isBootstrapping {
                    ProgressView("Securing vault…")
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
                forceCreate = false
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
            forceCreate = false
        }
    }
}
