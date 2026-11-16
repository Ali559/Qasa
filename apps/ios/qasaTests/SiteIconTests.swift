import CryptoKit
import Foundation
import Testing
@testable import qasa

/// Site icons (`docs/site-icons.md`): every case in `docs/site-icons-vectors.json`, which
/// Android, the desktop and the extension run too, plus the cache's TTLs and sealing.
@MainActor
struct SiteIconTests {
    struct Vectors: Decodable {
        struct HostCase: Decodable { let website: String; let host: String? }
        struct LetterCase: Decodable { let name: String; let letter: String }
        struct PickCase: Decodable { let url: String; let html: String; let icons: [String] }
        let host: [HostCase]
        let letter: [LetterCase]
        let pickIcons: [PickCase]
    }

    static let vectors: Vectors = {
        // qasaTests/ → apps/ios → apps → repository root; read in place so it stays the one source.
        let root = URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent().deletingLastPathComponent()
            .deletingLastPathComponent().deletingLastPathComponent()
        let data = try! Data(contentsOf: root.appendingPathComponent("docs/site-icons-vectors.json"))
        return try! JSONDecoder().decode(Vectors.self, from: data)
    }()

    @Test func hostVectors() {
        #expect(!Self.vectors.host.isEmpty)
        for c in Self.vectors.host {
            #expect(SiteIconRules.host(forWebsite: c.website) == c.host, "website \(c.website)")
        }
    }

    @Test func letterVectors() {
        #expect(!Self.vectors.letter.isEmpty)
        for c in Self.vectors.letter {
            #expect(SiteIconRules.letter(for: c.name) == c.letter, "name \(c.name)")
            #expect(SiteLogo(name: c.name).letter == c.letter)
        }
    }

    @Test func pickIconsVectors() throws {
        #expect(!Self.vectors.pickIcons.isEmpty)
        for c in Self.vectors.pickIcons {
            let page = try #require(URL(string: c.url))
            let picked = SiteIconRules.pickIcons(html: c.html, pageURL: page).map(\.absoluteString)
            #expect(picked == c.icons, "page \(c.url): \(c.html)")
        }
    }

    @Test func redirectTargetsAreCheckedLikeHosts() {
        #expect(SiteIconRules.isAskable(URL(string: "https://github.com/x")!))
        #expect(!SiteIconRules.isAskable(URL(string: "http://github.com/")!))
        #expect(!SiteIconRules.isAskable(URL(string: "https://192.168.1.1/")!))
        #expect(!SiteIconRules.isAskable(URL(string: "https://[::1]/")!))
        #expect(!SiteIconRules.isAskable(URL(string: "https://nas.local/")!))
    }

    @Test func dataIconsArePngOrIcoOnly() {
        let page = URL(string: "https://example.com/")!
        let html = #"<link rel="icon" href="data:image/png;base64,AAAA"><link rel="icon" href="data:image/svg+xml;base64,AAAA">"#
        #expect(SiteIconRules.pickIcons(html: html, pageURL: page).map(\.absoluteString) == ["data:image/png;base64,AAAA"])
    }

    @Test func normalisesToA64PixelPNG() throws {
        // A 1x1 PNG is under 16 × 16: refused.
        let tiny = Data(base64Encoded: "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==")!
        #expect(SiteIconImage.normalizedPNG(from: tiny) == nil)
        #expect(SiteIconImage.normalizedPNG(from: Data("<svg/>".utf8)) == nil)
    }

    // MARK: - Cache

    final class Clock { var now = Date(timeIntervalSince1970: 1_800_000_000) }

    private func makeCache(_ clock: Clock) -> SiteIconCache {
        let dir = FileManager.default.temporaryDirectory.appendingPathComponent("site-icons-\(UUID().uuidString)")
        return SiteIconCache(directory: dir, now: { clock.now })
    }

