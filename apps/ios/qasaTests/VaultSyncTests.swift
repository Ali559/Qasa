import CryptoKit
import Foundation
import SwiftData
import Testing
@testable import qasa

private func memoryContainer() throws -> ModelContainer {
    try ModelContainer(
        for: Schema(VaultSchema.models),
        configurations: [ModelConfiguration(isStoredInMemoryOnly: true)]
    )
}

/// Runs a blocking call on a background thread, as the app does with the FFI.
private func offMain<T: Sendable>(_ work: @escaping @Sendable () -> T) async -> T {
    await withCheckedContinuation { continuation in
        DispatchQueue.global(qos: .userInitiated).async { continuation.resume(returning: work()) }
    }
}

final class MemoryVaultMeta: SyncVaultMetaStore, @unchecked Sendable {
    var vaultExists = false
    var kdfSalt: Data?
    var passwordVerifier: Data?
    var kdfVersion: Int?
    var accountId: String?
    var deviceFingerprint: String?

    static func withVault(account: String = "acct-1") -> MemoryVaultMeta {
        let m = MemoryVaultMeta()
        m.vaultExists = true
        m.kdfSalt = Data(repeating: 1, count: 32)
        m.passwordVerifier = Data(repeating: 2, count: 32)
        m.kdfVersion = 1
        m.accountId = account
        m.deviceFingerprint = "fp-host"
        return m
    }
}

// MARK: - Tombstones

/// Uses the shared repository and session, so these run one at a time.
@Suite(.serialized)
@MainActor
struct VaultTombstoneTests {
    private func setUp() async throws -> ModelContainer {
        let container = try memoryContainer()
        VaultSession.shared.installKeysForTesting(encKey: SymmetricKey(size: .bits256), searchKey: SymmetricKey(size: .bits256))
        VaultRepository.shared.autofillSyncEnabled = false
        await VaultRepository.shared.configure(context: container.mainContext)
        return container
    }

    private func tearDown() {
        VaultSession.shared.lock()
        VaultRepository.shared.cache.clear()
        VaultRepository.shared.resetContext()
    }

