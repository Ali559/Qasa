import SwiftUI

/// Unlocking the app (`design/README.md` rule 6d, option B "Prompt first, sheet on cancel").
/// A returning user never sees a login page: the screen is the splash (the background, the
/// app icon centred where the system launch screen puts it, the wordmark under it). With Face
/// ID / Touch ID on and the 14-day check not due, the system prompt starts by itself; when it
/// is cancelled or fails, or biometrics are off or due, the unlock sheet (rule 6b's layout)
/// rises over the splash and the icon moves up above it. The sheet can't be dismissed.
struct LoginView: View {
    /// The unlocked password, and whether Face ID / Touch ID released it (`true`) or it
    /// was typed (`false`): only a typed one restarts the master-password interval.
    /// Returns whether the vault opened; `false` brings the sheet back.
    var onUnlocked: (_ password: String, _ viaBiometrics: Bool) async -> Bool

    @Environment(AppPreferences.self) private var prefs
    @Environment(\.scenePhase) private var scenePhase
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var password = ""
    @State private var errorMessage: String?
    /// Wrong password: shown on the password row itself.
    @State private var passwordError: String?
    /// Biometrics are on for this vault (Keychain, so not observable: refreshed on appear
    /// and on every return to the foreground).
    @State private var biometricsEnabled = VaultKeychainStore.biometricsEnabled
    /// The 14-day master-password unlock is due: no biometric button, no auto prompt.
    @State private var masterPasswordDue = BiometricPolicy.isMasterPasswordDue
    /// One automatic Face ID / Touch ID prompt per appearance. Armed on appear and when the
    /// app goes to the background; consumed by the prompt. The system prompt itself only
    /// moves the scene to `.inactive`, never `.background`, so cancelling it can't re-arm
    /// it and loop.
    @State private var autoPromptArmed = true
    @State private var isAuthenticating = false
    /// The unlock sheet is up over the splash.
    @State private var showsSheet = false
    /// Unlocked: the sheet goes away while the root opens the vault.
    @State private var didUnlock = false
    /// The sheet's measured height, so the icon can sit centred in the space above it.
    @State private var sheetHeight: CGFloat = 360

    private var showsBiometricButton: Bool { biometricsEnabled && !masterPasswordDue }

    private var locale: Locale { prefs.locale }

    private var canUnlock: Bool {
        !password.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
    }

    /// The icon is "up" while the sheet is.
    private var iconUp: Bool { showsSheet && !didUnlock }

    var body: some View {
        // The whole screen, keyboard included, so the icon never moves with the safe area.
        GeometryReader { proxy in
            splash
                .position(x: proxy.size.width / 2, y: iconCentre(fullHeight: proxy.size.height))
        }
        .ignoresSafeArea()
        .background(Theme.background.ignoresSafeArea())
        .animation(reduceMotion ? nil : QasaTokens.Motion.standard, value: iconUp)
        .sheet(isPresented: Binding(get: { showsSheet && !didUnlock }, set: { _ in })) {
            SelfSizingSheet {
                UnlockSheetLayout(
                    title: L10n.tr("auth.unlock.sheetTitle", locale: locale),
                    password: $password,
                    error: passwordError,
                    unlockDisabled: !canUnlock,
                    showsBiometrics: showsBiometricButton,
                    biometricsDisabled: isAuthenticating,
                    onUnlock: attemptUnlock,
                    onBiometrics: { Task { await unlockWithBiometrics(automatic: false) } }
                ) {
                    sheetNotes
                }
                .onChange(of: password) { _, _ in passwordError = nil }
            }
            .onGeometryChange(for: CGFloat.self) { $0.size.height } action: { height in
                // The content's height plus the sheet's own bottom inset (home indicator).
                sheetHeight = height + 34
            }
            .interactiveDismissDisabled()
            .environment(prefs)
        }
        .onAppear {
            autoPromptArmed = true
            refreshBiometricState()
            autoPromptIfNeeded()
        }
        .onChange(of: scenePhase) { _, phase in
            switch phase {
            case .background:
                autoPromptArmed = true
            case .active:
                refreshBiometricState()
                autoPromptIfNeeded()
            default:
                break
            }
        }
    }

    /// The icon with the wordmark under it; the icon itself is what sits on the centre line.
    private var splash: some View {
        Image("SplashIcon")
            .resizable()
            .frame(width: 96, height: 96)
            .accessibilityHidden(true)
            .overlay(alignment: .top) {
                Text(L10n.tr("brand.name", locale: locale))
                    .font(.qasa(26, weight: .bold))
                    .foregroundColor(Theme.accent)
                    .fixedSize()
                    .offset(y: 96 + 14)
                    .accessibilityAddTraits(.isHeader)
            }
    }

    /// Screen centre at rest (where the launch screen draws the icon); with the sheet up,
    /// the icon and wordmark sit centred in the space above the sheet.
    private func iconCentre(fullHeight: CGFloat) -> CGFloat {
        guard iconUp else { return fullHeight / 2 }
        // Icon (96) + gap (14) + wordmark (~34): centre that block above the sheet.
        let block: CGFloat = 96 + 14 + 34
        let above = fullHeight - sheetHeight
        let centre = above / 2 - (block / 2 - 48)
        // Never under the status bar / Dynamic Island.
        return max(120, min(centre, fullHeight / 2))
    }

