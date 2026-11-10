import SwiftUI
import UIKit
import UniformTypeIdentifiers
import LocalAuthentication
import SwiftData

struct SettingsView: View {
    var onOpenMenu: (() -> Void)? = nil
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
    @State private var showBackups = false
    @State private var showChangeMasterPassword = false
    @State private var showAutoLock = false
    @State private var exportDocument: CSVDocument?
    @State private var linkedDeviceCount = 0
    @State private var statusMessage: String?
    @State private var showDeleteAccountAlert = false
    @State private var isDeletingAccount = false
    /// The Keychain isn't observable; read when the screen appears.
    @State private var canDeleteAccount = AccountDeletion.isAvailable
    @Environment(\.modelContext) private var modelContext

    private var locale: Locale { prefs.locale }

    /// Seal state under the biometric toggle, the same three states Android shows: the
    /// password is sealed, the device can't do biometrics, or it gets sealed on the next
    /// master-password unlock (turning the toggle on does not seal anything by itself).
    private var biometricSubtitle: String {
        guard biometricUnlock else { return "" }
        if VaultKeychainStore.biometricPasswordStored {
            return L10n.tr("settings.biometricSealed", locale: locale)
        }
        if !LAContext().canEvaluatePolicy(.deviceOwnerAuthenticationWithBiometrics, error: nil) {
            return L10n.tr("settings.biometricNoGate", locale: locale)
        }
        return L10n.tr("settings.biometricPending", locale: locale)
    }

