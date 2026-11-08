import Foundation
import SwiftData

/// The vault key material and account the sync session reads and (on a new device) adopts.
/// The real one is the Keychain; tests use an in-memory one.
nonisolated protocol SyncVaultMetaStore: AnyObject, Sendable {
    var vaultExists: Bool { get set }
    var kdfSalt: Data? { get set }
    var passwordVerifier: Data? { get set }
    var kdfVersion: Int? { get set }
    var accountId: String? { get set }
    var deviceFingerprint: String? { get set }
}

nonisolated final class KeychainSyncVaultMeta: SyncVaultMetaStore, @unchecked Sendable {
    var vaultExists: Bool {
        get { VaultKeychainStore.vaultExists }
        set { VaultKeychainStore.vaultExists = newValue }
    }
    var kdfSalt: Data? {
        get { VaultKeychainStore.kdfSalt }
        set { VaultKeychainStore.kdfSalt = newValue }
    }
    var passwordVerifier: Data? {
        get { VaultKeychainStore.passwordVerifier }
        set { VaultKeychainStore.passwordVerifier = newValue }
    }
    var kdfVersion: Int? {
        get { VaultKeychainStore.kdfVersion }
        set { VaultKeychainStore.kdfVersion = newValue }
    }
    var accountId: String? {
        get { VaultKeychainStore.accountId }
        set { VaultKeychainStore.accountId = newValue }
    }
    var deviceFingerprint: String? {
        get { VaultKeychainStore.deviceFingerprint }
        set { VaultKeychainStore.deviceFingerprint = newValue }
    }
}

