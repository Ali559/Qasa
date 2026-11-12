import SwiftUI
import SwiftData
import LocalAuthentication

enum VaultClipboard {
    static func copy(_ value: String) {
        UIPasteboard.general.string = value
        UIImpactFeedbackGenerator(style: .light).impactOccurred()
    }
}

/// An editor field. Single-line: a row of the enclosing `FieldGroup`. `multiline`: its own
/// notes card, placed after the group.
struct FormTextField: View {
    let title: String
    @Binding var text: String
    var isSecure: Bool = false
    /// An example of what to type ("MM/YY"), never the title again.
    var placeholder: String? = nil
    var keyboard: UIKeyboardType = .default
    /// The field's rule message: the row turns red and the message is listed under the group.
    var error: String? = nil
    /// Notes / content: a growing card (`Theme.notesMinHeight`) instead of a row.
    var multiline: Bool = false

    var body: some View {
        if multiline {
            NotesFieldCard(label: title, text: $text, hint: placeholder, error: error)
        } else {
            TextFieldRow(
                label: title,
                text: $text,
                hint: placeholder,
                isSecure: isSecure,
                isPlain: isSecure || keyboard == .emailAddress || keyboard == .URL,
                keyboard: keyboard,
                error: error
            )
        }
    }
}

struct CountryPickerField: View {
    @Binding var countryCode: String
    /// When `true`, options show dial codes (e.g. `Iraq (+964)`). Use for phone forms only.
    var showsDialCode: Bool = false
    var error: String? = nil

    var body: some View {
        PickerFieldRow(
            label: L10n.tr("vault.country", locale: AppLanguage.current.locale),
            selection: $countryCode,
            error: error
        ) {
            Country.all.map {
                PickerOption(value: $0.code, label: $0.pickerLabel(showsDialCode: showsDialCode), search: Country.dialCode(for: $0.code))
            }
        }
    }
}

/// The item's collection: its own one-row group, with a button to make a new one.
struct CollectionPickerField: View {
    @Query(filter: VaultCollection.live, sort: \VaultCollection.createdAt) private var storedCollections: [VaultCollection]

    /// Decrypted names, sorted; unreadable ones are skipped.
    private var collections: [VaultRepository.NamedCollection] { VaultRepository.named(storedCollections) }

    @Binding var collectionId: UUID?
    @Environment(\.modelContext) private var modelContext
    @State private var newName = ""
    @State private var showNew = false

    private var locale: Locale { AppLanguage.current.locale }

    var body: some View {
        FieldGroup {
            PickerFieldRow(label: L10n.tr("vault.collection", locale: locale), selection: $collectionId, options: {
                [PickerOption(value: UUID?.none, label: L10n.tr("common.none", locale: locale))]
                    + collections.map { PickerOption(value: Optional($0.id), label: $0.name) }
            }) {
                FieldIconButton(
                    icon: .folderAdd,
                    tint: Theme.accent,
                    accessibilityLabel: L10n.tr("vault.newCollection", locale: locale)
                ) {
                    showNew = true
                }
            }
        }
        .textPromptSheet(
            isPresented: $showNew,
            title: L10n.tr("vault.newCollection", locale: locale),
            placeholder: L10n.tr("common.name", locale: locale),
            text: $newName,
            confirmTitle: L10n.tr("common.create", locale: locale),
            onDismiss: { newName = "" }
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
        VStack(alignment: .leading, spacing: Theme.fieldLabelSpacing) {
            SectionLabel(text: L10n.tr("vault.color", locale: AppLanguage.current.locale))
            HStack(spacing: 12) {
                // Selected: a check in the canvas colour on the swatch and a `textPrimary` ring
                // 3 outside it, so the pick reads in both themes and without colour.
                ForEach(NoteColor.allCases, id: \.self) { c in
                    let selected = color == c
                    Circle()
                        .fill(c.color)
                        .frame(width: 28, height: 28)
                        .overlay {
                            if selected {
                                QasaIconImage(.check, size: 16).foregroundColor(Theme.background)
                            }
                        }
                        .padding(3)
                        .overlay(Circle().stroke(Theme.textPrimary, lineWidth: selected ? 2 : 0))
                        .contentShape(Circle())
                        .onTapGesture { color = c }
                        .accessibilityElement()
                        .accessibilityLabel(c.displayName)
                        .accessibilityAddTraits(selected ? [.isButton, .isSelected] : .isButton)
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
