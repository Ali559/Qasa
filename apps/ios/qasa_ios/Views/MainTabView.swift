import SwiftUI

struct MainTabView: View {
    var body: some View {
        TabView {
            VaultListView()
                .tabItem { Label("Vault", systemImage: "lock.fill") }

            BreachMonitorView()
                .tabItem { Label("Breach", systemImage: "exclamationmark.shield.fill") }

            GeneratorView()
                .tabItem { Label("Gen", systemImage: "wand.and.stars") }

            VPNView()
                .tabItem { Label("VPN", systemImage: "network") }

            SettingsView()
                .tabItem { Label("Settings", systemImage: "gearshape.fill") }
        }
        .tint(Theme.mint)
        .preferredColorScheme(.dark)
    }
}

#Preview {
    MainTabView()
}
