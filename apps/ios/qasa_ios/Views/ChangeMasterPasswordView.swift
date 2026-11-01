import SwiftUI

struct ChangeMasterPasswordView: View {
    @Environment(\.dismiss) private var dismiss
    @Environment(AppPreferences.self) private var prefs

    @State private var currentPassword = ""
    @State private var newPassword = ""
    @State private var confirmPassword = ""
    @State private var passwordVisible = false
    @State private var isSaving = false
    @State private var errorMessage: String?
    @State private var didSucceed = false

    private var locale: Locale { prefs.locale }

    private var canSave: Bool {
        !currentPassword.isEmpty
            && newPassword.count >= 8
            && newPassword == confirmPassword
            && newPassword != currentPassword
            && !isSaving
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 20) {
                    Text(L10n.tr("settings.changeMasterSubtitle", locale: locale))
                        .font(.qasa(14))
                        .foregroundColor(Theme.textSecondary)
                        .fixedSize(horizontal: false, vertical: true)

                    field(
                        title: L10n.tr("settings.currentMasterPassword", locale: locale),
                        text: $currentPassword
                    )
                    field(
                        title: L10n.tr("settings.newMasterPassword", locale: locale),
                        text: $newPassword
                    )
                    field(
                        title: L10n.tr("settings.confirmMasterPassword", locale: locale),
                        text: $confirmPassword
                    )

                    if let errorMessage {
                        Text(errorMessage)
                            .font(.qasa(13))
                            .foregroundColor(Theme.danger)
                    }

                    if didSucceed {
                        Text(L10n.tr("settings.masterPasswordChanged", locale: locale))
                            .font(.qasa(13))
                            .foregroundColor(Theme.success)
                    }

                    Button {
                        Task { await save() }
                    } label: {
                        HStack {
                            if isSaving { ProgressView().tint(Theme.onAccentChip) }
                            Text(L10n.tr("settings.saveMasterPassword", locale: locale))
                                .font(.qasa(16, weight: .semibold))
                        }
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 14)
                        .foregroundColor(Theme.onAccentChip)
                        .background(canSave ? Theme.accent : Theme.accent.opacity(0.35))
                        .cornerRadius(10)
                    }
                    .disabled(!canSave)
                }
                .padding(Theme.screenPadding)
            }
            .background(Theme.background.ignoresSafeArea())
            .navigationTitle(L10n.tr("settings.changeMasterPassword", locale: locale))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button(L10n.tr("settings.cancel", locale: locale)) { dismiss() }
                }
            }
        }
    }

    private func field(title: String, text: Binding<String>) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            SectionLabel(text: title)
            ThemedTextField(
                icon: "lock.fill",
                placeholder: title,
                text: text,
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
        }
    }

    @MainActor
    private func save() async {
        errorMessage = nil
        guard newPassword == confirmPassword else {
            errorMessage = L10n.tr("settings.passwordMismatch", locale: locale)
            return
        }
        isSaving = true
        defer { isSaving = false }
        do {
            let derived = try VaultSession.shared.derivePasswordChange(
                current: currentPassword,
                newPassword: newPassword
            )
            try VaultRepository.shared.rekeyAllItems(
                oldEnc: derived.oldEnc,
                newEnc: derived.newEnc,
                newSearch: derived.newSearch
            )
            VaultSession.shared.commitPasswordChange(
                newSalt: derived.newSalt,
                newVerifier: derived.newVerifier,
                newPassword: derived.newPassword,
                newEnc: derived.newEnc,
                newSearch: derived.newSearch
            )
            SharedVaultKeychain.kdfSalt = VaultKeychainStore.kdfSalt
            SharedVaultKeychain.passwordVerifier = VaultKeychainStore.passwordVerifier
            didSucceed = true
            try? await Task.sleep(nanoseconds: 700_000_000)
            dismiss()
        } catch let error as VaultCryptoError {
            errorMessage = localized(error)
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    private func localized(_ error: VaultCryptoError) -> String {
        switch error {
        case .wrongPassword:
            return L10n.tr("settings.wrongMasterPassword", locale: locale)
        case .weakPassword:
            return L10n.tr("settings.weakMasterPassword", locale: locale)
        default:
            return error.localizedDescription
        }
    }
}
