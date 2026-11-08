import SwiftUI
import SwiftData

struct CollectionsView: View {
    var onOpenMenu: (() -> Void)? = nil

    @Environment(\.modelContext) private var modelContext
    @Query(filter: VaultCollection.live, sort: \VaultCollection.name) private var collections: [VaultCollection]

    @Bindable private var cache = VaultRepository.shared.cache

    @State private var showCreate = false
    @State private var newName = ""
    @State private var editing: VaultCollection?
    @State private var editName = ""
    @State private var deleting: VaultCollection?

    var body: some View {
        ZStack(alignment: .bottomTrailing) {
            VStack(spacing: 0) {
                AppMenuHeader(title: L10n.tr("nav.collections", locale: AppPreferences.shared.locale), onMenu: onOpenMenu)

                ScrollView {
                    LazyVStack(spacing: 12) {
                        if collections.isEmpty {
                            ContentUnavailableView(
                                L10n.tr("collections.noneTitle"),
                                systemImage: "folder",
                                description: Text(L10n.tr("collections.empty"))
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
        .alert(L10n.tr("collections.createTitle"), isPresented: $showCreate) {
            TextField(L10n.tr("common.name"), text: $newName)
            Button(L10n.tr("common.cancel"), role: .cancel) {}
            Button(L10n.tr("common.create")) { createCollection() }
        }
        .alert(L10n.tr("collections.rename"), isPresented: Binding(
            get: { editing != nil },
            set: { if !$0 { editing = nil } }
        )) {
            TextField(L10n.tr("common.name"), text: $editName)
            Button(L10n.tr("common.cancel"), role: .cancel) { editing = nil }
            Button(L10n.tr("common.save")) { saveRename() }
        }
        .alert(L10n.tr("collections.deleteTitle"), isPresented: Binding(
            get: { deleting != nil },
            set: { if !$0 { deleting = nil } }
        )) {
            Button(L10n.tr("common.cancel"), role: .cancel) { deleting = nil }
            Button(L10n.tr("common.delete"), role: .destructive) { confirmDelete() }
        } message: {
            Text(L10n.tr("collections.deleteMessage"))
        }
    }

    private func collectionCard(_ collection: VaultCollection) -> some View {
        let count = cache.rows.filter { $0.collectionId == collection.id }.count
        return HStack(spacing: 14) {
            Image(systemName: "folder.fill")
                .font(.qasa(18))
                .foregroundColor(Theme.accent)
                .frame(width: 48, height: 48)
                .background(Theme.chrome)
                .cornerRadius(10)
                .overlay(
                    RoundedRectangle(cornerRadius: 10)
                        .stroke(Theme.strokeChrome, lineWidth: 1)
                )

            VStack(alignment: .leading, spacing: 4) {
                Text(collection.name)
                    .font(.qasa(16, weight: .semibold))
                    .foregroundColor(Theme.textPrimary)
                Text(String(
                    format: L10n.tr(count == 1 ? "collections.itemCountOne" : "collections.itemCount"),
                    locale: AppLanguage.current.locale,
                    arguments: [count]
                ))
                    .font(.qasa(13))
                    .foregroundColor(Theme.textSecondary)
            }

            Spacer()

            Menu {
                Button {
                    editing = collection
                    editName = collection.name
                } label: {
                    Label(L10n.tr("common.edit"), systemImage: "pencil")
                }
                Button(role: .destructive) {
                    deleting = collection
                } label: {
                    Label(L10n.tr("common.delete"), systemImage: "trash")
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
        editing.updatedAt = Date()
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
        VaultRepository.deleteCollection(deleting, context: modelContext)
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
                        L10n.tr("collections.emptyCollectionTitle"),
                        systemImage: "tray",
                        description: Text(String(
                            format: L10n.tr("collections.assignHint"),
                            collection.name
                        ))
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
            .appLocalization()
        }
    }
}
