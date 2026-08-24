import SwiftUI

struct VPNView: View {
    @State private var connected = true
    @State private var killSwitch = true
    @State private var autoConnect = false
    @State private var selectedServer = "Kurdistan / Iraq Server - Auto"

    var body: some View {
        VStack(spacing: 0) {
            KasoBrandHeader(
                trailing: AnyView(
                    Button {} label: {
                        Image(systemName: "line.3.horizontal")
                            .font(.system(size: 16, weight: .medium))
                            .foregroundColor(Theme.textSecondary)
                            .padding(8)
                    }
                )
            )

            ScrollView {
                VStack(spacing: 32) {
                    VStack(spacing: 16) {
                        Text("Kaso VPN")
                            .font(.system(size: 28, weight: .heavy))
                            .foregroundColor(Theme.textPrimary)

                        HStack(spacing: 12) {
                            Image(systemName: "globe")
                                .foregroundColor(Theme.cyan)
                            Text(selectedServer)
                                .font(.system(size: 16))
                                .foregroundColor(Theme.textPrimary)
                                .lineLimit(1)
                            Image(systemName: "chevron.down")
                                .font(.system(size: 10, weight: .semibold))
                                .foregroundColor(Theme.textSecondary)
                        }
                        .padding(.horizontal, 25)
                        .padding(.vertical, 9)
                        .background(Theme.cardBackground)
                        .clipShape(Capsule())
                        .overlay(Capsule().stroke(Theme.stroke, lineWidth: 1))
                    }

                    VStack(spacing: 0) {
                        Button {
                            withAnimation(.spring(response: 0.35, dampingFraction: 0.7)) {
                                connected.toggle()
                            }
                        } label: {
                            ZStack {
                                Circle()
                                    .stroke(Theme.cyan.opacity(0.3), lineWidth: 1)
                                    .frame(width: 160, height: 160)
                                Circle()
                                    .stroke((connected ? Theme.mint : Theme.stroke).opacity(0.5), lineWidth: 1)
                                    .frame(width: 140, height: 140)
                                Circle()
                                    .fill(Theme.cardBackground)
                                    .frame(width: 128, height: 128)
                                    .overlay(
                                        Circle().stroke(connected ? Theme.mint : Theme.stroke, lineWidth: 2)
                                    )
                                    .shadow(color: connected ? Theme.mint.opacity(0.4) : .clear, radius: 20)
                                Image(systemName: "power")
                                    .font(.system(size: 36, weight: .medium))
                                    .foregroundColor(connected ? Theme.mint : Theme.textSecondary)
                            }
                        }

                        VStack(spacing: 8) {
                            Text(connected ? "Connected" : "Disconnected")
                                .font(.system(size: 20, weight: .semibold))
                                .foregroundColor(connected ? Theme.success : Theme.textSecondary)
                            Text("Protected by WireGuard®")
                                .font(.kasoMono(12))
                                .tracking(0.6)
                                .foregroundColor(Theme.textSecondary)
                        }
                        .padding(.top, 32)
                    }
                    .padding(.vertical, 12)

                    LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible())], spacing: 12) {
                        StatBox(label: "Download", value: "45.2", unit: "Mbps", icon: "arrow.down.to.line", iconColor: Theme.mint)
                        StatBox(label: "Upload", value: "18.7", unit: "Mbps", icon: "arrow.up.to.line", iconColor: Theme.mint)
                    }

                    metricCard(icon: "key.fill", label: "VIRTUAL IP", value: "10.0.0.42", highlighted: false)
                    metricCard(icon: "lock.shield.fill", label: "ENCRYPTION", value: "WireGuard", highlighted: true)

                    CardContainer(background: Theme.cardMuted) {
                        VStack(alignment: .leading, spacing: 16) {
                            Text("Security Features")
                                .font(.system(size: 20, weight: .semibold))
                                .foregroundColor(Theme.textPrimary)
                                .padding(.bottom, 8)
                                .frame(maxWidth: .infinity, alignment: .leading)
                                .overlay(alignment: .bottom) {
                                    Rectangle().fill(Theme.stroke).frame(height: 1)
                                }

                            ToggleRow(
                                icon: "hand.raised.fill",
                                iconColor: Theme.danger,
                                title: "Kill Switch",
                                subtitle: "Block internet if VPN disconnects",
                                isOn: $killSwitch,
                                iconBackground: Theme.dangerBackground.opacity(0.35)
                            )

                            ToggleRow(
                                icon: "arrow.triangle.2.circlepath",
                                iconColor: Theme.cyan,
                                title: "Auto-connect",
                                subtitle: "On public or untrusted Wi-Fi",
                                isOn: $autoConnect,
                                iconBackground: Color(hex: "001B21")
                            )
                        }
                    }
                }
                .padding(Theme.screenPadding)
                .padding(.top, 16)
                .padding(.bottom, 40)
            }
        }
        .background(Theme.chrome.ignoresSafeArea())
    }

    private func metricCard(icon: String, label: String, value: String, highlighted: Bool) -> some View {
        VStack(spacing: 8) {
            Image(systemName: icon)
                .foregroundColor(Theme.mint)
            Text(label)
                .font(.kasoMono(12))
                .tracking(0.6)
                .foregroundColor(Theme.textSecondary)
            Text(value)
                .font(.system(size: 16, design: .monospaced))
                .foregroundColor(Theme.textPrimary)
        }
        .frame(maxWidth: .infinity)
        .padding(17)
        .background(Theme.cardBackground)
        .cornerRadius(Theme.cornerRadius)
        .overlay(
            RoundedRectangle(cornerRadius: Theme.cornerRadius)
                .stroke(highlighted ? Theme.mint : Theme.stroke, lineWidth: highlighted ? 1.5 : 1)
        )
    }
}

#Preview {
    VPNView()
}