    @ViewBuilder
    private var sheetNotes: some View {
        if let errorMessage {
            Text(errorMessage)
                .font(.qasa(13, weight: .medium))
                .foregroundColor(Theme.danger)
                .fixedSize(horizontal: false, vertical: true)
        }
        if biometricsEnabled && masterPasswordDue {
            HStack(alignment: .top, spacing: 8) {
                QasaIconImage(.login, size: 13)
                    .foregroundColor(Theme.accent)
                    .accessibilityHidden(true)
                Text(
                    String(
                        format: L10n.tr("auth.unlock.masterPasswordDueFmt", locale: locale),
                        locale: locale,
                        BiometricPolicy.masterPasswordIntervalDays
                    )
                )
                .font(.qasa(13))
                .foregroundColor(Theme.textSecondary)
                .fixedSize(horizontal: false, vertical: true)
            }
            .padding(.top, 2)
            .accessibilityElement(children: .combine)
        }
    }

    private func refreshBiometricState() {
        biometricsEnabled = VaultKeychainStore.biometricsEnabled
        masterPasswordDue = BiometricPolicy.isMasterPasswordDue
    }

    private func presentSheet() {
        guard !didUnlock, !showsSheet else { return }
        showsSheet = true
    }

    /// Starts Face ID / Touch ID on its own when it's allowed, at most once per arming.
    /// When it isn't (biometrics off, or the 14-day check due) the sheet rises instead.
    private func autoPromptIfNeeded() {
        guard !didUnlock else { return }
        guard VaultSession.shared.canOfferBiometricUnlock,
              VaultKeychainStore.biometricPasswordStored else {
            presentSheet()
            return
        }
        guard autoPromptArmed, scenePhase == .active, !isAuthenticating else { return }
        autoPromptArmed = false
        Task {
            // Let the window settle first: at launch and right after returning to the
            // foreground, a Face ID request can be cancelled by the system before it shows.
            try? await Task.sleep(nanoseconds: 450_000_000)
            guard scenePhase == .active, !isAuthenticating else {
                autoPromptArmed = true
                return
            }
            await unlockWithBiometrics(automatic: true)
        }
    }

    private func attemptUnlock() {
        let trimmed = password.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return }

        // A vault written by a newer KDF version must not be reported as a wrong
        // password — that pushes the user toward "reset my vault", i.e. data loss.
        if let version = VaultSession.shared.unsupportedKdfVersion {
            errorMessage = String(
                format: L10n.tr("error.vaultNewerKdf", locale: locale),
                version
            )
            return
        }

        // Try the password exactly as typed first: vaults are created with the raw password,
        // so trimming first locked out anyone whose password starts or ends with a space.
        // The trimmed form still works for passwords set via Change Master Password (trimmed).
        for candidate in [password, trimmed] where !candidate.isEmpty {
            if VaultAuthStore.verifyMasterPassword(candidate) {
                errorMessage = nil
                passwordError = nil
                finishUnlock(candidate, viaBiometrics: false)
                return
            }
            if candidate == trimmed { break }
        }
        errorMessage = nil
        passwordError = L10n.tr("auth.unlock.wrongPassword", locale: locale)
    }

    /// Hands the password to the root, which opens the vault; the sheet goes away meanwhile.
    /// If the root refuses it (a stale biometric password, say), the sheet comes back.
    private func finishUnlock(_ password: String, viaBiometrics: Bool) {
        didUnlock = true
        Task {
            guard !(await onUnlocked(password, viaBiometrics)) else { return }
            didUnlock = false
            showsSheet = false
            if viaBiometrics {
                errorMessage = L10n.tr("auth.unlock.biometricsFailed", locale: locale)
            } else {
                passwordError = L10n.tr("auth.unlock.wrongPassword", locale: locale)
            }
            presentSheet()
        }
    }

    /// `automatic`: the prompt started on its own. A cancel or failure then stays quiet and
    /// just brings up the sheet; its biometric button and the password are both there to retry.
    private func unlockWithBiometrics(automatic: Bool) async {
        guard !isAuthenticating else { return }
        guard VaultKeychainStore.biometricsEnabled else {
            errorMessage = L10n.tr("auth.unlock.enableBiometricsHint", locale: locale)
            presentSheet()
            return
        }
        isAuthenticating = true
        defer { isAuthenticating = false }
        if let unlockedPassword = await VaultSession.shared.unlockWithBiometrics() {
            errorMessage = nil
            finishUnlock(unlockedPassword, viaBiometrics: true)
        } else {
            // The interval may have run out since the screen appeared.
            refreshBiometricState()
            if !automatic {
                errorMessage = L10n.tr("auth.unlock.biometricsFailed", locale: locale)
            }
            presentSheet()
        }
    }
}

#Preview {
    LoginView(onUnlocked: { _, _ in false })
        .environment(AppPreferences.shared)
}
