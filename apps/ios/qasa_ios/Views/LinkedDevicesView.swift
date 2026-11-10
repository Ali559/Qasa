import SwiftUI
import UIKit

struct LinkedDevicesView: View {
    @Environment(\.dismiss) private var dismiss
    @Environment(AppSession.self) private var session
    @Environment(AppPreferences.self) private var prefs

    @State private var devices: [RemoteDevice] = []
    @State private var isLoading = false
    @State private var errorMessage: String?
    @State private var pendingUnlink: RemoteDevice?
    @State private var pendingLogout: RemoteDevice?
    @State private var showUnlinkAllConfirm = false
    @State private var showPremium = false
    @State private var isActing = false
    /// The device a restored backup may replace, awaiting confirmation.
    @State private var pendingReplace: RestoreKeyDevice?
    /// "<old device> was unlinked…" after a replacement.
    @State private var noticeMessage: String?

    private var registration = DeviceRegistrationService.shared
    /// Read live: a replacement registers this device under a new id.
    private var currentDeviceId: String? { LicenseManager.shared.parsed?.deviceId }
    private var locale: Locale { prefs.locale }

    private var tierLabel: String {
        let used = max(devices.count, registration.devicesUsed)
        let limit = session.deviceLimit
        return String(
            format: L10n.tr("devices.tierUsed", locale: locale),
            session.tierLabel,
            used,
            limit
        )
    }

    private var canUnlinkOthers: Bool {
        session.isPremium && devices.filter { !$0.isPrimary }.count > 0
    }

