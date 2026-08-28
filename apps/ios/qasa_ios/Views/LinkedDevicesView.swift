import SwiftUI
import UIKit

struct LinkedDevicesView: View {
    @Environment(\.dismiss) private var dismiss
    @Environment(AppSession.self) private var session

    @State private var devices: [RemoteDevice] = []
    @State private var isLoading = false
    @State private var errorMessage: String?
    @State private var pendingUnlink: RemoteDevice?

    private var registration = DeviceRegistrationService.shared
    private var currentDeviceId = LicenseManager.shared.parsed?.deviceId

    private var tierLabel: String {
        let used = max(devices.count, registration.devicesUsed)
        let limit = session.deviceLimit
        return "\(session.tierLabel): \(used)/\(limit) Used"
    }

    var body: some View {
        VStack(spacing: 0) {
            HStack(spacing: 16) {
                Button { dismiss() } label: {
                    Image(systemName: "chevron.left")
                        .font(.system(size: 16, weight: .semibold))
                        .foregroundColor(Theme.textPrimary)
                        .frame(width: 32, height: 32)
                }
                Text("Linked Devices")
                    .font(.system(size: 20, weight: .semibold))
                    .foregroundColor(Theme.textOnChrome)
                Spacer()
                StatusPill(
                    text: tierLabel,
                    dotColor: session.isPremium ? Theme.success : Theme.goldMuted,
                    textColor: Theme.textSecondary,
                    background: Color(hex: "2A2A2B")
                )
            }
            .padding(.horizontal, 16)
            .frame(height: 64)
            .background(Theme.chrome)
            .overlay(alignment: .bottom) {
                Rectangle().fill(Theme.strokeChrome).frame(height: 1)
            }

            ScrollView {
                VStack(alignment: .leading, spacing: 24) {
                    if !session.isPremium {
                        upgradeHintBanner
                    }

                    if let limitError = registration.lastLimitError {
                        limitExceededBanner(limitError)
                    }

                    VStack(alignment: .leading, spacing: 16) {
                        HStack {
                            Text("Active Devices")
                                .font(.system(size: 20, weight: .semibold))
                                .foregroundColor(Theme.textPrimary)
                            Spacer()
                            if isLoading {
                                ProgressView()
                            }
                        }

                        if let errorMessage {
                            Text(errorMessage)
                                .font(.system(size: 14))
                                .foregroundColor(Theme.danger)
                        }

                        if devices.isEmpty, !isLoading {
                            Text(L10n.tr("devices.noneLinked"))
                                .font(.qasa(14))
                                .foregroundColor(Theme.textSecondary)
                        }

                        ForEach(devices) { device in
                            deviceCard(device)
                        }
                    }

                    infoNote
                }
                .padding(.horizontal, Theme.screenPadding)
                .padding(.vertical, 24)
            }
        }
        .background(Theme.chrome.ignoresSafeArea())
        .task { await loadDevices() }
        .alert("Unlink Device?", isPresented: Binding(
            get: { pendingUnlink != nil },
            set: { if !$0 { pendingUnlink = nil } }
        )) {
            Button("Cancel", role: .cancel) { pendingUnlink = nil }
            Button("Unlink", role: .destructive) {
                if let pendingUnlink {
                    Task { await unlink(pendingUnlink) }
                }
            }
        } message: {
            if let pendingUnlink {
                Text("Unlink \(pendingUnlink.device_name)? It will be signed out immediately.")
            }
        }
    }

    private func loadDevices() async {
        isLoading = true
        defer { isLoading = false }
        do {
            devices = try await registration.fetchDevices()
            errorMessage = nil
        } catch {
            if let limit = registration.lastLimitError?.devices {
                devices = limit
            }
            errorMessage = error.localizedDescription
        }
    }

