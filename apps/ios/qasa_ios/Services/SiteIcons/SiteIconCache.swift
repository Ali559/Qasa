import CryptoKit
import Foundation

/// One host's cached answer: `{ png | none, checkedAt }` (`docs/site-icons.md`, Cache), with
/// a failure kept apart from `none` because it is retried sooner.
nonisolated struct SiteIconCacheEntry: Codable, Equatable, Sendable {
    enum Kind: String, Codable, Sendable { case icon, none, failure }

    let host: String
    let kind: Kind
    /// The 64 × 64 PNG: for `.icon`, and kept on a `.failure` that refreshed an icon.
    let png: Data?
    let checkedAt: Date
    /// `1` once the PNG is shaped for its circle (`docs/site-icons.md` step 7). An entry
    /// written before Phase 11 has none and is shaped when read.
    var shape: Int? = SiteIconCacheEntry.currentShape

    static let currentShape = 1

    init(host: String, kind: Kind, png: Data?, checkedAt: Date, shape: Int? = SiteIconCacheEntry.currentShape) {
        self.host = host
        self.kind = kind
        self.png = png
        self.checkedAt = checkedAt
        self.shape = shape
    }

    /// How long an answer stands before the site is asked again: an icon 30 days (it still
    /// shows meanwhile), `none` 7 days, a network failure 1 day.
    static func ttl(for kind: Kind) -> TimeInterval {
        switch kind {
        case .icon: return 30 * 86_400
        case .none: return 7 * 86_400
        case .failure: return 86_400
        }
    }

    func isFresh(at now: Date) -> Bool {
        now.timeIntervalSince(checkedAt) < Self.ttl(for: kind)
    }
}

/// The per-device site icon cache. Each host is one file in Application Support
/// (`SiteIcons/`, excluded from backup, complete file protection), sealed with the vault key
/// by `VaultCrypto` (AES-GCM, the same AEAD as items). The file name is an HMAC of the host
/// under a key derived from the vault key, so not even the list of hosts is readable without
/// it. Never synced, never in a backup or export.
final class SiteIconCache {
    static let shared = SiteIconCache(directory: SiteIconCache.defaultDirectory)

    static var defaultDirectory: URL {
        let base = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
        return base.appendingPathComponent("SiteIcons", isDirectory: true)
    }

    let directory: URL
    /// Injected so tests can move time.
    var now: () -> Date

    init(directory: URL, now: @escaping () -> Date = Date.init) {
        self.directory = directory
        self.now = now
    }

    func entry(host: String, key: SymmetricKey) -> SiteIconCacheEntry? {
        let file = fileURL(host: host, key: key)
        guard let blob = try? Data(contentsOf: file) else { return nil }
        guard let plain = try? VaultCrypto.decrypt(blob, key: key),
              let entry = try? JSONDecoder().decode(SiteIconCacheEntry.self, from: plain),
              entry.host == host else {
            try? FileManager.default.removeItem(at: file)
            return nil
        }
        guard entry.shape != SiteIconCacheEntry.currentShape else { return entry }
        // Written before step 7: shape its PNG and write it back. No site is asked.
        let shaped = SiteIconCacheEntry(
            host: entry.host, kind: entry.kind,
            png: entry.png.map { SiteIconImage.shapedPNG(fromUnshaped: $0) ?? $0 },
            checkedAt: entry.checkedAt
        )
        try? write(shaped, key: key)
        return shaped
    }

    /// Whether `host` has an answer that still stands (nothing to ask).
    func isFresh(host: String, key: SymmetricKey) -> Bool {
        entry(host: host, key: key)?.isFresh(at: now()) ?? false
    }

    @discardableResult
    func store(host: String, result: SiteIconFetchResult, key: SymmetricKey) -> SiteIconCacheEntry? {
        let newEntry: SiteIconCacheEntry
        switch result {
        case .icon(let png): newEntry = SiteIconCacheEntry(host: host, kind: .icon, png: png, checkedAt: now())
        case .none: newEntry = SiteIconCacheEntry(host: host, kind: .none, png: nil, checkedAt: now())
        case .failure:
            // A failed refresh keeps the icon already shown; the site is asked again in a day.
            newEntry = SiteIconCacheEntry(host: host, kind: .failure, png: entry(host: host, key: key)?.png, checkedAt: now())
        }
        do {
            try write(newEntry, key: key)
            return newEntry
        } catch {
            return nil
        }
    }

    /// Seals and writes `entry` to its host's file. Internal so tests can plant an old entry.
    func write(_ entry: SiteIconCacheEntry, key: SymmetricKey) throws {
        try prepareDirectory()
        let blob = try VaultCrypto.encrypt(try JSONEncoder().encode(entry), key: key)
        try blob.write(to: fileURL(host: entry.host, key: key), options: [.atomic, .completeFileProtection])
    }

    /// Deletes every cached icon (vault reset, sign out, master password change, setting off).
    func clear() {
        try? FileManager.default.removeItem(at: directory)
    }

    private func prepareDirectory() throws {
        guard !FileManager.default.fileExists(atPath: directory.path) else { return }
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        var url = directory
        var values = URLResourceValues()
        values.isExcludedFromBackup = true
        try url.setResourceValues(values)
    }

    private func fileURL(host: String, key: SymmetricKey) -> URL {
        let nameKey = HKDF<SHA256>.deriveKey(
            inputKeyMaterial: key,
            info: Data("qasa.site-icons.file-name".utf8),
            outputByteCount: 32
        )
        let mac = HMAC<SHA256>.authenticationCode(for: Data(host.utf8), using: nameKey)
        let name = Data(mac).map { String(format: "%02x", $0) }.joined()
        return directory.appendingPathComponent(name + ".bin")
    }
}
