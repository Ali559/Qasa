import SwiftUI

struct ChangeMasterPasswordView: View {
    @Environment(\.dismiss) private var dismiss
    @Environment(AppPreferences.self) private var prefs

    @State private var currentPassword = ""
    @State private var newPassword = ""
    @State private var confirmPassword = ""
    @State private var isSaving = false
    @State private var errorMessage: String?
    /// Messages shown on their rows.
    @State private var currentError: String?
    @State private var newError: String?
    @State private var confirmError: String?
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

                    FieldGroup {
                        PasswordFieldRow(
                            label: L10n.tr("settings.currentMasterPassword", locale: locale),
                            text: $currentPassword,
                            error: currentError
                        )
                        PasswordFieldRow(
                            label: L10n.tr("settings.newMasterPassword", locale: locale),
                            text: $newPassword,
                            contentType: .newPassword,
                            error: newError
                        )
                        PasswordFieldRow(
                            label: L10n.tr("settings.confirmMasterPassword", locale: locale),
                            text: $confirmPassword,
                            contentType: .newPassword,
                            error: confirmError
                        )
                    }

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
                }
                .padding(Theme.screenPadding)
            }
            .scrollDismissesKeyboard(.interactively)
            .background(Theme.background.ignoresSafeArea())
            .bottomActionBar {
                Button {
                    Task { await save() }
                } label: {
                    HStack(spacing: 8) {
                        if isSaving { ProgressView().tint(Theme.onAccent) }
                        Text(L10n.tr("settings.saveMasterPassword", locale: locale))
                    }
                }
                .buttonStyle(.qasaPrimary)
                .disabled(!canSave)
            }
            .sheetHeader(L10n.tr("settings.changeMasterPassword", locale: locale))
        }
    }

    @MainActor
    private func save() async {
        errorMessage = nil
        currentError = nil
        newError = nil
        confirmError = nil
        guard newPassword == confirmPassword else {
            confirmError = L10n.tr("settings.passwordMismatch", locale: locale)
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
        } catch VaultCryptoError.wrongPassword {
            currentError = localized(.wrongPassword)
        } catch VaultCryptoError.weakPassword {
            newError = localized(.weakPassword)
        } catch let error as VaultCryptoError {
            errorMessage = localized(error)
        } catch {
            errorMessage = L10n.message(for: error)
        }
    }

    private func localized(_ error: VaultCryptoError) -> String {
        switch error {
        case .wrongPassword:
            return L10n.tr("settings.wrongMasterPassword", locale: locale)
        case .weakPassword:
            return L10n.tr("settings.weakMasterPassword", locale: locale)
        default:
            return L10n.message(for: error)
        }
    }
}
