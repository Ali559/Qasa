import SwiftUI
import SwiftData

struct CollectionsView: View {
    var onOpenMenu: (() -> Void)? = nil

    @Environment(\.modelContext) private var modelContext
    @Query(filter: VaultCollection.live, sort: \VaultCollection.createdAt) private var storedCollections: [VaultCollection]

    /// Decrypted names, sorted; unreadable ones are skipped.
    private var collections: [VaultRepository.NamedCollection] { VaultRepository.named(storedCollections) }


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
                                icon: .folder,
                                description: Text(L10n.tr("collections.empty"))
                            )
                            .padding(.top, 48)
                        } else {
                            ForEach(collections) { entry in
                                NavigationLink {
                                    CollectionDetailView(collection: entry.collection)
                                } label: {
                                    collectionCard(entry.collection, name: entry.name)
                                }
                                .buttonStyle(.plain)
                            }
                        }
                    }
                    .padding(Theme.screenPadding)
                    .padding(.bottom, 100)
                }
            }

            MintFAB(icon: .add) {
                newName = ""
                showCreate = true
            }
            .padding(.trailing, Theme.screenPadding)
            .padding(.bottom, 24)
        }
        .background(Theme.background.ignoresSafeArea())
        .toolbar(.hidden, for: .navigationBar)
        .textPromptSheet(
            isPresented: $showCreate,
            title: L10n.tr("collections.createTitle"),
            placeholder: L10n.tr("common.name"),
            text: $newName,
            confirmTitle: L10n.tr("common.create")
        ) {
            createCollection()
        }
        // Dismissal clears the target on the next turn, so the confirm action still sees it.
        .textPromptSheet(
            isPresented: Binding(
                get: { editing != nil },
                set: { if !$0 { DispatchQueue.main.async { editing = nil } } }
            ),
            title: L10n.tr("collections.rename"),
            placeholder: L10n.tr("common.name"),
            text: $editName,
            confirmTitle: L10n.tr("common.save")
        ) {
            saveRename()
        }
        .confirmSheet(
            isPresented: Binding(
                get: { deleting != nil },
                set: { if !$0 { DispatchQueue.main.async { deleting = nil } } }
            ),
            title: L10n.tr("collections.deleteTitle"),
            message: L10n.tr("collections.deleteMessage"),
            icon: .delete,
            confirmTitle: L10n.tr("common.delete"),
            cancelTitle: L10n.tr("common.cancel"),
            destructive: true
        ) {
            confirmDelete()
        }
    }

    private func collectionCard(_ collection: VaultCollection, name: String) -> some View {
        let count = cache.rows.filter { $0.collectionId == collection.id }.count
        return HStack(spacing: 14) {
            IconBadge(icon: .folder)

            VStack(alignment: .leading, spacing: 4) {
                Text(name)
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
                    editName = name
                } label: {
                    Label { Text(L10n.tr("common.edit")) } icon: { QasaIconImage(.edit, size: 17) }
                }
                Button(role: .destructive) {
                    deleting = collection
                } label: {
                    Label { Text(L10n.tr("common.delete")) } icon: { QasaIconImage(.delete, size: 17) }
                }
            } label: {
                QasaIconImage(.moreVertical, size: 16)
                    .foregroundColor(Theme.textSecondary)
                    .frame(width: 36, height: 36)
            }
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 10)
        .frame(minHeight: Theme.rowMinHeight)
        .background(Theme.cardBackground)
        .clipShape(RoundedRectangle(cornerRadius: Theme.cornerRadius, style: .continuous))
        .overlay(
            RoundedRectangle(cornerRadius: Theme.cornerRadius, style: .continuous)
                .stroke(Theme.stroke, lineWidth: 1)
        )
    }

    private func createCollection() {
        let trimmed = newName.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return }
        guard (try? VaultRepository.makeCollection(name: trimmed, context: modelContext)) != nil else { return }
        try? modelContext.save()
        newName = ""
    }

    private func saveRename() {
        guard let editing else { return }
        let trimmed = editName.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return }
        try? VaultRepository.renameCollection(editing, to: trimmed, context: modelContext)
        self.editing = nil
    }

    private func confirmDelete() {
        guard let deleting else { return }
        for row in cache.rows where row.collectionId == deleting.id {
            try? VaultRepository.shared.removeFromCollection(itemId: row.id)
        }
        VaultRepository.deleteCollection(deleting, context: modelContext)
        self.deleting = nil
    }
}

struct CollectionDetailView: View {
    let collection: VaultCollection

    @Bindable private var cache = VaultRepository.shared.cache
    @State private var editorMode: EditorPresentation?

    private var name: String { VaultRepository.collectionName(collection) ?? "" }

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
                        icon: .inbox,
                        description: Text(String(
                            format: L10n.tr("collections.assignHint"),
                            name
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
        .navigationTitle(name)
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