    private func unlink(_ device: RemoteDevice) async {
        do {
            try await registration.unlinkDevice(id: device.id)
            pendingUnlink = nil
            await loadDevices()
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    private func deviceCard(_ device: RemoteDevice) -> some View {
        let isCurrent = device.id == currentDeviceId
        return VStack(alignment: .leading, spacing: 16) {
            HStack(spacing: 16) {
                Image(systemName: device.platform.lowercased().contains("ios") ? "iphone" : "desktopcomputer")
                    .font(.system(size: 28))
                    .foregroundColor(Theme.mint)
                    .frame(width: 40)

                VStack(alignment: .leading, spacing: 4) {
                    HStack(spacing: 8) {
                        Text(device.device_name)
                            .font(.system(size: 16, weight: .bold))
                            .foregroundColor(Theme.textPrimary)
                        if isCurrent {
                            PillBadge(text: "THIS DEVICE", color: Theme.mintDark, solid: true)
                        }
                    }
                    Text(device.platform.uppercased())
                        .font(.system(size: 14))
                        .foregroundColor(Theme.textSecondary)
                }
            }
            .padding(.leading, 8)

            Button {
                pendingUnlink = device
            } label: {
                HStack(spacing: 8) {
                    Image(systemName: "link")
                    Text("Unlink")
                }
                .font(.system(size: 14))
                .frame(maxWidth: .infinity)
                .padding(.vertical, 9)
                .foregroundColor(isCurrent ? Theme.textTertiary : Theme.textSecondary)
                .overlay(
                    RoundedRectangle(cornerRadius: 8)
                        .stroke(Theme.strokeChrome, lineWidth: 1)
                )
            }
            .disabled(isCurrent)
        }
        .padding(21)
        .background(Theme.cardBackground)
        .cornerRadius(Theme.cornerRadius)
        .overlay(
            RoundedRectangle(cornerRadius: Theme.cornerRadius)
                .stroke(Theme.stroke, lineWidth: 1)
        )
        .overlay(alignment: .leading) {
            if isCurrent {
                UnevenRoundedRectangle(
                    topLeadingRadius: Theme.cornerRadius,
                    bottomLeadingRadius: Theme.cornerRadius,
                    bottomTrailingRadius: 0,
                    topTrailingRadius: 0
                )
                .fill(Theme.mint)
                .frame(width: 4)
            }
        }
        .clipShape(RoundedRectangle(cornerRadius: Theme.cornerRadius))
    }

    private func limitExceededBanner(_ error: DeviceLimitErrorBody) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Device limit reached")
                .font(.system(size: 16, weight: .bold))
                .foregroundColor(Theme.danger)
            Text(error.message)
                .font(.system(size: 14))
                .foregroundColor(Theme.textSecondary)
        }
        .padding(17)
        .background(Theme.dangerBackground.opacity(0.2))
        .cornerRadius(Theme.cornerRadius)
        .overlay(
            RoundedRectangle(cornerRadius: Theme.cornerRadius)
                .stroke(Theme.danger.opacity(0.4), lineWidth: 1)
        )
    }

    private var upgradeHintBanner: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack(alignment: .top, spacing: 16) {
                ZStack {
                    Circle()
                        .fill(Theme.gold.opacity(0.2))
                        .frame(width: 44, height: 44)
                    Image(systemName: "star.fill")
                        .foregroundColor(Theme.gold)
                }
                VStack(alignment: .leading, spacing: 4) {
                    Text("Need more devices?")
                        .font(.system(size: 16, weight: .bold))
                        .foregroundColor(Theme.gold)
                    Text("Upgrade to Premium for up to 5 simultaneous devices.")
                        .font(.system(size: 14))
                        .foregroundColor(Theme.textSecondary)
                        .fixedSize(horizontal: false, vertical: true)
                }
            }
        }
        .padding(21)
        .background(
            LinearGradient(
                colors: [Theme.gold.opacity(0.1), Theme.gold.opacity(0.05)],
                startPoint: .topLeading,
                endPoint: .bottomTrailing
            )
        )
        .cornerRadius(Theme.cornerRadius)
        .overlay(
            RoundedRectangle(cornerRadius: Theme.cornerRadius)
                .stroke(Theme.gold.opacity(0.2), lineWidth: 1)
        )
    }

    private var infoNote: some View {
        HStack(alignment: .top, spacing: 12) {
            Image(systemName: "info.circle.fill")
                .foregroundColor(Theme.info)
                .font(.system(size: 18))
            Text("Unlinking a device signs it out immediately. Local vault data on that device is not deleted from here.")
                .font(.system(size: 14))
                .foregroundColor(Theme.textSecondary)
                .fixedSize(horizontal: false, vertical: true)
        }
        .padding(17)
        .background(Theme.cardMuted)
        .cornerRadius(8)
        .overlay(
            RoundedRectangle(cornerRadius: 8)
                .stroke(Theme.strokeChrome, lineWidth: 1)
        )
        .padding(.top, 8)
    }
}

#Preview {
    LinkedDevicesView()
        .environment(AppSession())
}