    var body: some View {
        VStack(spacing: 0) {
            AppMenuHeader(title: L10n.tr("settings.title", locale: locale), onMenu: onOpenMenu)

            ScrollView {
                VStack(alignment: .leading, spacing: 24) {
                    profileCard

                    settingsSection(title: L10n.tr("settings.security", locale: locale)) {
                        Button { showChangeMasterPassword = true } label: {
                            NavChevronRow(
                                icon: "ellipsis",
                                title: L10n.tr("settings.changeMasterPassword", locale: locale)
                            )
                        }
                        .buttonStyle(.plain)
                        Divider().background(Theme.stroke)
                        ToggleRow(
                            icon: "touchid",
                            iconColor: Theme.textPrimary,
                            title: L10n.tr("settings.biometricUnlock", locale: locale),
                            subtitle: biometricSubtitle,
                            isOn: $biometricUnlock
                        )
                        .onChange(of: biometricUnlock) { _, newValue in
                            VaultKeychainStore.biometricsEnabled = newValue
                            SharedVaultKeychain.biometricsEnabled = newValue
                        }
                        Divider().background(Theme.stroke)
                        Button {
                            openAutofillSettings()
                        } label: {
                            NavChevronRow(
                                icon: "text.cursor",
                                title: L10n.tr("settings.autofill", locale: locale),
                                subtitle: L10n.tr("settings.autofillSubtitle", locale: locale)
                            )
                        }
                        .buttonStyle(.plain)
                        Divider().background(Theme.stroke)
                        Button { showAutoLock = true } label: {
                            NavChevronRow(
                                icon: "clock",
                                title: L10n.tr("settings.autoLock", locale: locale),
                                trailingText: String(
                                    format: L10n.tr("settings.autoLockMinutesFmt", locale: locale),
                                    prefs.autoLockMinutes
                                )
                            )
                        }
                        .buttonStyle(.plain)
                    }

                    VStack(alignment: .leading, spacing: 8) {
                        HStack {
                            SectionLabel(text: L10n.tr("settings.linkedDevices", locale: locale))
                            Spacer()
                            Text(String(format: L10n.tr("settings.activeCount", locale: locale), linkedDeviceCount))
                                .font(.qasaLabel(12))
                                .foregroundColor(Theme.accent)
                        }
                        CardContainer {
                            Button { showLinkedDevices = true } label: {
                                NavChevronRow(
                                    icon: "iphone",
                                    title: UIDevice.current.name,
                                    subtitle: L10n.tr("settings.thisDevice", locale: locale),
                                    iconColor: Theme.accent
                                )
                            }
                            .buttonStyle(.plain)
                            Divider().background(Theme.stroke)
                            Button { showLANSync = true } label: {
                                NavChevronRow(
                                    icon: "wifi",
                                    title: L10n.tr("settings.syncNetwork", locale: locale),
                                    subtitle: session.isPremium
                                        ? L10n.tr("settings.premiumLanSync", locale: locale)
                                        : L10n.tr("settings.requiresPremium", locale: locale)
                                )
                            }
                            .buttonStyle(.plain)
                        }
                    }

                    settingsSection(title: L10n.tr("settings.appearance", locale: locale)) {
                        Menu {
                            ForEach(AppLanguage.allCases) { lang in
                                Button(lang.displayName) { prefs.language = lang }
                            }
                        } label: {
                            NavChevronRow(
                                icon: "globe",
                                title: L10n.tr("settings.language", locale: locale),
                                trailingText: prefs.language.displayName
                            )
                        }
                        Divider().background(Theme.stroke)
                        Menu {
                            ForEach(AppThemePreference.allCases) { theme in
                                Button(L10n.tr(theme.displayNameKey, locale: locale)) {
                                    prefs.theme = theme
                                }
                            }
                        } label: {
                            NavChevronRow(
                                icon: "moon.fill",
                                title: L10n.tr("settings.theme", locale: locale),
                                trailingText: L10n.tr(prefs.theme.displayNameKey, locale: locale)
                            )
                        }
                    }

                    settingsSection(title: L10n.tr("settings.dataAccount", locale: locale)) {
                        Button { showBackups = true } label: {
                            NavChevronRow(
                                icon: "externaldrive.badge.checkmark",
                                title: L10n.tr("backup.title", locale: locale),
                                subtitle: L10n.tr("backup.subtitle", locale: locale)
                            )
                        }
                        .buttonStyle(.plain)
                        Divider().background(Theme.stroke)
                        Button { showImport = true } label: {
                            NavChevronRow(
                                icon: "square.and.arrow.down",
                                title: L10n.tr("settings.importCsv", locale: locale)
                            )
                        }
                        .buttonStyle(.plain)
                        Divider().background(Theme.stroke)
                        if let exportDocument {
                            // Save/share the file rather than the clipboard: a
                            // plaintext vault backup on `UIPasteboard.general` is
                            // readable by every app and survives in clipboard history.
                            ShareLink(
                                item: exportDocument,
                                preview: SharePreview("qasa-vault-backup.csv")
                            ) {
                                NavChevronRow(
                                    icon: "square.and.arrow.up",
                                    title: L10n.tr("settings.exportCsv", locale: locale)
                                )
                            }
                            .buttonStyle(.plain)
                            Divider().background(Theme.stroke)
                        } else {
                            Button {
                                performExport()
                            } label: {
                                NavChevronRow(
                                    icon: "square.and.arrow.up",
                                    title: L10n.tr("settings.exportCsv", locale: locale)
                                )
                            }
                            .buttonStyle(.plain)
                            Divider().background(Theme.stroke)
                        }
                        Button { showClearDataAlert = true } label: {
                            NavChevronRow(
                                icon: "xmark.bin",
                                title: L10n.tr("settings.clearLocalData", locale: locale),
                                destructive: true
                            )
                        }
                        .buttonStyle(.plain)
                        Divider().background(Theme.stroke)
                        Button { showLogoutConfirm = true } label: {
                            NavChevronRow(
                                icon: "rectangle.portrait.and.arrow.right",
                                title: L10n.tr("settings.logout", locale: locale)
                            )
                        }
                        .buttonStyle(.plain)
                        if canDeleteAccount {
                            Divider().background(Theme.stroke)
                            Button { showDeleteAccountAlert = true } label: {
                                NavChevronRow(
                                    icon: "person.crop.circle.badge.xmark",
                                    title: L10n.tr("settings.deleteAccount", locale: locale),
                                    destructive: true
                                )
                            }
                            .buttonStyle(.plain)
                            .disabled(isDeletingAccount)
                        }
                    }

                    if let statusMessage {
                        Text(statusMessage)
                            .font(.qasa(12))
                            .foregroundColor(Theme.accent)
                    }
                }
                .padding(Theme.screenPadding)
                .padding(.bottom, 40)
            }
        }
        .background(Theme.background.ignoresSafeArea())
        .alert(L10n.tr("settings.clearAlertTitle", locale: locale), isPresented: $showClearDataAlert) {
            Button(L10n.tr("settings.cancel", locale: locale), role: .cancel) {}
            Button(L10n.tr("settings.clear", locale: locale), role: .destructive) {
                VaultAuthStore.resetVaultAuth()
            }
        } message: {
            Text(L10n.tr("settings.clearAlertMessage", locale: locale))
        }
        .alert(L10n.tr("settings.lockAlertTitle", locale: locale), isPresented: $showLogoutConfirm) {
            Button(L10n.tr("settings.cancel", locale: locale), role: .cancel) {}
            Button(L10n.tr("settings.logout", locale: locale), role: .destructive) {
                onLogout?()
            }
        } message: {
            Text(L10n.tr("settings.lockAlertMessage", locale: locale))
        }
        .alert(L10n.tr("settings.deleteAccountTitle", locale: locale), isPresented: $showDeleteAccountAlert) {
            Button(L10n.tr("common.cancel", locale: locale), role: .cancel) {}
            Button(L10n.tr("settings.deleteAccount", locale: locale), role: .destructive) {
                Task { await deleteAccount() }
            }
        } message: {
            Text(L10n.tr("settings.deleteAccountMessage", locale: locale))
        }
        .overlay {
            if isDeletingAccount {
                ProgressView()
                    .padding(24)
                    .background(Theme.cardBackground)
                    .cornerRadius(12)
            }
        }
        .sheet(isPresented: $showLinkedDevices) { LinkedDevicesView().appLocalization() }
        .sheet(isPresented: $showChangeMasterPassword) {
            ChangeMasterPasswordView()
                .appLocalization()
        }
        .sheet(isPresented: $showAutoLock) {
            AutoLockSettingsView()
                .appLocalization()
        }
        .sheet(isPresented: $showPremium) {
            Group {
                if session.isPremium {
                    ManageSubscriptionView(embedded: false)
                } else {
                    PremiumUpgradeView(embedded: false)
                }
            }
            .environment(session)
            .appLocalization()
        }
        .sheet(isPresented: $showBackups) {
            BackupsView()
                .appLocalization()
        }
        .sheet(isPresented: $showLANSync) {
            LANSyncView(onUpgrade: {
                showLANSync = false
                showPremium = true
            })
                .environment(session)
                .appLocalization()
        }
        .fileImporter(
            isPresented: $showImport,
            allowedContentTypes: [.commaSeparatedText, .plainText],
            allowsMultipleSelection: false
        ) { result in
            if case .success(let urls) = result, let url = urls.first {
                // The picker hands back a security-scoped URL; without this the read
                // fails once we leave the callback.
                let scoped = url.startAccessingSecurityScopedResource()
                defer { if scoped { url.stopAccessingSecurityScopedResource() } }
                do {
                    let data = try Data(contentsOf: url)
                    guard let text = String(data: data, encoding: .utf8) else {
                        statusMessage = String(
                            format: L10n.tr("settings.importFailed", locale: locale),
                            L10n.tr("error.decoding", locale: locale)
                        )
                        return
                    }
                    performImport(csv: text)
                } catch {
                    statusMessage = String(
                        format: L10n.tr("settings.importFailed", locale: locale),
                        error.localizedDescription
                    )
                }
            }
        }
        .task {
            canDeleteAccount = AccountDeletion.isAvailable
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
                    .stroke(Theme.accent, lineWidth: 2)
                    .frame(width: 56, height: 56)
                    .overlay(
                        Image(systemName: "person.fill")
                            .font(.title2)
                            .foregroundColor(Theme.textSecondary)
                    )
                VStack(alignment: .leading, spacing: 4) {
                    Text(session.displayName)
                        .font(.qasa(16, weight: .bold))
                        .foregroundColor(Theme.textPrimary)
                    Text(session.email)
                        .font(.qasa(14))
                        .foregroundColor(Theme.textSecondary)
                    HStack(spacing: 4) {
                        Image(systemName: session.isPremium ? "trophy.fill" : "person")
                            .font(.qasa(10))
                        Text(
                            session.isPremium
                                ? L10n.tr("settings.premiumAccount", locale: locale)
                                : L10n.tr("settings.freeAccount", locale: locale)
                        )
                        .font(.qasaLabel(12))
                    }
                    .foregroundColor(session.isPremium ? Theme.accent : Theme.textSecondary)
                    .padding(.horizontal, 8)
                    .padding(.vertical, 3)
                    .overlay(Capsule().stroke(session.isPremium ? Theme.accent : Theme.stroke, lineWidth: 1))
                }
                Spacer()
            }

            Button { showPremium = true } label: {
                Text(
                    session.isPremium
                        ? L10n.tr("settings.manageSubscription", locale: locale)
                        : L10n.tr("settings.upgradePremium", locale: locale)
                )
                .font(.qasa(14, weight: .medium))
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

    private func openAutofillSettings() {
        let candidates = [
            "App-prefs:PASSWORDS",
            "App-prefs:root=PASSWORDS",
            UIApplication.openSettingsURLString
        ]
        for raw in candidates {
            guard let url = URL(string: raw), UIApplication.shared.canOpenURL(url) else { continue }
            UIApplication.shared.open(url)
            return
        }
    }

    // MARK: - Account deletion

    /// `DELETE /v1/account`, then the local erase and the logout path, so the root shows
    /// Create vault. On failure nothing local changes and the status line says so.
    private func deleteAccount() async {
        guard let accountId = VaultKeychainStore.accountId else { return }
        isDeletingAccount = true
        statusMessage = nil
        defer { isDeletingAccount = false }
        do {
            try await AccountDeletion.deleteAccount(
                accountId: accountId,
                reauthorize: { await DeviceRegistrationService.shared.registerIfNeeded() },
                eraseLocal: { AccountDeletion.eraseThisDevice(context: modelContext) }
            )
            onLogout?()
        } catch {
            statusMessage = L10n.tr("settings.deleteAccountFailed", locale: locale)
        }
    }

    // MARK: - CSV backup

    private func performExport() {
        let (payloads, failures) = VaultRepository.shared.allPayloads()
        guard !payloads.isEmpty else {
            statusMessage = L10n.tr("settings.exportEmpty", locale: locale)
            return
        }
        // Deliberately not written to `UIPasteboard.general`: that would expose every
        // password in the vault to any app and to the system clipboard history.
        exportDocument = CSVDocument(text: VaultCSV.exportCSV(payloads: payloads))
        if failures > 0 {
            // Do not imply a complete backup when some rows could not be decrypted.
            statusMessage = String(
                format: L10n.tr("settings.exportPartial", locale: locale),
                payloads.count,
                failures
            )
        } else {
            statusMessage = String(
                format: L10n.tr("settings.exportPrepared", locale: locale),
                payloads.count
            )
        }
    }

    private func performImport(csv: String) {
        do {
            let result = try VaultCSV.parseImport(csv)
            let candidates = result.imported
            guard !candidates.isEmpty else {
                statusMessage = L10n.tr("settings.importNothing", locale: locale)
                return
            }
            // Skip anything already present so a repeated import is a no-op.
            let (existing, _) = VaultRepository.shared.allPayloads()
            let existingPrints = Set(existing.map(VaultCSV.fingerprint))

            var added = 0
            var skipped = 0
            var flagged = 0
            var firstError: Error?
            for candidate in candidates {
                // Compared in stored (normalized) form, so a repeated import stays a no-op.
                let payload = VaultValidation.normalizedOrSame(candidate)
                guard !existingPrints.contains(VaultCSV.fingerprint(payload)) else {
                    skipped += 1
                    continue
                }
                do {
                    // Stored even with field issues; counted so the user knows to check them.
                    if try VaultRepository.shared.importItem(payload).flagged { flagged += 1 }
                    added += 1
                } catch {
                    firstError = firstError ?? error
                }
            }

            Task { await VaultRepository.shared.warmCache() }

            if let firstError, added == 0 {
                statusMessage = String(
                    format: L10n.tr("settings.importFailed", locale: locale),
                    firstError.localizedDescription
                )
            } else if skipped > 0 {
                statusMessage = String(
                    format: L10n.tr("settings.importSkipped", locale: locale),
                    added,
                    skipped
                )
            } else {
                statusMessage = String(
                    format: L10n.tr("settings.parsedCsv", locale: locale),
                    added
                )
            }
            if flagged > 0, let message = statusMessage {
                statusMessage = message + "\n" + String(
                    format: L10n.tr("validation.importFlaggedFmt", locale: locale),
                    locale: locale,
                    flagged
                )
            }
        } catch {
            statusMessage = String(
                format: L10n.tr("settings.importFailed", locale: locale),
                error.localizedDescription
            )
        }
    }
}

#Preview {
    SettingsView(onOpenMenu: {})
        .environment(AppSession())
        .environment(AppPreferences.shared)
}
