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
        UnlockSheetLayout(
            title: L10n.tr("vault.unlockProtectedNote", locale: locale),
            password: $password,
            error: wrong ? L10n.tr("auth.unlock.wrongPassword", locale: locale) : nil,
            unlockDisabled: password.isEmpty || checking,
            showsBiometrics: biometricsAvailable,
            onUnlock: checkPassword,
            onBiometrics: askBiometrics
        )
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

/// The unlock sheet's layout, shared by a protected item (rule 6b) and the app's own unlock
/// over the splash (rule 6d): the shared header, the `protected.title` message, the main
/// password as a one-row group (error on its row), an optional note under it, Unlock as the
/// primary button and `auth.unlock.biometrics` as the secondary one.
struct UnlockSheetLayout<Note: View>: View {
    @Environment(\.themePalette) private var theme
    let title: String
    @Binding var password: String
    let error: String?
    let unlockDisabled: Bool
    let showsBiometrics: Bool
    var biometricsDisabled: Bool = false
    let onUnlock: () -> Void
    let onBiometrics: () -> Void
    @ViewBuilder var note: Note

    private var locale: Locale { AppLanguage.current.locale }

    var body: some View {
        VStack(spacing: 0) {
            OwnSheetTitle(title: title)
            VStack(spacing: QasaTokens.Space.sheetActionsGap) {
                Text(L10n.tr("protected.title", locale: locale))
                    .font(.qasa(15))
                    .foregroundColor(theme.textSecondary)
                    .multilineTextAlignment(.center)
                    .fixedSize(horizontal: false, vertical: true)
                    .frame(maxWidth: .infinity)
                VStack(alignment: .leading, spacing: 8) {
                    FieldGroup {
                        PasswordFieldRow(
                            label: L10n.tr("auth.unlock.masterPassword", locale: locale),
                            text: $password,
                            hint: L10n.tr("auth.unlock.placeholder", locale: locale),
                            error: error,
                            onSubmit: onUnlock
                        )
                    }
                    note
                }
                VStack(spacing: QasaTokens.Space.sheetButtonGap) {
                    Button(L10n.tr("auth.unlock.button", locale: locale), action: onUnlock)
                        .buttonStyle(.qasaPrimary)
                        .disabled(unlockDisabled)
                    if showsBiometrics {
                        Button(L10n.tr("auth.unlock.biometrics", locale: locale), action: onBiometrics)
                            .buttonStyle(.qasaSecondary)
                            .disabled(biometricsDisabled)
                    }
                }
            }
        }
    }
}

extension UnlockSheetLayout where Note == EmptyView {
    init(
        title: String,
        password: Binding<String>,
        error: String?,
        unlockDisabled: Bool,
        showsBiometrics: Bool,
        onUnlock: @escaping () -> Void,
        onBiometrics: @escaping () -> Void
    ) {
        self.init(
            title: title, password: password, error: error, unlockDisabled: unlockDisabled,
            showsBiometrics: showsBiometrics, onUnlock: onUnlock, onBiometrics: onBiometrics
        ) { EmptyView() }
    }
}
