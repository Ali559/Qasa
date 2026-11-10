import SwiftUI
import SwiftData

@main
struct qasaApp: App {
    @UIApplicationDelegateAdaptor(QasaAppDelegate.self) private var appDelegate
    private let container: ModelContainer
    /// Non-nil when the on-disk vault store could not be opened. The store file is
    /// left untouched so the data stays recoverable — see `VaultStoreErrorView`.
    private let storeFailure: VaultStoreFailure?
    @State private var prefs = AppPreferences.shared

    init() {
        if LocalVaultReset.shouldWipeOnLaunch {
            LocalVaultReset.wipeStoreFiles()
            LocalVaultReset.wipeSecretsAndWelcome()
        }
        let schema = Schema(VaultSchema.models)
        let config = ModelConfiguration(isStoredInMemoryOnly: false)
        do {
            container = try ModelContainer(for: schema, configurations: [config])
            storeFailure = nil
        } catch {
            // Never delete the store to recover. Store creation can fail transiently
            // (SQLite lock, out-of-space, WAL contention) and destroying the file on
            // those turns a recoverable hiccup into permanent, unrecoverable data loss.
            // Fall back to an in-memory container so the UI can explain the problem.
            let failure = VaultStoreFailure(
                underlying: String(describing: error),
                storeURL: config.url
            )
            storeFailure = failure
            container = (try? ModelContainer(
                for: schema,
                configurations: [ModelConfiguration(isStoredInMemoryOnly: true)]
            )) ?? Self.inMemoryContainer(for: schema)
        }
    }

    /// Last-resort container so the process can still render the error screen.
    private static func inMemoryContainer(for schema: Schema) -> ModelContainer {
        // `ModelConfiguration(isStoredInMemoryOnly: true)` cannot realistically fail.
        // If it somehow does, crashing is better than running against a real store we
        // could not open.
        let config = ModelConfiguration(isStoredInMemoryOnly: true)
        do {
            return try ModelContainer(for: schema, configurations: [config])
        } catch {
            fatalError("Failed to create in-memory ModelContainer: \(error)")
        }
    }

    var body: some Scene {
        WindowGroup {
            if let storeFailure {
                VaultStoreErrorView(failure: storeFailure)
                    .preferredColorScheme(colorScheme)
                    .environment(\.locale, prefs.locale)
                    .environment(\.layoutDirection, prefs.layoutDirection)
                    .environment(prefs)
            } else {
                RootView()
                    .preferredColorScheme(colorScheme)
                    .environment(\.locale, prefs.locale)
                    .environment(\.layoutDirection, prefs.layoutDirection)
                    .environment(prefs)
                    .modelContainer(container)
            }
        }
    }

    private var colorScheme: ColorScheme? {
        switch prefs.theme {
        case .system: return nil
        case .light: return .light
        case .dark: return .dark
        }
    }

    struct RootView: View {
        @Environment(\.modelContext) private var modelContext
        @Environment(\.colorScheme) private var systemScheme
        @Environment(\.scenePhase) private var scenePhase
        @Environment(AppPreferences.self) private var prefs
        @State private var unlocked = false
        @State private var showPairing = false
        @State private var showRestoreBackup = false
        @State private var session = AppSession()
        @State private var isBootstrapping = false
        @State private var createError: String? = nil
        @State private var isShielded = true
        /// `VaultAuthStore.vaultExists` lives in the Keychain, which SwiftUI can't observe.
        /// Bumped when pairing creates a vault, so the root re-reads it and shows Login.
        @State private var vaultRevision = 0
        /// A confirmed device replacement failed (`backup.replace.failed`).
        @State private var replaceFailed = false
        /// "<old device> was unlinked…", shown for a few seconds after a replacement.
        @State private var replacedNotice: String?
        private var registration = DeviceRegistrationService.shared

        private var showLogin: Bool {
            _ = vaultRevision
            return VaultAuthStore.vaultExists
        }

        private var showWelcome: Bool {
            !VaultAuthStore.vaultExists && !prefs.hasCompletedWelcome
        }

        private var effectiveScheme: ColorScheme {
            switch prefs.theme {
            case .system: return systemScheme
            case .light: return .light
            case .dark: return .dark
            }
        }

