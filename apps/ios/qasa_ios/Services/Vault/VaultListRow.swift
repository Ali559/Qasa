import Foundation

struct VaultListRow: Identifiable, Equatable, Sendable {
    let id: UUID
    let displayName: String
    let subtitle: String
    let kind: VaultItemKind
    let createdAt: Date
    let updatedAt: Date
    let lastUsedAt: Date?
    let collectionId: UUID?
    let letterSection: String

    init(
        id: UUID,
        displayName: String,
        subtitle: String,
        kind: VaultItemKind,
        createdAt: Date,
        updatedAt: Date,
        lastUsedAt: Date?,
        collectionId: UUID?,
        letterSection: String? = nil
    ) {
        self.id = id
        self.displayName = displayName
        self.subtitle = subtitle
        self.kind = kind
        self.createdAt = createdAt
        self.updatedAt = updatedAt
        self.lastUsedAt = lastUsedAt
        self.collectionId = collectionId
        self.letterSection = letterSection ?? Self.section(for: displayName)
    }

    init(record: VaultItemRecord, envelope: VaultEnvelope) {
        self.init(
            id: record.id,
            displayName: envelope.displayName,
            subtitle: envelope.subtitle,
            kind: record.kind,
            createdAt: record.createdAt,
            updatedAt: record.updatedAt,
            lastUsedAt: record.lastUsedAt,
            collectionId: envelope.collectionId ?? record.collectionId
        )
    }

    static func section(for sortKey: String) -> String {
        let trimmed = sortKey.trimmingCharacters(in: .whitespacesAndNewlines)
        guard let first = trimmed.first else { return "#" }
        let upper = String(first).uppercased()
        if upper.rangeOfCharacter(from: .letters) != nil {
            return upper
        }
        return "#"
    }
}
