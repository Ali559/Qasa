import Foundation
import LocalAuthentication
import Security

/// Keychain access shared between app and Credential Provider (access group).
nonisolated enum SharedVaultKeychain {
    private static let service = "io.kaso.qasa.vault"

    enum Key: String, CaseIterable {
        case vaultExists
        case kdfSalt
        case passwordVerifier
        case kdfVersion
        case biometricsEnabled
        case biometricPassword
        /// When the vault was last opened with the master password (see `BiometricPolicy`).
        case lastMasterPasswordUnlock
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
            // Big-endian, matching the setter (see `VaultKeychainStore.kdfVersion`).
            return (Int(data[0]) << 24)
                | (Int(data[1]) << 16)
                | (Int(data[2]) << 8)
                | Int(data[3])
        }
        set { setData(newValue.map { v in withUnsafeBytes(of: Int32(v).bigEndian) { Data($0) } }, for: .kdfVersion) }
    }

    static var biometricsEnabled: Bool {
        get { bool(for: .biometricsEnabled) ?? false }
        set {
            setBool(newValue, for: .biometricsEnabled)
            if !newValue { delete(.biometricPassword) }
        }
    }

    /// When the vault was last unlocked, created or re-keyed with the master password
    /// (never by biometrics). Stored in the shared access group so the AutoFill extension
    /// reads the same value as the app. 8 bytes: big-endian bit pattern of the Unix time.
    static var lastMasterPasswordUnlock: Date? {
        get {
            guard let data = data(for: .lastMasterPasswordUnlock), data.count == 8 else { return nil }
            let bits = data.reduce(UInt64(0)) { ($0 << 8) | UInt64($1) }
            let seconds = Double(bitPattern: bits)
            guard seconds.isFinite else { return nil }
            return Date(timeIntervalSince1970: seconds)
        }
        set {
            setData(
                newValue.map { date in
                    withUnsafeBytes(of: date.timeIntervalSince1970.bitPattern.bigEndian) { Data($0) }
                },
                for: .lastMasterPasswordUnlock
            )
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

        var query = baseQuery(for: .biometricPassword)
        query[kSecAttrAccessControl as String] = access
        query[kSecValueData as String] = Data(password.utf8)
        SecItemAdd(query as CFDictionary, nil)
    }

    static func loadBiometricPassword(reason: String = "Unlock Qasa vault") -> String? {
        guard biometricsEnabled else { return nil }
        let context = LAContext()
        context.localizedReason = reason
        var query = baseQuery(for: .biometricPassword)
        query[kSecReturnData as String] = true
        query[kSecMatchLimit as String] = kSecMatchLimitOne
        query[kSecUseAuthenticationContext as String] = context
        var result: AnyObject?
        let status = SecItemCopyMatching(query as CFDictionary, &result)
        guard status == errSecSuccess, let data = result as? Data else { return nil }
        return String(data: data, encoding: .utf8)
    }

    // MARK: - Private

    private static func baseQuery(for key: Key) -> [String: Any] {
        [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: key.rawValue,
            kSecAttrAccessGroup as String: AppGroup.keychainAccessGroup
        ]
    }

    private static func data(for key: Key) -> Data? {
        var query = baseQuery(for: key)
        query[kSecReturnData as String] = true
        query[kSecMatchLimit as String] = kSecMatchLimitOne
        var result: AnyObject?
        let status = SecItemCopyMatching(query as CFDictionary, &result)
        guard status == errSecSuccess else { return nil }
        return result as? Data
    }

    private static func bool(for key: Key) -> Bool? {
        guard let data = data(for: key), data.count == 1 else { return nil }
        return data[0] == 1
    }

    private static func setData(_ value: Data?, for key: Key) {
        delete(key)
        guard let value else { return }
        var query = baseQuery(for: key)
        query[kSecValueData as String] = value
        query[kSecAttrAccessible as String] = kSecAttrAccessibleWhenUnlockedThisDeviceOnly
        SecItemAdd(query as CFDictionary, nil)
    }

    private static func setBool(_ value: Bool, for key: Key) {
        setData(Data([value ? 1 : 0]), for: key)
    }

    private     static func delete(_ key: Key) {
        SecItemDelete(baseQuery(for: key) as CFDictionary)
    }

    static func resetAll() {
        for key in Key.allCases {
            delete(key)
        }
    }
}
