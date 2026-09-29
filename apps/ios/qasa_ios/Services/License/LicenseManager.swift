import Foundation
import Observation

struct ParsedLicense {
    let accountId: String
    let deviceId: String
    let tier: String
    let deviceLimit: Int
    let expiresAt: Date
    let entitlements: [String]

    var isPremium: Bool {
        tier.lowercased() == "premium" && expiresAt > Date()
    }
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
            // Signature-verified. An unverified token is treated as absent, so a forged
            // or expired one cannot grant premium.
            parsed = LicenseTokenVerifier.parse(token)
        }
    }

    /// Returns false when the token could not be verified, in which case nothing is
    /// stored and the previously verified license (if any) stays in effect.
    @discardableResult
    func applyToken(_ token: String) -> Bool {
        guard let verified = LicenseTokenVerifier.parse(token) else {
            // Do not store a token we cannot verify — a bad token must not replace a
            // good one, and must not become the persisted source of truth.
            lastError = "license_token_rejected"
            return false
        }
        VaultKeychainStore.licenseToken = token
        parsed = verified
        lastError = nil
        return true
    }

    func clear() {
        VaultKeychainStore.licenseToken = nil
        parsed = nil
        lastError = nil
    }

    /// Ensures a verifying key is available, then re-parses any stored token.
    ///
    /// Must run before `applyToken` is trusted, and before the UI reads `isPremium`,
    /// because verification fails closed without a key.
    @discardableResult
    func bootstrapVerifyingKey() async -> Bool {
        let hadKey = LicenseTokenVerifier.cachedVerifyingKey != nil
        let ok = await LicenseTokenVerifier.refreshVerifyingKey()
        if let token = VaultKeychainStore.licenseToken {
            parsed = LicenseTokenVerifier.parse(token)
        }
        return ok || hadKey
    }

    @discardableResult
    func refresh() async -> Bool {
        guard VaultKeychainStore.licenseToken != nil else { return false }

        // A refresh that ran while the verifying key was unavailable would decode a
        // perfectly valid new token, reject it as unverifiable, and leave the user
        // paying for premium that never unlocks. Make sure the key exists first.
        if LicenseTokenVerifier.cachedVerifyingKey == nil {
            _ = await LicenseTokenVerifier.refreshVerifyingKey()
        }

        do {
            let response: LicenseRefreshResponse = try await QasaAPIClient.shared.request(
                QasaConfig.licenseRefreshURL,
                method: "GET",
                authorized: true
            )
            if applyToken(response.license_token) { return true }
            // A second key attempt covers a key rotated between bootstrap and now.
            _ = await LicenseTokenVerifier.refreshVerifyingKey()
            return applyToken(response.license_token)
        } catch {
            lastError = error.localizedDescription
            return false
        }
    }
}
