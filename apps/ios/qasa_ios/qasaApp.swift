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
            fatalError("Failed to create ModelContainer: \(error)")
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
        @State private var vaultCreated = false

        var body: some View {
            if vaultCreated {
                MainTabView()
            } else {
                CreateVaultView(onVaultCreated: { vaultCreated = true })
            }
        }
    }
}