/// This phone's vault as the Wi‑Fi sync session (`core/qasa-sync`) sees it.
///
/// Threading: Rust calls these methods on its own worker thread while a session runs.
/// Every call opens a fresh `ModelContext` on the (Sendable) container and finishes with
/// it before returning, so nothing here touches the main actor's context. After rows are
/// written, `onApplied` runs (by default it rebuilds the list cache on the main actor).
///
/// Rows cross as stored: envelope and payload stay vault-key ciphertext, and iOS's payload
/// JSON is already the canonical wire shape.
nonisolated final class VaultSyncStore: SyncStoreCallback, @unchecked Sendable {
    private let container: ModelContainer
    private let meta: SyncVaultMetaStore
    private let deviceName: String
    private let onApplied: @Sendable () -> Void
    private let onIntro: @Sendable () -> Void

    init(
        container: ModelContainer,
        deviceName: String,
        meta: SyncVaultMetaStore = KeychainSyncVaultMeta(),
        onIntro: @escaping @Sendable () -> Void = {},
        onApplied: @escaping @Sendable () -> Void = VaultSyncStore.refreshVaultOnMain
    ) {
        self.container = container
        self.deviceName = deviceName
        self.meta = meta
        self.onIntro = onIntro
        self.onApplied = onApplied
    }

    static let refreshVaultOnMain: @Sendable () -> Void = {
        Task { @MainActor in
            await VaultRepository.shared.warmCache()
        }
    }

    // MARK: SyncStoreCallback

    func intro() -> FfiIntro {
        onIntro()
        return FfiIntro(
            deviceName: deviceName,
            platform: "ios",
            accountId: meta.accountId,
            vault: currentVault()
        )
    }

    func snapshot() -> [FfiItem] {
        let context = ModelContext(container)
        let records = (try? context.fetch(FetchDescriptor<VaultItemRecord>())) ?? []
        return records.compactMap { record in
            // Legacy plaintext rows are migrated on unlock; they never cross the wire.
            guard record.schemaVersion >= 1 else { return nil }
            if record.deletedAt == nil, record.encryptedPayload.isEmpty { return nil }
            return FfiItem(
                id: record.id.uuidString,
                kind: record.kindRaw,
                envelope: record.deletedAt == nil ? record.encryptedEnvelope : Data(),
                payload: record.deletedAt == nil ? record.encryptedPayload : Data(),
                schemaVersion: Int64(record.schemaVersion),
                createdAtMs: Self.ms(record.createdAt),
                updatedAtMs: Self.ms(record.updatedAt),
                lastUsedAtMs: record.lastUsedAt.map(Self.ms),
                collectionId: record.collectionId?.uuidString,
                deletedAtMs: record.deletedAt.map(Self.ms)
            )
        }
    }

    func collections() -> [FfiCollection] {
        let context = ModelContext(container)
        let rows = (try? context.fetch(FetchDescriptor<VaultCollection>())) ?? []
        return rows.map { c in
            FfiCollection(
                id: c.id.uuidString,
                name: c.name,
                createdAtMs: Self.ms(c.createdAt),
                updatedAtMs: Self.ms(c.updatedAt ?? c.createdAt),
                deletedAtMs: c.deletedAt.map(Self.ms)
            )
        }
    }

    /// A new device takes the other's vault key material and account. The account id
    /// comes from inside the authenticated channel only (this closes the rebinding hole).
    /// Never replaces a vault that already exists here.
    func adopt(accountId: String?, vault: FfiVaultMeta) -> Bool {
        guard currentVault() == nil, !vault.salt.isEmpty, !vault.verifier.isEmpty else { return false }
        meta.kdfSalt = vault.salt
        meta.passwordVerifier = vault.verifier
        meta.kdfVersion = vault.kdfVersion == 0 ? 1 : Int(vault.kdfVersion)
        if let accountId, !accountId.isEmpty {
            meta.accountId = accountId
        }
        if meta.deviceFingerprint == nil {
            meta.deviceFingerprint = UUID().uuidString.lowercased()
        }
        meta.vaultExists = true
        return true
    }

    func apply(items: [FfiItem], collections: [FfiCollection]) -> Int64 {
        let context = ModelContext(container)
        var written: Int64 = 0
        do {
            for item in items {
                guard let id = UUID(uuidString: item.id) else { continue }
                let record = try fetchItem(id: id, in: context) ?? {
                    let r = VaultItemRecord(kind: VaultItemKind(rawValue: item.kind) ?? .login)
                    r.id = id
                    context.insert(r)
                    return r
                }()
                record.kindRaw = item.kind
                record.encryptedEnvelope = item.envelope
                record.encryptedPayload = item.payload
                record.schemaVersion = Int(item.schemaVersion)
                record.legacyPayloadData = nil
                record.createdAt = Self.date(item.createdAtMs)
                record.updatedAt = Self.date(item.updatedAtMs)
                record.lastUsedAt = item.lastUsedAtMs.map(Self.date)
                record.collectionId = item.collectionId.flatMap(UUID.init(uuidString:))
                record.deletedAt = item.deletedAtMs.map(Self.date)
                if record.deletedAt != nil {
                    record.encryptedEnvelope = Data()
                    record.encryptedPayload = Data()
                }
                // Tokens are keyed per device; rebuilt on the next unlock (warmCache).
                let tokens = try context.fetch(FetchDescriptor<VaultSearchToken>(predicate: #Predicate { $0.itemId == id }))
                tokens.forEach(context.delete)
                written += 1
            }
            for incoming in collections {
                guard let id = UUID(uuidString: incoming.id) else { continue }
                var descriptor = FetchDescriptor<VaultCollection>(predicate: #Predicate { $0.id == id })
                descriptor.fetchLimit = 1
                let collection = try context.fetch(descriptor).first ?? {
                    let c = VaultCollection(name: incoming.name)
                    c.id = id
                    context.insert(c)
                    return c
                }()
                collection.name = incoming.name
                collection.createdAt = Self.date(incoming.createdAtMs)
                collection.updatedAt = Self.date(incoming.updatedAtMs)
                collection.deletedAt = incoming.deletedAtMs.map(Self.date)
                written += 1
            }
            try context.save()
        } catch {
            return -1
        }
        if written > 0 { onApplied() }
        return written
    }

    func nowMs() -> Int64 {
        Self.ms(Date())
    }

    // MARK: helpers

    private func currentVault() -> FfiVaultMeta? {
        guard meta.vaultExists, let salt = meta.kdfSalt, let verifier = meta.passwordVerifier else { return nil }
        return FfiVaultMeta(salt: salt, kdfVersion: UInt32(meta.kdfVersion ?? 1), verifier: verifier)
    }

    private func fetchItem(id: UUID, in context: ModelContext) throws -> VaultItemRecord? {
        var descriptor = FetchDescriptor<VaultItemRecord>(predicate: #Predicate { $0.id == id })
        descriptor.fetchLimit = 1
        return try context.fetch(descriptor).first
    }

    static func ms(_ date: Date) -> Int64 {
        Int64((date.timeIntervalSince1970 * 1000).rounded())
    }

    static func date(_ ms: Int64) -> Date {
        Date(timeIntervalSince1970: Double(ms) / 1000)
    }
}
