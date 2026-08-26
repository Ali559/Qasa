import SwiftUI

struct VPNView: View {
    var onOpenMenu: () -> Void

    init(onOpenMenu: @escaping () -> Void = {}) {
        self.onOpenMenu = onOpenMenu
    }

    private var vpn = VPNService.shared

    @State private var killSwitch = true
    @State private var autoConnect = false

    var body: some View {
        @Bindable var vpn = vpn
        VStack(spacing: 0) {
            AppMenuHeader(title: "VPN", onMenu: onOpenMenu)

            ScrollView {
                VStack(spacing: 32) {
                    VStack(spacing: 16) {
                        Text("Qasa VPN")
                            .font(.system(size: 28, weight: .heavy))
                            .foregroundColor(Theme.textPrimary)

                        HStack(spacing: 12) {
                            Image(systemName: "globe")
                                .foregroundColor(Theme.cyan)
                            Text(serverLabel)
                                .font(.system(size: 16))
                                .foregroundColor(Theme.textPrimary)
                                .lineLimit(1)
                        }
                        .padding(.horizontal, 25)
                        .padding(.vertical, 9)
                        .background(Theme.cardBackground)
                        .clipShape(Capsule())
                        .overlay(Capsule().stroke(Theme.stroke, lineWidth: 1))
                    }

                    VStack(spacing: 0) {
                        Button {
                            Task {
                                if vpn.connected {
                                    await vpn.disconnect()
                                } else {
                                    await vpn.connect()
                                }
                            }
                        } label: {
                            ZStack {
                                Circle()
                                    .stroke(Theme.cyan.opacity(0.3), lineWidth: 1)
                                    .frame(width: 160, height: 160)
                                Circle()
                                    .stroke((vpn.connected ? Theme.mint : Theme.stroke).opacity(0.5), lineWidth: 1)
                                    .frame(width: 140, height: 140)
                                Circle()
                                    .fill(Theme.cardBackground)
                                    .frame(width: 128, height: 128)
                                    .overlay(
                                        Circle().stroke(vpn.connected ? Theme.mint : Theme.stroke, lineWidth: 2)
                                    )
                                    .shadow(color: vpn.connected ? Theme.mint.opacity(0.4) : .clear, radius: 20)
                                if vpn.isBusy {
                                    ProgressView()
                                } else {
                                    Image(systemName: "power")
                                        .font(.system(size: 36, weight: .medium))
                                        .foregroundColor(vpn.connected ? Theme.mint : Theme.textSecondary)
                                }
                            }
                        }
                        .disabled(vpn.isBusy)

                        VStack(spacing: 8) {
                            Text(vpn.connected ? "Connected" : "Disconnected")
                                .font(.system(size: 20, weight: .semibold))
                                .foregroundColor(vpn.connected ? Theme.success : Theme.textSecondary)
                            Text("API session only — WireGuard tunnel coming soon")
                                .font(.kasoMono(12))
                                .tracking(0.6)
                                .foregroundColor(Theme.textSecondary)
                                .multilineTextAlignment(.center)
                        }
                        .padding(.top, 32)
                    }
                    .padding(.vertical, 12)

                    if let error = vpn.lastError {
                        Text(error)
                            .font(.system(size: 13))
                            .foregroundColor(Theme.danger)
                            .multilineTextAlignment(.center)
                    }

                    metricCard(icon: "key.fill", label: "VIRTUAL IP", value: vpn.assignedIP ?? "—", highlighted: false)
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
        .task { await vpn.refreshStatus() }
    }

    private var serverLabel: String {
        if let city = vpn.city, let node = vpn.nodeName {
            return "\(city) — \(node)"
        }
        return "Auto-select server"
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
