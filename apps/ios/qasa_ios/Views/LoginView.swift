import SwiftUI

struct LoginView: View {
    var onUnlocked: () -> Void
    var onCreateVault: () -> Void

    @State private var password = ""
    @State private var passwordVisible = false
    @State private var errorMessage: String?
    @State private var biometricsAvailable = true

    private var canUnlock: Bool {
        !password.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
    }

    var body: some View {
        VStack(spacing: 0) {
            KasoBrandHeader(
                trailing: AnyView(StatusPill(text: "100% Local & Offline"))
            )

            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    VStack(alignment: .leading, spacing: 8) {
                        Text("Unlock Vault")
                            .font(.system(size: 28, weight: .heavy))
                            .foregroundColor(Theme.textPrimary)
                        Text("Enter your master password to access your local vault.")
                            .font(.system(size: 14))
                            .foregroundColor(Theme.textSecondary)
                            .fixedSize(horizontal: false, vertical: true)
                    }

                    VStack(alignment: .leading, spacing: 8) {
                        SectionLabel(text: "Master Password")
                        ThemedTextField(
                            icon: "lock.fill",
                            placeholder: "Enter password",
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
                                    Text("Unlock with Biometrics")
                                        .font(.system(size: 16, weight: .semibold))
                                        .foregroundColor(Theme.textPrimary)
                                    Text("Face ID or Fingerprint")
                                        .font(.kasoMono(12))
                                        .tracking(0.6)
                                        .foregroundColor(Theme.textSecondary)
                                }
                                Spacer()
                                Image(systemName: "chevron.right")
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
                                Text("Local unlock only")
                                    .font(.system(size: 16, weight: .semibold))
                                    .foregroundColor(Theme.textPrimary)
                            }
                            Text("Your vault never leaves this device. Kaso cannot reset or recover your master password.")
                                .font(.system(size: 14))
                                .foregroundColor(Theme.textSecondary)
                                .fixedSize(horizontal: false, vertical: true)
                        }
                    }
                    .padding(.top, 8)
                    .padding(.bottom, 24)

                    Button(action: onCreateVault) {
                        Text("New here? Create a vault")
                            .font(.system(size: 14, weight: .semibold))
                            .foregroundColor(Theme.mint)
                            .frame(maxWidth: .infinity)
                    }
                    .padding(.bottom, 100)
                }
                .padding(.horizontal, Theme.screenPadding)
                .padding(.top, 24)
            }

            VStack(spacing: 0) {
                Divider().background(Theme.stroke)
                PrimaryButton(title: "Unlock Vault", icon: "lock.open.fill") {
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
        .preferredColorScheme(.dark)
    }

    private func attemptUnlock() {
        let trimmed = password.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return }

        if VaultAuthStore.verifyMasterPassword(trimmed) {
            errorMessage = nil
            onUnlocked()
        } else {
            errorMessage = "Incorrect master password."
        }
    }

    private func unlockWithBiometrics() {
        unlockIfNeeded(protected: true) { success in
            if success {
                errorMessage = nil
                onUnlocked()
            } else {
                errorMessage = "Biometric unlock failed. Enter your master password."
            }
        }
    }
}

#Preview {
    LoginView(onUnlocked: {}, onCreateVault: {})
}