    var body: some View {
        VStack(spacing: 0) {
            header

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
                            Text(L10n.tr("devices.activeDevices", locale: locale))
                                .font(.qasa(20, weight: .semibold))
                                .foregroundColor(Theme.textPrimary)
                            Spacer()
                            if isLoading || isActing || registration.isReplacing {
                                ProgressView()
                            }
                        }

                        if let noticeMessage {
                            Text(noticeMessage)
                                .font(.qasa(14, weight: .medium))
                                .foregroundColor(Theme.accent)
                                .fixedSize(horizontal: false, vertical: true)
                        }

                        if let errorMessage {
                            Text(errorMessage)
                                .font(.qasa(14))
                                .foregroundColor(Theme.danger)
                        }

                        if devices.isEmpty, !isLoading {
                            Text(L10n.tr("devices.noneLinked", locale: locale))
                                .font(.qasa(14))
                                .foregroundColor(Theme.textSecondary)
                        }

                        ForEach(devices) { device in
                            deviceCard(device)
                        }

                        if canUnlinkOthers {
                            Button {
                                showUnlinkAllConfirm = true
                            } label: {
                                HStack(spacing: 8) {
                                    Image(systemName: "link.badge.minus")
                                    Text(L10n.tr("devices.unlinkAllOthers", locale: locale))
                                }
                                .font(.qasa(14, weight: .semibold))
                                .frame(maxWidth: .infinity)
                                .padding(.vertical, 12)
                                .foregroundColor(Theme.danger)
                                .overlay(
                                    RoundedRectangle(cornerRadius: 8)
                                        .stroke(Theme.danger.opacity(0.45), lineWidth: 1)
                                )
                            }
                            .disabled(isActing)
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
        .sheet(isPresented: $showPremium) {
            PremiumUpgradeView(embedded: false)
                .environment(session)
                .appLocalization()
        }
        .alert(
            L10n.tr("devices.unlinkTitle", locale: locale),
            isPresented: Binding(
                get: { pendingUnlink != nil },
                set: { if !$0 { pendingUnlink = nil } }
            )
        ) {
            Button(L10n.tr("settings.cancel", locale: locale), role: .cancel) { pendingUnlink = nil }
            Button(L10n.tr("devices.unlink", locale: locale), role: .destructive) {
                if let pendingUnlink {
                    Task { await unlink(pendingUnlink) }
                }
            }
        } message: {
            if let pendingUnlink {
                Text(
                    String(
                        format: L10n.tr("devices.unlinkMessage", locale: locale),
                        pendingUnlink.device_name
                    )
                )
            }
        }
        .alert(
            L10n.tr("devices.logoutTitle", locale: locale),
            isPresented: Binding(
                get: { pendingLogout != nil },
                set: { if !$0 { pendingLogout = nil } }
            )
        ) {
            Button(L10n.tr("settings.cancel", locale: locale), role: .cancel) { pendingLogout = nil }
            Button(L10n.tr("devices.logout", locale: locale), role: .destructive) {
                if let pendingLogout {
                    Task { await logoutDevice(pendingLogout) }
                }
            }
        } message: {
            if let pendingLogout {
                Text(
                    String(
                        format: L10n.tr("devices.logoutMessage", locale: locale),
                        pendingLogout.device_name
                    )
                )
            }
        }
        .alert(
            L10n.tr("backup.replace.title", locale: locale),
            isPresented: Binding(
                get: { pendingReplace != nil },
                set: { if !$0 { pendingReplace = nil } }
            ),
            presenting: pendingReplace
        ) { _ in
            Button(L10n.tr("backup.replace.button", locale: locale)) {
                Task { await replaceOldDevice() }
            }
            Button(L10n.tr("backup.reminder.dismiss", locale: locale), role: .cancel) {}
        } message: { device in
            Text(String(format: L10n.tr("backup.replace.bodyFmt", locale: locale), device.device_name))
        }
        .alert(
            L10n.tr("devices.unlinkAllTitle", locale: locale),
            isPresented: $showUnlinkAllConfirm
        ) {
            Button(L10n.tr("settings.cancel", locale: locale), role: .cancel) {}
            Button(L10n.tr("devices.unlinkAllOthers", locale: locale), role: .destructive) {
                Task { await unlinkOthers() }
            }
        } message: {
            Text(L10n.tr("devices.unlinkAllMessage", locale: locale))
        }
    }

    private var header: some View {
        HStack(spacing: 16) {
            Button { dismiss() } label: {
                Image(systemName: "chevron.backward")
                    .font(.qasa(16, weight: .semibold))
                    .foregroundColor(Theme.textPrimary)
                    .frame(width: 32, height: 32)
            }
            Text(L10n.tr("devices.title", locale: locale))
                .font(.qasa(20, weight: .semibold))
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
    }

    private func loadDevices() async {
        isLoading = true
        defer { isLoading = false }
        do {
            devices = try await registration.fetchDevices()
            errorMessage = nil
        } catch QasaAPIError.unauthorized {
            if let limit = registration.lastLimitError?.devices {
                devices = limit
            }
            errorMessage = L10n.tr("devices.sessionExpired", locale: locale)
        } catch {
            if let limit = registration.lastLimitError?.devices {
                devices = limit
            }
            errorMessage = error.localizedDescription
        }
    }

    private func unlink(_ device: RemoteDevice) async {
        isActing = true
        defer { isActing = false }
        do {
            try await registration.unlinkDevice(id: device.id)
            pendingUnlink = nil
            if device.id == currentDeviceId {
                LicenseManager.shared.clear()
            }
            await loadDevices()
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    /// Remote logout = revoke license (same as unlink). Current device locks locally without unlinking.
    private func logoutDevice(_ device: RemoteDevice) async {
        isActing = true
        defer { isActing = false }
        pendingLogout = nil
        if device.id == currentDeviceId {
            dismiss()
            AutoLockMonitor.shared.requestLock()
            return
        }
        do {
            try await registration.unlinkDevice(id: device.id)
            await loadDevices()
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    /// Takes the place of the device the restored backup came from, then continues as
    /// after any successful registration.
    private func replaceOldDevice() async {
        errorMessage = nil
        noticeMessage = nil
        switch await registration.replaceOldDevice() {
        case .replaced(let oldName):
            await AppBootstrap.onDeviceReplaced(session: session)
            if let oldName {
                noticeMessage = String(format: L10n.tr("backup.replace.doneFmt", locale: locale), oldName)
            }
            await loadDevices()
        case .failed:
            errorMessage = L10n.tr("backup.replace.failed", locale: locale)
        }
    }

    private func unlinkOthers() async {
        isActing = true
        defer { isActing = false }
        do {
            let remaining = try await registration.unlinkOtherDevices()
            devices = remaining
            if let current = currentDeviceId, !remaining.contains(where: { $0.id == current }) {
                LicenseManager.shared.clear()
            }
            errorMessage = nil
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    private func deviceCard(_ device: RemoteDevice) -> some View {
        let isCurrent = device.id == currentDeviceId
        let locationText = device.location?.trimmingCharacters(in: .whitespacesAndNewlines)
        let activated = formatDate(device.activatedAt)

        return VStack(alignment: .leading, spacing: 14) {
            Button {
                if !session.isPremium {
                    showPremium = true
                }
            } label: {
                HStack(alignment: .top, spacing: 16) {
                    Image(systemName: platformIcon(device.platform))
                        .font(.qasa(28))
                        .foregroundColor(Theme.accent)
                        .frame(width: 40)

                    VStack(alignment: .leading, spacing: 6) {
                        HStack(spacing: 8) {
                            Text(device.device_name)
                                .font(.qasa(16, weight: .bold))
                                .foregroundColor(Theme.textPrimary)
                                .multilineTextAlignment(.leading)
                            if isCurrent {
                                PillBadge(
                                    text: L10n.tr("devices.thisDevice", locale: locale),
                                    color: Theme.accentDark,
                                    solid: true
                                )
                            }
                            if device.isPrimary {
                                PillBadge(
                                    text: L10n.tr("devices.primary", locale: locale),
                                    color: Theme.goldMuted,
                                    solid: false
                                )
                            }
                        }

                        Text(device.platform.uppercased())
                            .font(.qasa(13))
                            .foregroundColor(Theme.textSecondary)

                        if let locationText, !locationText.isEmpty {
                            Label(locationText, systemImage: "mappin.and.ellipse")
                                .font(.qasa(13))
                                .foregroundColor(Theme.textSecondary)
                        }

                        if let activated {
                            Text(
                                String(
                                    format: L10n.tr("devices.activatedAt", locale: locale),
                                    activated
                                )
                            )
                            .font(.qasaLabel(12))
                            .foregroundColor(Theme.textTertiary)
                        }
                    }

                    Spacer(minLength: 0)

                    if !session.isPremium {
                        Image(systemName: "lock.fill")
                            .font(.qasa(12, weight: .semibold))
                            .foregroundColor(Theme.goldMuted)
                    }
                }
                .padding(.leading, 8)
            }
            .buttonStyle(.plain)
            .disabled(session.isPremium)

            if session.isPremium {
                HStack(spacing: 10) {
                    actionChip(
                        title: L10n.tr("devices.logout", locale: locale),
                        systemImage: "rectangle.portrait.and.arrow.right",
                        destructive: false
                    ) {
                        pendingLogout = device
                    }

                    actionChip(
                        title: L10n.tr("devices.unlink", locale: locale),
                        systemImage: "link",
                        destructive: true,
                        disabled: device.isPrimary && devices.count > 1
                    ) {
                        if device.isPrimary && devices.count > 1 {
                            errorMessage = L10n.tr("devices.cannotUnlinkPrimary", locale: locale)
                        } else {
                            pendingUnlink = device
                        }
                    }
                }
            } else if !isCurrent {
                // Free recovery: allow unlinking a blocking remote device without upgrade.
                Button {
                    pendingUnlink = device
                } label: {
                    HStack(spacing: 8) {
                        Image(systemName: "link")
                        Text(L10n.tr("devices.unlink", locale: locale))
                    }
                    .font(.qasa(14))
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 9)
                    .foregroundColor(Theme.textSecondary)
                    .overlay(
                        RoundedRectangle(cornerRadius: 8)
                            .stroke(Theme.strokeChrome, lineWidth: 1)
                    )
                }
            }
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
                .fill(Theme.accent)
                .frame(width: 4)
            }
        }
        .clipShape(RoundedRectangle(cornerRadius: Theme.cornerRadius))
    }

    private func actionChip(
        title: String,
        systemImage: String,
        destructive: Bool,
        disabled: Bool = false,
        action: @escaping () -> Void
    ) -> some View {
        Button(action: action) {
            HStack(spacing: 6) {
                Image(systemName: systemImage)
                Text(title)
            }
            .font(.qasa(13, weight: .medium))
            .frame(maxWidth: .infinity)
            .padding(.vertical, 9)
            .foregroundColor(
                disabled
                    ? Theme.textTertiary
                    : (destructive ? Theme.danger : Theme.textSecondary)
            )
            .overlay(
                RoundedRectangle(cornerRadius: 8)
                    .stroke(
                        destructive ? Theme.danger.opacity(0.35) : Theme.strokeChrome,
                        lineWidth: 1
                    )
            )
        }
        .disabled(disabled || isActing)
    }

    private func platformIcon(_ platform: String) -> String {
        let p = platform.lowercased()
        if p.contains("ios") || p.contains("iphone") || p.contains("ipad") {
            return "iphone"
        }
        if p.contains("android") {
            return "smartphone"
        }
        if p.contains("mac") {
            return "laptopcomputer"
        }
        return "desktopcomputer"
    }

    private func formatDate(_ date: Date?) -> String? {
        guard let date else { return nil }
        let formatter = DateFormatter()
        formatter.locale = locale
        formatter.dateStyle = .medium
        formatter.timeStyle = .short
        return formatter.string(from: date)
    }

    private func limitExceededBanner(_ error: DeviceLimitErrorBody) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(L10n.tr("devices.limitReached", locale: locale))
                .font(.qasa(16, weight: .bold))
                .foregroundColor(Theme.danger)
            Text(error.message)
                .font(.qasa(14))
                .foregroundColor(Theme.textSecondary)
            restoreKeyNote(error)
        }
        .padding(17)
        .background(Theme.dangerBackground.opacity(0.2))
        .cornerRadius(Theme.cornerRadius)
        .overlay(
            RoundedRectangle(cornerRadius: Theme.cornerRadius)
                .stroke(Theme.danger.opacity(0.4), lineWidth: 1)
        )
    }

    /// What the restored backup's key can do about the limit (nothing when none was sent).
    @ViewBuilder
    private func restoreKeyNote(_ error: DeviceLimitErrorBody) -> some View {
        switch error.restoreKeyStatus {
        case .replaceable:
            if registration.canReplaceOldDevice, let device = error.replaceable_device {
                Button {
                    pendingReplace = device
                } label: {
                    HStack(spacing: 8) {
                        Image(systemName: "arrow.triangle.2.circlepath")
                        Text(L10n.tr("backup.replace.button", locale: locale))
                    }
                    .font(.qasa(14, weight: .semibold))
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 10)
                    .foregroundColor(Theme.accent)
                    .overlay(
                        RoundedRectangle(cornerRadius: 8)
                            .stroke(Theme.accent.opacity(0.45), lineWidth: 1)
                    )
                }
                .disabled(isActing || registration.isReplacing)
                .padding(.top, 4)
            }
        case .unknown:
            restoreKeyText("backup.replace.unknown")
        case .notEnough:
            restoreKeyText("backup.replace.notEnough")
        case nil:
            EmptyView()
        }
    }

    private func restoreKeyText(_ key: String) -> some View {
        Text(L10n.tr(key, locale: locale))
            .font(.qasa(14))
            .foregroundColor(Theme.textSecondary)
            .fixedSize(horizontal: false, vertical: true)
    }

    private var upgradeHintBanner: some View {
        Button { showPremium = true } label: {
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
                        Text(L10n.tr("devices.needMore", locale: locale))
                            .font(.qasa(16, weight: .bold))
                            .foregroundColor(Theme.gold)
                        Text(L10n.tr("devices.upgradeHint", locale: locale))
                            .font(.qasa(14))
                            .foregroundColor(Theme.textSecondary)
                            .fixedSize(horizontal: false, vertical: true)
                            .multilineTextAlignment(.leading)
                    }
                    Spacer(minLength: 0)
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
        .buttonStyle(.plain)
    }

    private var infoNote: some View {
        HStack(alignment: .top, spacing: 12) {
            Image(systemName: "info.circle.fill")
                .foregroundColor(Theme.info)
                .font(.qasa(18))
            Text(L10n.tr("devices.infoNote", locale: locale))
                .font(.qasa(14))
                .foregroundColor(Theme.textSecondary)
                .fixedSize(horizontal: false, vertical: true)
                .multilineTextAlignment(.leading)
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
        .environment(AppPreferences.shared)
}
