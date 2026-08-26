import SwiftUI

struct CreateVaultView: View {
    @State private var password: String = ""
    @State private var confirmPassword: String = ""
    @State private var biometricsEnabled = true
    @State private var passwordVisible = false
    @State private var errorMessage: String?
    var onVaultCreated: (String, Bool) -> Void
    var onLogin: (() -> Void)? = nil

    private var strength: (label: String, filled: Int, color: Color) {
        switch password.count {
        case 0: return ("", 0, Theme.stroke)
        case 1..<4: return ("Weak", 1, Theme.danger)
        case 4..<8: return ("Fair", 2, Theme.goldMuted)
        default: return ("Strong", 3, Theme.success)
        }
    }

    private var canCreate: Bool {
        !password.isEmpty && password == confirmPassword
    }

    var body: some View {
        VStack(spacing: 0) {
            KasoBrandHeader(
                trailing: AnyView(StatusPill(text: "100% Local & Offline"))
            )

            ScrollView {
                VStack(alignment: .leading, spacing: 12) {
                    VStack(alignment: .leading, spacing: 8) {
                        Text("Create Vault")
                            .font(.system(size: 28, weight: .heavy))
                            .foregroundColor(Theme.textPrimary)
                        Text("Set a master password to secure your local vault. This is the only key.")
                            .font(.system(size: 14))
                            .foregroundColor(Theme.textSecondary)
                            .fixedSize(horizontal: false, vertical: true)
                    }
                    .padding(.bottom, 4)

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

                        if !password.isEmpty {
                            VStack(spacing: 4) {
                                HStack {
                                    Text("Strength")
                                        .font(.kasoMono(12))
                                        .tracking(0.6)
                                        .foregroundColor(Theme.textSecondary)
                                    Spacer()
                                    Text(strength.label)
                                        .font(.kasoMono(12))
                                        .tracking(0.6)
                                        .foregroundColor(Theme.mint)
                                }
                                StrengthBar(segments: 3, filled: strength.filled, color: strength.color)
                            }
                            .padding(.top, 4)
                        }
                    }

                    VStack(alignment: .leading, spacing: 8) {
                        SectionLabel(text: "Confirm Password")
                        ThemedTextField(
                            icon: "lock.rotation",
                            placeholder: "Re-enter password",
                            text: $confirmPassword,
                            isSecure: true
                        )
                    }
                    .padding(.top, 4)

                    if let errorMessage {
                        Text(errorMessage)
                            .font(.system(size: 13, weight: .medium))
                            .foregroundColor(Theme.danger)
                    }

                    CardContainer {
                        HStack {
                            ZStack {
                                Circle()
                                    .fill(Theme.cardBackgroundLight)
                                    .frame(width: 40, height: 40)
                                Image(systemName: "touchid")
                                    .foregroundColor(Theme.mint)
                            }
                            VStack(alignment: .leading, spacing: 0) {
                                Text("Enable Biometrics")
                                    .font(.system(size: 16))
                                    .foregroundColor(Theme.textPrimary)
                                Text("Use Face ID or Fingerprint")
                                    .font(.kasoMono(12))
                                    .tracking(0.6)
                                    .foregroundColor(Theme.textSecondary)
                            }
                            Spacer()
                            Toggle("", isOn: $biometricsEnabled)
                                .labelsHidden()
                                .tint(Theme.mint)
                        }
                    }
                    .padding(.top, 12)

                    CardContainer(background: Theme.cardBackgroundLight) {
                        ZStack(alignment: .topTrailing) {
                            Image(systemName: "shield.fill")
                                .font(.system(size: 80))
                                .foregroundColor(Theme.stroke.opacity(0.5))
                                .offset(x: 24, y: -24)

                            VStack(alignment: .leading, spacing: 12) {
                                HStack(spacing: 8) {
                                    Image(systemName: "exclamationmark.triangle.fill")
                                        .foregroundColor(Theme.goldMuted)
                                    Text("Zero-Knowledge")
                                        .font(.system(size: 20, weight: .semibold))
                                        .foregroundColor(Theme.goldMuted)
                                }
                                Text("Your master password cannot be recovered by anyone—not even Qasa. If you lose it, your vault data is permanently inaccessible.")
                                    .font(.system(size: 14))
                                    .foregroundColor(Theme.textSecondary)
                                    .fixedSize(horizontal: false, vertical: true)
                            }
                        }
                    }
                    .padding(.top, 12)

                    if let onLogin {
                        Button(action: onLogin) {
                            Text("Already have a vault? Unlock")
                                .font(.system(size: 14, weight: .semibold))
                                .foregroundColor(Theme.mint)
                                .frame(maxWidth: .infinity)
                        }
                        .padding(.top, 8)
                    }

                    Color.clear.frame(height: 100)
                }
                .padding(.horizontal, Theme.screenPadding)
                .padding(.top, 24)
            }

            VStack(spacing: 0) {
                Divider().background(Theme.stroke)
                PrimaryButton(title: "Create Local Vault", icon: "key.fill") {
                    createVault()
                }
                .opacity(canCreate ? 1 : 0.5)
                .disabled(!canCreate)
                .padding(.horizontal, Theme.screenPadding)
                .padding(.top, 17)
                .padding(.bottom, 32)
            }
            .background(Theme.chrome.opacity(0.92))
        }
        .background(Theme.background.ignoresSafeArea())
        .preferredColorScheme(.dark)
    }

    private func createVault() {
        guard canCreate else {
            errorMessage = password != confirmPassword ? "Passwords do not match." : "Enter a master password."
            return
        }
        errorMessage = nil
        onVaultCreated(password, biometricsEnabled)
    }
}

#Preview {
    CreateVaultView(onVaultCreated: { _, _ in }, onLogin: {})
}
