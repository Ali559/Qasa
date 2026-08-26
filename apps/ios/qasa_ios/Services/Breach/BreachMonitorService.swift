import CryptoKit
import Foundation

struct BreachCheckResult: Identifiable, Hashable {
    let id = UUID()
    let email: String
    let breached: Bool
    let sources: [String]
}

enum BreachMonitorService {
    static func hashParts(for email: String) -> (prefix: String, suffix: String)? {
        let normalized = email.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        guard normalized.contains("@") else { return nil }
        let digest = SHA256.hash(data: Data(normalized.utf8))
        let hex = digest.map { String(format: "%02x", $0) }.joined()
        guard hex.count == 64 else { return nil }
        let prefix = String(hex.prefix(5))
        let suffix = String(hex.dropFirst(5))
        return (prefix, suffix)
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
