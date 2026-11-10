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
        .confirmSheet(
            isPresented: $showConfirm,
            title: L10n.tr("breach.rotateConfirmTitle"),
            message: selected.map { String(format: L10n.tr("breach.rotateConfirmMessage"), $0.name) },
            icon: "key",
            confirmTitle: L10n.tr("breach.rotateReplace"),
            cancelTitle: L10n.tr("common.cancel"),
            destructive: true
        ) {
            save()
        }
        .onAppear(perform: load)
    }

    // MARK: - States

    private var emptyState: some View {
        VStack(alignment: .leading, spacing: 20) {
            Text(L10n.tr("breach.rotateNoLogins"))
                .font(.qasa(15))
                .foregroundColor(Theme.textSecondary)
            Spacer(minLength: 0)
        }
        .padding(.horizontal, Theme.screenPadding)
        .padding(.top, 24)
        .frame(maxWidth: .infinity, alignment: .leading)
        .bottomActionBar {
            PrimaryButton(title: L10n.tr("common.close")) { dismiss() }
        }
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
            }
            .padding(.horizontal, Theme.screenPadding)
            .padding(.top, 20)
            .padding(.bottom, 24)
        }
        .bottomActionBar {
            PrimaryButton(title: L10n.tr("breach.rotateSave"), icon: "key.fill") {
                showConfirm = true
            }
            .disabled(selected == nil || isSaving)
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
        .padding(.horizontal, 14)
        .padding(.vertical, 10)
        .frame(maxWidth: .infinity, minHeight: Theme.rowMinHeight, alignment: .leading)
        .background(isSelected ? Theme.accent.opacity(0.12) : Theme.cardBackground)
        .clipShape(RoundedRectangle(cornerRadius: Theme.cornerRadius, style: .continuous))
        .overlay(
            RoundedRectangle(cornerRadius: Theme.cornerRadius, style: .continuous)
                .stroke(isSelected ? Theme.accent : Theme.stroke, lineWidth: 1)
        )

        return Group {
            if selectable {
                Button { selectedId = login.id } label: { row }
                    .buttonStyle(PressableStyle())
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
            QasaButtonLabel(title: title, icon: icon)
        }
        .buttonStyle(.qasaSecondary)
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
            guard case .login = try VaultRepository.shared.payload(for: selected.id) else {
                throw VaultCryptoError.itemNotFound
            }
            try VaultRepository.shared.rotatePassword(itemId: selected.id, to: generator.password)
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
