package alibarznji.qasa.screens.vault.list

import alibarznji.qasa.credentials.AutofillIndexEntry
import alibarznji.qasa.vault.VaultItemKind
import alibarznji.qasa.vault.VaultListRow

/**
 * Everything the list shows for one tab + query, computed the way
 * `VaultListView.swift:25-47` does: Suggested, then Most Recent, then A–Z.
 */
internal data class VaultListSections(
    val filtered: List<VaultListRow>,
    val suggested: List<VaultListRow>,
    val recent: List<VaultListRow>,
    val letters: List<Pair<String, List<VaultListRow>>>,
)

internal fun buildVaultListSections(
    rows: List<VaultListRow>,
    /** `null` is the "All" tab. */
    tabKind: VaultItemKind?,
    query: String,
    context: SuggestedContext,
    index: List<AutofillIndexEntry>,
): VaultListSections {
    val filtered = rows.filter { row ->
        val kindOk = tabKind == null || row.kind == tabKind
        val qOk = query.isBlank() || row.displayName.contains(query, true) || row.subtitle.contains(query, true)
        kindOk && qOk
    }
    // Suggested only on "All", and (Android) only while not searching: a search result
    // list should not repeat matches above the alphabetical sections.
    val suggested = if (tabKind == null && query.isBlank()) {
        VaultSuggestionEngine.suggest(filtered, context, index)
    } else {
        emptyList()
    }
    // iOS: a kind tab with more than 10 items gets the 8 most recently updated on top.
    val recent = if (tabKind != null && filtered.size > 10) {
        filtered.sortedByDescending { it.updatedAt }.take(8)
    } else {
        emptyList()
    }
    val letters = filtered
        .groupBy { it.letterSection() }
        .toList()
        .sortedWith(compareBy({ if (it.first == "#") 1 else 0 }, { it.first }))
        .map { (letter, items) -> letter to items.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.displayName }) }
    return VaultListSections(filtered, suggested, recent, letters)
}

/** iOS `VaultListRow.section(for:)`: first letter upper-cased, everything else under "#". */
internal fun VaultListRow.letterSection(): String {
    val first = displayName.trim().firstOrNull() ?: return "#"
    val upper = first.uppercaseChar()
    return if (upper.isLetter()) upper.toString() else "#"
}
