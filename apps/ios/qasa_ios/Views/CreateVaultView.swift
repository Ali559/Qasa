import SwiftUI

struct CreateVaultView: View {
    @Environment(AppPreferences.self) private var prefs
    @State private var password: String = ""
    @State private var confirmPassword: String = ""
    @State private var biometricsEnabled = true
    @State private var passwordVisible = false
    @State private var errorMessage: String?
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

                    VStack(alignment: .leading, spacing: 8) {
                        SectionLabel(text: L10n.tr("auth.create.confirmPassword", locale: locale))
                        ThemedTextField(
                            icon: "lock.rotation",
                            placeholder: L10n.tr("auth.create.reenter", locale: locale),
                            text: $confirmPassword,
                            isSecure: true
                        )
                    }
                    .padding(.top, 4)

                    if let errorMessage {
                        Text(errorMessage)
                            .font(.qasa(13, weight: .medium))
                            .foregroundColor(Theme.danger)
                    }

                    CardContainer {
                        HStack {
                            ZStack {
                                Circle()
                                    .fill(Theme.cardBackgroundLight)
                                    .frame(width: 40, height: 40)
                                Image(systemName: "touchid")
                                    .foregroundColor(Theme.accent)
                            }
                            VStack(alignment: .leading, spacing: 0) {
                                Text(L10n.tr("auth.create.enableBiometrics", locale: locale))
                                    .font(.qasa(16))
                                    .foregroundColor(Theme.textPrimary)
                                Text(L10n.tr("auth.create.biometricsHint", locale: locale))
                                    .font(.qasaLabel(12))
                                    .foregroundColor(Theme.textSecondary)
                            }
                            Spacer()
                            Toggle("", isOn: $biometricsEnabled)
                                .labelsHidden()
                                .tint(Theme.accent)
                        }
                    }
                    .padding(.top, 12)

                    CardContainer(background: Theme.cardBackgroundLight) {
                        ZStack {
                            GeometryReader { geo in
                                Image(systemName: "shield.fill")
                                    .font(.qasa(88, weight: .bold))
                                    .foregroundStyle(Theme.accent.opacity(0.14))
                                    // Pin to the physical trailing edge so RTL does not leave a hollow watermark on the leading side.
                                    .position(x: geo.size.width - 8, y: 28)
                                    .allowsHitTesting(false)
                            }

                            VStack(alignment: .leading, spacing: 12) {
                                HStack(spacing: 8) {
                                    Image(systemName: "lock.fill")
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
                        Button(action: onLogin) {
                            Text(L10n.tr("auth.create.alreadyHave", locale: locale))
                                .font(.qasa(14, weight: .semibold))
                                .foregroundColor(Theme.accent)
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
                PrimaryButton(title: L10n.tr("auth.create.buttonLocal", locale: locale), icon: "key.fill") {
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
        .environment(prefs)
    }

    private func createVault() {
        guard canCreate else {
            errorMessage = password.isEmpty
                ? L10n.tr("auth.create.enterPassword", locale: locale)
                : password.count < 8
                    ? L10n.tr("settings.weakMasterPassword", locale: locale)
                    : L10n.tr("auth.create.mismatch", locale: locale)
            return
        }
        errorMessage = nil
        onVaultCreated(password, biometricsEnabled)
    }
}

#Preview {
    CreateVaultView(onVaultCreated: { _, _ in }, onLogin: {})
        .environment(AppPreferences.shared)
}
