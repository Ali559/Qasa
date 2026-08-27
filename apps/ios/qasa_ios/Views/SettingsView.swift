import SwiftUI
import UIKit
import UniformTypeIdentifiers

struct SettingsView: View {
    var onOpenMenu: () -> Void = {}
    var onLogout: (() -> Void)? = nil

    @Environment(AppSession.self) private var session
    @Environment(AppPreferences.self) private var prefs
    @State private var biometricUnlock = VaultKeychainStore.biometricsEnabled
    @State private var showClearDataAlert = false
    @State private var showLinkedDevices = false
    @State private var showPremium = false
    @State private var showLogoutConfirm = false
    @State private var showLANSync = false
    @State private var showImport = false
    @State private var exportText: String?
    @State private var pairingCode: String?
    @State private var linkedDeviceCount = 0
    @State private var statusMessage: String?

    var body: some View {
        VStack(spacing: 0) {
            AppMenuHeader(title: "Settings", onMenu: onOpenMenu)

            ScrollView {
                VStack(alignment: .leading, spacing: 24) {
                    profileCard

                    settingsSection(title: "Security") {
                        NavChevronRow(icon: "ellipsis", title: "Change Master Password")
                        Divider().background(Theme.stroke)
                        ToggleRow(
                            icon: "touchid",
                            iconColor: Theme.textPrimary,
                            title: "Biometric Unlock",
                            subtitle: "",
                            isOn: $biometricUnlock
                        )
                        .onChange(of: biometricUnlock) { _, newValue in
                            VaultKeychainStore.biometricsEnabled = newValue
                        }
                        Divider().background(Theme.stroke)
                        NavChevronRow(icon: "clock", title: "Auto-lock", trailingText: "5 mins")
                    }

                    VStack(alignment: .leading, spacing: 8) {
                        HStack {
                            SectionLabel(text: "Linked Devices")
                            Spacer()
                            Text("\(linkedDeviceCount) Active")
                                .font(.kasoMono(12))
                                .foregroundColor(Theme.mint)
                        }
                        CardContainer {
                            Button { showLinkedDevices = true } label: {
                                NavChevronRow(
                                    icon: "iphone",
                                    title: UIDevice.current.name,
                                    subtitle: "(This Device)",
                                    iconColor: Theme.mint
                                )
                            }
                            .buttonStyle(.plain)
                            Divider().background(Theme.stroke)
                            Button {
                                if let id = VaultKeychainStore.accountId.flatMap(UUID.init(uuidString:)) {
                                    pairingCode = PairingSessionStore.shared.publish(accountId: id)
                                }
                            } label: {
                                NavChevronRow(
                                    icon: "qrcode",
                                    title: "Show pairing code",
                                    subtitle: pairingCode.map { "Code: \($0) (10 min)" } ?? "For a new device on Wi‑Fi"
                                )
                            }
                            .buttonStyle(.plain)
                            Divider().background(Theme.stroke)
                            Button { showLANSync = true } label: {
                                NavChevronRow(
                                    icon: "wifi",
                                    title: "Sync on this network",
                                    subtitle: session.isPremium ? "Premium LAN sync" : "Requires Premium"
                                )
                            }
                            .buttonStyle(.plain)
                        }
                    }

                    settingsSection(title: "Appearance") {
                        Menu {
                            ForEach(AppLanguage.allCases) { lang in
                                Button(lang.displayName) { prefs.language = lang }
                            }
                        } label: {
                            NavChevronRow(icon: "globe", title: "Language", trailingText: prefs.language.displayName)
                        }
                        Divider().background(Theme.stroke)
                        Menu {
                            ForEach(AppThemePreference.allCases) { theme in
                                Button(theme.displayName) { prefs.theme = theme }
                            }
                        } label: {
                            NavChevronRow(icon: "moon.fill", title: "Theme", trailingText: prefs.theme.displayName)
                        }
                    }

                    settingsSection(title: "Data & Account") {
                        Button { showImport = true } label: {
                            NavChevronRow(icon: "square.and.arrow.down", title: "Import CSV")
                        }
                        .buttonStyle(.plain)
                        Divider().background(Theme.stroke)
                        Button {
                            // Lightweight export of list metadata; full crypto export is local-only.
                            exportText = VaultCSV.exportCSV(rows: [])
                            statusMessage = "Export prepared (open vault list for full CSV in a follow-up)."
                            UIPasteboard.general.string = exportText
                        } label: {
                            NavChevronRow(icon: "square.and.arrow.up", title: "Export Vault CSV")
                        }
                        .buttonStyle(.plain)
                        Divider().background(Theme.stroke)
                        Button { showClearDataAlert = true } label: {
                            NavChevronRow(icon: "xmark.bin", title: "Clear Local Data", destructive: true)
                        }
                        .buttonStyle(.plain)
                        Divider().background(Theme.stroke)
                        Button { showLogoutConfirm = true } label: {
                            NavChevronRow(icon: "rectangle.portrait.and.arrow.right", title: "Logout")
                        }
                        .buttonStyle(.plain)
                    }

                    if let statusMessage {
                        Text(statusMessage)
                            .font(.caption)
                            .foregroundColor(Theme.mint)
                    }
                }
                .padding(Theme.screenPadding)
                .padding(.bottom, 40)
            }
        }
        .background(Theme.background.ignoresSafeArea())
        .alert("Clear Local Data?", isPresented: $showClearDataAlert) {
            Button("Cancel", role: .cancel) {}
            Button("Clear", role: .destructive) {
                VaultAuthStore.resetVaultAuth()
            }
        } message: {
            Text("This permanently deletes your local vault. This cannot be undone.")
        }
        .alert("Lock Vault?", isPresented: $showLogoutConfirm) {
            Button("Cancel", role: .cancel) {}
            Button("Logout", role: .destructive) {
                onLogout?()
            }
        } message: {
            Text("You’ll need your master password to unlock again.")
        }
        .sheet(isPresented: $showLinkedDevices) { LinkedDevicesView() }
        .sheet(isPresented: $showPremium) {
            Group {
                if session.isPremium {
                    ManageSubscriptionView(embedded: false)
                } else {
                    PremiumUpgradeView(embedded: false)
                }
            }
            .environment(session)
        }
        .sheet(isPresented: $showLANSync) {
            LANSyncView()
                .environment(session)
        }
        .fileImporter(
            isPresented: $showImport,
            allowedContentTypes: [.commaSeparatedText, .plainText],
            allowsMultipleSelection: false
        ) { result in
            if case .success(let urls) = result, let url = urls.first,
               let data = try? Data(contentsOf: url),
               let text = String(data: data, encoding: .utf8) {
                do {
                    let rows = try VaultCSV.parseImport(text)
                    statusMessage = "Parsed \(rows.count) CSV row(s). Import into vault from editor next."
                } catch {
                    statusMessage = error.localizedDescription
                }
            }
        }
        .task {
            if let devices = try? await DeviceRegistrationService.shared.fetchDevices() {
                linkedDeviceCount = devices.count
            }
            await AppPreferences.shared.syncLocaleToServer()
        }
    }

