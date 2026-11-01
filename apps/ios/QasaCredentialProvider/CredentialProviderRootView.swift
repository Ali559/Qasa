import SwiftUI

struct CredentialProviderRootView: View {
    let serviceIdentifiers: [String]
    let preferredRecordId: UUID?
    let onCancel: () -> Void
    let onProvideLogin: (_ user: String, _ password: String) -> Void

    @ObservedObject private var ephemeral = EphemeralPasteboardSession.shared
    @State private var candidates: [AutofillCandidate] = []
    @State private var showUnlock = false
    @State private var pending: AutofillCandidate?
    @State private var password = ""
    @State private var unlockError: String?
    @State private var isUnlocking = false
    @State private var query = ""
    @State private var showedPaymentDrawer = false

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
                                            .font(.system(size: 16, weight: .semibold))
                                            .foregroundStyle(.primary)
                                        Text(rowSubtitle(item))
                                            .font(.system(size: 13))
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
                    .textFieldStyle(.roundedBorder)
                    .padding(.horizontal)

                if let unlockError {
                    Text(unlockError)
                        .font(.footnote)
                        .foregroundStyle(.red)
                }

                Button {
                    Task { await unlockWithPassword() }
                } label: {
                    Text(isUnlocking
                          ? AutofillL10n.tr("autofill.unlocking", "Unlocking…")
                          : AutofillL10n.tr("autofill.unlock", "Unlock"))
                        .frame(maxWidth: .infinity)
                }
                .buttonStyle(.borderedProminent)
                .disabled(password.isEmpty || isUnlocking)
                .padding(.horizontal)

                if SharedVaultKeychain.biometricsEnabled {
                    Button {
                        Task { await unlockWithBiometrics() }
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
            .onAppear {
                if SharedVaultKeychain.biometricsEnabled {
                    Task { await unlockWithBiometrics() }
                }
            }
        }
        .presentationDetents([.medium])
    }

    private func load() {
        let entries = AutofillIndexStore.load().entries
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

    private func unlockWithBiometrics() async {
        isUnlocking = true
        defer { isUnlocking = false }
        guard let session = AutofillUnlock.unlockWithBiometrics() else {
            unlockError = AutofillL10n.tr("autofill.biometricFailed", "Biometric unlock failed.")
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
            case .login(let login):
                let user = login.username.isEmpty ? login.email : login.username
                onProvideLogin(user, login.password)
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
        }
    }
}
