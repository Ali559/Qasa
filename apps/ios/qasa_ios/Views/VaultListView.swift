import SwiftUI
import SwiftData

struct VaultListView: View {
    var onOpenMenu: (() -> Void)? = nil
    /// A new login handed over (a password from the Generator, or one AutoFill generated
    /// and the user chose to save): opens the new-login editor with it prefilled, then clears.
    @Binding var pendingLogin: LoginFields?

    init(onOpenMenu: (() -> Void)? = nil, pendingLogin: Binding<LoginFields?> = .constant(nil)) {
        self.onOpenMenu = onOpenMenu
        self._pendingLogin = pendingLogin
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

    /// Suggested, most recent, then every letter; each one card (design/README.md rule 6e).
    private struct ListSection: Identifiable {
        let id: String
        let title: String
        let rows: [VaultListRow]
    }

    private var sections: [ListSection] {
        var out: [ListSection] = []
        if selectedTab == .all, !suggestedRows.isEmpty {
            out.append(ListSection(id: "suggested", title: suggestedProvider.context.title, rows: suggestedRows))
        }
        if !recentRows.isEmpty {
            out.append(ListSection(id: "recent", title: L10n.tr("vault.mostRecent", locale: prefs.locale), rows: recentRows))
        }
        out += letterSections.map { ListSection(id: "letter-" + $0.letter, title: $0.letter, rows: $0.items) }
        return out
    }

    var body: some View {
        ZStack(alignment: .bottomTrailing) {
            VStack(spacing: 0) {
                AppMenuHeader(title: L10n.tr("vault.titleYours", locale: prefs.locale), onMenu: onOpenMenu)

                ScrollView {
                    VStack(alignment: .leading, spacing: QasaTokens.Space.formGap) {
                        searchField
                        tabChips

                        if !sections.isEmpty {
                            VStack(alignment: .leading, spacing: 0) {
                                ForEach(Array(sections.enumerated()), id: \.element.id) { index, section in
                                    VaultSection(title: section.title, isFirst: index == 0) {
                                        ForEach(section.rows) { row in
                                            rowLink(for: row)
                                        }
                                    }
                                }
                            }
                        }

                        if filteredRows.isEmpty {
                            ContentUnavailableView(
                                L10n.tr("vault.emptyTitle", locale: prefs.locale),
                                icon: .inbox,
                                // A search with no hits says so instead of "add your first item".
                                description: Text(L10n.tr(
                                    searchText.trimmingCharacters(in: .whitespaces).isEmpty
                                        ? "vault.emptyHint" : "vault.noSearchMatches",
                                    locale: prefs.locale
                                ))
                            )
                            .frame(maxWidth: .infinity)
                            .padding(.top, 40)
                        }
                    }
                    .padding(.horizontal, Theme.screenPadding)
                    .padding(.top, QasaTokens.Space.pageTop)
                    .padding(.bottom, 100)
                }
            }

            MintFAB(icon: .addPlain) {
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
        .onAppear(perform: consumePendingLogin)
        .onChange(of: pendingLogin) { _, _ in consumePendingLogin() }
        .sheet(item: $editorMode) { presentation in
            VaultItemEditor(mode: presentation.mode) {
                editorMode = nil
            }
            .appLocalization()
        }
    }

    private var searchField: some View {
        HStack(spacing: 10) {
            QasaIconImage(.search, size: 16)
                .foregroundColor(Theme.textSecondary)
            TextField(L10n.tr("vault.searchPlaceholder", locale: prefs.locale), text: $searchText)
                .multilineTextAlignment(.leading)
                .submitLabel(.search)
            if !searchText.isEmpty {
                FieldIconButton(
                    icon: .close,
                    tint: Theme.textTertiary,
                    accessibilityLabel: L10n.tr("common.cancel", locale: prefs.locale)
                ) {
                    searchText = ""
                }
            }
        }
        .qasaField()
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
        .buttonStyle(.vaultRow)
    }

    private func consumePendingLogin() {
        guard let login = pendingLogin, !login.password.isEmpty else { return }
        pendingLogin = nil
        showAddMenu = false
        editorMode = EditorPresentation(mode: .create(.login(login)))
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
