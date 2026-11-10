import SwiftUI

struct CredentialProviderRootView: View {
    /// A password request, or an iOS 18 one-time-code request (only logins with 2FA).
    enum Mode { case password, oneTimeCode }

    let mode: Mode
    let serviceIdentifiers: [String]
    let preferredRecordId: UUID?
    let onCancel: () -> Void
    let onProvideLogin: (_ user: String, _ password: String) -> Void
    let onProvideCode: (_ code: String) -> Void

    @ObservedObject private var ephemeral = EphemeralPasteboardSession.shared
    @State private var candidates: [AutofillCandidate] = []
    @State private var showUnlock = false
    @State private var pending: AutofillCandidate?
    @State private var password = ""
    @State private var unlockError: String?
    @State private var isUnlocking = false
    @State private var query = ""
    @State private var showedPaymentDrawer = false
    /// Read when the unlock sheet appears (Keychain, not observable).
    @State private var masterPasswordDue = false
    @State private var showsBiometricButton = false

    private var filtered: [AutofillCandidate] {
        let q = query.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        guard !q.isEmpty else { return candidates }
        return candidates.filter {
            $0.title.lowercased().contains(q) || $0.subtitle.lowercased().contains(q)
        }
    }

    var body: some View {
        NavigationStack {
            ZStack {
                List {
                    if filtered.isEmpty {
                        ContentUnavailableView(
                            AutofillL10n.tr("autofill.noMatches", "No matches"),
                            systemImage: "key.fill",
                            description: Text(AutofillL10n.tr("autofill.noMatchesHint", "Try searching all vault items."))
                        )
                    } else {
                        ForEach(filtered) { item in
                            Button {
                                select(item)
                            } label: {
                                HStack {
                                    VStack(alignment: .leading, spacing: 4) {
                                        Text(item.title)
                                            .font(QasaFontFace.font(size: 16, weight: .semibold))
                                            .foregroundStyle(.primary)
                                        Text(rowSubtitle(item))
                                            .font(QasaFontFace.font(size: 13))
                                            .foregroundStyle(.secondary)
                                    }
                                    Spacer()
                                    Image(systemName: icon(for: item.kind))
                                        .foregroundStyle(.secondary)
                                }
                            }
                        }
                    }
                }
                .searchable(text: $query, prompt: AutofillL10n.tr("autofill.search", "Search"))

                EphemeralSecretDrawer(session: ephemeral)
            }
            .navigationTitle(AutofillL10n.tr("autofill.title", "Qasa Autofill"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button(AutofillL10n.tr("common.cancel", "Cancel"), action: onCancel)
                }
            }
            .onAppear(perform: load)
            .onChange(of: ephemeral.isPresented) { _, presented in
                if !presented, showedPaymentDrawer {
                    showedPaymentDrawer = false
                    onCancel()
                }
            }
            .sheet(isPresented: $showUnlock) {
                unlockSheet
            }
        }
    }

    private var unlockSheet: some View {
        NavigationStack {
            VStack(spacing: 16) {
                SecureField(AutofillL10n.tr("auth.unlock.placeholder", "Master password"), text: $password)
                    .qasaExtensionField()
                    .padding(.horizontal)

                if let unlockError {
                    Text(unlockError)
                        .font(QasaFontFace.font(size: 13))
                        .foregroundStyle(QasaSharedPalette.danger)
                }

                if masterPasswordDue {
                    Text(AutofillBiometricGate.dueHint)
                        .font(QasaFontFace.font(size: 13))
                        .foregroundStyle(.secondary)
                        .multilineTextAlignment(.center)
                        .fixedSize(horizontal: false, vertical: true)
                        .padding(.horizontal)
                }

                Button {
                    Task { await unlockWithPassword() }
                } label: {
                    Text(isUnlocking
                          ? AutofillL10n.tr("autofill.unlocking", "Unlocking…")
                          : AutofillL10n.tr("autofill.unlock", "Unlock"))
                }
                .buttonStyle(QasaExtensionPrimaryButtonStyle())
                .disabled(password.isEmpty || isUnlocking)
                .padding(.horizontal)

                // Hidden while the 14-day master-password unlock is due (`BiometricPolicy`).
                if showsBiometricButton {
                    Button {
                        Task { await unlockWithBiometrics(automatic: false) }
                    } label: {
                        Label(
                            AutofillL10n.tr("autofill.useBiometrics", "Use Face ID / Touch ID"),
                            systemImage: "faceid"
                        )
                    }
                }

                Spacer()
            }
            .padding(.top, 24)
            .navigationTitle(AutofillL10n.tr("autofill.unlockTitle", "Unlock vault"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button(AutofillL10n.tr("common.cancel", "Cancel")) {
                        showUnlock = false
                        pending = nil
                        password = ""
                        unlockError = nil
                    }
                }
            }
            // Once per presentation of the sheet; a cancelled prompt leaves the button.
            .onAppear {
                masterPasswordDue = AutofillBiometricGate.masterPasswordDue
                showsBiometricButton = AutofillBiometricGate.showsBiometricButton
                if AutofillUnlock.canOfferBiometricUnlock {
                    Task { await unlockWithBiometrics(automatic: true) }
                }
            }
        }
        .presentationDetents([.medium])
    }

