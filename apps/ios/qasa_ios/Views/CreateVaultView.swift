import SwiftUI

struct CreateVaultView: View {
    @State private var password: String = ""
    @State private var confirmPassword: String = ""
    @State private var biometricsEnabled = true
    @State private var passwordVisible = false
    var onVaultCreated: () -> Void

    var strength: (label: String, progress: CGFloat, color: Color) {
        switch password.count {
        case 0..<4: return ("Weak", 0.25, Theme.danger)
        case 4..<8: return ("Fair", 0.6, Theme.gold)
        default: return ("Strong", 1.0, Theme.mint)
        }
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 24) {
                HStack {
                    Label("Kaso", systemImage: "shield.fill")
                        .font(.headline)
                        .foregroundColor(Theme.mint)
                    Spacer()
                    PillBadge(text: "100% Local & Offline", color: Theme.mint)
                }
                .padding(.top, 8)

                VStack(alignment: .leading, spacing: 6) {
                    Text("Create Vault")
                        .font(.largeTitle.bold())
                        .foregroundColor(Theme.textPrimary)
                    Text("Set a master password to secure your local vault. This is the only key.")
                        .font(.subheadline)
                        .foregroundColor(Theme.textSecondary)
                }

                VStack(alignment: .leading, spacing: 8) {
                    SectionLabel(text: "Master Password")
                    HStack {
                        Image(systemName: "lock.fill")
                            .foregroundColor(Theme.textSecondary)
                        if passwordVisible {
                            TextField("Enter password", text: $password)
                                .foregroundColor(Theme.textPrimary)
                        } else {
                            SecureField("Enter password", text: $password)
                                .foregroundColor(Theme.textPrimary)
                        }
                        Button {
                            passwordVisible.toggle()
                        } label: {
                            Image(systemName: passwordVisible ? "eye.slash" : "eye")
                                .foregroundColor(Theme.textSecondary)
                        }
                    }
                    .padding()
                    .background(Theme.cardBackground)
                    .cornerRadius(Theme.cornerRadius)

                    HStack {
                        Text("Strength")
                            .font(.caption2)
                            .foregroundColor(Theme.textSecondary)
                        Spacer()
                        Text(strength.label)
                            .font(.caption2.weight(.semibold))
                            .foregroundColor(strength.color)
                    }
                    GeometryReader { geo in
                        ZStack(alignment: .leading) {
                            Capsule().fill(Theme.cardBackgroundLight)
                            Capsule().fill(strength.color)
                                .frame(width: geo.size.width * strength.progress)
                        }
                    }
                    .frame(height: 4)
                }

                VStack(alignment: .leading, spacing: 8) {
                    SectionLabel(text: "Confirm Password")
                    HStack {
                        Image(systemName: "lock.rotation")
                            .foregroundColor(Theme.textSecondary)
                        SecureField("Re-enter password", text: $confirmPassword)
                            .foregroundColor(Theme.textPrimary)
                    }
                    .padding()
                    .background(Theme.cardBackground)
                    .cornerRadius(Theme.cornerRadius)
                }

                CardContainer {
                    HStack {
                        Image(systemName: "faceid")
                            .foregroundColor(Theme.mint)
                            .frame(width: 28)
                        VStack(alignment: .leading, spacing: 2) {
                            Text("Enable Biometrics")
                                .foregroundColor(Theme.textPrimary)
                                .font(.subheadline.weight(.medium))
                            Text("Use Face ID or Fingerprint")
                                .foregroundColor(Theme.textSecondary)
                                .font(.caption)
                        }
                        Spacer()
                        Toggle("", isOn: $biometricsEnabled)
                            .labelsHidden()
                            .tint(Theme.mint)
                    }
                }

                HStack(alignment: .top, spacing: 10) {
                    Image(systemName: "exclamationmark.triangle.fill")
                        .foregroundColor(Theme.gold)
                    VStack(alignment: .leading, spacing: 4) {
                        Text("Zero-Knowledge")
                            .foregroundColor(Theme.gold)
                            .font(.subheadline.weight(.semibold))
                        Text("Your master password cannot be recovered by anyone—not even Kaso. If you lose it, your vault data is permanently inaccessible.")
                            .font(.caption)
                            .foregroundColor(Theme.textSecondary)
                    }
                }
                .padding()
                .background(Theme.gold.opacity(0.08))
                .cornerRadius(Theme.cornerRadius)
                .overlay(
                    RoundedRectangle(cornerRadius: Theme.cornerRadius)
                        .stroke(Theme.gold.opacity(0.3), lineWidth: 1)
                )

                PrimaryButton(title: "Create Local Vault", icon: "lock.shield.fill") {
                    onVaultCreated()
                }
                .padding(.top, 4)
            }
            .padding(20)
        }
        .background(Theme.background.ignoresSafeArea())
    }
}

#Preview {
    CreateVaultView(onVaultCreated: {})
}
