import CryptoKit
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

/// The folder model after Wi‑Fi sync but before names were encrypted.
private enum PreNameEncryptionSchema {
    @Model final class VaultCollection {
        var id: UUID
        var name: String
        var createdAt: Date
        var updatedAt: Date?
        var deletedAt: Date?
        init(name: String) { id = UUID(); self.name = name; createdAt = Date(); updatedAt = createdAt }
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

    /// Adding the optional `nameEncrypted` opens an existing store in place; the unlock-time
    /// migration then encrypts every plaintext name and blanks it, and the store re-opened
    /// from disk holds only ciphertext.
    @Test @MainActor func plaintextFolderNamesMigrateToCiphertext() throws {
        let dir = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        try FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        defer { try? FileManager.default.removeItem(at: dir) }
        let url = dir.appendingPathComponent("vault.store")

        var workId = UUID()
        var deadId = UUID()
        let stamp = Date(timeIntervalSince1970: 1_700_000_000)
        do {
            let old = try ModelContainer(
                for: Schema([PreNameEncryptionSchema.VaultCollection.self]),
                configurations: [ModelConfiguration(url: url)]
            )
            let work = PreNameEncryptionSchema.VaultCollection(name: "Work")
            work.updatedAt = stamp
            let dead = PreNameEncryptionSchema.VaultCollection(name: "Gone")
            dead.deletedAt = stamp
            workId = work.id
            deadId = dead.id
            old.mainContext.insert(work)
            old.mainContext.insert(dead)
            try old.mainContext.save()
        }

        let key = SymmetricKey(size: .bits256)
        do {
            let new = try ModelContainer(for: Schema(VaultSchema.models), configurations: [ModelConfiguration(url: url)])
            let folders = try new.mainContext.fetch(FetchDescriptor<VaultCollection>())
            #expect(folders.count == 2)
            #expect(folders.allSatisfy { $0.nameEncrypted == nil })
            try VaultRepository.encryptLegacyCollectionNames(context: new.mainContext, key: key)
            // A second run (every unlock) finds nothing to do.
            try VaultRepository.encryptLegacyCollectionNames(context: new.mainContext, key: key)
        }

        let reopened = try ModelContainer(for: Schema(VaultSchema.models), configurations: [ModelConfiguration(url: url)])
        let folders = try reopened.mainContext.fetch(FetchDescriptor<VaultCollection>())
        #expect(folders.allSatisfy { $0.name.isEmpty })
        let work = try #require(folders.first { $0.id == workId })
        #expect(try VaultRepository.decryptCollectionName(try #require(work.nameEncrypted), key: key) == "Work")
        #expect(work.updatedAt == stamp)
        let dead = try #require(folders.first { $0.id == deadId })
        #expect(dead.nameEncrypted == nil)
    }
}
