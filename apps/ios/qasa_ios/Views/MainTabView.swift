import SwiftUI

/// Kept for compatibility; the app shell now owns navigation.
struct MainTabView: View {
    var body: some View {
        AppShellView()
    }
}

#Preview {
    MainTabView()
}
