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
        if let key = try? VaultSession.shared.requireEncKey() {
            try? Self.encryptLegacyCollectionNames(context: context, key: key)
        }
        await warmCache()
    }

    func warmCache() async {
        guard let context else { return }
        importPendingSaves()
        let descriptor = FetchDescriptor<VaultItemRecord>(
            predicate: VaultItemRecord.live,
            sortBy: [SortDescriptor(\.updatedAt, order: .reverse)]
        )
        guard let records = try? context.fetch(descriptor) else { return }
        // Items that arrived over Wi‑Fi sync carry no search tokens (they are keyed per
        // device's search key, which only exists while unlocked).
        rebuildMissingSearchTokens(records)
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

    /// Where a decrypted payload being encrypted comes from. Rows that arrive as ciphertext
    /// (Wi‑Fi sync, backup restore) never pass through here: they get the structural check
    /// in `VaultSyncStore.apply` (`VaultRecordGuard`) and are stored unchanged.
    enum WriteOrigin {
        /// The add/edit form: normalized, then refused (nothing written) while any field
        /// issue remains.
        case user
        /// CSV import: normalized and always stored, even with issues (they are counted
        /// and shown on the item; the editor makes the user fix them on the next save).
        case csvImport
        /// An existing item the app (not the user) rewrites without touching its fields:
        /// re-encrypting a legacy plaintext row at unlock, or taking it out of a deleted
        /// folder. Stored as is, so an older item with issues is never blocked or rewritten.
        case storedAsIs
    }

    /// A user save (add/edit form). Throws `VaultValidationError` and writes nothing while
    /// the normalized payload has any issue.
    func save(payload: VaultPayload, id: UUID? = nil) throws -> VaultListRow {
        try write(payload: payload, id: id, origin: .user).row
    }

    /// A CSV-imported item: stored normalized, issues or not. `flagged` is true when it
    /// still has issues, so the import can say how many need checking.
    func importItem(_ payload: VaultPayload) throws -> (row: VaultListRow, flagged: Bool) {
        let (row, issues) = try write(payload: payload, id: nil, origin: .csvImport)
        return (row, !issues.isEmpty)
    }

    /// Replaces only a login's password (rotating a breached one). The other fields are not
    /// the user's edit here, so an older item with issues is not blocked (Android parity:
    /// `BreachViewModel.rotatePassword`).
    func rotatePassword(itemId: UUID, to password: String) throws {
        guard case .login(var f) = try self.payload(for: itemId) else { return }
        f.password = password
        _ = try write(payload: .login(f), id: itemId, origin: .storedAsIs)
    }

    /// Takes an item out of its folder (the folder is being deleted). Not a user edit of its
    /// fields, so it is stored as is.
    func removeFromCollection(itemId: UUID) throws {
        var item = try self.payload(for: itemId)
        switch item {
        case .login(var f):
            f.collectionId = nil
            item = .login(f)
        case .secureNote(var f):
            f.collectionId = nil
            item = .secureNote(f)
        default:
            break
        }
        _ = try write(payload: item, id: itemId, origin: .storedAsIs)
    }

    /// The single write path for decrypted payloads: normalize and validate per `origin`,
    /// then encrypt.
    private func write(payload input: VaultPayload, id: UUID?, origin: WriteOrigin) throws -> (row: VaultListRow, issues: [FfiFieldIssue]) {
        guard let context else { throw VaultCryptoError.vaultLocked }
        let payload: VaultPayload
        let issues: [FfiFieldIssue]
        switch origin {
        case .user:
            payload = try VaultValidation.normalized(input)
            issues = try VaultValidation.validate(payload)
            guard issues.isEmpty else { throw VaultValidationError(issues: issues) }
        case .csvImport:
            payload = try VaultValidation.normalized(input)
            issues = try VaultValidation.validate(payload)
        case .storedAsIs:
            payload = input
            issues = []
        }
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
        return (row, issues)
    }

    /// Deletes as a tombstone: the row stays (empty) so the delete reaches other devices
    /// over Wi‑Fi sync, and every read skips it.
    func delete(id: UUID) throws {
        guard let context, let record = fetchRecord(id: id) else { return }
        deleteSearchTokens(for: id)
        Self.markDeleted(record, at: Date())
        try context.save()
        cache.remove(id: id)
        Task { await syncAutofillIndex() }
    }

    /// Clears an item's data and marks it deleted (plan §6.3).
    static func markDeleted(_ record: VaultItemRecord, at now: Date) {
        record.encryptedEnvelope = Data()
        record.encryptedPayload = Data()
        record.legacyPayloadData = nil
        record.deletedAt = now
        record.updatedAt = now
    }

    // MARK: - Folders
    //
    // Folder names are vault-key ciphertext (`VaultCollection.nameEncrypted`, plan
    // decision 5), in the same blob format as item envelopes, so Wi‑Fi sync carries them
    // as stored. Every read goes through `collectionName(_:)`.

    static func encryptCollectionName(_ name: String, key: SymmetricKey) throws -> Data {
        try VaultCrypto.encrypt(Data(name.utf8), key: key)
    }

    static func decryptCollectionName(_ blob: Data, key: SymmetricKey) throws -> String {
        guard let name = String(data: try VaultCrypto.decrypt(blob, key: key), encoding: .utf8) else {
            throw VaultCryptoError.invalidCiphertext
        }
        return name
    }

    /// The folder's name, decrypted with the session key. `nil` while locked, for a
    /// tombstone, or when the name doesn't decrypt (skipped like unreadable items).
    static func collectionName(_ collection: VaultCollection) -> String? {
        guard collection.deletedAt == nil else { return nil }
        if let blob = collection.nameEncrypted, !blob.isEmpty {
            guard let key = try? VaultSession.shared.requireEncKey() else { return nil }
            return try? decryptCollectionName(blob, key: key)
        }
        // Saved before names were encrypted and not migrated yet (that runs at unlock).
        return collection.name.isEmpty ? nil : collection.name
    }

    struct NamedCollection: Identifiable {
        let collection: VaultCollection
        let name: String
        var id: UUID { collection.id }
    }

    /// Live folders with readable names, sorted by name (the store can't sort ciphertext).
    static func named(_ collections: [VaultCollection]) -> [NamedCollection] {
        collections
            .compactMap { c in collectionName(c).map { NamedCollection(collection: c, name: $0) } }
            .sorted { $0.name.localizedStandardCompare($1.name) == .orderedAscending }
    }

    /// Inserts a new folder with its name encrypted under the session key. The caller saves.
    @discardableResult
    static func makeCollection(name: String, context: ModelContext) throws -> VaultCollection {
        let key = try VaultSession.shared.requireEncKey()
        let collection = VaultCollection(nameEncrypted: try encryptCollectionName(name, key: key))
        context.insert(collection)
        return collection
    }

    static func renameCollection(_ collection: VaultCollection, to name: String, context: ModelContext) throws {
        let key = try VaultSession.shared.requireEncKey()
        collection.nameEncrypted = try encryptCollectionName(name, key: key)
        collection.name = ""
        collection.updatedAt = Date()
        try context.save()
    }

    /// Deletes a folder as a tombstone, like items. A tombstone keeps no name at all.
    static func deleteCollection(_ collection: VaultCollection, context: ModelContext) {
        let now = Date()
        collection.name = ""
        collection.nameEncrypted = nil
        collection.deletedAt = now
        collection.updatedAt = now
        try? context.save()
    }

    /// Encrypts folder names saved before names were encrypted and blanks the plaintext;
    /// tombstones just lose theirs. Runs at every unlock; after the first one there is
    /// nothing left to do. Not an edit: `updatedAt` stays, so this never wins a sync merge.
    static func encryptLegacyCollectionNames(context: ModelContext, key: SymmetricKey) throws {
        let legacy = try context.fetch(FetchDescriptor<VaultCollection>(predicate: #Predicate { $0.name != "" }))
        guard !legacy.isEmpty else { return }
        do {
            for collection in legacy {
                if collection.deletedAt == nil {
                    collection.nameEncrypted = try encryptCollectionName(collection.name, key: key)
                } else {
                    collection.nameEncrypted = nil
                }
                collection.name = ""
            }
            try context.save()
        } catch {
            context.rollback()
            throw error
        }
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
        let descriptor = FetchDescriptor<VaultItemRecord>(predicate: VaultItemRecord.live)
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
        let descriptor = FetchDescriptor<VaultItemRecord>(predicate: VaultItemRecord.live)
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

    /// Decrypt every item and folder name with `oldEnc` / re-encrypt with `newEnc`, and
    /// rebuild search tokens. All or nothing: anything unreadable under `oldEnc` throws
    /// before a single row is saved.
    func rekeyAllItems(oldEnc: SymmetricKey, newEnc: SymmetricKey, newSearch: SymmetricKey) throws {
        guard let context else { throw VaultCryptoError.vaultLocked }
        do {
            try rekeyRecords(oldEnc: oldEnc, newEnc: newEnc, newSearch: newSearch, context: context)
            try context.save()
        } catch {
            context.rollback()
            throw error
        }
        Task { await warmCache(); await syncAutofillIndex() }
    }

    /// Reads everything under `oldEnc` first and only then writes, so an unreadable item or
    /// folder name throws before any row changes (a re-key never silently drops one).
    private func rekeyRecords(oldEnc: SymmetricKey, newEnc: SymmetricKey, newSearch: SymmetricKey, context: ModelContext) throws {
        let records = try context.fetch(FetchDescriptor<VaultItemRecord>(predicate: VaultItemRecord.live))
        var decoded: [(VaultItemRecord, VaultPayload, VaultEnvelope)] = []
        for record in records {
            if record.schemaVersion >= 1, !record.encryptedPayload.isEmpty {
                let payloadData = try VaultCrypto.decrypt(record.encryptedPayload, key: oldEnc)
                let payload = try JSONDecoder().decode(VaultPayload.self, from: payloadData)
                let envelope: VaultEnvelope
                if !record.encryptedEnvelope.isEmpty {
                    let envelopeData = try VaultCrypto.decrypt(record.encryptedEnvelope, key: oldEnc)
                    envelope = try JSONDecoder().decode(VaultEnvelope.self, from: envelopeData)
                } else {
                    envelope = VaultEnvelope(from: payload)
                }
                decoded.append((record, payload, envelope))
            } else if let legacy = record.legacyPayloadData {
                let payload = try JSONDecoder().decode(VaultPayload.self, from: legacy)
                decoded.append((record, payload, VaultEnvelope(from: payload)))
            }
        }

        // Folder names, like the desktop: `updatedAt` stays (a re-key is not a rename).
        let collections = try context.fetch(FetchDescriptor<VaultCollection>())
        var names: [(VaultCollection, String)] = []
        for collection in collections where collection.deletedAt == nil {
            if let blob = collection.nameEncrypted, !blob.isEmpty {
                names.append((collection, try Self.decryptCollectionName(blob, key: oldEnc)))
            } else if !collection.name.isEmpty {
                names.append((collection, collection.name))
            }
        }

        // Encrypt everything before touching a row.
        var sealedItems: [(VaultItemRecord, Data, Data, VaultPayload, VaultEnvelope)] = []
        for (record, payload, envelope) in decoded {
            sealedItems.append((
                record,
                try VaultCrypto.encrypt(try JSONEncoder().encode(envelope), key: newEnc),
                try VaultCrypto.encrypt(try JSONEncoder().encode(payload), key: newEnc),
                payload,
                envelope
            ))
        }
        let sealedNames = try names.map { ($0.0, try Self.encryptCollectionName($0.1, key: newEnc)) }

        let now = Date()
        for (record, envelopeBlob, payloadBlob, payload, envelope) in sealedItems {
            record.encryptedEnvelope = envelopeBlob
            record.encryptedPayload = payloadBlob
            record.schemaVersion = 1
            record.legacyPayloadData = nil
            record.updatedAt = now
            replaceSearchTokens(for: record.id, envelope: envelope, payload: payload, searchKey: newSearch)
        }
        for (collection, blob) in sealedNames {
            collection.nameEncrypted = blob
            collection.name = ""
        }
        for collection in collections where collection.deletedAt != nil {
            collection.name = ""
            collection.nameEncrypted = nil
        }
    }

    // MARK: - Private

    private func fetchRecord(id: UUID) -> VaultItemRecord? {
        guard let context else { return nil }
        var descriptor = FetchDescriptor<VaultItemRecord>(predicate: #Predicate { $0.id == id && $0.deletedAt == nil })
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

    /// Builds search tokens for live items that have none (synced items, or items whose
    /// tokens a sync update dropped). Needs the vault unlocked.
    private func rebuildMissingSearchTokens(_ records: [VaultItemRecord]) {
        guard let context, let searchKey = try? VaultSession.shared.requireSearchKey() else { return }
        let tokens = (try? context.fetch(FetchDescriptor<VaultSearchToken>())) ?? []
        let indexed = Set(tokens.map(\.itemId))
        var changed = false
        for record in records where !indexed.contains(record.id) {
            guard let payload = try? decryptPayload(record) else { continue }
            let envelope = (try? decryptEnvelope(record)) ?? VaultEnvelope(from: payload)
            replaceSearchTokens(for: record.id, envelope: envelope, payload: payload, searchKey: searchKey)
            changed = true
        }
        if changed { try? context.save() }
    }

    private func migrateLegacyIfNeeded() async {
        guard let context else { return }
        let descriptor = FetchDescriptor<VaultItemRecord>(predicate: VaultItemRecord.live)
        guard let records = try? context.fetch(descriptor) else { return }
        guard records.contains(where: { $0.schemaVersion < 1 && ($0.legacyPayloadData != nil || !$0.encryptedPayload.isEmpty) }) else { return }
        guard VaultSession.shared.isUnlocked else { return }

        for record in records where record.schemaVersion < 1 {
            guard let legacy = record.legacyPayloadData, !legacy.isEmpty else { continue }
            guard let payload = try? JSONDecoder().decode(VaultPayload.self, from: legacy) else { continue }
            _ = try? write(payload: payload, id: record.id, origin: .storedAsIs)
        }
        try? context.save()
    }

    // MARK: logins saved from the AutoFill extension (iOS 26.2, design rule 6c)

    /// What to do with a login the extension saved, against the vault's logins.
    enum PendingSaveAction: Equatable {
        /// Already saved with this password.
        case skip
        /// The same site and username with another password: the user chose to save it.
        case updatePassword(UUID)
        case create
    }

    static func pendingSaveAction(for login: PendingSaveInbox.Login, existing: [(id: UUID, fields: LoginFields)]) -> PendingSaveAction {
        let host = AutofillContextMatcher.normalizeHost(login.website)
        let user = login.username.trimmingCharacters(in: .whitespaces).lowercased()
        let sameSite = existing.filter { pair in
            host != nil && AutofillContextMatcher.normalizeHost(pair.fields.website) == host
        }
        let sameUser = sameSite.filter { pair in
            let f = pair.fields
            return [f.username, f.email].contains { $0.trimmingCharacters(in: .whitespaces).lowercased() == user }
        }
        if sameUser.contains(where: { $0.fields.password == login.password }) { return .skip }
        if !user.isEmpty, let match = sameUser.first { return .updatePassword(match.id) }
        return .create
    }

    /// Imports and clears `PendingSaveInbox` (sealed with the vault key, so only while
    /// unlocked). New logins are stored like an import; a known login gets its new password.
    func importPendingSaves() {
        guard PendingSaveInbox.hasPending(), VaultSession.shared.isUnlocked, let context,
              let key = try? VaultSession.shared.requireEncKey() else { return }
        let waiting = PendingSaveInbox.drain(key: key)
        guard !waiting.isEmpty else { return }
        let records = (try? context.fetch(FetchDescriptor<VaultItemRecord>(predicate: VaultItemRecord.live))) ?? []
        var logins: [(id: UUID, fields: LoginFields)] = records.compactMap { record in
            guard case .login(let f) = try? decryptPayload(record) else { return nil }
            return (record.id, f)
        }
        for login in waiting {
            switch Self.pendingSaveAction(for: login, existing: logins) {
            case .skip:
                continue
            case .updatePassword(let id):
                try? rotatePassword(itemId: id, to: login.password)
            case .create:
                let isEmail = login.username.contains("@")
                let fields = LoginFields(
                    itemName: login.title,
                    email: isEmail ? login.username : "",
                    username: isEmail ? "" : login.username,
                    password: login.password,
                    website: login.website
                )
                if let row = try? importItem(.login(fields)).row { logins.append((row.id, fields)) }
            }
        }
    }

    /// Unit tests turn this off so they never touch the AutoFill extension's real index.
    var autofillSyncEnabled = true

    private func syncAutofillIndex() async {
        guard autofillSyncEnabled, VaultSession.shared.isUnlocked, let context else { return }
        let descriptor = FetchDescriptor<VaultItemRecord>(predicate: VaultItemRecord.live)
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