        var body: some View {
            Group {
                if unlocked {
                    AppShellView(onLogout: handleLogout)
                        .environment(session)
                        .simultaneousGesture(
                            DragGesture(minimumDistance: 0)
                                .onChanged { _ in AutoLockMonitor.shared.noteActivity() }
                        )
                } else if showLogin {
                    LoginView(
                        onUnlocked: { password in
                            Task { await handleUnlock(password: password) }
                        },
                        onCreateVault: {}
                    )
                } else if showWelcome {
                    WelcomePreferencesView(onContinue: {})
                } else {
                    CreateVaultView(
                        onVaultCreated: { password, biometrics in
                            Task { await handleCreate(password: password, biometrics: biometrics) }
                        },
                        onPairExisting: { showPairing = true },
                        onRestoreBackup: { showRestoreBackup = true },
                        onLogin: nil
                    )
                }
            }
            .environment(\.themePalette, ThemePalette.resolved(for: effectiveScheme))
            .environment(\.locale, prefs.locale)
            .environment(\.layoutDirection, prefs.layoutDirection)
            .font(.qasa(16))
            // Cover the vault before the system takes an app-switcher/Control Centre
            // snapshot. Without this, whatever is on screen lands in that snapshot.
            .privacyShield(!isShielded)
            // Refresh localized strings without resetting unlock state (state lives on RootView).
            .id(prefs.language.rawValue)
            .fullScreenCover(isPresented: $showPairing) {
                PairExistingVaultView(
                    // Wi‑Fi join: the vault now exists here, so the root shows Login and
                    // the same main password opens it. Account-ID paste: the root still
                    // shows Create vault, under the linked account.
                    onPaired: {
                        showPairing = false
                        vaultRevision += 1
                    },
                    onCancel: { showPairing = false }
                )
                .modelContext(modelContext)
                .environment(prefs)
                // A presented cover doesn't inherit the palette set on the root content.
                .environment(\.themePalette, ThemePalette.resolved(for: effectiveScheme))
                .appLocalization()
            }
            .fullScreenCover(isPresented: $showRestoreBackup) {
                BackupRestoreView(
                    mode: .setup,
                    // The backup's vault and account are now this device's: unlock with the
                    // password just typed and continue as after a Wi‑Fi sync adoption
                    // (device registration under the adopted account, warm cache…).
                    onAdopted: { password in
                        showRestoreBackup = false
                        vaultRevision += 1
                        Task { await handleUnlock(password: password) }
                    }
                )
                .modelContext(modelContext)
                .environment(prefs)
                .environment(\.themePalette, ThemePalette.resolved(for: effectiveScheme))
                .appLocalization()
            }
            .overlay {
                if registration.isReplacing {
                    ProgressView()
                        .padding(24)
                        .background(Theme.cardBackground)
                        .cornerRadius(12)
                } else if isBootstrapping {
                    ProgressView(L10n.tr("auth.securingVault", locale: prefs.locale))
                        .padding(24)
                        .background(Theme.cardBackground)
                        .cornerRadius(12)
                }
            }
            .safeAreaInset(edge: .top, spacing: 0) {
                if let host = session.configurationError {
                    // A Release build with no https QASA_API_URL reaches nothing at all.
                    // Say so instead of letting every screen look broken.
                    HStack(spacing: 8) {
                        Image(systemName: "wifi.exclamationmark")
                        Text(
                            String(
                                format: L10n.tr("error.apiHostMisconfigured", locale: prefs.locale),
                                host
                            )
                        )
                        .fixedSize(horizontal: false, vertical: true)
                        Spacer(minLength: 0)
                    }
                    .font(.qasa(12, weight: .medium))
                    .foregroundColor(Theme.danger)
                    .padding(.horizontal, 14)
                    .padding(.vertical, 10)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(Theme.cardBackground)
                }
            }
            .overlay(alignment: .top) {
                if let replacedNotice {
                    Text(replacedNotice)
                        .font(.qasa(13, weight: .semibold))
                        .foregroundColor(Theme.textPrimary)
                        .fixedSize(horizontal: false, vertical: true)
                        .padding(.horizontal, 14)
                        .padding(.vertical, 10)
                        .background(Theme.cardBackground)
                        .cornerRadius(12)
                        .padding(.horizontal, Theme.screenPadding)
                        .padding(.top, 8)
                        .transition(.move(edge: .top).combined(with: .opacity))
                }
            }
            // Right after a setup restore hit the device limit: offer to take the place of
            // the device the backup came from. "Not now" leaves the same button on Linked
            // Devices.
            .alert(
                L10n.tr("backup.replace.title", locale: prefs.locale),
                isPresented: Binding(
                    get: { unlocked && registration.replacePrompt != nil },
                    set: { if !$0 { registration.replacePrompt = nil } }
                ),
                presenting: registration.replacePrompt
            ) { _ in
                Button(L10n.tr("backup.replace.button", locale: prefs.locale)) {
                    Task { await replaceOldDevice() }
                }
                Button(L10n.tr("backup.reminder.dismiss", locale: prefs.locale), role: .cancel) {}
            } message: { device in
                Text(String(format: L10n.tr("backup.replace.bodyFmt", locale: prefs.locale), device.device_name))
            }
            .alert(
                L10n.tr("backup.replace.title", locale: prefs.locale),
                isPresented: $replaceFailed
            ) {
                Button(L10n.tr("common.ok", locale: prefs.locale), role: .cancel) {}
            } message: {
                Text(L10n.tr("backup.replace.failed", locale: prefs.locale))
            }
            .alert(
                L10n.tr("error.vaultCreateTitle", locale: prefs.locale),
                isPresented: Binding(
                    get: { createError != nil },
                    set: { if !$0 { createError = nil } }
                ),
                presenting: createError
            ) { _ in
                Button(L10n.tr("common.ok", locale: prefs.locale), role: .cancel) { createError = nil }
            } message: { detail in
                Text(detail)
            }
            .task {
                // `onChange` never fires for the initial value, so an app that launches
                // straight into `.active` would keep the shield stuck on.
                AutoLockMonitor.shared.handleScenePhase(scenePhase)
                isShielded = AutoLockMonitor.shared.isObscured
            }
            .onChange(of: scenePhase) { _, phase in
                AutoLockMonitor.shared.handleScenePhase(phase)
                isShielded = AutoLockMonitor.shared.isObscured
                if phase == .active {
                    // Bell badge on foreground (silent without a license token).
                    Task { await NotificationService.shared.refreshUnreadCount() }
                    // Premium may have ended (or been bought elsewhere) while away.
                    if unlocked {
                        session.handleForeground()
                        BackupService.shared.runIfDue(container: modelContext.container)
                    }
                }
            }
            .onReceive(NotificationCenter.default.publisher(for: AutoLockMonitor.obscureNotification)) { _ in
                isShielded = true
            }
            .onReceive(NotificationCenter.default.publisher(for: AutoLockMonitor.revealNotification)) { _ in
                isShielded = false
            }
            .onReceive(NotificationCenter.default.publisher(for: AutoLockMonitor.forceLockNotification)) { _ in
                handleLogout()
            }
        }

