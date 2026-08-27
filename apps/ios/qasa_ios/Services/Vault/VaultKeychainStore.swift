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
            kSecValueData as String: Data(password.utf8)
        ]
        SecItemAdd(query as CFDictionary, nil)
    }

    static func loadBiometricPassword() -> String? {
        guard biometricsEnabled else { return nil }
        let context = LAContext()
        context.localizedReason = "Unlock Qasa vault"
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: Key.biometricPassword.rawValue,
            kSecReturnData as String: true,
            kSecMatchLimit as String: kSecMatchLimitOne,
            kSecUseAuthenticationContext as String: context
        ]
        var result: AnyObject?
        let status = SecItemCopyMatching(query as CFDictionary, &result)
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
            kSecAttrAccount as String: key.rawValue
        ]
    }

    private static func data(for key: Key) -> Data? {
        var query = self.query(for: key)
        query[kSecReturnData as String] = true
        query[kSecMatchLimit as String] = kSecMatchLimitOne
        var result: AnyObject?
        let status = SecItemCopyMatching(query as CFDictionary, &result)
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
