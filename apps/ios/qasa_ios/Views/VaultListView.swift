import SwiftUI
import SwiftData

struct VaultListView: View {
    var onOpenMenu: () -> Void

    init(onOpenMenu: @escaping () -> Void = {}) {
        self.onOpenMenu = onOpenMenu
    }

    @Environment(AppPreferences.self) private var prefs
    @Bindable private var cache = VaultRepository.shared.cache

    @State private var selectedTab: VaultFilterTab = .all
    @State private var searchText = ""
    @State private var showAddMenu = false
    @State private var editorMode: EditorPresentation?
    @State private var suggestedProvider = MockSuggestedContextProvider()

    private struct EditorPresentation: Identifiable {
        let id = UUID()
        let mode: VaultItemEditor.Mode
    }

    private var filteredRows: [VaultListRow] {
        cache.filter(kind: selectedTab.kind, query: searchText)
    }

    private var suggestedRows: [VaultListRow] {
        guard selectedTab == .all else { return [] }
        return VaultSuggestionEngine.suggest(rows: filteredRows, context: suggestedProvider.context)
    }

    private var recentRows: [VaultListRow] {
        guard selectedTab != .all, filteredRows.count > 10 else { return [] }
        return Array(filteredRows.sorted { $0.updatedAt > $1.updatedAt }.prefix(8))
    }

    private var letterSections: [(letter: String, items: [VaultListRow])] {
        let grouped = Dictionary(grouping: filteredRows) { $0.letterSection }
        let keys = grouped.keys.sorted { a, b in
            if a == "#" { return false }
            if b == "#" { return true }
            return a < b
        }
        return keys.map { ($0, grouped[$0]!.sorted { $0.displayName.localizedCaseInsensitiveCompare($1.displayName) == .orderedAscending }) }
    }

    var body: some View {
        ZStack(alignment: .bottomTrailing) {
            VStack(spacing: 0) {
                BrandMenuHeader(onMenu: onOpenMenu, titleSize: 20)

                ScrollView {
                    VStack(alignment: .leading, spacing: 16) {
                        searchField
                        tabChips

                        if selectedTab == .all, !suggestedRows.isEmpty {
                            sectionHeader(suggestedProvider.context.title)
                            ForEach(suggestedRows) { row in
                                rowLink(for: row)
                            }
                        }

                        if !recentRows.isEmpty {
                            sectionHeader(L10n.tr("vault.mostRecent", locale: prefs.locale))
                            ForEach(recentRows) { row in
                                rowLink(for: row)
                            }
                        }

                        ForEach(letterSections, id: \.letter) { section in
                            sectionHeader(section.letter)
                            ForEach(section.items) { row in
                                rowLink(for: row)
                            }
                        }

                        if filteredRows.isEmpty {
                            ContentUnavailableView(
                                L10n.tr("vault.emptyTitle", locale: prefs.locale),
                                systemImage: "tray",
                                description: Text(L10n.tr("vault.emptyHint", locale: prefs.locale))
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
        .toolbar(.hidden, for: .navigationBar)
        .sheet(isPresented: $showAddMenu) {
            AddItemMenuView(tab: selectedTab) { payload in
                showAddMenu = false
                DispatchQueue.main.asyncAfter(deadline: .now() + 0.35) {
                    editorMode = EditorPresentation(mode: .create(payload))
                }
            } onCancel: {
                showAddMenu = false
            }
            .appLocalization()
            .presentationDetents([.medium, .large])
        }
        .sheet(item: $editorMode) { presentation in
            VaultItemEditor(mode: presentation.mode) {
                editorMode = nil
            }
            .appLocalization()
        }
    }

    private var searchField: some View {
        HStack(spacing: 12) {
            Image(systemName: "magnifyingglass")
                .foregroundColor(Theme.textSecondary)
            TextField(L10n.tr("vault.searchPlaceholder", locale: prefs.locale), text: $searchText)
                .font(.qasa(16))
                .multilineTextAlignment(.leading)
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
                    FilterChip(
                        title: L10n.tr(tab.localizationKey, locale: prefs.locale),
                        isSelected: selectedTab == tab
                    ) {
                        selectedTab = tab
                    }
                }
            }
        }
    }

    private func sectionHeader(_ title: String) -> some View {
        Text(title)
            .font(.qasa(14, weight: .semibold))
            .foregroundColor(Theme.textSecondary)
            .padding(.top, 8)
            .frame(maxWidth: .infinity, alignment: .leading)
    }

    private func rowLink(for row: VaultListRow) -> some View {
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
    NavigationStack {
        VaultListView()
    }
    .modelContainer(for: VaultSchema.models, inMemory: true)
}
