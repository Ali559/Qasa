import SwiftUI
import SwiftData
import LocalAuthentication

enum VaultClipboard {
    static func copy(_ value: String) {
        UIPasteboard.general.string = value
        UIImpactFeedbackGenerator(style: .light).impactOccurred()
    }
}

struct FormFieldLabel: View {
    let text: String
    var body: some View {
        SectionLabel(text: text)
    }
}

struct FormTextField: View {
    let title: String
    @Binding var text: String
    var isSecure: Bool = false

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            FormFieldLabel(text: title)
            Group {
                if isSecure {
                    SecureField(title, text: $text)
                } else {
                    TextField(title, text: $text)
                }
            }
            .font(.qasa(16))
            .multilineTextAlignment(.leading)
            .foregroundColor(Theme.textPrimary)
            .padding(14)
            .background(Theme.cardBackground)
            .cornerRadius(Theme.cornerRadius)
            .overlay(
                RoundedRectangle(cornerRadius: Theme.cornerRadius)
                    .stroke(Theme.stroke, lineWidth: 1)
            )
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

struct CountryPickerField: View {
    @Binding var countryCode: String
    /// When `true`, options show dial codes (e.g. `Iraq (+964)`). Use for phone forms only.
    var showsDialCode: Bool = false

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            FormFieldLabel(text: L10n.tr("vault.country", locale: AppLanguage.current.locale))
            CountryMenuPicker(countryCode: $countryCode, showsDialCode: showsDialCode)
                .padding(12)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(Theme.cardBackground)
                .cornerRadius(Theme.cornerRadius)
                .overlay(
                    RoundedRectangle(cornerRadius: Theme.cornerRadius)
                        .stroke(Theme.stroke, lineWidth: 1)
                )
        }
    }
}

/// Reusable country menu — with or without dial codes.
struct CountryMenuPicker: View {
    @Binding var countryCode: String
    var showsDialCode: Bool = false

    var body: some View {
        Picker(L10n.tr("vault.country", locale: AppLanguage.current.locale), selection: $countryCode) {
            ForEach(Country.all) { country in
                Text(country.pickerLabel(showsDialCode: showsDialCode)).tag(country.code)
            }
        }
        .pickerStyle(.menu)
        .labelsHidden()
        .tint(Theme.accent)
    }
}

struct CollectionPickerField: View {
    @Query(filter: VaultCollection.live, sort: \VaultCollection.createdAt) private var storedCollections: [VaultCollection]

    /// Decrypted names, sorted; unreadable ones are skipped.
    private var collections: [VaultRepository.NamedCollection] { VaultRepository.named(storedCollections) }

    @Binding var collectionId: UUID?
    @Environment(\.modelContext) private var modelContext
    @State private var newName = ""
    @State private var showNew = false

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            FormFieldLabel(text: L10n.tr("vault.collection", locale: AppLanguage.current.locale))
            HStack {
                Picker(L10n.tr("vault.collection", locale: AppLanguage.current.locale), selection: $collectionId) {
                    Text(L10n.tr("common.none", locale: AppLanguage.current.locale)).tag(UUID?.none)
                    ForEach(collections) { c in
                        Text(c.name).tag(Optional(c.id))
                    }
                }
                .tint(Theme.accent)
                Button {
                    showNew = true
                } label: {
                    Image(systemName: "folder.badge.plus")
                        .foregroundColor(Theme.accent)
                }
            }
            .padding(12)
            .background(Theme.cardBackground)
            .cornerRadius(Theme.cornerRadius)
            .overlay(
                RoundedRectangle(cornerRadius: Theme.cornerRadius)
                    .stroke(Theme.stroke, lineWidth: 1)
            )
        }
        .alert(L10n.tr("vault.newCollection", locale: AppLanguage.current.locale), isPresented: $showNew) {
            TextField(L10n.tr("common.name", locale: AppLanguage.current.locale), text: $newName)
            Button(L10n.tr("common.cancel", locale: AppLanguage.current.locale), role: .cancel) { newName = "" }
            Button(L10n.tr("common.create", locale: AppLanguage.current.locale)) {
                let trimmed = newName.trimmingCharacters(in: .whitespacesAndNewlines)
                guard !trimmed.isEmpty else { return }
                guard let c = try? VaultRepository.makeCollection(name: trimmed, context: modelContext) else { return }
                collectionId = c.id
                newName = ""
            }
        }
    }
}

struct NoteColorPicker: View {
    @Binding var color: NoteColor

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            FormFieldLabel(text: L10n.tr("vault.color", locale: AppLanguage.current.locale))
            HStack(spacing: 12) {
                ForEach(NoteColor.allCases, id: \.self) { c in
                    Circle()
                        .fill(c.color)
                        .frame(width: 28, height: 28)
                        .overlay(
                            Circle().stroke(Color.white, lineWidth: color == c ? 2 : 0)
                        )
                        .onTapGesture { color = c }
                }
            }
        }
    }
}

struct VaultFormChrome<Content: View>: View {
    let title: String
    let canSave: Bool
    let isEditing: Bool
    let onCancel: () -> Void
    let onSave: () -> Void
    @ViewBuilder var content: Content

    private var locale: Locale { AppLanguage.current.locale }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    content
                }
                .padding(Theme.screenPadding)
            }
            .background(Theme.background.ignoresSafeArea())
            .navigationTitle(title)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button(L10n.tr("common.cancel", locale: locale), action: onCancel)
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button(
                        isEditing
                            ? L10n.tr("common.update", locale: locale)
                            : L10n.tr("common.save", locale: locale),
                        action: onSave
                    )
                    .disabled(!canSave)
                    .tint(Theme.accent)
                }
            }
        }
        .appLocalization()
    }
}

/// Gates a `protectItem` note behind device-owner authentication.
///
/// Fails **closed**: previously this called `completion(true)` whenever
/// `canEvaluatePolicy` failed (no enrolled biometrics, simulator, `errSecNotAvailable`),
/// which opened protected notes with no authentication at all. A protected note must
/// never be more accessible because the hardware cannot do face ID.
func unlockIfNeeded(protected: Bool, completion: @escaping (Bool) -> Void) {
    guard protected else {
        completion(true)
        return
    }
    let context = LAContext()
    var error: NSError?
    guard context.canEvaluatePolicy(.deviceOwnerAuthentication, error: &error) else {
        completion(false)
        return
    }
    context.evaluatePolicy(
        .deviceOwnerAuthentication,
        localizedReason: L10n.tr("vault.unlockProtectedNote")
    ) { success, _ in
        DispatchQueue.main.async { completion(success) }
    }
}
