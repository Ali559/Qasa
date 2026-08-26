import SwiftUI
import SwiftData

struct CollectionsView: View {
    var onOpenMenu: () -> Void

    @Environment(\.modelContext) private var modelContext
    @Query(sort: \VaultCollection.name) private var collections: [VaultCollection]

    @Bindable private var cache = VaultRepository.shared.cache

    @State private var showCreate = false
    @State private var newName = ""
    @State private var editing: VaultCollection?
    @State private var editName = ""
    @State private var deleting: VaultCollection?

    var body: some View {
        ZStack(alignment: .bottomTrailing) {
            VStack(spacing: 0) {
                AppMenuHeader(title: "Collections", onMenu: onOpenMenu)

                ScrollView {
                    LazyVStack(spacing: 12) {
                        if collections.isEmpty {
                            ContentUnavailableView(
                                "No Collections",
                                systemImage: "folder",
                                description: Text("Tap + to create your first collection.")
                            )
                            .padding(.top, 48)
                        } else {
                            ForEach(collections, id: \.id) { collection in
                                NavigationLink {
                                    CollectionDetailView(collection: collection)
                                } label: {
                                    collectionCard(collection)
                                }
                                .buttonStyle(.plain)
                            }
                        }
                    }
                    .padding(Theme.screenPadding)
                    .padding(.bottom, 100)
                }
            }

            MintFAB(systemImage: "plus") {
                newName = ""
                showCreate = true
            }
            .padding(.trailing, Theme.screenPadding)
            .padding(.bottom, 24)
        }
        .background(Theme.background.ignoresSafeArea())
        .toolbar(.hidden, for: .navigationBar)
        .alert("New Collection", isPresented: $showCreate) {
            TextField("Name", text: $newName)
            Button("Cancel", role: .cancel) {}
            Button("Create") { createCollection() }
        }
        .alert("Rename Collection", isPresented: Binding(
            get: { editing != nil },
            set: { if !$0 { editing = nil } }
        )) {
            TextField("Name", text: $editName)
            Button("Cancel", role: .cancel) { editing = nil }
            Button("Save") { saveRename() }
        }
        .alert("Delete Collection?", isPresented: Binding(
            get: { deleting != nil },
            set: { if !$0 { deleting = nil } }
        )) {
            Button("Cancel", role: .cancel) { deleting = nil }
            Button("Delete", role: .destructive) { confirmDelete() }
        } message: {
            Text("Items stay in your vault but will no longer belong to this collection.")
        }
    }

    private func collectionCard(_ collection: VaultCollection) -> some View {
        let count = cache.rows.filter { $0.collectionId == collection.id }.count
        return HStack(spacing: 14) {
            Image(systemName: "folder.fill")
                .font(.system(size: 18))
                .foregroundColor(Theme.mint)
                .frame(width: 48, height: 48)
                .background(Theme.chrome)
                .cornerRadius(10)
                .overlay(
                    RoundedRectangle(cornerRadius: 10)
                        .stroke(Theme.strokeChrome, lineWidth: 1)
                )

            VStack(alignment: .leading, spacing: 4) {
                Text(collection.name)
                    .font(.system(size: 16, weight: .semibold))
                    .foregroundColor(Theme.textPrimary)
                Text("\(count) item\(count == 1 ? "" : "s")")
                    .font(.system(size: 13))
                    .foregroundColor(Theme.textSecondary)
            }

            Spacer()

            Menu {
                Button {
                    editing = collection
                    editName = collection.name
                } label: {
                    Label("Edit", systemImage: "pencil")
                }
                Button(role: .destructive) {
                    deleting = collection
                } label: {
                    Label("Delete", systemImage: "trash")
                }
            } label: {
                Image(systemName: "ellipsis")
                    .rotationEffect(.degrees(90))
                    .foregroundColor(Theme.textSecondary)
                    .frame(width: 36, height: 36)
            }
        }
        .padding(16)
        .background(Theme.cardBackground)
        .cornerRadius(Theme.cornerRadius)
        .overlay(
            RoundedRectangle(cornerRadius: Theme.cornerRadius)
                .stroke(Theme.stroke, lineWidth: 1)
        )
    }

    private func createCollection() {
        let trimmed = newName.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return }
        modelContext.insert(VaultCollection(name: trimmed))
        try? modelContext.save()
        newName = ""
    }

    private func saveRename() {
        guard let editing else { return }
        let trimmed = editName.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return }
        editing.name = trimmed
        try? modelContext.save()
        self.editing = nil
    }

    private func confirmDelete() {
        guard let deleting else { return }
        for row in cache.rows where row.collectionId == deleting.id {
            if var payload = try? VaultRepository.shared.payload(for: row.id) {
                switch payload {
                case .login(var f):
                    f.collectionId = nil
                    payload = .login(f)
                case .secureNote(var f):
                    f.collectionId = nil
                    payload = .secureNote(f)
                default:
                    break
                }
                _ = try? VaultRepository.shared.save(payload: payload, id: row.id)
            }
        }
        modelContext.delete(deleting)
        try? modelContext.save()
        self.deleting = nil
    }
}

struct CollectionDetailView: View {
    let collection: VaultCollection

    @Bindable private var cache = VaultRepository.shared.cache
    @State private var editorMode: EditorPresentation?

    private struct EditorPresentation: Identifiable {
        let id = UUID()
        let mode: VaultItemEditor.Mode
    }

    private var collectionItems: [VaultListRow] {
        cache.rows
            .filter { $0.collectionId == collection.id }
            .sorted { $0.displayName.localizedCaseInsensitiveCompare($1.displayName) == .orderedAscending }
    }

    var body: some View {
        ScrollView {
            LazyVStack(spacing: 12) {
                if collectionItems.isEmpty {
                    ContentUnavailableView(
                        "Empty Collection",
                        systemImage: "tray",
                        description: Text("Assign items to “\(collection.name)” from the item editor.")
                    )
                    .padding(.top, 40)
                } else {
                    ForEach(collectionItems) { row in
                        NavigationLink {
                            VaultItemDetailView(itemId: row.id) {
                                editorMode = EditorPresentation(mode: .edit(row.id))
                            } onDeleted: {}
                        } label: {
                            VaultItemRow(row: row) {
                                editorMode = EditorPresentation(mode: .edit(row.id))
                            } onDeleted: {}
                        }
                        .buttonStyle(.plain)
                    }
                }
            }
            .padding(Theme.screenPadding)
            .padding(.bottom, 40)
        }
        .background(Theme.background.ignoresSafeArea())
        .navigationTitle(collection.name)
        .navigationBarTitleDisplayMode(.inline)
        .toolbar(.visible, for: .navigationBar)
        .sheet(item: $editorMode) { presentation in
            VaultItemEditor(mode: presentation.mode) {
                editorMode = nil
            }
        }
    }
}
