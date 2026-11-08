import Foundation
import SwiftData
import Testing
@testable import qasa

/// The models as they were before Wi‑Fi sync added tombstones (same entity names).
private enum PreSyncSchema {
    @Model final class VaultCollection {
        var id: UUID
        var name: String
        var createdAt: Date
        init(name: String) { id = UUID(); self.name = name; createdAt = Date() }
    }

    @Model final class VaultItemRecord {
        var id: UUID
        var kindRaw: String
        var encryptedEnvelope: Data
        var encryptedPayload: Data
        var schemaVersion: Int
        var legacyPayloadData: Data?
        var updatedAt: Date
        var createdAt: Date
        var lastUsedAt: Date?
        var collectionId: UUID?
        init() {
            id = UUID(); kindRaw = "login"; encryptedEnvelope = Data([1]); encryptedPayload = Data([2])
            schemaVersion = 1; updatedAt = Date(); createdAt = Date()
        }
    }
}

struct VaultStoreMigrationTests {
    /// Adding the optional `deletedAt` / `updatedAt` must open an existing store in place
    /// (lightweight migration), keeping its rows live.
    @Test @MainActor func preSyncStoreOpensWithTombstoneFields() throws {
        let dir = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        try FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        defer { try? FileManager.default.removeItem(at: dir) }
        let url = dir.appendingPathComponent("vault.store")

        var itemId = UUID()
        do {
            let old = try ModelContainer(
                for: Schema([PreSyncSchema.VaultItemRecord.self, PreSyncSchema.VaultCollection.self]),
                configurations: [ModelConfiguration(url: url)]
            )
            let item = PreSyncSchema.VaultItemRecord()
            itemId = item.id
            old.mainContext.insert(item)
            old.mainContext.insert(PreSyncSchema.VaultCollection(name: "Work"))
            try old.mainContext.save()
        }

        let new = try ModelContainer(for: Schema(VaultSchema.models), configurations: [ModelConfiguration(url: url)])
        let items = try new.mainContext.fetch(FetchDescriptor<VaultItemRecord>(predicate: VaultItemRecord.live))
        #expect(items.map(\.id) == [itemId])
        #expect(items.first?.encryptedPayload == Data([2]))
        let folders = try new.mainContext.fetch(FetchDescriptor<VaultCollection>(predicate: VaultCollection.live))
        #expect(folders.map(\.name) == ["Work"])
        #expect(folders.first?.updatedAt == nil)
    }
}
