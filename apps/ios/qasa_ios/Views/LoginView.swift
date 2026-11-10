import SwiftUI

struct LoginView: View {
    /// The unlocked password, and whether Face ID / Touch ID released it (`true`) or it
    /// was typed (`false`): only a typed one restarts the master-password interval.
    var onUnlocked: (_ password: String, _ viaBiometrics: Bool) -> Void
    var onCreateVault: () -> Void

    @Environment(AppPreferences.self) private var prefs
    @Environment(\.scenePhase) private var scenePhase
    @State private var password = ""
    @State private var passwordVisible = false
    @State private var errorMessage: String?
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

    private var showsBiometricButton: Bool { biometricsEnabled && !masterPasswordDue }

    private var locale: Locale { prefs.locale }

    private var canUnlock: Bool {
        !password.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
    }

    var body: some View {
        VStack(spacing: 0) {
            KasoBrandHeader(
                trailing: AnyView(StatusPill(text: L10n.tr("brand.localOffline", locale: locale)))
            )

            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    VStack(alignment: .leading, spacing: 8) {
                        Text(L10n.tr("auth.unlock.title", locale: locale))
                            .font(.qasa(28, weight: .bold))
                            .foregroundColor(Theme.textPrimary)
                        Text(L10n.tr("auth.unlock.subtitle", locale: locale))
                            .font(.qasa(14))
                            .foregroundColor(Theme.textSecondary)
                            .fixedSize(horizontal: false, vertical: true)
                    }

                    VStack(alignment: .leading, spacing: 8) {
                        SectionLabel(text: L10n.tr("auth.unlock.masterPassword", locale: locale))
                        ThemedTextField(
                            icon: "lock.fill",
                            placeholder: L10n.tr("auth.unlock.placeholder", locale: locale),
                            text: $password,
                            isSecure: !passwordVisible,
                            trailing: AnyView(
                                FieldIconButton(
                                    systemImage: passwordVisible ? "eye.slash" : "eye",
                                    accessibilityLabel: passwordVisible
                                        ? L10n.tr("vault.hidePassword", locale: locale)
                                        : L10n.tr("vault.showPassword", locale: locale)
                                ) {
                                    passwordVisible.toggle()
                                }
                            )
                        )

                        if let errorMessage {
                            Text(errorMessage)
                                .font(.qasa(13, weight: .medium))
                                .foregroundColor(Theme.danger)
                        }

                        if biometricsEnabled && masterPasswordDue {
                            HStack(alignment: .top, spacing: 8) {
                                Image(systemName: "key.fill")
                                    .font(.system(size: 13, weight: .semibold))
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

                    // Only offered when biometrics were enabled for this vault (Android parity);
                    // tapping a row that can only answer "enable biometrics first" was a dead end.
                    // Hidden while the 14-day master-password unlock is due (`BiometricPolicy`).
                    if showsBiometricButton {
                        Button {
                            Task { await unlockWithBiometrics(automatic: false) }
                        } label: {
                            SettingsRow(
                                icon: "touchid",
                                title: L10n.tr("auth.unlock.biometrics", locale: locale),
                                subtitle: L10n.tr("auth.unlock.biometricsHint", locale: locale)
                            ) {
                                RowChevron()
                            }
                            .padding(.horizontal, 14)
                            .background(Theme.cardBackground)
                            .cornerRadius(Theme.cornerRadius)
                            .overlay(
                                RoundedRectangle(cornerRadius: Theme.cornerRadius)
                                    .stroke(Theme.stroke, lineWidth: 1)
                            )
                        }
                        .buttonStyle(PressableStyle())
                        .disabled(isAuthenticating)
                        .padding(.top, 4)
                    }

                    CardContainer(background: Theme.cardBackgroundLight) {
                        VStack(alignment: .leading, spacing: 8) {
                            HStack(spacing: 8) {
                                Image(systemName: "shield.fill")
                                    .foregroundColor(Theme.accent)
                                Text(L10n.tr("auth.unlock.localOnly", locale: locale))
                                    .font(.qasa(16, weight: .semibold))
                                    .foregroundColor(Theme.textPrimary)
                            }
                            Text(L10n.tr("auth.unlock.localOnlyBody", locale: locale))
                                .font(.qasa(14))
                                .foregroundColor(Theme.textSecondary)
                                .fixedSize(horizontal: false, vertical: true)
                        }
                    }
                    .padding(.top, 8)
                    .padding(.bottom, 24)
                }
                .padding(.horizontal, Theme.screenPadding)
                .padding(.top, 24)
            }
            .scrollDismissesKeyboard(.interactively)
        }
        .background(Theme.background.ignoresSafeArea())
        .bottomActionBar {
            PrimaryButton(title: L10n.tr("auth.unlock.button", locale: locale), icon: "lock.open.fill") {
                attemptUnlock()
            }
            .disabled(!canUnlock)
        }
        .environment(prefs)
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

    private func refreshBiometricState() {
        biometricsEnabled = VaultKeychainStore.biometricsEnabled
        masterPasswordDue = BiometricPolicy.isMasterPasswordDue
    }

    /// Starts Face ID / Touch ID on its own when it's allowed, at most once per arming.
    private func autoPromptIfNeeded() {
        guard autoPromptArmed, scenePhase == .active, !isAuthenticating else { return }
        guard VaultSession.shared.canOfferBiometricUnlock,
              VaultKeychainStore.biometricPasswordStored else { return }
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
                onUnlocked(candidate, false)
                return
            }
            if candidate == trimmed { break }
        }
        errorMessage = L10n.tr("auth.unlock.wrongPassword", locale: locale)
    }

    /// `automatic`: the prompt started on its own. A cancel or failure then stays quiet;
    /// the biometric button and the password field are both still there to retry.
    private func unlockWithBiometrics(automatic: Bool) async {
        guard !isAuthenticating else { return }
        guard VaultKeychainStore.biometricsEnabled else {
            errorMessage = L10n.tr("auth.unlock.enableBiometricsHint", locale: locale)
            return
        }
        isAuthenticating = true
        defer { isAuthenticating = false }
        if let unlockedPassword = await VaultSession.shared.unlockWithBiometrics() {
            errorMessage = nil
            onUnlocked(unlockedPassword, true)
        } else {
            // The interval may have run out since the screen appeared.
            refreshBiometricState()
            if !automatic {
                errorMessage = L10n.tr("auth.unlock.biometricsFailed", locale: locale)
            }
        }
    }
}

#Preview {
    LoginView(onUnlocked: { _, _ in }, onCreateVault: {})
        .environment(AppPreferences.shared)
}
