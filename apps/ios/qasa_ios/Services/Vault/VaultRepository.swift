import CryptoKit
import Foundation
import Observation
import SwiftData

@Observable
final class VaultRepository {
    static let shared = VaultRepository()

    let cache = VaultMetadataCache()
    private var context: ModelContext?
    private var searchDebounceTask: Task<Void, Never>?

    private init() {}

    func ensureConfigured(context: ModelContext) {
        if self.context == nil {
            self.context = context
        }
    }

    func configure(context: ModelContext) async {
        self.context = context
        await migrateLegacyIfNeeded()
        await warmCache()
    }

    func warmCache() async {
        guard let context else { return }
        let descriptor = FetchDescriptor<VaultItemRecord>(sortBy: [SortDescriptor(\.updatedAt, order: .reverse)])
        guard let records = try? context.fetch(descriptor) else { return }
        var pairs: [(VaultItemRecord, VaultEnvelope)] = []
        for record in records {
            if let envelope = try? decryptEnvelope(record) {
                pairs.append((record, envelope))
            }
        }
        await MainActor.run {
            cache.rebuild(from: pairs)
        }
        await syncAutofillIndex()
    }

    func save(payload: VaultPayload, id: UUID? = nil) throws -> VaultListRow {
        guard let context else { throw VaultCryptoError.vaultLocked }
        let encKey = try VaultSession.shared.requireEncKey()
        let searchKey = try VaultSession.shared.requireSearchKey()
        let envelope = VaultEnvelope(from: payload)

        let record: VaultItemRecord
        if let id, let existing = fetchRecord(id: id) {
            record = existing
            record.kindRaw = payload.kind.rawValue
            record.updatedAt = Date()
            record.collectionId = envelope.collectionId
        } else {
            record = VaultItemRecord(kind: payload.kind)
            record.collectionId = envelope.collectionId
            context.insert(record)
        }

        record.encryptedEnvelope = try VaultCrypto.encrypt(try JSONEncoder().encode(envelope), key: encKey)
        record.encryptedPayload = try VaultCrypto.encrypt(try JSONEncoder().encode(payload), key: encKey)
        record.schemaVersion = 1
        record.legacyPayloadData = nil

        replaceSearchTokens(for: record.id, envelope: envelope, payload: payload, searchKey: searchKey)
        try context.save()

        let row = VaultListRow(record: record, envelope: envelope)
        cache.upsert(row)
        Task { await syncAutofillIndex() }
        return row
    }

    func delete(id: UUID) throws {
        guard let context, let record = fetchRecord(id: id) else { return }
        deleteSearchTokens(for: id)
        context.delete(record)
        try context.save()
        cache.remove(id: id)
        Task { await syncAutofillIndex() }
    }

    func payload(for id: UUID) throws -> VaultPayload {
        guard let record = fetchRecord(id: id) else { throw VaultCryptoError.itemNotFound }
        return try decryptPayload(record)
    }

    func markUsed(id: UUID) throws {
        guard let context, let record = fetchRecord(id: id) else { return }
        record.lastUsedAt = Date()
        record.updatedAt = Date()
        try context.save()
        if let existing = cache.row(for: id) {
            let row = VaultListRow(
                id: existing.id,
                displayName: existing.displayName,
                subtitle: existing.subtitle,
                kind: existing.kind,
                createdAt: existing.createdAt,
                updatedAt: record.updatedAt,
                lastUsedAt: record.lastUsedAt,
                collectionId: existing.collectionId
            )
            cache.upsert(row)
        }
    }

    func allLoginPayloads() throws -> [(UUID, LoginFields)] {
        guard let context else { return [] }
        let descriptor = FetchDescriptor<VaultItemRecord>()
        let records = (try? context.fetch(descriptor)) ?? []
        return records.compactMap { record -> (UUID, LoginFields)? in
            guard record.kind == .login else { return nil }
            guard case .login(let fields) = try? decryptPayload(record) else { return nil }
            return (record.id, fields)
        }
    }

    /// Every decryptable payload in the vault. Used by CSV export/backup.
    ///
    /// Records that fail to decrypt are skipped rather than aborting the export, so one
    /// corrupted row cannot cost the user their whole backup. `decryptFailureCount`
    /// reports how many were dropped so the UI can say so instead of implying a
    /// complete backup.
    func allPayloads() -> (payloads: [VaultPayload], decryptFailureCount: Int) {
        guard let context else { return ([], 0) }
        let descriptor = FetchDescriptor<VaultItemRecord>()
        guard let records = try? context.fetch(descriptor) else { return ([], 0) }
        var out: [VaultPayload] = []
        var failures = 0
        for record in records {
            if let payload = try? decryptPayload(record) {
                out.append(payload)
            } else {
                failures += 1
            }
        }
        return (out, failures)
    }

    func filteredRows(kind: VaultItemKind?, query: String) -> [VaultListRow] {
        cache.filter(kind: kind, query: query)
    }

    func resetContext() {
        context = nil
    }

