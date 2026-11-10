import CryptoKit
import Foundation
import SwiftData
import Testing
@testable import qasa

/// The repository's write-path policy. Uses the shared repository and session, so these
/// run one at a time and stay synchronous (no suspension another suite could interleave).
@Suite(.serialized)
@MainActor
struct VaultValidationRepositoryTests {
    private static let badCard = VaultPayload.card(CardFields(
        itemName: "Visa", number: "4111111111111112", securityCode: "123", expiryDate: "0829"
    ))

    private func setUp() throws -> ModelContainer {
        let container = try ModelContainer(
            for: Schema(VaultSchema.models),
            configurations: [ModelConfiguration(isStoredInMemoryOnly: true)]
        )
        VaultSession.shared.installKeysForTesting(encKey: SymmetricKey(size: .bits256), searchKey: SymmetricKey(size: .bits256))
        VaultRepository.shared.autofillSyncEnabled = false
        VaultRepository.shared.resetContext()
        VaultRepository.shared.ensureConfigured(context: container.mainContext)
        return container
    }

    private func tearDown() {
        VaultSession.shared.lock()
        VaultRepository.shared.cache.clear()
        VaultRepository.shared.resetContext()
    }

    @Test func invalidUserSaveThrowsAndWritesNothing() throws {
        let container = try setUp()
        defer { tearDown() }
        #expect(throws: VaultValidationError.self) {
            _ = try VaultRepository.shared.save(payload: Self.badCard)
        }
        do {
            _ = try VaultRepository.shared.save(payload: Self.badCard)
        } catch let error as VaultValidationError {
            #expect(error.issues.map(\.code) == ["number:cardNumberChecksum"])
        }
        #expect(try container.mainContext.fetchCount(FetchDescriptor<VaultItemRecord>()) == 0)
        #expect(try container.mainContext.fetchCount(FetchDescriptor<VaultSearchToken>()) == 0)
    }

    @Test func validUserSaveIsStoredNormalized() throws {
        let container = try setUp()
        defer { tearDown() }
        let good = VaultPayload.card(CardFields(itemName: "Visa", number: "4111111111111111", securityCode: "123", expiryDate: "0829"))
        let row = try VaultRepository.shared.save(payload: good)
        #expect(try container.mainContext.fetchCount(FetchDescriptor<VaultItemRecord>()) == 1)
        guard case .card(let stored) = try VaultRepository.shared.payload(for: row.id) else {
            Issue.record("not a card")
            return
        }
        #expect(stored.number == "4111 1111 1111 1111")
        #expect(stored.expiryDate == "08/29")
    }

    @Test func csvImportStoresAndCountsAnInvalidItem() throws {
        let container = try setUp()
        defer { tearDown() }
        let result = try VaultRepository.shared.importItem(Self.badCard)
        #expect(result.flagged)
        #expect(try container.mainContext.fetchCount(FetchDescriptor<VaultItemRecord>()) == 1)
        guard case .card(let stored) = try VaultRepository.shared.payload(for: result.row.id) else {
            Issue.record("not a card")
            return
        }
        // Normalized even though it still has an issue.
        #expect(stored.expiryDate == "08/29")
        #expect(!VaultValidation.issues(.card(stored)).isEmpty)

        let clean = try VaultRepository.shared.importItem(.login(LoginFields(itemName: "GitHub", website: " github.com ")))
        #expect(!clean.flagged)
    }
}

/// Wi‑Fi sync apply and backup restore store ciphertext unchanged: only the structural
/// guard applies.
struct VaultRecordGuardTests {
    private func item(kind: String = "login", envelope: Data = Data([1]), payload: Data = Data([2]), schema: Int64 = 1, deleted: Int64? = nil) -> FfiItem {
        FfiItem(
            id: UUID().uuidString, kind: kind, envelope: envelope, payload: payload, schemaVersion: schema,
            createdAtMs: 1_700_000_000_000, updatedAtMs: 1_700_000_000_000, lastUsedAtMs: nil,
            collectionId: nil, deletedAtMs: deleted
        )
    }

    @Test func applySkipsStructurallyInvalidRows() throws {
        let container = try ModelContainer(
            for: Schema(VaultSchema.models),
            configurations: [ModelConfiguration(isStoredInMemoryOnly: true)]
        )
        let store = VaultSyncStore(container: container, deviceName: "Test", meta: MemoryVaultMeta.withVault(), onApplied: {})
        let good = item()
        let tombstone = item(envelope: Data(), payload: Data(), deleted: 1_700_000_000_000)
        let written = store.apply(items: [
            good,
            tombstone,
            item(kind: "bogus"),
            item(payload: Data()),
            item(envelope: Data()),
            item(schema: 0),
        ], collections: [])
        #expect(written == 2)
        #expect(store.lastRejectedCount == 4)
        let rows = try ModelContext(container).fetch(FetchDescriptor<VaultItemRecord>())
        #expect(Set(rows.map(\.id.uuidString)) == [good.id, tombstone.id])
        let copy = try #require(rows.first { $0.id.uuidString == good.id })
        #expect(copy.encryptedPayload == Data([2]))
    }
}
