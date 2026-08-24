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
            // Schema evolved (e.g. MonitoredEmail) — reset local store for development.
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
                .onAppear {
                    VaultSeedData.seedIfNeeded(context: container.mainContext)
                }
        }
    }

    struct RootView: View {
        @State private var unlocked = false
        @State private var forceCreate = false

        private var showLogin: Bool {
            VaultAuthStore.vaultExists && !forceCreate
        }

        var body: some View {
            Group {
                if unlocked {
                    AppShellView(onLogout: {
                        unlocked = false
                        forceCreate = false
                    })
                } else if showLogin {
                    LoginView(
                        onUnlocked: { unlocked = true },
                        onCreateVault: { forceCreate = true }
                    )
                } else {
                    CreateVaultView(
                        onVaultCreated: {
                            unlocked = true
                            forceCreate = false
                        },
                        onLogin: VaultAuthStore.vaultExists ? { forceCreate = false } : nil
                    )
                }
            }
        }
    }
}