    private func load() {
        // Profile items are only inserted as text; a code request lists logins with 2FA.
        let entries = AutofillIndexStore.load().entries.filter { entry in
            switch mode {
            case .password: return entry.kind.isCredentialListKind
            case .oneTimeCode: return entry.kind == .login && entry.hasTOTP == true
            }
        }
        var list = AutofillContextMatcher.candidates(
            from: entries,
            serviceIdentifiers: serviceIdentifiers,
            hint: inferHint(from: entries)
        )
        if let preferredRecordId,
           let preferred = entries.first(where: { $0.id == preferredRecordId }) {
            let candidate = AutofillCandidate(
                id: preferred.id,
                kind: preferred.kind,
                title: preferred.title,
                subtitle: preferred.subtitle,
                last4: preferred.last4,
                score: 1000,
                encryptedSecrets: preferred.encryptedSecrets
            )
            list.removeAll { $0.id == preferred.id }
            list.insert(candidate, at: 0)
        }
        candidates = list
    }

    private func inferHint(from entries: [AutofillIndexEntry]) -> AutofillContextHint {
        // Prefer login when service identifiers look like domains.
        if serviceIdentifiers.contains(where: { $0.contains(".") }) {
            return .login
        }
        if entries.contains(where: { $0.kind == .card }) {
            return .paymentCard
        }
        return .unknown
    }

    private func select(_ item: AutofillCandidate) {
        pending = item
        showUnlock = true
        unlockError = nil
        password = ""
    }

    private func unlockWithBiometrics(automatic: Bool) async {
        guard !isUnlocking else { return }
        isUnlocking = true
        defer { isUnlocking = false }
        guard let session = AutofillUnlock.unlockWithBiometrics() else {
            masterPasswordDue = AutofillBiometricGate.masterPasswordDue
            showsBiometricButton = AutofillBiometricGate.showsBiometricButton
            if !automatic {
                unlockError = AutofillL10n.tr("autofill.biometricFailed", "Biometric unlock failed.")
            }
            return
        }
        await complete(with: session)
    }

    private func unlockWithPassword() async {
        isUnlocking = true
        defer { isUnlocking = false }
        guard let session = AutofillUnlock.unlock(password: password) else {
            unlockError = AutofillL10n.tr("autofill.wrongPassword", "Wrong master password.")
            return
        }
        await complete(with: session)
    }

    private func complete(with session: AutofillUnlock.Session) async {
        guard let pending else { return }
        do {
            let secrets = try AutofillUnlock.decryptSecrets(pending, session: session)
            showUnlock = false
            password = ""
            switch secrets {
            case .login(let login) where mode == .oneTimeCode:
                guard let code = TOTP(stored: login.totp)?.code() else {
                    unlockError = AutofillL10n.tr("autofill.decryptFailed", "Could not decrypt item.")
                    return
                }
                onProvideCode(code)
            case .login(let login):
                let user = login.username.isEmpty ? login.email : login.username
                // The site usually asks for the 2FA code right after the password.
                TOTPPasteboard.copyCurrentCode(stored: login.totp)
                onProvideLogin(user, login.password)
            case .name, .email, .phone, .address, .company:
                // Never listed here (see `load`).
                onCancel()
            case .card, .bank:
                showedPaymentDrawer = true
                ephemeral.present(secrets: secrets)
                self.pending = nil
            }
        } catch {
            unlockError = AutofillL10n.tr("autofill.decryptFailed", "Could not decrypt item.")
        }
    }

    private func rowSubtitle(_ item: AutofillCandidate) -> String {
        if let last4 = item.last4, !last4.isEmpty {
            return "•••• \(last4)"
        }
        return item.subtitle
    }

    private func icon(for kind: AutofillItemKind) -> String {
        switch kind {
        case .login: return "person.crop.circle"
        case .card: return "creditcard"
        case .bankAccount: return "building.columns"
        case .name: return "person.text.rectangle"
        case .email: return "envelope"
        case .phone: return "phone"
        case .address: return "house"
        case .company: return "building.2"
        }
    }
}
