import Foundation
import LocalAuthentication
import Security

nonisolated enum VaultKeychainStore {
    private static let service = "io.kaso.qasa.vault"

    enum Key: String {
        case vaultExists
        case kdfSalt
        case passwordVerifier
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
        var query = self.query(for: key)
        query[kSecValueData as String] = value
        query[kSecAttrAccessible as String] = kSecAttrAccessibleWhenUnlockedThisDeviceOnly
        SecItemAdd(query as CFDictionary, nil)
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
