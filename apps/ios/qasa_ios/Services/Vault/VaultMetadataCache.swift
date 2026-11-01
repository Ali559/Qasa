import Foundation

@Observable
final class VaultMetadataCache {
    private(set) var rows: [VaultListRow] = []

    func clear() {
        rows = []
    }

    func rebuild(from items: [(record: VaultItemRecord, envelope: VaultEnvelope)]) {
        rows = items
            .map { VaultListRow(record: $0.record, envelope: $0.envelope) }
            .sorted { $0.displayName.localizedCaseInsensitiveCompare($1.displayName) == .orderedAscending }
    }

    func upsert(_ row: VaultListRow) {
        if let index = rows.firstIndex(where: { $0.id == row.id }) {
            rows[index] = row
        } else {
            rows.append(row)
        }
        rows.sort { $0.displayName.localizedCaseInsensitiveCompare($1.displayName) == .orderedAscending }
    }

    func remove(id: UUID) {
        rows.removeAll { $0.id == id }
    }

    func row(for id: UUID) -> VaultListRow? {
        rows.first { $0.id == id }
    }

    func filter(kind: VaultItemKind?) -> [VaultListRow] {
        guard let kind else { return rows }
        return rows.filter { $0.kind == kind }
    }

    func search(query: String) -> [VaultListRow] {
        let trimmed = query.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return rows }
        return rows.filter {
            $0.displayName.localizedCaseInsensitiveContains(trimmed)
                || $0.subtitle.localizedCaseInsensitiveContains(trimmed)
        }
    }

    func filter(kind: VaultItemKind?, query: String) -> [VaultListRow] {
        let base = filter(kind: kind)
        let trimmed = query.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return base }
        return base.filter {
            $0.displayName.localizedCaseInsensitiveContains(trimmed)
                || $0.subtitle.localizedCaseInsensitiveContains(trimmed)
        }
    }
}
