import SwiftData
import SwiftUI

/// Breach card "Rotate Password": pick the vault login that uses the breached email,
/// generate a fresh password, and replace the saved one only after explicit confirmation.
struct RotatePasswordSheet: View {
    let email: String
    var onSaved: (String) -> Void

    @Environment(\.dismiss) private var dismiss
    @Environment(\.modelContext) private var modelContext

    @StateObject private var generator = PasswordGeneratorModel()
    @State private var logins: [RotateLoginCandidate] = []
    @State private var selectedId: UUID?
    @State private var showConfirm = false
    @State private var errorMessage: String?
    @State private var isSaving = false

    private var selected: RotateLoginCandidate? {
        logins.first { $0.id == selectedId }
    }

    var body: some View {
        NavigationStack {
            Group {
                if logins.isEmpty {
                    emptyState
                } else {
                    content
                        .toolbar {
                            ToolbarItem(placement: .cancellationAction) {
                                Button(L10n.tr("common.cancel")) { dismiss() }
                            }
                        }
                }
            }
            .background(Theme.background.ignoresSafeArea())
            .navigationTitle(L10n.tr("breach.rotateTitle"))
            .navigationBarTitleDisplayMode(.inline)
        }
        .alert(L10n.tr("breach.rotateConfirmTitle"), isPresented: $showConfirm) {
            Button(L10n.tr("common.cancel"), role: .cancel) {}
            Button(L10n.tr("breach.rotateReplace"), role: .destructive) { save() }
        } message: {
            if let selected {
                Text(String(format: L10n.tr("breach.rotateConfirmMessage"), selected.name))
            }
        }
        .onAppear(perform: load)
    }

    // MARK: - States

    private var emptyState: some View {
        VStack(alignment: .leading, spacing: 20) {
            Text(L10n.tr("breach.rotateNoLogins"))
                .font(.qasa(15))
                .foregroundColor(Theme.textSecondary)
            PrimaryButton(title: L10n.tr("common.close"), cornerRadius: 8) { dismiss() }
            Spacer(minLength: 0)
        }
        .padding(.horizontal, Theme.screenPadding)
        .padding(.top, 24)
    }

    private var content: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 20) {
                if logins.count > 1 {
                    loginPicker
                } else if let only = logins.first {
                    loginRow(only, selectable: false)
                }
                passwordCard
                Text(L10n.tr("breach.rotateHint"))
                    .font(.qasa(13))
                    .foregroundColor(Theme.textSecondary)
                if let errorMessage {
                    Text(errorMessage)
                        .font(.qasa(13))
                        .foregroundColor(Theme.danger)
                }
                PrimaryButton(title: L10n.tr("breach.rotateSave"), icon: "key.fill", cornerRadius: 8) {
                    showConfirm = true
                }
                .opacity(selected == nil || isSaving ? 0.5 : 1)
                .disabled(selected == nil || isSaving)
            }
            .padding(.horizontal, Theme.screenPadding)
            .padding(.top, 20)
            .padding(.bottom, 32)
        }
    }

    private var loginPicker: some View {
        VStack(alignment: .leading, spacing: 10) {
            SectionLabel(text: L10n.tr("breach.rotatePickLogin"))
            ForEach(logins) { login in
                loginRow(login, selectable: true)
            }
        }
    }

    private func loginRow(_ login: RotateLoginCandidate, selectable: Bool) -> some View {
        let isSelected = selectable && login.id == selectedId
        let row = HStack(spacing: 12) {
            Image(systemName: "person.badge.key")
                .foregroundColor(isSelected ? Theme.accent : Theme.textSecondary)
            VStack(alignment: .leading, spacing: 3) {
                Text(login.name)
                    .font(.qasa(15, weight: .semibold))
                    .foregroundColor(Theme.textPrimary)
                if !login.subtitle.isEmpty {
                    Text(login.subtitle)
                        .font(.qasa(13))
                        .foregroundColor(Theme.textSecondary)
                }
            }
            Spacer(minLength: 0)
            if isSelected {
                Image(systemName: "checkmark.circle.fill")
                    .foregroundColor(Theme.accent)
            }
        }
        .padding(14)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(isSelected ? Theme.accent.opacity(0.12) : Theme.cardBackground)
        .cornerRadius(Theme.cornerRadius)
        .overlay(
            RoundedRectangle(cornerRadius: Theme.cornerRadius)
                .stroke(isSelected ? Theme.accent : Theme.stroke, lineWidth: 1)
        )

        return Group {
            if selectable {
                Button { selectedId = login.id } label: { row }
                    .buttonStyle(.plain)
            } else {
                row
            }
        }
    }

    private var passwordCard: some View {
        CardContainer(padding: 18) {
            VStack(alignment: .leading, spacing: 14) {
                SectionLabel(text: L10n.tr("breach.rotateNewPassword"))
                Text(generator.password)
                    .font(.kasoMono(20, weight: .bold))
                    .foregroundColor(Theme.textPrimary)
                    .lineLimit(1)
                    .minimumScaleFactor(0.4)
                    .textSelection(.enabled)
                    .frame(maxWidth: .infinity, alignment: .leading)
                HStack(spacing: 10) {
                    actionButton(
                        title: L10n.tr("gen.regenerate"),
                        icon: "arrow.clockwise"
                    ) { generator.regenerate() }
                    actionButton(
                        title: generator.copied ? L10n.tr("gen.copied") : L10n.tr("gen.copy"),
                        icon: generator.copied ? "checkmark" : "doc.on.doc"
                    ) { generator.copyPassword() }
                }
            }
        }
    }

    private func actionButton(title: String, icon: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            HStack(spacing: 6) {
                Image(systemName: icon)
                Text(title)
            }
            .font(.qasa(14))
            .frame(maxWidth: .infinity)
            .padding(.vertical, 11)
            .background(Theme.accentDark.opacity(0.2))
            .foregroundColor(Theme.accent)
            .cornerRadius(8)
            .overlay(
                RoundedRectangle(cornerRadius: 8)
                    .stroke(Theme.accent.opacity(0.3), lineWidth: 1)
            )
        }
        .buttonStyle(.plain)
    }

    // MARK: - Data

    private func load() {
        VaultRepository.shared.ensureConfigured(context: modelContext)
        let target = Self.normalize(email)
        let matches = ((try? VaultRepository.shared.allLoginPayloads()) ?? [])
            .filter { _, fields in
                Self.normalize(fields.email) == target || Self.normalize(fields.username) == target
            }
            .map { id, fields in
                RotateLoginCandidate(
                    id: id,
                    name: fields.itemName.isEmpty ? (fields.email.isEmpty ? fields.username : fields.email) : fields.itemName,
                    subtitle: fields.email.isEmpty ? fields.username : fields.email
                )
            }
            .sorted { $0.name.localizedCaseInsensitiveCompare($1.name) == .orderedAscending }
        logins = matches
        selectedId = matches.count == 1 ? matches[0].id : nil
        generator.regenerate()
    }

    private func save() {
        guard let selected, !generator.password.isEmpty else { return }
        isSaving = true
        defer { isSaving = false }
        do {
            guard case .login(var fields) = try VaultRepository.shared.payload(for: selected.id) else {
                throw VaultCryptoError.itemNotFound
            }
            fields.password = generator.password
            _ = try VaultRepository.shared.save(payload: .login(fields), id: selected.id)
            errorMessage = nil
            onSaved(selected.name)
            dismiss()
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    private static func normalize(_ value: String) -> String {
        value.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
    }
}

struct RotateLoginCandidate: Identifiable, Equatable {
    let id: UUID
    let name: String
    let subtitle: String
}