    /// Decrypt every item with `oldEnc` / re-encrypt with `newEnc`, and rebuild search tokens.
    func rekeyAllItems(oldEnc: SymmetricKey, newEnc: SymmetricKey, newSearch: SymmetricKey) throws {
        guard let context else { throw VaultCryptoError.vaultLocked }
        let descriptor = FetchDescriptor<VaultItemRecord>()
        let records = try context.fetch(descriptor)

        for record in records {
            let payload: VaultPayload
            let envelope: VaultEnvelope

            if record.schemaVersion >= 1, !record.encryptedPayload.isEmpty {
                let payloadData = try VaultCrypto.decrypt(record.encryptedPayload, key: oldEnc)
                payload = try JSONDecoder().decode(VaultPayload.self, from: payloadData)
                if !record.encryptedEnvelope.isEmpty {
                    let envelopeData = try VaultCrypto.decrypt(record.encryptedEnvelope, key: oldEnc)
                    envelope = try JSONDecoder().decode(VaultEnvelope.self, from: envelopeData)
                } else {
                    envelope = VaultEnvelope(from: payload)
                }
            } else if let legacy = record.legacyPayloadData {
                payload = try JSONDecoder().decode(VaultPayload.self, from: legacy)
                envelope = VaultEnvelope(from: payload)
            } else {
                continue
            }

            record.encryptedEnvelope = try VaultCrypto.encrypt(try JSONEncoder().encode(envelope), key: newEnc)
            record.encryptedPayload = try VaultCrypto.encrypt(try JSONEncoder().encode(payload), key: newEnc)
            record.schemaVersion = 1
            record.legacyPayloadData = nil
            record.updatedAt = Date()
            replaceSearchTokens(for: record.id, envelope: envelope, payload: payload, searchKey: newSearch)
        }

        try context.save()
        Task { await warmCache(); await syncAutofillIndex() }
    }

    // MARK: - Private

    private func fetchRecord(id: UUID) -> VaultItemRecord? {
        guard let context else { return nil }
        var descriptor = FetchDescriptor<VaultItemRecord>(predicate: #Predicate { $0.id == id })
        descriptor.fetchLimit = 1
        return try? context.fetch(descriptor).first
    }

    private func decryptEnvelope(_ record: VaultItemRecord) throws -> VaultEnvelope {
        if record.schemaVersion >= 1, !record.encryptedEnvelope.isEmpty {
            let key = try VaultSession.shared.requireEncKey()
            let data = try VaultCrypto.decrypt(record.encryptedEnvelope, key: key)
            return try JSONDecoder().decode(VaultEnvelope.self, from: data)
        }
        if let legacy = record.legacyPayloadData {
            let payload = try JSONDecoder().decode(VaultPayload.self, from: legacy)
            return VaultEnvelope(from: payload)
        }
        throw VaultCryptoError.invalidCiphertext
    }

    private func decryptPayload(_ record: VaultItemRecord) throws -> VaultPayload {
        if record.schemaVersion >= 1, !record.encryptedPayload.isEmpty {
            let key = try VaultSession.shared.requireEncKey()
            let data = try VaultCrypto.decrypt(record.encryptedPayload, key: key)
            return try JSONDecoder().decode(VaultPayload.self, from: data)
        }
        if let legacy = record.legacyPayloadData {
            return try JSONDecoder().decode(VaultPayload.self, from: legacy)
        }
        throw VaultCryptoError.invalidCiphertext
    }

    private func replaceSearchTokens(for itemId: UUID, envelope: VaultEnvelope, payload: VaultPayload, searchKey: SymmetricKey) {
        guard let context else { return }
        deleteSearchTokens(for: itemId)
        var texts = [envelope.displayName, envelope.subtitle]
        texts.append(contentsOf: VaultSearchIndex.searchableStrings(from: payload))
        var hashes = Set<String>()
        for text in texts where !text.isEmpty {
            for hash in VaultSearchIndex.tokenHashes(for: text, searchKey: searchKey) {
                hashes.insert(hash)
            }
        }
        for hash in hashes {
            context.insert(VaultSearchToken(itemId: itemId, tokenHash: hash))
        }
    }

    private func deleteSearchTokens(for itemId: UUID) {
        guard let context else { return }
        let descriptor = FetchDescriptor<VaultSearchToken>(predicate: #Predicate { $0.itemId == itemId })
        if let tokens = try? context.fetch(descriptor) {
            for token in tokens { context.delete(token) }
        }
    }

    private func migrateLegacyIfNeeded() async {
        guard let context else { return }
        let descriptor = FetchDescriptor<VaultItemRecord>()
        guard let records = try? context.fetch(descriptor) else { return }
        guard records.contains(where: { $0.schemaVersion < 1 && ($0.legacyPayloadData != nil || !$0.encryptedPayload.isEmpty) }) else { return }
        guard VaultSession.shared.isUnlocked else { return }

        for record in records where record.schemaVersion < 1 {
            guard let legacy = record.legacyPayloadData, !legacy.isEmpty else { continue }
            guard let payload = try? JSONDecoder().decode(VaultPayload.self, from: legacy) else { continue }
            _ = try? save(payload: payload, id: record.id)
        }
        try? context.save()
    }

    private func syncAutofillIndex() async {
        guard VaultSession.shared.isUnlocked, let context else { return }
        let descriptor = FetchDescriptor<VaultItemRecord>()
        guard let records = try? context.fetch(descriptor) else { return }
        var pairs: [(UUID, VaultPayload)] = []
        for record in records {
            guard let payload = try? decryptPayload(record) else { continue }
            switch payload {
            case .login, .card, .bankAccount:
                pairs.append((record.id, payload))
            default:
                continue
            }
        }
        AutofillVaultSync.rebuild(from: pairs)
    }
}