    @Test func ttlsPerKind() {
        let clock = Clock()
        let cache = makeCache(clock)
        defer { cache.clear() }
        let key = SymmetricKey(size: .bits256)
        let day: TimeInterval = 86_400

        cache.store(host: "icon.com", result: .icon(Data([1, 2, 3])), key: key)
        cache.store(host: "none.com", result: .none, key: key)
        cache.store(host: "fail.com", result: .failure, key: key)
        #expect(cache.isFresh(host: "icon.com", key: key))
        #expect(cache.isFresh(host: "none.com", key: key))
        #expect(cache.isFresh(host: "fail.com", key: key))
        #expect(!cache.isFresh(host: "never.com", key: key))

        clock.now += day + 1
        #expect(!cache.isFresh(host: "fail.com", key: key))
        #expect(cache.isFresh(host: "none.com", key: key))

        clock.now += 6 * day
        #expect(!cache.isFresh(host: "none.com", key: key))
        #expect(cache.isFresh(host: "icon.com", key: key))

        clock.now += 23 * day
        #expect(!cache.isFresh(host: "icon.com", key: key))
        // A stale icon still shows until it is refreshed.
        #expect(cache.entry(host: "icon.com", key: key)?.png == Data([1, 2, 3]))
    }

    @Test func failedRefreshKeepsTheIconAndRetriesInADay() {
        let clock = Clock()
        let cache = makeCache(clock)
        defer { cache.clear() }
        let key = SymmetricKey(size: .bits256)
        cache.store(host: "a.com", result: .icon(Data([9])), key: key)
        clock.now += 31 * 86_400
        cache.store(host: "a.com", result: .failure, key: key)
        #expect(cache.entry(host: "a.com", key: key)?.png == Data([9]))
        #expect(cache.isFresh(host: "a.com", key: key))
        clock.now += 86_400 + 1
        #expect(!cache.isFresh(host: "a.com", key: key))
    }

    @Test func sealedWithTheVaultKey() throws {
        let clock = Clock()
        let cache = makeCache(clock)
        defer { cache.clear() }
        let key = SymmetricKey(size: .bits256)
        cache.store(host: "secret-host.com", result: .icon(Data([7])), key: key)
        let files = try FileManager.default.contentsOfDirectory(atPath: cache.directory.path)
        #expect(files.count == 1)
        #expect(!files[0].contains("secret"))
        let blob = try Data(contentsOf: cache.directory.appendingPathComponent(files[0]))
        #expect(blob.range(of: Data("secret-host".utf8)) == nil)
        // Another key finds nothing.
        #expect(cache.entry(host: "secret-host.com", key: SymmetricKey(size: .bits256)) == nil)
        let values = try cache.directory.resourceValues(forKeys: [.isExcludedFromBackupKey])
        #expect(values.isExcludedFromBackup == true)
        cache.clear()
        #expect(!FileManager.default.fileExists(atPath: cache.directory.path))
    }

    @Test func storeAsksEachHostOnceAndOnlyWhenEnabled() async throws {
        let clock = Clock()
        let cache = makeCache(clock)
        defer { cache.clear() }
        let key = SymmetricKey(size: .bits256)
        let asked = AskLog()
        var enabled = true
        let store = SiteIconStore(
            cache: cache,
            fetch: { host in await asked.add(host); return .none },
            isEnabled: { enabled },
            vaultKey: { key }
        )
        store.request(website: "https://github.com/login")
        store.request(website: "github.com")
        store.request(website: "http://192.168.1.1")
        for _ in 0..<100 where !cache.isFresh(host: "github.com", key: key) {
            try await Task.sleep(nanoseconds: 10_000_000)
        }
        #expect(await asked.hosts == ["github.com"])
        store.request(website: "github.com")
        enabled = false
        store.request(website: "gitlab.com")
        try await Task.sleep(nanoseconds: 50_000_000)
        #expect(await asked.hosts == ["github.com"])
    }

    actor AskLog {
        var hosts: [String] = []
        func add(_ host: String) { hosts.append(host) }
    }
}
