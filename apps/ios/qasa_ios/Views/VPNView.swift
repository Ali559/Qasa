import SwiftUI

struct VPNView: View {
    var onOpenMenu: (() -> Void)? = nil
    var onUpgrade: () -> Void = {}

    @Environment(AppSession.self) private var session
    private var vpn = VPNService.shared

    // Saved like Android (`qasa.vpn.*`); the page is premium-only, and nothing acts on these
    // until a real tunnel exists (server follow-up plan).
    @AppStorage("qasa.vpn.killSwitch") private var killSwitch = true
    @AppStorage("qasa.vpn.autoConnect") private var autoConnect = false

    private var locale: Locale { AppLanguage.current.locale }

    var body: some View {
        @Bindable var vpn = vpn
        VStack(spacing: 0) {
            AppMenuHeader(title: L10n.tr("nav.vpn", locale: locale), onMenu: onOpenMenu)

            if !session.isPremium {
                premiumGate
            } else {
                premiumContent(vpn: vpn)
            }
        }
        .background(Theme.chrome.ignoresSafeArea())
        .task {
            if session.isPremium {
                await vpn.refreshStatus()
            }
        }
    }

    private var premiumGate: some View {
        VStack(alignment: .leading, spacing: 16) {
            Text(L10n.tr("settings.requiresPremium", locale: locale))
                .font(.qasa(14))
                .foregroundColor(Theme.textSecondary)
            PrimaryButton(title: L10n.tr("settings.upgradePremium", locale: locale), icon: "star.fill") {
                onUpgrade()
            }
            Spacer(minLength: 0)
        }
        .padding(Theme.screenPadding)
        .padding(.top, 24)
    }

    private func premiumContent(vpn: VPNService) -> some View {
        ScrollView {
            VStack(spacing: 32) {
                VStack(spacing: 16) {
                    Text(L10n.tr("vpn.brand", locale: locale))
                        .font(.qasa(28, weight: .heavy))
                        .foregroundColor(Theme.textPrimary)

                    HStack(spacing: 12) {
                        Image(systemName: "globe")
                            .foregroundColor(Theme.cyan)
                        Text(serverLabel)
                            .font(.qasa(16))
                            .foregroundColor(Theme.textPrimary)
                            .lineLimit(1)
                    }
                    .padding(.horizontal, 25)
                    .padding(.vertical, 9)
                    .background(Theme.cardBackground)
                    .clipShape(Capsule())
                    .overlay(Capsule().stroke(Theme.stroke, lineWidth: 1))
                }

                powerDial(vpn: vpn)

                if let error = vpn.lastError {
                    Text(error)
                        .font(.qasa(13))
                        .foregroundColor(Theme.danger)
                        .multilineTextAlignment(.center)
                }

                metricCard(
                    icon: "key.fill",
                    label: L10n.tr("vpn.virtualIP", locale: locale),
                    value: vpn.assignedIP ?? "—",
                    highlighted: false
                )
                metricCard(
                    icon: "lock.shield.fill",
                    label: L10n.tr("vpn.encryption", locale: locale),
                    value: "WireGuard",
                    highlighted: true
                )

                securityFeaturesCard
            }
            .padding(Theme.screenPadding)
            .padding(.top, 16)
            .padding(.bottom, 40)
        }
    }

    private func powerDial(vpn: VPNService) -> some View {
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
                        .stroke((vpn.connected ? Theme.accent : Theme.stroke).opacity(0.5), lineWidth: 1)
                        .frame(width: 140, height: 140)
                    Circle()
                        .fill(Theme.cardBackground)
                        .frame(width: 128, height: 128)
                        .overlay(
                            Circle().stroke(vpn.connected ? Theme.accent : Theme.stroke, lineWidth: 2)
                        )
                        .shadow(color: vpn.connected ? Theme.accent.opacity(0.4) : .clear, radius: 20)
                    if vpn.isBusy {
                        ProgressView()
                    } else {
                        Image(systemName: "power")
                            .font(.system(size: 36, weight: .medium))
                            .foregroundColor(vpn.connected ? Theme.accent : Theme.textSecondary)
                    }
                }
            }
            .buttonStyle(PressableStyle())
            .disabled(vpn.isBusy)

            VStack(spacing: 8) {
                Text(vpn.connected
                     ? L10n.tr("vpn.connected", locale: locale)
                     : L10n.tr("vpn.disconnected", locale: locale))
                    .font(.qasa(20, weight: .semibold))
                    .foregroundColor(vpn.connected ? Theme.success : Theme.textSecondary)
                Text(L10n.tr("vpn.sessionOnly", locale: locale))
                    .font(.qasa(12))
                    .foregroundColor(Theme.textSecondary)
                    .multilineTextAlignment(.center)
            }
            .padding(.top, 32)
        }
        .padding(.vertical, 12)
    }

    private var securityFeaturesCard: some View {
        CardContainer(background: Theme.cardMuted) {
            VStack(alignment: .leading, spacing: 16) {
                Text(L10n.tr("vpn.securityFeatures", locale: locale))
                    .font(.qasa(20, weight: .semibold))
                    .foregroundColor(Theme.textPrimary)
                    .padding(.bottom, 8)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .overlay(alignment: .bottom) {
                        Rectangle().fill(Theme.stroke).frame(height: 1)
                    }

                ToggleRow(
                    icon: "hand.raised.fill",
                    iconColor: Theme.danger,
                    title: L10n.tr("vpn.killSwitch", locale: locale),
                    subtitle: L10n.tr("vpn.killSwitchSub", locale: locale),
                    isOn: $killSwitch,
                    iconBackground: Theme.dangerBackground.opacity(0.35)
                )

                ToggleRow(
                    icon: "arrow.triangle.2.circlepath",
                    iconColor: Theme.cyan,
                    title: L10n.tr("vpn.autoConnect", locale: locale),
                    subtitle: L10n.tr("vpn.autoConnectSub", locale: locale),
                    isOn: $autoConnect,
                    iconBackground: Theme.cyan.opacity(0.12)
                )
            }
        }
    }

    private var serverLabel: String {
        if let city = vpn.city, let node = vpn.nodeName {
            return "\(city) — \(node)"
        }
        return L10n.tr("vpn.autoSelect", locale: locale)
    }

    private func metricCard(icon: String, label: String, value: String, highlighted: Bool) -> some View {
        VStack(spacing: 8) {
            Image(systemName: icon)
                .foregroundColor(Theme.accent)
            Text(label)
                .font(.qasa(12, weight: .semibold))
                .foregroundColor(Theme.textSecondary)
            Text(value)
                .font(.kasoMono(16, weight: .regular))
                .foregroundColor(Theme.textPrimary)
        }
        .frame(maxWidth: .infinity)
        .padding(17)
        .background(Theme.cardBackground)
        .clipShape(RoundedRectangle(cornerRadius: Theme.cornerRadius, style: .continuous))
        .overlay(
            RoundedRectangle(cornerRadius: Theme.cornerRadius, style: .continuous)
                .stroke(highlighted ? Theme.accent : Theme.stroke, lineWidth: highlighted ? 1.5 : 1)
        )
    }
}

#Preview {
    VPNView()
        .environment(AppSession())
}