        @MainActor
        private func handleCreate(password: String, biometrics: Bool) async {
            isBootstrapping = true
            defer { isBootstrapping = false }
            do {
                try VaultAuthStore.createVault(masterPassword: password, biometricsEnabled: biometrics)
                await AppBootstrap.onVaultUnlocked(context: modelContext, session: session)
                unlocked = true
                AutoLockMonitor.shared.start(onLock: handleLogout)
                BackupService.shared.runIfDue(container: modelContext.container)
            } catch {
                // Silently swallowing this left the spinner stopping with no message
                // and no way to tell the user why creation failed.
                createError = String(describing: error)
            }
        }

        @MainActor
        private func handleUnlock(password: String) async {
            guard VaultAuthStore.verifyMasterPassword(password) else { return }
            isBootstrapping = true
            defer { isBootstrapping = false }
            await AppBootstrap.onVaultUnlocked(context: modelContext, session: session)
            unlocked = true
            AutoLockMonitor.shared.start(onLock: handleLogout)
            BackupService.shared.runIfDue(container: modelContext.container)
        }

        @MainActor
        private func replaceOldDevice() async {
            switch await registration.replaceOldDevice() {
            case .replaced(let oldName):
                await AppBootstrap.onDeviceReplaced(session: session)
                if let oldName {
                    showReplacedNotice(String(
                        format: L10n.tr("backup.replace.doneFmt", locale: prefs.locale),
                        oldName
                    ))
                }
            case .failed:
                replaceFailed = true
            }
        }

        @MainActor
        private func showReplacedNotice(_ text: String) {
            withAnimation { replacedNotice = text }
            Task {
                try? await Task.sleep(for: .seconds(4))
                withAnimation { replacedNotice = nil }
            }
        }

        @MainActor
        private func handleLogout() {
            AutoLockMonitor.shared.stop()
            AppBootstrap.onLogout()
            session.syncFromLicense()
            unlocked = false
        }
    }
}