    private var profileCard: some View {
        CardContainer {
            HStack(spacing: 12) {
                Circle()
                    .stroke(Theme.mint, lineWidth: 2)
                    .frame(width: 56, height: 56)
                    .overlay(
                        Image(systemName: "person.fill")
                            .font(.title2)
                            .foregroundColor(Theme.textSecondary)
                    )
                VStack(alignment: .leading, spacing: 4) {
                    Text(session.username)
                        .font(.system(size: 16, weight: .bold))
                        .foregroundColor(Theme.textPrimary)
                    Text(session.email)
                        .font(.system(size: 14))
                        .foregroundColor(Theme.textSecondary)
                    HStack(spacing: 4) {
                        Image(systemName: session.isPremium ? "trophy.fill" : "person")
                            .font(.system(size: 10))
                        Text(session.isPremium ? "Premium Account" : "Free Account")
                            .font(.kasoMono(12))
                    }
                    .foregroundColor(session.isPremium ? Theme.mint : Theme.textSecondary)
                    .padding(.horizontal, 8)
                    .padding(.vertical, 3)
                    .overlay(Capsule().stroke(session.isPremium ? Theme.mint : Theme.stroke, lineWidth: 1))
                }
                Spacer()
            }

            Button { showPremium = true } label: {
                Text(session.isPremium ? "Manage Subscription" : "Upgrade to Premium")
                    .font(.system(size: 14, weight: .medium))
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 10)
                    .background(Theme.cardBackgroundLight)
                    .foregroundColor(Theme.textPrimary)
                    .cornerRadius(8)
            }
            .buttonStyle(.plain)
            .padding(.top, 12)
        }
    }

    private func settingsSection(title: String, @ViewBuilder content: () -> some View) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            SectionLabel(text: title)
            CardContainer { content() }
        }
    }
}

#Preview {
    SettingsView(onOpenMenu: {})
        .environment(AppSession())
        .environment(AppPreferences.shared)
}
