import Foundation
import Observation

struct ParsedLicense {
    let accountId: String
    let deviceId: String
    let tier: String
    let deviceLimit: Int
    let expiresAt: Date
    let entitlements: [String]

    var isPremium: Bool { tier.lowercased() == "premium" }
    var hasBreach: Bool { entitlements.contains("breach") || isPremium }
    var hasVPN: Bool { entitlements.contains("vpn") || isPremium }
}

@Observable
final class LicenseManager {
    static let shared = LicenseManager()

    private(set) var parsed: ParsedLicense?
    private(set) var lastError: String?

    var isPremium: Bool { parsed?.isPremium ?? false }
    var deviceLimit: Int { parsed?.deviceLimit ?? 1 }
    var tierLabel: String { parsed?.tier.capitalized ?? "Free" }

    private init() {
        if let token = VaultKeychainStore.licenseToken {
            parsed = Self.parseJWT(token)
        }
    }

    func applyToken(_ token: String) {
        VaultKeychainStore.licenseToken = token
        parsed = Self.parseJWT(token)
        lastError = nil
    }

    func clear() {
        VaultKeychainStore.licenseToken = nil
        parsed = nil
        lastError = nil
    }

    @discardableResult
    func refresh() async -> Bool {
        guard VaultKeychainStore.licenseToken != nil else { return false }
        do {
            let response: LicenseRefreshResponse = try await QasaAPIClient.shared.request(
                QasaConfig.licenseRefreshURL,
                method: "GET",
                authorized: true
            )
            applyToken(response.license_token)
            return true
        } catch {
            lastError = error.localizedDescription
            return false
        }
    }

    static func parseJWT(_ token: String) -> ParsedLicense? {
        let parts = token.split(separator: ".")
        guard parts.count >= 2 else { return nil }
        var payload = String(parts[1])
        payload = payload.replacingOccurrences(of: "-", with: "+")
            .replacingOccurrences(of: "_", with: "/")
        let padding = (4 - payload.count % 4) % 4
        payload += String(repeating: "=", count: padding)
        guard let data = Data(base64Encoded: payload),
              let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
              let sub = json["sub"] as? String,
              let did = json["did"] as? String,
              let tier = json["tier"] as? String,
              let exp = jsonNumber(json["exp"]) else {
            return nil
        }
        let limit = Int(jsonNumber(json["device_limit"]) ?? 1)
        let entitlements = json["entitlements"] as? [String] ?? []
        return ParsedLicense(
            accountId: sub,
            deviceId: did,
            tier: tier,
            deviceLimit: max(limit, 1),
            expiresAt: Date(timeIntervalSince1970: exp),
            entitlements: entitlements
        )
    }

    private static func jsonNumber(_ value: Any?) -> TimeInterval? {
        if let d = value as? Double { return d }
        if let i = value as? Int { return TimeInterval(i) }
        if let i = value as? Int64 { return TimeInterval(i) }
        if let u = value as? UInt64 { return TimeInterval(u) }
        if let n = value as? NSNumber { return n.doubleValue }
        return nil
    }
}
