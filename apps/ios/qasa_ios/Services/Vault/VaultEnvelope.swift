import Foundation

struct VaultEnvelope: Codable, Equatable, Sendable {
    var displayName: String
    var subtitle: String
    var collectionId: UUID?

    init(displayName: String, subtitle: String, collectionId: UUID? = nil) {
        self.displayName = displayName
        self.subtitle = subtitle
        self.collectionId = collectionId
    }

    init(from payload: VaultPayload) {
        let name = payload.displayName.trimmingCharacters(in: .whitespacesAndNewlines)
        self.displayName = name.isEmpty ? "Untitled" : name
        self.subtitle = payload.subtitle
        self.collectionId = payload.organizationCollectionId
    }
}
