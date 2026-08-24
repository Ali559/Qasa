import SwiftUI

struct VPNView: View {
    @State private var connected = true
    @State private var killSwitch = false
    @State private var autoConnect = false
    @State private var selectedServer = "Kurdistan / Iraq Server - Auto"

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 20) {
                    HStack {
                        Text("Kaso VPN")
                            .font(.title2.bold())
                            .foregroundColor(Theme.textPrimary)
                        Spacer()
                    }

                    HStack {
                        Image(systemName: "globe")
                        Text(selectedServer)
                            .lineLimit(1)
                        Spacer()
                        Image(systemName: "chevron.down")
                    }
                    .foregroundColor(Theme.textPrimary)
                    .padding()
                    .background(Theme.cardBackground)
                    .cornerRadius(Theme.cornerRadius)

                    ZStack {
                        Circle()
                            .stroke(connected ? Theme.mint.opacity(0.3) : Theme.stroke, lineWidth: 2)
                            .frame(width: 200, height: 200)
                        Circle()
                            .stroke(connected ? Theme.mint.opacity(0.15) : Theme.stroke, lineWidth: 30)
                            .frame(width: 170, height: 170)
                        Button {
                            connected.toggle()
                        } label: {
                            Image(systemName: "power")
                                .font(.system(size: 40))
                                .foregroundColor(connected ? Theme.mint : Theme.textSecondary)
                                .frame(width: 100, height: 100)
                                .background(Theme.cardBackground)
                                .clipShape(Circle())
                        }
                    }
                    .padding(.vertical, 12)

                    VStack(spacing: 4) {
                        Text(connected ? "Connected" : "Disconnected")
                            .font(.headline)
                            .foregroundColor(connected ? Theme.mint : Theme.textSecondary)
                        Text("Protected by WireGuard®")
                            .font(.caption)
                            .foregroundColor(Theme.textSecondary)
                    }

                    HStack(spacing: 12) {
                        StatBox(label: "Download", value: "45.2", unit: "Mbps", icon: "arrow.down", iconColor: Theme.mint)
                        StatBox(label: "Upload", value: "18.7", unit: "Mbps", icon: "arrow.up", iconColor: Theme.mint)
                    }

                    CardContainer {
                        HStack {
                            Image(systemName: "key.fill")
                                .foregroundColor(Theme.mint)
                            VStack(alignment: .leading, spacing: 2) {
                                Text("VIRTUAL IP")
                                    .font(.caption2)
                                    .foregroundColor(Theme.textSecondary)
                                Text("10.0.0.42")
                                    .font(.subheadline.weight(.semibold))
                                    .foregroundColor(Theme.textPrimary)
                            }
                            Spacer()
                        }
                    }

                    CardContainer {
                        HStack {
                            Image(systemName: "lock.rotation")
                                .foregroundColor(Theme.mint)
                            VStack(alignment: .leading, spacing: 2) {
                                Text("ENCRYPTION")
                                    .font(.caption2)
                                    .foregroundColor(Theme.textSecondary)
                                Text("WireGuard")
                                    .font(.subheadline.weight(.semibold))
                                    .foregroundColor(Theme.textPrimary)
                            }
                            Spacer()
                        }
                    }

                    CardContainer {
                        SectionLabel(text: "Security Features")
                            .padding(.bottom, 6)
                        ToggleRow(icon: "xmark.shield.fill", iconColor: Theme.danger, title: "Kill Switch", subtitle: "Block internet if VPN disconnects", isOn: $killSwitch)
                        Divider().background(Theme.stroke)
                        ToggleRow(icon: "wifi", iconColor: Theme.mint, title: "Auto-connect", subtitle: "On public or untrusted Wi-Fi", isOn: $autoConnect)
                    }
                }
                .padding(20)
            }
            .background(Theme.background.ignoresSafeArea())
        }
    }
}

#Preview {
    VPNView()
}
