import SwiftUI

struct CreateVaultView: View {
    @Environment(AppPreferences.self) private var prefs
    @State private var password: String = ""
    @State private var confirmPassword: String = ""
    @State private var biometricsEnabled = true
    @State private var passwordError: String?
    @State private var confirmError: String?
    var onVaultCreated: (String, Bool) -> Void
    var onPairExisting: (() -> Void)? = nil
    var onRestoreBackup: (() -> Void)? = nil
    var onLogin: (() -> Void)? = nil

    private var locale: Locale { prefs.locale }

    private var strength: (label: String, filled: Int, color: Color) {
        switch password.count {
        case 0: return ("", 0, Theme.stroke)
        case 1..<4: return (L10n.tr("auth.create.weak", locale: locale), 1, Theme.danger)
        case 4..<8: return (L10n.tr("auth.create.fair", locale: locale), 2, Theme.goldMuted)
        default: return (L10n.tr("auth.create.strong", locale: locale), 3, Theme.success)
        }
    }

    /// At least 8 characters, the same rule as Android and as Change Master Password.
    private var canCreate: Bool {
        password.count >= 8 && password == confirmPassword
    }

    var body: some View {
        VStack(spacing: 0) {
            KasoBrandHeader(
                trailing: AnyView(StatusPill(text: L10n.tr("brand.localOffline", locale: locale)))
            )

            ScrollView {
                VStack(alignment: .leading, spacing: 12) {
                    VStack(alignment: .leading, spacing: 8) {
                        if !prefs.userName.isEmpty {
                            Text(String(format: L10n.tr("onboarding.hello", locale: locale), prefs.userName))
                                .font(.qasa(17, weight: .semibold))
                                .foregroundColor(Theme.accent)
                        }
                        Text(L10n.tr("auth.create.title", locale: locale))
                            .font(.qasa(28, weight: .bold))
                            .foregroundColor(Theme.textPrimary)
                        Text(L10n.tr("auth.create.subtitle", locale: locale))
                            .font(.qasa(14))
                            .foregroundColor(Theme.textSecondary)
                            .fixedSize(horizontal: false, vertical: true)
                        if onPairExisting != nil {
                            Button {
                                onPairExisting?()
                            } label: {
                                Text(L10n.tr("auth.create.pairExisting", locale: locale))
                                    .font(.qasa(14, weight: .semibold))
                                    .foregroundColor(Theme.accent)
                            }
                            .padding(.top, 4)
                        }
                        if let onRestoreBackup {
                            Button(action: onRestoreBackup) {
                                VStack(alignment: .leading, spacing: 2) {
                                    Text(L10n.tr("backup.restore", locale: locale))
                                        .font(.qasa(14, weight: .semibold))
                                        .foregroundColor(Theme.accent)
                                    Text(L10n.tr("backup.restoreSubtitle", locale: locale))
                                        .font(.qasaLabel(12))
                                        .foregroundColor(Theme.textSecondary)
                                        .multilineTextAlignment(.leading)
                                }
                            }
                            .padding(.top, 4)
                        }
                    }
                    .padding(.bottom, 4)

                    VStack(alignment: .leading, spacing: 8) {
                        FieldGroup {
                            PasswordFieldRow(
                                label: L10n.tr("auth.unlock.masterPassword", locale: locale),
                                text: $password,
                                contentType: .newPassword,
                                error: passwordError
                            )
                            PasswordFieldRow(
                                label: L10n.tr("auth.create.confirmPassword", locale: locale),
                                text: $confirmPassword,
                                contentType: .newPassword,
                                error: confirmError
                            )
                        }

                        if !password.isEmpty {
                            VStack(spacing: 4) {
                                HStack {
                                    Text(L10n.tr("auth.create.strength", locale: locale))
                                        .font(.qasaLabel(12))
                                        .foregroundColor(Theme.textSecondary)
                                    Spacer()
                                    Text(strength.label)
                                        .font(.qasaLabel(12))
                                        .foregroundColor(Theme.accent)
                                }
                                StrengthBar(segments: 3, filled: strength.filled, color: strength.color)
                            }
                            .padding(.top, 4)
                        }
                    }


                    SettingsSection {
                        ToggleRow(
                            icon: .fingerprint,
                            iconColor: Theme.accent,
                            title: L10n.tr("auth.create.enableBiometrics", locale: locale),
                            subtitle: L10n.tr("auth.create.biometricsHint", locale: locale),
                            isOn: $biometricsEnabled
                        )
                    }
                    .padding(.top, 12)

                    CardContainer(background: Theme.cardBackgroundLight) {
                        ZStack {
                            GeometryReader { geo in
                                QasaIconImage(.shield, size: 88)
                                    .foregroundStyle(Theme.accent.opacity(0.14))
                                    // Pin to the physical trailing edge so RTL does not leave a hollow watermark on the leading side.
                                    .position(x: geo.size.width - 8, y: 28)
                                    .allowsHitTesting(false)
                            }

                            VStack(alignment: .leading, spacing: 12) {
                                HStack(spacing: 8) {
                                    QasaIconImage(.lock, size: 16)
                                        .foregroundColor(Theme.accent)
                                    Text(L10n.tr("auth.create.zeroKnowledge", locale: locale))
                                        .font(.qasa(16, weight: .semibold))
                                        .foregroundColor(Theme.textPrimary)
                                }
                                Text(L10n.tr("auth.create.zeroKnowledgeBody", locale: locale))
                                    .font(.qasa(14))
                                    .foregroundColor(Theme.textSecondary)
                                    .fixedSize(horizontal: false, vertical: true)
                            }
                        }
                        .clipped()
                    }
                    .padding(.top, 12)

                    if let onLogin {
                        Button(L10n.tr("auth.create.alreadyHave", locale: locale), action: onLogin)
                            .buttonStyle(.qasaPlain)
                            .padding(.top, 4)
                    }
                }
                .padding(.horizontal, Theme.screenPadding)
                .padding(.top, 24)
                .padding(.bottom, 24)
            }
            .scrollDismissesKeyboard(.interactively)
        }
        .background(Theme.background.ignoresSafeArea())
        .bottomActionBar {
            PrimaryButton(title: L10n.tr("auth.create.buttonLocal", locale: locale), icon: .login) {
                createVault()
            }
            .disabled(!canCreate)
        }
        .environment(prefs)
    }

    private func createVault() {
        // Each message on the row it is about (design/README.md, "Labelled fields").
        passwordError = nil
        confirmError = nil
        guard canCreate else {
            if password.isEmpty {
                passwordError = L10n.tr("auth.create.enterPassword", locale: locale)
            } else if password.count < 8 {
                passwordError = L10n.tr("settings.weakMasterPassword", locale: locale)
            } else {
                confirmError = L10n.tr("auth.create.mismatch", locale: locale)
            }
            return
        }
        onVaultCreated(password, biometricsEnabled)
    }
}

#Preview {
    CreateVaultView(onVaultCreated: { _, _ in }, onLogin: {})
        .environment(AppPreferences.shared)
}
