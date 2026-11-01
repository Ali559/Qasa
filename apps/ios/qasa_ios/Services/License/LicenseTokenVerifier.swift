import CryptoKit
import Foundation

enum LicenseTokenError: Error {
    case malformed
    case algorithmNotEdDSA
    case noVerifyingKey
    case badSignature
    case expired
}

/// Ed25519 verification for server-issued license tokens.
///
/// Previously the client decoded the JWT payload and used it verbatim without ever
/// checking the signature, so anyone could hand-write `{"tier":"premium"}` and unlock
/// breach monitoring, VPN and LAN sync offline, with no server contact at all.
///
/// The verifying key is fetched once from `/.well-known/qasa-license-key` and cached.
/// If the build supplies `QASA_LICENSE_PUBLIC_KEY` that pinned key wins and a
/// server-side key change is detected rather than silently trusted.
enum LicenseTokenVerifier {

    /// Optional pinned key (base64 of the raw 32-byte Ed25519 public key).
    private static var pinnedKeyB64: String? {
        guard let raw = Bundle.main.object(forInfoDictionaryKey: "QASA_LICENSE_PUBLIC_KEY") as? String,
              !raw.isEmpty else { return nil }
        return raw
    }

    // MARK: - Key material

    private static let cacheKey = "qasa.license.verifyingKey.b64"

    static var cachedVerifyingKey: Data? {
        if let pinned = pinnedKeyB64, let data = Data(base64Encoded: pinned) {
            return data
        }
        guard let raw = UserDefaults.standard.string(forKey: cacheKey),
              let data = Data(base64Encoded: raw) else { return nil }
        return data
    }

    static func cacheVerifyingKey(base64 raw: String) {
        // Never overwrite a pinned key: if the build pins one, the pinned one is the
        // only one we trust, and learning a new key from the server would defeat it.
        guard pinnedKeyB64 == nil, Data(base64Encoded: raw) != nil else { return }
        UserDefaults.standard.set(raw, forKey: cacheKey)
    }

    static func clearCachedVerifyingKey() {
        UserDefaults.standard.removeObject(forKey: cacheKey)
    }

    /// `nonisolated` because `QasaCodec.decode` is not actor-isolated and this project
    /// defaults declarations to `MainActor`.
    nonisolated struct WellKnownKey: Decodable, Sendable {
        let algorithm: String
        let public_key: String
    }

    /// Fetches and caches the server's verifying key. Safe to call repeatedly.
    @discardableResult
    static func refreshVerifyingKey() async -> Bool {
        if pinnedKeyB64 != nil { return cachedVerifyingKey != nil }
        do {
            let response: WellKnownKey = try await QasaAPIClient.shared.request(
                QasaConfig.licenseKeyURL,
                method: "GET",
                authorized: false
            )
            guard response.algorithm == "EdDSA" else { return false }
            cacheVerifyingKey(base64: response.public_key)
            return cachedVerifyingKey != nil
        } catch {
            return false
        }
    }

    // MARK: - Verification

    /// Verifies signature and expiry, then decodes the claims.
    ///
    /// Fails closed: any structural problem, a missing key, a non-EdDSA algorithm
    /// (including the classic `alg: none` downgrade), or a bad signature yields `nil`.
    static func parse(_ token: String) -> ParsedLicense? {
        let parts = token.split(separator: ".", omittingEmptySubsequences: false)
        guard parts.count == 3 else { return nil }

        // Reject the classic downgrade before doing anything else.
        guard let headerData = base64urlDecode(String(parts[0])),
              let header = try? JSONSerialization.jsonObject(with: headerData) as? [String: Any],
              (header["alg"] as? String) == "EdDSA" else { return nil }

        guard let signature = base64urlDecode(String(parts[2])), signature.count == 64 else { return nil }
        guard let verifyingKey = cachedVerifyingKey, verifyingKey.count == 32 else { return nil }
        guard let publicKey = try? Curve25519.Signing.PublicKey(rawRepresentation: verifyingKey) else {
            return nil
        }

        let signingInput = Data("\(parts[0]).\(parts[1])".utf8)
        // Ed25519 signatures are raw 64-byte values in CryptoKit (there is no
        // ECDSASignature type for Curve25519 — that is P-256 only). `isValidSignature`
        // returns false for a wrong-length or malformed signature, so no extra parsing.
        guard publicKey.isValidSignature(signature, for: signingInput) else { return nil }

        // Signature is valid — now decode.
        guard let payloadData = base64urlDecode(String(parts[1])),
              let json = try? JSONSerialization.jsonObject(with: payloadData) as? [String: Any],
              let sub = json["sub"] as? String,
              let did = json["did"] as? String,
              let tier = json["tier"] as? String,
              let exp = jsonNumber(json["exp"]) else { return nil }

        guard exp > Date().timeIntervalSince1970 else { return nil }

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

    // MARK: - Helpers

    static func base64urlDecode(_ input: String) -> Data? {
        var value = input
            .replacingOccurrences(of: "-", with: "+")
            .replacingOccurrences(of: "_", with: "/")
        let padding = (4 - value.count % 4) % 4
        if padding > 0 { value += String(repeating: "=", count: padding) }
        return Data(base64Encoded: value)
    }

    static func jsonNumber(_ value: Any?) -> TimeInterval? {
        if let d = value as? Double { return d }
        if let i = value as? Int { return TimeInterval(i) }
        if let i = value as? Int64 { return TimeInterval(i) }
        if let u = value as? UInt64 { return TimeInterval(u) }
        if let n = value as? NSNumber { return n.doubleValue }
        return nil
    }
}
