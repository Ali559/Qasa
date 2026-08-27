import CryptoKit
import Foundation

struct BreachCheckResult: Identifiable, Hashable {
    let id = UUID()
    let email: String
    let breached: Bool
    let sources: [String]
}

struct BreachMonitorRegisterResponse: Decodable {
    let ok: Bool?
    let matched: Bool?
    let alerted: Bool?
    let details_require_premium: Bool?
}

enum BreachMonitorService {
    static func fullHash(for email: String) -> String? {
        let normalized = email.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        guard normalized.contains("@") else { return nil }
        let digest = SHA256.hash(data: Data(normalized.utf8))
        return digest.map { String(format: "%02x", $0) }.joined()
    }

    static func hashParts(for email: String) -> (prefix: String, suffix: String)? {
        guard let hex = fullHash(for: email), hex.count == 64 else { return nil }
        let prefix = String(hex.prefix(5))
        let suffix = String(hex.dropFirst(5))
        return (prefix, suffix)
    }

    /// Upload SHA-256 of normalized email for server-side monitoring (free + premium).
    /// On match the server sends a **device push**, not email.
    static func registerMonitor(email: String, labelHint: String? = nil) async throws -> BreachMonitorRegisterResponse {
        guard let hash = fullHash(for: email) else {
            throw URLError(.badURL)
        }
        struct Body: Encodable {
            let email_hash: String
            let label_hint: String?
        }
        return try await QasaAPIClient.shared.request(
            QasaConfig.breachMonitorURL,
            method: "POST",
            body: Body(email_hash: hash, label_hint: labelHint)
        )
    }

    static func unregisterMonitor(email: String) async {
        guard let hash = fullHash(for: email) else { return }
        struct Body: Encodable { let email_hash: String }
        struct Ok: Decodable { let ok: Bool? }
        _ = try? await QasaAPIClient.shared.request(
            QasaConfig.breachMonitorURL,
            method: "DELETE",
            body: Body(email_hash: hash)
        ) as Ok
    }

    static func checkEmail(_ email: String) async throws -> BreachCheckResult {
        guard let parts = hashParts(for: email) else {
            return BreachCheckResult(email: email, breached: false, sources: [])
        }
        let response: BreachPrefixResponse = try await QasaAPIClient.shared.request(
            QasaConfig.breachPrefixURL(parts.prefix)
        )
        let matches = response.suffixes.filter { $0.suffix.lowercased() == parts.suffix }
        return BreachCheckResult(
            email: email,
            breached: !matches.isEmpty,
            sources: matches.map(\.source)
        )
    }

    static func checkEmails(_ emails: [String]) async -> [BreachCheckResult] {
        var results: [BreachCheckResult] = []
        for email in emails {
            if let result = try? await checkEmail(email) {
                results.append(result)
            } else {
                results.append(BreachCheckResult(email: email, breached: false, sources: []))
            }
        }
        return results
    }
}
