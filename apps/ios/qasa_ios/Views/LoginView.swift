import SwiftUI

struct LoginView: View {
    var onUnlocked: (String) -> Void
    var onCreateVault: () -> Void

    @Environment(AppPreferences.self) private var prefs
    @State private var password = ""
    @State private var passwordVisible = false
    @State private var errorMessage: String?
    @State private var biometricsAvailable = true

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
                            .font(.system(size: 28, weight: .heavy))
                            .foregroundColor(Theme.textPrimary)
                        Text(L10n.tr("auth.unlock.subtitle", locale: locale))
                            .font(.system(size: 14))
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
                                Button {
                                    passwordVisible.toggle()
                                } label: {
                                    Image(systemName: passwordVisible ? "eye.slash" : "eye")
                                        .foregroundColor(Theme.textSecondary)
                                }
                            )
                        )

                        if let errorMessage {
                            Text(errorMessage)
                                .font(.system(size: 13, weight: .medium))
                                .foregroundColor(Theme.danger)
                        }
                    }

                    if biometricsAvailable {
                        Button {
                            unlockWithBiometrics()
                        } label: {
                            HStack(spacing: 12) {
                                Image(systemName: "touchid")
                                    .font(.system(size: 22))
                                    .foregroundColor(Theme.mint)
                                    .frame(width: 40, height: 40)
                                    .background(Theme.cardBackgroundLight)
                                    .clipShape(Circle())

                                VStack(alignment: .leading, spacing: 2) {
                                    Text(L10n.tr("auth.unlock.biometrics", locale: locale))
                                        .font(.system(size: 16, weight: .semibold))
                                        .foregroundColor(Theme.textPrimary)
                                    Text(L10n.tr("auth.unlock.biometricsHint", locale: locale))
                                        .font(.kasoMono(12))
                                        .tracking(0.6)
                                        .foregroundColor(Theme.textSecondary)
                                }
                                Spacer()
                                Image(systemName: "chevron.forward")
                                    .font(.system(size: 13, weight: .semibold))
                                    .foregroundColor(Theme.textTertiary)
                            }
                            .padding(16)
                            .background(Theme.cardBackground)
                            .cornerRadius(Theme.cornerRadius)
                            .overlay(
                                RoundedRectangle(cornerRadius: Theme.cornerRadius)
                                    .stroke(Theme.stroke, lineWidth: 1)
                            )
                        }
                        .buttonStyle(.plain)
                        .padding(.top, 4)
                    }

                    CardContainer(background: Theme.cardBackgroundLight) {
                        VStack(alignment: .leading, spacing: 8) {
                            HStack(spacing: 8) {
                                Image(systemName: "shield.fill")
                                    .foregroundColor(Theme.mint)
                                Text(L10n.tr("auth.unlock.localOnly", locale: locale))
                                    .font(.system(size: 16, weight: .semibold))
                                    .foregroundColor(Theme.textPrimary)
                            }
                            Text(L10n.tr("auth.unlock.localOnlyBody", locale: locale))
                                .font(.system(size: 14))
                                .foregroundColor(Theme.textSecondary)
                                .fixedSize(horizontal: false, vertical: true)
                        }
                    }
                    .padding(.top, 8)
                    .padding(.bottom, 100)
                }
                .padding(.horizontal, Theme.screenPadding)
                .padding(.top, 24)
            }

            VStack(spacing: 0) {
                Divider().background(Theme.stroke)
                PrimaryButton(title: L10n.tr("auth.unlock.button", locale: locale), icon: "lock.open.fill") {
                    attemptUnlock()
                }
                .opacity(canUnlock ? 1 : 0.5)
                .disabled(!canUnlock)
                .padding(.horizontal, Theme.screenPadding)
                .padding(.top, 17)
                .padding(.bottom, 32)
            }
            .background(Theme.chrome.opacity(0.92))
        }
        .background(Theme.background.ignoresSafeArea())
        .environment(prefs)
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

        if VaultAuthStore.verifyMasterPassword(trimmed) {
            errorMessage = nil
            onUnlocked(trimmed)
        } else {
            errorMessage = L10n.tr("auth.unlock.wrongPassword", locale: locale)
        }
    }

    private func unlockWithBiometrics() {
        guard VaultKeychainStore.biometricsEnabled else {
            errorMessage = L10n.tr("auth.unlock.enableBiometricsHint", locale: locale)
            return
        }
        if let unlockedPassword = VaultSession.shared.unlockWithBiometrics() {
            errorMessage = nil
            onUnlocked(unlockedPassword)
        } else {
            errorMessage = L10n.tr("auth.unlock.biometricsFailed", locale: locale)
        }
    }
}

#Preview {
    LoginView(onUnlocked: { _ in }, onCreateVault: {})
        .environment(AppPreferences.shared)
}
