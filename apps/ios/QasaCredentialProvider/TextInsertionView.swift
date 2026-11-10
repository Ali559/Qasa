import SwiftUI

/// iOS 18 text insertion: "AutoFill → Qasa" in any text field (docs/autofill-plan.md §3.4).
/// Unlock first (Face ID starts on its own when it is on, the master password is always
/// there), then pick an item, then the one value to insert. This is how a card number,
/// an expiry, an address line or a phone number reaches a form on iOS: Apple doesn't let
/// a provider fill whole card or address forms.
struct TextInsertionView: View {
    let onCancel: () -> Void
    let onInsert: (_ text: String) -> Void

    @State private var session: AutofillUnlock.Session?
    @State private var password = ""
    @State private var unlockError: String?
    @State private var isUnlocking = false
    @State private var query = ""
    @State private var entries: [AutofillIndexEntry] = []
    @State private var picked: (entry: AutofillIndexEntry, choices: [AutofillTextChoice])?

    /// Cards and profile items first: they are what text insertion is for.
    private static let order: [AutofillItemKind] = [.card, .address, .name, .email, .phone, .company, .login, .bankAccount]

    var body: some View {
        NavigationStack {
            Group {
                if session == nil {
                    unlockForm
                } else if let picked {
                    choiceList(picked.entry, picked.choices)
                } else {
                    itemList
                }
            }
            .navigationTitle(AutofillL10n.tr("autofill.insertTitle", "Insert from Qasa"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button(AutofillL10n.tr("common.cancel", "Cancel"), action: onCancel)
                }
            }
        }
        .onAppear {
            entries = AutofillIndexStore.load().entries.sorted { a, b in
                let ia = Self.order.firstIndex(of: a.kind) ?? 99
                let ib = Self.order.firstIndex(of: b.kind) ?? 99
                return ia != ib ? ia < ib : a.title.localizedCaseInsensitiveCompare(b.title) == .orderedAscending
            }
            if SharedVaultKeychain.biometricsEnabled {
                Task { await unlockWithBiometrics() }
            }
        }
    }

    // MARK: unlock

    private var unlockForm: some View {
        VStack(spacing: 16) {
            Text(AutofillL10n.tr("autofill.unlockTitle", "Unlock Qasa"))
                .font(.headline)
            SecureField(AutofillL10n.tr("auth.unlock.placeholder", "Master password"), text: $password)
                .textFieldStyle(.roundedBorder)
                .submitLabel(.go)
                .onSubmit { Task { await unlockWithPassword() } }
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
                    Label(AutofillL10n.tr("autofill.useBiometrics", "Use Face ID / Touch ID"), systemImage: "faceid")
                }
            }
            Spacer()
        }
        .padding(.top, 24)
    }

    private func unlockWithBiometrics() async {
        isUnlocking = true
        defer { isUnlocking = false }
        guard let unlocked = AutofillUnlock.unlockWithBiometrics() else {
            unlockError = AutofillL10n.tr("autofill.biometricFailed", "Biometric unlock failed.")
            return
        }
        session = unlocked
    }

    private func unlockWithPassword() async {
        guard !password.isEmpty else { return }
        isUnlocking = true
        defer { isUnlocking = false }
        guard let unlocked = AutofillUnlock.unlock(password: password) else {
            unlockError = AutofillL10n.tr("autofill.wrongPassword", "Wrong master password.")
            return
        }
        password = ""
        session = unlocked
    }

    // MARK: items

    private var filtered: [AutofillIndexEntry] {
        let q = query.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        guard !q.isEmpty else { return entries }
        return entries.filter { $0.title.lowercased().contains(q) || $0.subtitle.lowercased().contains(q) }
    }

    private var itemList: some View {
        List {
            if filtered.isEmpty {
                ContentUnavailableView(
                    AutofillL10n.tr("autofill.noMatches", "No matches"),
                    systemImage: "tray",
                    description: Text(AutofillL10n.tr("autofill.noMatchesHint", "Try searching all vault items."))
                )
            }
            ForEach(filtered) { entry in
                Button {
                    open(entry)
                } label: {
                    HStack {
                        Image(systemName: Self.icon(for: entry.kind))
                            .foregroundStyle(.secondary)
                            .frame(width: 28)
                        VStack(alignment: .leading, spacing: 2) {
                            Text(entry.title)
                                .font(.system(size: 16, weight: .semibold))
                                .foregroundStyle(.primary)
                            let hint = entry.last4.map { "•••• \($0)" } ?? entry.subtitle
                            if !hint.isEmpty {
                                Text(hint)
                                    .font(.system(size: 13))
                                    .foregroundStyle(.secondary)
                            }
                        }
                    }
                }
            }
        }
        .searchable(text: $query, prompt: AutofillL10n.tr("autofill.search", "Search"))
    }

    private func open(_ entry: AutofillIndexEntry) {
        guard let session else { return }
        do {
            let secrets = try AutofillUnlock.decryptSecrets(entry, session: session)
            let choices = AutofillTextChoices.choices(for: secrets)
            // One value: no second step.
            if choices.count == 1 {
                onInsert(choices[0].value)
            } else {
                picked = (entry, choices)
            }
        } catch {
            unlockError = AutofillL10n.tr("autofill.decryptFailed", "Could not decrypt item.")
        }
    }

    // MARK: values

    private func choiceList(_ entry: AutofillIndexEntry, _ choices: [AutofillTextChoice]) -> some View {
        List {
            Section(AutofillL10n.tr("autofill.chooseField", "Choose what to insert")) {
                ForEach(choices) { choice in
                    Button {
                        onInsert(choice.value)
                    } label: {
                        VStack(alignment: .leading, spacing: 2) {
                            Text(choice.label)
                                .font(.system(size: 13))
                                .foregroundStyle(.secondary)
                            Text(choice.preview)
                                .font(.system(size: 16, design: choice.isSecret ? .monospaced : .default))
                                .foregroundStyle(.primary)
                                .lineLimit(1)
                        }
                    }
                }
            }
        }
        .toolbar {
            ToolbarItem(placement: .topBarLeading) {
                Button {
                    picked = nil
                } label: {
                    Label(entry.title, systemImage: "chevron.backward")
                }
            }
        }
    }

    static func icon(for kind: AutofillItemKind) -> String {
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
