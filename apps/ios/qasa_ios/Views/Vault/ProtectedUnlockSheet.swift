import LocalAuthentication
import SwiftUI

/// The plain cover a protected item shows until it is unlocked (`design/README.md` rule 6b):
/// the background, the item's lock badge, nothing of the item.
struct ProtectedItemCover: View {
    var body: some View {
        VStack {
            Spacer()
            IconBadge(icon: .lock)
                .scaleEffect(1.2)
            Spacer()
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Theme.background.ignoresSafeArea())
        .accessibilityElement()
        .accessibilityLabel(L10n.tr("vault.unlockProtectedNote"))
    }
}

/// The unlock sheet of a protected item: the shared header, the `protected.title` message,
/// the main password as a one-row group, Unlock at the bottom. With fingerprint / face on,
/// the biometric prompt starts as the sheet opens and a secondary button starts it again.
/// No Cancel: dragging it down closes it, and the item goes back to the list.
struct ProtectedUnlockSheet: View {
    @Environment(\.themePalette) private var theme
    let onUnlocked: () -> Void

    @State private var password = ""
    @State private var wrong = false
    @State private var checking = false
    @State private var biometricsTried = false

    private var locale: Locale { AppLanguage.current.locale }
    private var biometricsAvailable: Bool {
        VaultKeychainStore.biometricsEnabled
            && LAContext().canEvaluatePolicy(.deviceOwnerAuthenticationWithBiometrics, error: nil)
    }

    var body: some View {
        VStack(spacing: 0) {
            OwnSheetTitle(title: L10n.tr("vault.unlockProtectedNote", locale: locale))
            VStack(spacing: QasaTokens.Space.sheetActionsGap) {
                Text(L10n.tr("protected.title", locale: locale))
                    .font(.qasa(15))
                    .foregroundColor(theme.textSecondary)
                    .multilineTextAlignment(.center)
                    .fixedSize(horizontal: false, vertical: true)
                    .frame(maxWidth: .infinity)
                FieldGroup {
                    PasswordFieldRow(
                        label: L10n.tr("auth.unlock.masterPassword", locale: locale),
                        text: $password,
                        hint: L10n.tr("auth.unlock.placeholder", locale: locale),
                        error: wrong ? L10n.tr("auth.unlock.wrongPassword", locale: locale) : nil,
                        onSubmit: checkPassword
                    )
                }
                VStack(spacing: QasaTokens.Space.sheetButtonGap) {
                    Button(L10n.tr("auth.unlock.button", locale: locale), action: checkPassword)
                        .buttonStyle(.qasaPrimary)
                        .disabled(password.isEmpty || checking)
                    if biometricsAvailable {
                        Button(L10n.tr("auth.unlock.biometrics", locale: locale), action: askBiometrics)
                            .buttonStyle(.qasaSecondary)
                    }
                }
            }
        }
        .onChange(of: password) { _, _ in wrong = false }
        .onAppear {
            guard !biometricsTried, biometricsAvailable else { return }
            biometricsTried = true
            askBiometrics()
        }
    }

    private func askBiometrics() {
        let context = LAContext()
        context.localizedFallbackTitle = ""
        context.evaluatePolicy(
            .deviceOwnerAuthenticationWithBiometrics,
            localizedReason: L10n.tr("vault.unlockProtectedNote", locale: locale)
        ) { ok, _ in
            DispatchQueue.main.async { if ok { onUnlocked() } }
        }
    }

    private func checkPassword() {
        guard !password.isEmpty, !checking else { return }
        checking = true
        let typed = password
        Task.detached(priority: .userInitiated) {
            let ok = VaultSession.shared.verify(password: typed)
            await MainActor.run {
                checking = false
                if ok { onUnlocked() } else { wrong = true }
            }
        }
    }
}