    @Test func deleteKeepsAnEmptyTombstone() async throws {
        let container = try await setUp()
        defer { tearDown() }
        let repo = VaultRepository.shared
        let row = try repo.save(payload: .login(LoginFields(itemName: "GitHub", password: "secret-1", website: "https://github.com")))
        let id = row.id
        let tokensBefore = try container.mainContext.fetch(FetchDescriptor<VaultSearchToken>(predicate: #Predicate { $0.itemId == id }))
        #expect(!tokensBefore.isEmpty)

        try repo.delete(id: id)

        let rows = try container.mainContext.fetch(FetchDescriptor<VaultItemRecord>(predicate: #Predicate { $0.id == id }))
        #expect(rows.count == 1)
        let tombstone = try #require(rows.first)
        #expect(tombstone.deletedAt != nil)
        #expect(tombstone.encryptedPayload.isEmpty)
        #expect(tombstone.encryptedEnvelope.isEmpty)
        #expect(tombstone.legacyPayloadData == nil)
        #expect(tombstone.updatedAt == tombstone.deletedAt)
        let tokensAfter = try container.mainContext.fetch(FetchDescriptor<VaultSearchToken>(predicate: #Predicate { $0.itemId == id }))
        #expect(tokensAfter.isEmpty)
    }

    @Test func deletedItemsNeverAppearInReads() async throws {
        // Keep the container alive: the repository's context dies with it.
        let container = try await setUp()
        defer { tearDown(); _ = container }
        let repo = VaultRepository.shared
        let kept = try repo.save(payload: .login(LoginFields(itemName: "Kept", password: "a")))
        let gone = try repo.save(payload: .login(LoginFields(itemName: "Gone", password: "b")))
        let note = try repo.save(payload: .secureNote(SecureNoteFields(title: "Note", content: "x", color: .mint, protectItem: false)))
        try repo.delete(id: gone.id)
        try repo.delete(id: note.id)
        await repo.warmCache()

        #expect(repo.cache.rows.map(\.id) == [kept.id])
        #expect(repo.cache.row(for: gone.id) == nil)
        #expect(try repo.allLoginPayloads().map(\.0) == [kept.id])
        let all = repo.allPayloads()
        #expect(all.payloads.count == 1)
        #expect(all.decryptFailureCount == 0)
        #expect(throws: (any Error).self) { try repo.payload(for: gone.id) }
        // Deleting again, or using a deleted item, is a no-op.
        try repo.delete(id: gone.id)
        try repo.markUsed(id: gone.id)
        #expect(repo.cache.rows.count == 1)
    }

    @Test func deletedFoldersAreTombstonesToo() async throws {
        let container = try await setUp()
        defer { tearDown() }
        let context = container.mainContext
        let work = VaultCollection(name: "Work")
        let home = VaultCollection(name: "Home")
        context.insert(work)
        context.insert(home)
        try context.save()

        VaultRepository.deleteCollection(work, context: context)

        let live = try context.fetch(FetchDescriptor<VaultCollection>(predicate: VaultCollection.live))
        #expect(live.map(\.name) == ["Home"])
        let all = try context.fetch(FetchDescriptor<VaultCollection>())
        #expect(all.count == 2)
        #expect(work.deletedAt != nil)
        #expect(work.updatedAt == work.deletedAt)
    }

    @Test func syncedItemsGetSearchTokensOnWarmCache() async throws {
        let container = try await setUp()
        defer { tearDown() }
        let repo = VaultRepository.shared
        let row = try repo.save(payload: .login(LoginFields(itemName: "Synced", password: "p")))
        let id = row.id
        // Sync drops an updated item's tokens (they are per device).
        for token in try container.mainContext.fetch(FetchDescriptor<VaultSearchToken>(predicate: #Predicate { $0.itemId == id })) {
            container.mainContext.delete(token)
        }
        try container.mainContext.save()

        await repo.warmCache()

        let tokens = try container.mainContext.fetch(FetchDescriptor<VaultSearchToken>(predicate: #Predicate { $0.itemId == id }))
        #expect(!tokens.isEmpty)
    }
}

// MARK: - Sync store

struct VaultSyncStoreTests {
    private func record(_ context: ModelContext, id: UUID = UUID(), updated: Date, deleted: Date? = nil, schema: Int = 1) -> VaultItemRecord {
        let r = VaultItemRecord(kind: .login)
        r.id = id
        r.encryptedEnvelope = deleted == nil ? Data([1, 2, 3]) : Data()
        r.encryptedPayload = deleted == nil ? Data([4, 5, 6, 7]) : Data()
        r.schemaVersion = schema
        r.createdAt = Date(timeIntervalSince1970: 1_700_000_000.123)
        r.updatedAt = updated
        r.deletedAt = deleted
        context.insert(r)
        return r
    }

    private func store(_ container: ModelContainer, _ meta: MemoryVaultMeta, name: String = "Test iPhone") -> VaultSyncStore {
        VaultSyncStore(container: container, deviceName: name, meta: meta, onApplied: {})
    }

    @Test func introReportsVaultOnlyWhenOneExists() throws {
        let container = try memoryContainer()
        let empty = store(container, MemoryVaultMeta()).intro()
        #expect(empty.platform == "ios")
        #expect(empty.vault == nil)

        let full = store(container, MemoryVaultMeta.withVault()).intro()
        #expect(full.accountId == "acct-1")
        #expect(full.vault?.salt == Data(repeating: 1, count: 32))
        #expect(full.vault?.kdfVersion == 1)
    }

    @Test func snapshotApplyRoundTrip() throws {
        let a = try memoryContainer()
        let ctx = ModelContext(a)
        let live = record(ctx, updated: Date(timeIntervalSince1970: 1_700_000_100.456))
        live.collectionId = UUID()
        live.lastUsedAt = Date(timeIntervalSince1970: 1_700_000_050)
        let dead = record(ctx, updated: Date(timeIntervalSince1970: 1_700_000_200), deleted: Date(timeIntervalSince1970: 1_700_000_200))
        _ = record(ctx, updated: Date(), schema: 0) // legacy: never sent
        let folder = VaultCollection(name: "Work")
        ctx.insert(folder)
        try ctx.save()

        let source = store(a, MemoryVaultMeta.withVault())
        let items = source.snapshot()
        let folders = source.collections()
        #expect(items.count == 2)
        #expect(Set(items.map(\.id)) == [live.id.uuidString, dead.id.uuidString])
        let deadItem = try #require(items.first { $0.id == dead.id.uuidString })
        #expect(deadItem.deletedAtMs == 1_700_000_200_000)
        #expect(deadItem.payload.isEmpty)
        #expect(folders.map(\.name) == ["Work"])

        let b = try memoryContainer()
        let target = store(b, MemoryVaultMeta.withVault())
        #expect(target.apply(items: items, collections: folders) == 3)

        let readBack = ModelContext(b)
        let rows = try readBack.fetch(FetchDescriptor<VaultItemRecord>())
        #expect(rows.count == 2)
        let copy = try #require(rows.first { $0.id == live.id })
        #expect(copy.encryptedEnvelope == Data([1, 2, 3]))
        #expect(copy.encryptedPayload == Data([4, 5, 6, 7]))
        #expect(copy.collectionId == live.collectionId)
        #expect(VaultSyncStore.ms(copy.updatedAt) == 1_700_000_100_456)
        #expect(copy.lastUsedAt != nil)
        #expect(copy.deletedAt == nil)
        #expect(rows.first { $0.id == dead.id }?.deletedAt != nil)
        #expect(try readBack.fetch(FetchDescriptor<VaultCollection>()).first?.id == folder.id)
        // Snapshots of both sides now match.
        #expect(Set(target.snapshot().map(\.updatedAtMs)) == Set(items.map(\.updatedAtMs)))
    }

    @Test func applyTurnsALiveItemIntoATombstone() throws {
        let container = try memoryContainer()
        let ctx = ModelContext(container)
        let item = record(ctx, updated: Date(timeIntervalSince1970: 1_700_000_000))
        ctx.insert(VaultSearchToken(itemId: item.id, tokenHash: "h"))
        try ctx.save()
        let s = store(container, MemoryVaultMeta.withVault())
        var wire = try #require(s.snapshot().first)
        wire.deletedAtMs = 1_800_000_000_000
        wire.updatedAtMs = 1_800_000_000_000
        wire.envelope = Data()
        wire.payload = Data()
        #expect(s.apply(items: [wire], collections: []) == 1)

        let check = ModelContext(container)
        let row = try #require(try check.fetch(FetchDescriptor<VaultItemRecord>()).first)
        #expect(row.deletedAt != nil)
        #expect(row.encryptedPayload.isEmpty)
        #expect(try check.fetch(FetchDescriptor<VaultSearchToken>()).isEmpty)
        #expect(try check.fetch(FetchDescriptor<VaultItemRecord>(predicate: VaultItemRecord.live)).isEmpty)
    }

    @Test func adoptOnlyWithoutAVault() throws {
        let container = try memoryContainer()
        let incoming = FfiVaultMeta(salt: Data(repeating: 7, count: 32), kdfVersion: 1, verifier: Data(repeating: 8, count: 32))

        let fresh = MemoryVaultMeta()
        #expect(store(container, fresh).adopt(accountId: "acct-new", vault: incoming))
        #expect(fresh.vaultExists)
        #expect(fresh.kdfSalt == incoming.salt)
        #expect(fresh.passwordVerifier == incoming.verifier)
        #expect(fresh.kdfVersion == 1)
        #expect(fresh.accountId == "acct-new")
        #expect(fresh.deviceFingerprint != nil)

        let existing = MemoryVaultMeta.withVault(account: "mine")
        #expect(!store(container, existing).adopt(accountId: "attacker", vault: incoming))
        #expect(existing.accountId == "mine")
        #expect(existing.kdfSalt == Data(repeating: 1, count: 32))
    }

    @Test func mainContextSeesRowsAppliedOnAnotherThread() async throws {
        let container = try memoryContainer()
        let main = await MainActor.run { () -> UUID in
            let r = record(container.mainContext, updated: Date(timeIntervalSince1970: 1_700_000_000))
            try? container.mainContext.save()
            return r.id
        }
        let s = store(container, MemoryVaultMeta.withVault())
        var wire = try #require(s.snapshot().first)
        wire.payload = Data([9, 9])
        wire.updatedAtMs = 1_800_000_000_000
        let item = wire
        let written = await offMain { s.apply(items: [item], collections: []) }
        #expect(written == 1)
        let payload = await MainActor.run { () -> Data? in
            let rows = try? container.mainContext.fetch(FetchDescriptor<VaultItemRecord>(predicate: #Predicate { $0.id == main }))
            return rows?.first?.encryptedPayload
        }
        #expect(payload == Data([9, 9]))
    }
}

// MARK: - Whole session through the Rust core

struct WiFiSyncSessionTests {
    @Test func newDeviceAdoptsVaultAndAccountOverLoopback() async throws {
        let hostContainer = try memoryContainer()
        let ctx = ModelContext(hostContainer)
        let item = VaultItemRecord(kind: .login)
        item.encryptedEnvelope = Data([1])
        item.encryptedPayload = Data([2])
        ctx.insert(item)
        ctx.insert(VaultCollection(name: "Work"))
        try ctx.save()
        let hostMeta = MemoryVaultMeta.withVault(account: "acct-host")
        let hostStore = VaultSyncStore(container: hostContainer, deviceName: "Host", meta: hostMeta, onApplied: {})

        let joinContainer = try memoryContainer()
        let joinMeta = MemoryVaultMeta()
        let joinStore = VaultSyncStore(container: joinContainer, deviceName: "New", meta: joinMeta, onApplied: {})

        let host = try SyncHost()
        let port = host.port()
        let code = host.code()
        #expect(code.count == 6)

        async let served = offMain { () -> FfiOutcome? in try? host.serve(store: hostStore) }
        let joined = await offMain { () -> Swift.Result<FfiOutcome, FfiSyncError> in
            do { return .success(try syncJoin(host: "127.0.0.1", port: port, code: code, store: joinStore)) }
            catch let e as FfiSyncError { return .failure(e) }
            catch { return .failure(.Unexpected) }
        }
        let outcome = try joined.get()
        #expect(outcome.adopted)
        #expect(outcome.peerName == "Host")
        #expect(outcome.received == 2)
        #expect(await served?.sent == 2)

        #expect(joinMeta.vaultExists)
        #expect(joinMeta.accountId == "acct-host")
        #expect(joinMeta.kdfSalt == hostMeta.kdfSalt)
        let rows = try ModelContext(joinContainer).fetch(FetchDescriptor<VaultItemRecord>())
        #expect(rows.map(\.id) == [item.id])
        #expect(rows.first?.encryptedPayload == Data([2]))
    }

    /// A wrong code changes nothing; the host keeps waiting (as `WiFiSyncController` does,
    /// it calls `serve` again) and the right code then works.
    @Test func wrongCodeChangesNothingAndHostCanRetry() async throws {
        let hostStore = VaultSyncStore(container: try memoryContainer(), deviceName: "Host", meta: MemoryVaultMeta.withVault(), onApplied: {})
        let joinMeta = MemoryVaultMeta()
        let joinStore = VaultSyncStore(container: try memoryContainer(), deviceName: "New", meta: joinMeta, onApplied: {})
        let host = try SyncHost()
        let port = host.port()
        let code = host.code()
        let wrong = code == "000000" ? "111111" : "000000"

        async let served = offMain { () -> FfiSyncError? in
            do { _ = try host.serve(store: hostStore); return nil } catch let e as FfiSyncError { return e } catch { return nil }
        }
        let joined = await offMain { () -> FfiSyncError? in
            do { _ = try syncJoin(host: "127.0.0.1", port: port, code: wrong, store: joinStore); return nil }
            catch let e as FfiSyncError { return e } catch { return nil }
        }
        if case .WrongCode = joined {} else { Issue.record("join: expected WrongCode, got \(String(describing: joined))") }
        // The host sees the mismatch as WrongCode, or as a dropped connection when the
        // joiner hangs up first; the controller retries on both.
        switch await served {
        case .WrongCode(let left): #expect(left == 2)
        case .Io: break
        case let other: Issue.record("host: expected WrongCode or Io, got \(String(describing: other))")
        }
        #expect(!joinMeta.vaultExists)
        #expect(joinMeta.accountId == nil)

        async let servedAgain = offMain { () -> FfiOutcome? in try? host.serve(store: hostStore) }
        let retry = await offMain { () -> FfiOutcome? in
            try? syncJoin(host: "127.0.0.1", port: port, code: code, store: joinStore)
        }
        #expect(retry?.adopted == true)
        #expect(await servedAgain != nil)
        #expect(joinMeta.vaultExists)
    }
}
