import SwiftUI

struct MainTabView: View {
    var body: some View {
        TabView {
            VaultListView()
                .tabItem { Label("Vault", systemImage: "lock.shield.fill") }

            BreachMonitorView()
                .tabItem { Label("Breach", systemImage: "exclamationmark.shield.fill") }

            VPNView()
                .tabItem { Label("VPN", systemImage: "network.badge.shield.half.filled") }

            SettingsView()
                .tabItem { Label("Settings", systemImage: "gearshape.fill") }
        }
        .tint(Theme.mint)
        .preferredColorScheme(.dark)
        .toolbarBackground(Theme.chrome.opacity(0.95), for: .tabBar)
        .toolbarBackground(.visible, for: .tabBar)
    }
}

#Preview {
    MainTabView()
}
