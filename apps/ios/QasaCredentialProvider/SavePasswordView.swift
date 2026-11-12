import SwiftUI

/// iOS 26.2 "Save to Qasa" (design rule 6c): the site, the username and the masked password,
/// then an unlock (Face ID starts on its own when it is on; the main password always works).
/// The extension cannot write the vault, so Save seals the login into `PendingSaveInbox`
/// with the vault key and the app imports it on its next unlock. Apple requires a Cancel
/// here (`prepareInterface(for: ASSavePasswordRequest)`), unlike the app's own sheets.
struct SavePasswordView: View {
    let site: String
    let username: String
    let onCancel: () -> Void
    let onSave: (_ session: AutofillUnlock.Session) -> Void

    @State private var password = ""
    @State private var unlockError: String?
    @State private var isUnlocking = false
    @State private var showsBiometricButton = false

    var body: some View {
        NavigationStack {
            VStack(spacing: 16) {
                VStack(spacing: 4) {
                    Text(site)
                        .font(QasaFontFace.font(size: 17, weight: .semibold))
                    if !username.isEmpty {
                        Text(username)
                            .font(QasaFontFace.font(size: 14))
                            .foregroundStyle(.secondary)
                    }
                }
                Text(AutofillL10n.tr("autofill.saveBody", "Unlock Qasa to save this login. It shows up in the app the next time you open it."))
                    .font(QasaFontFace.font(size: 14))
                    .foregroundStyle(.secondary)
                    .multilineTextAlignment(.center)
                    .fixedSize(horizontal: false, vertical: true)
                    .padding(.horizontal)
                SecureField(AutofillL10n.tr("auth.unlock.placeholder", "Main password"), text: $password)
                    .qasaExtensionField()
                    .submitLabel(.go)
                    .onSubmit { saveWithPassword() }
                    .padding(.horizontal)
                if let unlockError {
                    Text(unlockError)
                        .font(QasaFontFace.font(size: 13))
                        .foregroundStyle(QasaSharedPalette.danger)
                }
                Button(action: saveWithPassword) {
                    Text(AutofillL10n.tr("autofill.save", "Save"))
                }
                .buttonStyle(QasaExtensionPrimaryButtonStyle())
                .disabled(password.isEmpty || isUnlocking)
                .padding(.horizontal)
                if showsBiometricButton {
                    Button {
                        saveWithBiometrics(automatic: false)
                    } label: {
                        Label { Text(AutofillL10n.tr("autofill.useBiometrics", "Use Face ID / Touch ID")) } icon: { QasaIconImage(.face, size: 17) }
                    }
                }
                Spacer()
            }
            .padding(.top, 24)
            .navigationTitle(AutofillL10n.tr("autofill.saveTitle", "Save to Qasa"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button(AutofillL10n.tr("common.cancel", "Cancel"), action: onCancel)
                }
            }
        }
        .onAppear {
            showsBiometricButton = AutofillBiometricGate.showsBiometricButton
            if AutofillUnlock.canOfferBiometricUnlock { saveWithBiometrics(automatic: true) }
        }
    }

    private func saveWithBiometrics(automatic: Bool) {
        guard !isUnlocking else { return }
        isUnlocking = true
        defer { isUnlocking = false }
        guard let session = AutofillUnlock.unlockWithBiometrics() else {
            showsBiometricButton = AutofillBiometricGate.showsBiometricButton
            if !automatic { unlockError = AutofillL10n.tr("autofill.biometricFailed", "Biometric unlock failed.") }
            return
        }
        onSave(session)
    }

    private func saveWithPassword() {
        guard !password.isEmpty, !isUnlocking else { return }
        isUnlocking = true
        defer { isUnlocking = false }
        guard let session = AutofillUnlock.unlock(password: password) else {
            unlockError = AutofillL10n.tr("autofill.wrongPassword", "Wrong master password.")
            return
        }
        password = ""
        onSave(session)
    }
}
