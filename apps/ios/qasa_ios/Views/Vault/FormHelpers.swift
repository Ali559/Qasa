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
            .foregroundColor(Theme.textPrimary)
            .padding(14)
            .background(Theme.cardBackground)
            .cornerRadius(Theme.cornerRadius)
            .overlay(
                RoundedRectangle(cornerRadius: Theme.cornerRadius)
                    .stroke(Theme.stroke, lineWidth: 1)
            )
        }
    }
}

struct CountryPickerField: View {
    @Binding var countryCode: String
    /// When `true`, options show dial codes (e.g. `Iraq (+964)`). Use for phone forms only.
    var showsDialCode: Bool = false

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            FormFieldLabel(text: "Country")
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
        Picker("Country", selection: $countryCode) {
            ForEach(Country.all) { country in
                Text(country.pickerLabel(showsDialCode: showsDialCode)).tag(country.code)
            }
        }
        .pickerStyle(.menu)
        .labelsHidden()
        .tint(Theme.mint)
    }
}

struct CollectionPickerField: View {
    @Query(sort: \VaultCollection.name) private var collections: [VaultCollection]
    @Binding var collectionId: UUID?
    @Environment(\.modelContext) private var modelContext
    @State private var newName = ""
    @State private var showNew = false

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            FormFieldLabel(text: "Collection")
            HStack {
                Picker("Collection", selection: $collectionId) {
                    Text("None").tag(UUID?.none)
                    ForEach(collections, id: \.id) { c in
                        Text(c.name).tag(Optional(c.id))
                    }
                }
                .tint(Theme.mint)
                Button {
                    showNew = true
                } label: {
                    Image(systemName: "folder.badge.plus")
                        .foregroundColor(Theme.mint)
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
        .alert("New Collection", isPresented: $showNew) {
            TextField("Name", text: $newName)
            Button("Cancel", role: .cancel) { newName = "" }
            Button("Create") {
                let trimmed = newName.trimmingCharacters(in: .whitespacesAndNewlines)
                guard !trimmed.isEmpty else { return }
                let c = VaultCollection(name: trimmed)
                modelContext.insert(c)
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
            FormFieldLabel(text: "Color")
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
                    Button("Cancel", action: onCancel)
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button(isEditing ? "Update" : "Save", action: onSave)
                        .disabled(!canSave)
                        .tint(Theme.mint)
                }
            }
        }
    }
}

func unlockIfNeeded(protected: Bool, completion: @escaping (Bool) -> Void) {
    guard protected else {
        completion(true)
        return
    }
    let context = LAContext()
    var error: NSError?
    if context.canEvaluatePolicy(.deviceOwnerAuthentication, error: &error) {
        context.evaluatePolicy(.deviceOwnerAuthentication, localizedReason: "Unlock protected note") { success, _ in
            DispatchQueue.main.async { completion(success) }
        }
    } else {
        completion(true) // stub fallback when biometrics unavailable
    }
}
