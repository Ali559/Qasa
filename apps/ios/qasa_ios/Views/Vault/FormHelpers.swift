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

    private var value: String {
        Country.all.first { $0.code == countryCode }?.pickerLabel(showsDialCode: showsDialCode)
            ?? L10n.tr("common.none", locale: AppLanguage.current.locale)
    }

    var body: some View {
        PickerFieldRow(label: L10n.tr("vault.country", locale: AppLanguage.current.locale), value: value, error: error) {
            CountryMenuPicker(countryCode: $countryCode, showsDialCode: showsDialCode)
        }
    }
}

/// The country choices, with or without dial codes, for a `PickerFieldRow` menu.
struct CountryMenuPicker: View {
    @Binding var countryCode: String
    var showsDialCode: Bool = false

    var body: some View {
        Picker(L10n.tr("vault.country", locale: AppLanguage.current.locale), selection: $countryCode) {
            ForEach(Country.all) { country in
                Text(country.pickerLabel(showsDialCode: showsDialCode)).tag(country.code)
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

    private var value: String {
        collections.first { $0.id == collectionId }?.name ?? L10n.tr("common.none", locale: locale)
    }

    var body: some View {
        FieldGroup {
            PickerFieldRow(label: L10n.tr("vault.collection", locale: locale), value: value) {
                Picker(L10n.tr("vault.collection", locale: locale), selection: $collectionId) {
                    Text(L10n.tr("common.none", locale: locale)).tag(UUID?.none)
                    ForEach(collections) { c in
                        Text(c.name).tag(Optional(c.id))
                    }
                }
            } extra: {
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
            cancelTitle: L10n.tr("common.cancel", locale: locale),
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
        VStack(alignment: .leading, spacing: Theme.fieldLabelSpacing) {
            SectionLabel(text: L10n.tr("vault.color", locale: AppLanguage.current.locale))
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
