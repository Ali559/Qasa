import SwiftUI
import SwiftData

struct VaultListView: View {
    @Query(sort: \VaultItemRecord.sortKey) private var items: [VaultItemRecord]
    @Query(filter: #Predicate<AppNotification> { !$0.isRead }) private var unreadNotifications: [AppNotification]

    @State private var selectedTab: VaultFilterTab = .all
    @State private var searchText = ""
    @State private var showNotifications = false
    @State private var showAddMenu = false
    @State private var editorMode: EditorPresentation?
    @State private var suggestedProvider = MockSuggestedContextProvider()

    private struct EditorPresentation: Identifiable {
        let id = UUID()
        let mode: VaultItemEditor.Mode
    }

    private var filteredItems: [VaultItemRecord] {
        items.filter { item in
            let kindMatch = selectedTab.kind.map { item.kind == $0 } ?? true
            let searchMatch = searchText.isEmpty
                || item.displayName.localizedCaseInsensitiveContains(searchText)
                || item.payload.subtitle.localizedCaseInsensitiveContains(searchText)
            return kindMatch && searchMatch
        }
    }

    private var suggestedItems: [VaultItemRecord] {
        guard selectedTab == .all else { return [] }
        return VaultSuggestionEngine.suggest(items: filteredItems, context: suggestedProvider.context)
    }

    private var recentItems: [VaultItemRecord] {
        guard selectedTab != .all, filteredItems.count > 10 else { return [] }
        return Array(
            filteredItems.sorted { $0.updatedAt > $1.updatedAt }.prefix(8)
        )
    }

    private var letterSections: [(letter: String, items: [VaultItemRecord])] {
        let grouped = Dictionary(grouping: filteredItems) { $0.letterSection }
        let keys = grouped.keys.sorted { a, b in
            if a == "#" { return false }
            if b == "#" { return true }
            return a < b
        }
        return keys.map { ($0, grouped[$0]!.sorted { $0.sortKey.localizedCaseInsensitiveCompare($1.sortKey) == .orderedAscending }) }
    }

    var body: some View {
        ZStack(alignment: .bottomTrailing) {
            VStack(spacing: 0) {
                header
                ScrollView {
                    VStack(alignment: .leading, spacing: 16) {
                        searchField
                        tabChips

                        if selectedTab == .all, !suggestedItems.isEmpty {
                            sectionHeader(suggestedProvider.context.title)
                            ForEach(suggestedItems, id: \.id) { item in
                                row(for: item)
                            }
                        }

                        if !recentItems.isEmpty {
                            sectionHeader("Most Recent")
                            ForEach(recentItems, id: \.id) { item in
                                row(for: item)
                            }
                        }

                        ForEach(letterSections, id: \.letter) { section in
                            sectionHeader(section.letter)
                            ForEach(section.items, id: \.id) { item in
                                row(for: item)
                            }
                        }

                        if filteredItems.isEmpty {
                            ContentUnavailableView(
                                "No Items",
                                systemImage: "tray",
                                description: Text("Tap + to add your first vault item.")
                            )
                            .frame(maxWidth: .infinity)
                            .padding(.top, 40)
                        }
                    }
                    .padding(.horizontal, Theme.screenPadding)
                    .padding(.top, 24)
                    .padding(.bottom, 100)
                }
            }

            MintFAB(systemImage: "plus") {
                handleAddTapped()
            }
            .padding(.trailing, Theme.screenPadding)
            .padding(.bottom, 24)
        }
        .background(Theme.background.ignoresSafeArea())
        .sheet(isPresented: $showNotifications) {
            NotificationsView()
        }
        .sheet(isPresented: $showAddMenu) {
            AddItemMenuView(tab: selectedTab) { payload in
                showAddMenu = false
                DispatchQueue.main.asyncAfter(deadline: .now() + 0.35) {
                    editorMode = EditorPresentation(mode: .create(payload))
                }
            } onCancel: {
                showAddMenu = false
            }
            .presentationDetents([.medium, .large])
        }
        .sheet(item: $editorMode) { presentation in
            VaultItemEditor(mode: presentation.mode) {
                editorMode = nil
            }
        }
    }

    private var header: some View {
        KasoBrandHeader(
            titleSize: 14,
            trailing: AnyView(
                Button {
                    showNotifications = true
                } label: {
                    ZStack(alignment: .topTrailing) {
                        Image(systemName: "bell.fill")
                            .foregroundColor(Theme.textPrimary)
                            .frame(width: 40, height: 40)
                            .background(Theme.cardBackground)
                            .clipShape(Circle())
                            .overlay(Circle().stroke(Theme.strokeChrome, lineWidth: 1))

                        if !unreadNotifications.isEmpty {
                            Text("\(min(unreadNotifications.count, 9))")
                                .font(.system(size: 10, weight: .bold))
                                .foregroundColor(Theme.mintOnChip)
                                .padding(4)
                                .background(Theme.mint)
                                .clipShape(Circle())
                                .offset(x: 4, y: -4)
                        }
                    }
                }
            )
        )
    }

    private var searchField: some View {
        HStack(spacing: 12) {
            Image(systemName: "magnifyingglass")
                .foregroundColor(Theme.textSecondary)
            TextField("Search items...", text: $searchText)
                .foregroundColor(Theme.textPrimary)
                .tint(Theme.mint)
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 14)
        .background(Theme.cardBackground)
        .cornerRadius(Theme.cornerRadius)
        .overlay(
            RoundedRectangle(cornerRadius: Theme.cornerRadius)
                .stroke(Theme.stroke, lineWidth: 1)
        )
    }

    private var tabChips: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                ForEach(VaultFilterTab.allCases) { tab in
                    FilterChip(title: tab.title, isSelected: selectedTab == tab) {
                        selectedTab = tab
                    }
                }
            }
        }
    }

    private func sectionHeader(_ title: String) -> some View {
        Text(title)
            .font(.system(size: 14, weight: .semibold))
            .foregroundColor(Theme.textSecondary)
            .padding(.top, 8)
    }

    private func row(for item: VaultItemRecord) -> some View {
        VaultItemRow(item: item) {
            editorMode = EditorPresentation(mode: .edit(item))
        } onDeleted: {}
    }

    private func handleAddTapped() {
        switch selectedTab {
        case .login:
            editorMode = EditorPresentation(mode: .create(.login(LoginFields())))
        case .secureNote:
            editorMode = EditorPresentation(mode: .create(.secureNote(SecureNoteFields())))
        default:
            showAddMenu = true
        }
    }
}

#Preview {
    VaultListView()
        .modelContainer(for: VaultSchema.models, inMemory: true)
}
