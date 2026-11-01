import Foundation
import LocalAuthentication
import Security

nonisolated enum VaultKeychainStore {
    private static let service = "io.kaso.qasa.vault"

    enum Key: String {
        case vaultExists
        case kdfSalt
        case passwordVerifier
        case kdfVersion
        case accountId
        case deviceFingerprint
        case licenseToken
        case biometricsEnabled
        case biometricPassword
    }

    static var vaultExists: Bool {
        get { bool(for: .vaultExists) ?? false }
        set { setBool(newValue, for: .vaultExists) }
    }

    static var kdfSalt: Data? {
        get { data(for: .kdfSalt) }
        set { setData(newValue, for: .kdfSalt) }
    }

    static var passwordVerifier: Data? {
        get { data(for: .passwordVerifier) }
        set { setData(newValue, for: .passwordVerifier) }
    }

    /// KDF parameters used for the stored salt. `nil` means a legacy vault written
    /// before versioning, which is still the current v1 scheme.
    static var kdfVersion: Int? {
        get {
            guard let data = data(for: .kdfVersion), data.count == 4 else { return nil }
            // Big-endian, matching the setter. Reading it little-endian turned v1 into
            // 16777216 and locked every newly created vault out as "made by a newer version".
            return (Int(data[0]) << 24)
                | (Int(data[1]) << 16)
                | (Int(data[2]) << 8)
                | Int(data[3])
        }
        set {
            setData(newValue.map { v in withUnsafeBytes(of: Int32(v).bigEndian) { Data($0) } },
                   for: .kdfVersion)
        }
    }

    static var accountId: String? {
        get { string(for: .accountId) }
        set { setString(newValue, for: .accountId) }
    }

    static var deviceFingerprint: String? {
        get { string(for: .deviceFingerprint) }
        set { setString(newValue, for: .deviceFingerprint) }
    }

    static var licenseToken: String? {
        get { string(for: .licenseToken) }
        set { setString(newValue, for: .licenseToken) }
    }

    static var biometricsEnabled: Bool {
        get { bool(for: .biometricsEnabled) ?? false }
        set {
            setBool(newValue, for: .biometricsEnabled)
            if !newValue { delete(.biometricPassword) }
        }
    }

    static func storeBiometricPassword(_ password: String) {
        delete(.biometricPassword)
        guard biometricsEnabled else { return }
        var error: Unmanaged<CFError>?
        guard let access = SecAccessControlCreateWithFlags(
            nil,
            kSecAttrAccessibleWhenUnlockedThisDeviceOnly,
            .biometryCurrentSet,
            &error
        ) else { return }

        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: Key.biometricPassword.rawValue,
            kSecAttrAccessControl as String: access,
            kSecAttrAccessGroup as String: AppGroup.keychainAccessGroup,
            kSecValueData as String: Data(password.utf8)
        ]
        SecItemAdd(query as CFDictionary, nil)
    }

    /// Whether a biometric-sealed password exists, checked without showing Face ID / Touch ID:
    /// with interaction disallowed, a present item reports `errSecInteractionNotAllowed`.
    static var biometricPasswordStored: Bool {
        guard biometricsEnabled else { return false }
        let context = LAContext()
        context.interactionNotAllowed = true
        var query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: Key.biometricPassword.rawValue,
            kSecAttrAccessGroup as String: AppGroup.keychainAccessGroup,
            kSecMatchLimit as String: kSecMatchLimitOne,
            kSecUseAuthenticationContext as String: context
        ]
        func present(_ status: OSStatus) -> Bool {
            status == errSecSuccess || status == errSecInteractionNotAllowed
        }
        if present(SecItemCopyMatching(query as CFDictionary, nil)) { return true }
        query.removeValue(forKey: kSecAttrAccessGroup as String)
        return present(SecItemCopyMatching(query as CFDictionary, nil))
    }

    static func loadBiometricPassword() -> String? {
        guard biometricsEnabled else { return nil }
        let context = LAContext()
        context.localizedReason = "Unlock Qasa vault"
        var query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: Key.biometricPassword.rawValue,
            kSecAttrAccessGroup as String: AppGroup.keychainAccessGroup,
            kSecReturnData as String: true,
            kSecMatchLimit as String: kSecMatchLimitOne,
            kSecUseAuthenticationContext as String: context
        ]
        var result: AnyObject?
        var status = SecItemCopyMatching(query as CFDictionary, &result)
        if status != errSecSuccess {
            query.removeValue(forKey: kSecAttrAccessGroup as String)
            status = SecItemCopyMatching(query as CFDictionary, &result)
        }
        guard status == errSecSuccess, let data = result as? Data else { return nil }
        return String(data: data, encoding: .utf8)
    }

    static func resetVaultSecrets() {
        for key in Key.allCases {
            delete(key)
            // Also clear the pre–App Group location, otherwise "clear local data" leaves
            // the old salt, verifier, and plaintext-biometric password behind on disk.
            SecItemDelete(legacyQuery(for: key) as CFDictionary)
        }
    }

    // MARK: - Private

    private static func query(for key: Key) -> [String: Any] {
        [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: key.rawValue,
            kSecAttrAccessGroup as String: AppGroup.keychainAccessGroup
        ]
    }

    private static func legacyQuery(for key: Key) -> [String: Any] {
        [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: key.rawValue
        ]
    }

    private static func data(for key: Key) -> Data? {
        if let shared = copyMatching(query(for: key)) {
            return shared
        }
        // Migrate pre–App Group keychain items into the shared access group.
        guard let legacy = copyMatching(legacyQuery(for: key)) else { return nil }
        setData(legacy, for: key)
        SecItemDelete(legacyQuery(for: key) as CFDictionary)
        return legacy
    }

    private static func copyMatching(_ query: [String: Any]) -> Data? {
        var q = query
        q[kSecReturnData as String] = true
        q[kSecMatchLimit as String] = kSecMatchLimitOne
        var result: AnyObject?
        let status = SecItemCopyMatching(q as CFDictionary, &result)
        guard status == errSecSuccess else { return nil }
        return result as? Data
    }

    private static func string(for key: Key) -> String? {
        guard let data = data(for: key) else { return nil }
        return String(data: data, encoding: .utf8)
    }

    private static func bool(for key: Key) -> Bool? {
        guard let data = data(for: key), data.count == 1 else { return nil }
        return data[0] == 1
    }

    private static func setData(_ value: Data?, for key: Key) {
        delete(key)
        guard let value else { return }

        // Prefer the shared access group so the credential provider can read the salt
        // and verifier, then fall back to the app-private item. The read path does the
        // same, and a signed build without the App Group entitlement would otherwise
        // store the key material somewhere nothing can find it again.
        var query = self.query(for: key)
        query[kSecValueData as String] = value
        query[kSecAttrAccessible as String] = kSecAttrAccessibleWhenUnlockedThisDeviceOnly
        let status = SecItemAdd(query as CFDictionary, nil)
        if status == errSecSuccess { return }

        SecItemDelete(legacyQuery(for: key) as CFDictionary)
        var fallback = legacyQuery(for: key)
        fallback[kSecValueData as String] = value
        fallback[kSecAttrAccessible as String] = kSecAttrAccessibleWhenUnlockedThisDeviceOnly
        let fallbackStatus = SecItemAdd(fallback as CFDictionary, nil)
        if fallbackStatus != errSecSuccess {
            // A silently-failed write looks like a missing vault: the next unlock
            // reports "wrong password" and the user's data appears gone.
            assertionFailure(
                "Keychain write failed for \(key.rawValue) (shared: \(status), legacy: \(fallbackStatus))"
            )
        }
    }

    private static func setString(_ value: String?, for key: Key) {
        guard let value else {
            delete(key)
            return
        }
        setData(Data(value.utf8), for: key)
    }

    private static func setBool(_ value: Bool, for key: Key) {
        setData(Data([value ? 1 : 0]), for: key)
    }

    private static func delete(_ key: Key) {
        let query = self.query(for: key)
        SecItemDelete(query as CFDictionary)
    }
}

extension VaultKeychainStore.Key: CaseIterable {}
