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

/// A field's rule message (`VaultValidation.message`), shown under the field.
struct InlineFieldError: View {
    let message: String?

    var body: some View {
        if let message {
            Text(message)
                .font(.qasa(11))
                .foregroundColor(Theme.danger)
                .frame(maxWidth: .infinity, alignment: .leading)
                .multilineTextAlignment(.leading)
                .accessibilityLabel(message)
        }
    }
}

struct FormTextField: View {
    let title: String
    @Binding var text: String
    var isSecure: Bool = false
    /// Placeholder text; the title when `nil`.
    var placeholder: String? = nil
    var keyboard: UIKeyboardType = .default
    /// The field's rule message, shown under it in red.
    var error: String? = nil
    /// Notes / content: a growing text area (`Theme.notesMinHeight`) instead of one line.
    var multiline: Bool = false

    var body: some View {
        VStack(alignment: .leading, spacing: Theme.fieldLabelSpacing) {
            FormFieldLabel(text: title)
            Group {
                if multiline {
                    TextField(placeholder ?? title, text: $text, axis: .vertical)
                        .lineLimit(4...12)
                        .keyboardType(keyboard)
                        .multilineTextAlignment(.leading)
                        .padding(.vertical, 6)
                        .qasaNotesField(isError: error != nil)
                } else {
                    Group {
                        if isSecure {
                            SecureField(placeholder ?? title, text: $text)
                        } else {
                            TextField(placeholder ?? title, text: $text)
                        }
                    }
                    .keyboardType(keyboard)
                    .multilineTextAlignment(.leading)
                    .qasaField(isError: error != nil)
                }
            }
            InlineFieldError(message: error)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

/// A labelled menu picker drawn as a field (`Theme.fieldHeight`).
struct FormPickerField<Content: View>: View {
    let title: String
    var error: String? = nil
    @ViewBuilder var content: Content

    var body: some View {
        VStack(alignment: .leading, spacing: Theme.fieldLabelSpacing) {
            FormFieldLabel(text: title)
            content
                .pickerStyle(.menu)
                .labelsHidden()
                .qasaField(isError: error != nil, horizontalPadding: 4)
            InlineFieldError(message: error)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

struct CountryPickerField: View {
    @Binding var countryCode: String
    /// When `true`, options show dial codes (e.g. `Iraq (+964)`). Use for phone forms only.
    var showsDialCode: Bool = false
    var error: String? = nil

    var body: some View {
        FormPickerField(title: L10n.tr("vault.country", locale: AppLanguage.current.locale), error: error) {
            CountryMenuPicker(countryCode: $countryCode, showsDialCode: showsDialCode)
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
        VStack(alignment: .leading, spacing: Theme.fieldLabelSpacing) {
            FormFieldLabel(text: L10n.tr("vault.collection", locale: AppLanguage.current.locale))
            HStack(spacing: 4) {
                Picker(L10n.tr("vault.collection", locale: AppLanguage.current.locale), selection: $collectionId) {
                    Text(L10n.tr("common.none", locale: AppLanguage.current.locale)).tag(UUID?.none)
                    ForEach(collections) { c in
                        Text(c.name).tag(Optional(c.id))
                    }
                }
                .pickerStyle(.menu)
                .labelsHidden()
                Spacer(minLength: 0)
                FieldIconButton(
                    systemImage: "folder.badge.plus",
                    tint: Theme.accent,
                    accessibilityLabel: L10n.tr("vault.newCollection", locale: AppLanguage.current.locale)
                ) {
                    showNew = true
                }
            }
            .qasaField(horizontalPadding: 4)
        }
        .textPromptSheet(
            isPresented: $showNew,
            title: L10n.tr("vault.newCollection", locale: AppLanguage.current.locale),
            placeholder: L10n.tr("common.name", locale: AppLanguage.current.locale),
            text: $newName,
            confirmTitle: L10n.tr("common.create", locale: AppLanguage.current.locale),
            cancelTitle: L10n.tr("common.cancel", locale: AppLanguage.current.locale),
            onCancel: { newName = "" }
        ) {
            let trimmed = newName.trimmingCharacters(in: .whitespacesAndNewlines)
            guard !trimmed.isEmpty else { return }
            guard let c = try? VaultRepository.makeCollection(name: trimmed, context: modelContext) else { return }
            collectionId = c.id
            newName = ""
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
                            Circle().stroke(Theme.textPrimary, lineWidth: color == c ? 2 : 0)
                        )
                        .padding(3)
                        .contentShape(Circle())
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
                VStack(alignment: .leading, spacing: Theme.formSpacing) {
                    content
                }
                .padding(Theme.screenPadding)
            }
            .scrollDismissesKeyboard(.interactively)
            .background(Theme.background.ignoresSafeArea())
            .sheetHeader(title)
            // Save is the page's main action: pinned to the bottom, always in reach.
            .bottomActionBar {
                Button(
                    isEditing
                        ? L10n.tr("common.update", locale: locale)
                        : L10n.tr("common.save", locale: locale),
                    action: onSave
                )
                .buttonStyle(.qasaPrimary)
                .disabled(!canSave)
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
