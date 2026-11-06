import CryptoKit
import Foundation

/// Breached-password check against HIBP Pwned Passwords (no API key). Premium-only: callers
/// go through `PasswordHealthAnalyzer.analyzeWithBreaches`, which never calls this on Free.
/// k-anonymity: each password is SHA-1 hashed on device and only the first 5 hex characters
/// leave the phone. The Qasa server is not involved. Mirrors Android `PwnedPasswordsService`.
actor PwnedPasswordsService {
    static let shared = PwnedPasswordsService()

    private nonisolated static let rangeURL = URL(string: "https://api.pwnedpasswords.com/range/")!

    /// Range responses for this process, keyed by prefix. Breach data changes slowly.
    private var cache: [String: [String: Int]] = [:]

    /// Breach count per password, for passwords seen at least once. Throws when any range
    /// can't be fetched, so callers never show a partial "all clear".
    func breachCounts(for passwords: [String]) async throws -> [String: Int] {
        var byPrefix: [String: [(password: String, suffix: String)]] = [:]
        for password in Set(passwords) {
            let hex = Self.sha1Hex(password)
            byPrefix[String(hex.prefix(5)), default: []].append((password, String(hex.dropFirst(5))))
        }

        var counts: [String: Int] = [:]
        for (prefix, entries) in byPrefix {
            let range = try await range(for: prefix)
            for entry in entries {
                if let count = range[entry.suffix], count > 0 {
                    counts[entry.password] = count
                }
            }
        }
        return counts
    }

    /// Drops cached ranges, so nothing from a Premium session lingers after it lapses.
    func clearCache() {
        cache.removeAll()
    }

    private func range(for prefix: String) async throws -> [String: Int] {
        if let cached = cache[prefix] { return cached }
        var request = URLRequest(url: Self.rangeURL.appending(path: prefix))
        // Padding hides how many suffixes the prefix really has.
        request.setValue("true", forHTTPHeaderField: "Add-Padding")
        request.timeoutInterval = 15
        let (data, response) = try await URLSession.shared.data(for: request)
        guard (response as? HTTPURLResponse)?.statusCode == 200 else {
            throw URLError(.badServerResponse)
        }
        let parsed = Self.parseRange(String(decoding: data, as: UTF8.self))
        cache[prefix] = parsed
        return parsed
    }

    /// `SUFFIX:COUNT` per line. Padding rows have a count of 0.
    nonisolated static func parseRange(_ body: String) -> [String: Int] {
        var result: [String: Int] = [:]
        for line in body.split(whereSeparator: \.isNewline) {
            let parts = line.split(separator: ":")
            guard parts.count == 2, let count = Int(parts[1].trimmingCharacters(in: .whitespaces)) else { continue }
            result[parts[0].uppercased()] = count
        }
        return result
    }

    nonisolated static func sha1Hex(_ password: String) -> String {
        Insecure.SHA1.hash(data: Data(password.utf8)).map { String(format: "%02X", $0) }.joined()
    }
}
