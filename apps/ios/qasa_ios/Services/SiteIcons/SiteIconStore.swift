import CryptoKit
import Foundation
import Observation
import UIKit

/// The site icons the views show (`docs/site-icons.md`, When). A tile asks for its login's
/// website when drawn and a save asks for the saved one; a host with no fresh cache entry is
/// queued, at most 4 asked at a time, each host once. Nothing is asked unless the vault is
/// unlocked and the Website icons setting is on. Icons decrypted from the cache live in
/// memory only while unlocked; `forgetInMemory()` drops them at lock.
@Observable
final class SiteIconStore {
    static let shared = SiteIconStore()

    static let maxConcurrent = 4

    /// The app hosting the unit tests never asks real sites (tests inject their own store).
    static let isUnitTestHost = ProcessInfo.processInfo.environment["XCTestConfigurationFilePath"] != nil

    /// Host → icon, read by the tiles (an arrival redraws them).
    private(set) var icons: [String: UIImage] = [:]

    @ObservationIgnored private let cache: SiteIconCache
    @ObservationIgnored private let fetch: @Sendable (String) async -> SiteIconFetchResult
    @ObservationIgnored private let isEnabled: () -> Bool
    @ObservationIgnored private let vaultKey: () -> SymmetricKey?
    /// Hosts whose cache entry was read since unlock, with when they need asking again.
    @ObservationIgnored private var known: [String: SiteIconCacheEntry] = [:]
    @ObservationIgnored private var queue: [String] = []
    @ObservationIgnored private var inFlight: [String: Task<Void, Never>] = [:]
    /// Bumped by every forget/clear so an answer that was in flight is dropped.
    @ObservationIgnored private var generation = 0

    init(
        cache: SiteIconCache = .shared,
        fetch: @escaping @Sendable (String) async -> SiteIconFetchResult = { await SiteIconFetcher.shared.fetch(host: $0) },
        isEnabled: @escaping () -> Bool = { AppPreferences.shared.siteIconsEnabled && !SiteIconStore.isUnitTestHost },
        vaultKey: @escaping () -> SymmetricKey? = { try? VaultSession.shared.requireEncKey() }
    ) {
        self.cache = cache
        self.fetch = fetch
        self.isEnabled = isEnabled
        self.vaultKey = vaultKey
    }

    /// The icon to draw for `host`, or nil (the letter shows).
    func icon(forHost host: String?) -> UIImage? {
        guard let host, isEnabled() else { return nil }
        return icons[host]
    }

    /// Loads `website`'s cached icon and, when its host has no fresh entry, queues asking it.
    func request(website: String) {
        guard isEnabled(), let key = vaultKey(), let host = SiteIconRules.host(forWebsite: website) else { return }
        if known[host] == nil {
            if let entry = cache.entry(host: host, key: key) {
                known[host] = entry
                if let png = entry.png, let image = UIImage(data: png) { icons[host] = image }
            }
        }
        if let entry = known[host], entry.isFresh(at: cache.now()) { return }
        guard inFlight[host] == nil, !queue.contains(host) else { return }
        queue.append(host)
        pump()
    }

    private func pump() {
        while inFlight.count < Self.maxConcurrent, !queue.isEmpty {
            let host = queue.removeFirst()
            let fetch = self.fetch
            let started = generation
            inFlight[host] = Task { [weak self] in
                let result = await Task.detached(priority: .utility) { await fetch(host) }.value
                self?.finish(host: host, result: result, generation: started)
            }
        }
    }

    private func finish(host: String, result: SiteIconFetchResult, generation started: Int) {
        guard started == generation else { return }
        inFlight[host] = nil
        defer { pump() }
        guard isEnabled(), let key = vaultKey(), let entry = cache.store(host: host, result: result, key: key) else { return }
        known[host] = entry
        if let png = entry.png, let image = UIImage(data: png) {
            icons[host] = image
        } else {
            icons[host] = nil
        }
    }

    /// Drops everything decrypted or queued (the vault locked). The cache stays on disk.
    func forgetInMemory() {
        generation += 1
        inFlight.values.forEach { $0.cancel() }
        inFlight = [:]
        queue = []
        known = [:]
        if !icons.isEmpty { icons = [:] }
    }

    /// Forgets and deletes the cache (setting off, vault reset or sign out, new master password).
    func clearAll() {
        forgetInMemory()
        cache.clear()
    }
}
