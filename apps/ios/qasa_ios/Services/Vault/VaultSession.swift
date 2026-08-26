import CryptoKit
import Foundation
import Observation

@Observable
final class VaultSession {
    static let shared = VaultSession()

    private(set) var isUnlocked = false
    private var encKey: SymmetricKey?
    private var searchKey: SymmetricKey?

    var vaultExists: Bool { VaultKeychainStore.vaultExists }

    private init() {}

    func createVault(password: String, biometricsEnabled: Bool) throws {
        let salt = MasterKeyDeriver.generateSalt()
        let root = MasterKeyDeriver.deriveRootKey(password: password, salt: salt)
        let verifier = MasterKeyDeriver.passwordVerifier(rootKey: root)
        let subkeys = MasterKeyDeriver.deriveSubkeys(rootKey: root)

        if VaultKeychainStore.deviceFingerprint == nil {
            VaultKeychainStore.deviceFingerprint = UUID().uuidString.lowercased()
        }
        if VaultKeychainStore.accountId == nil {
            VaultKeychainStore.accountId = UUID().uuidString.lowercased()
        }

        VaultKeychainStore.kdfSalt = salt
        VaultKeychainStore.passwordVerifier = verifier
        VaultKeychainStore.vaultExists = true
        VaultKeychainStore.biometricsEnabled = biometricsEnabled

        encKey = subkeys.encKey
        searchKey = subkeys.searchKey
        isUnlocked = true
        if biometricsEnabled {
            VaultKeychainStore.storeBiometricPassword(password)
        }
    }

    @discardableResult
    func unlock(password: String) -> Bool {
        guard vaultExists,
              let salt = VaultKeychainStore.kdfSalt,
              let expected = VaultKeychainStore.passwordVerifier else {
            return false
        }
        let root = MasterKeyDeriver.deriveRootKey(password: password, salt: salt)
        guard MasterKeyDeriver.passwordVerifier(rootKey: root) == expected else {
            return false
        }
        let subkeys = MasterKeyDeriver.deriveSubkeys(rootKey: root)
        encKey = subkeys.encKey
        searchKey = subkeys.searchKey
        isUnlocked = true
        if VaultKeychainStore.biometricsEnabled {
            VaultKeychainStore.storeBiometricPassword(password)
        }
        return true
    }

    func unlockWithBiometrics() -> String? {
        guard let password = VaultKeychainStore.loadBiometricPassword() else { return nil }
        return unlock(password: password) ? password : nil
    }

    func lock() {
        encKey = nil
        searchKey = nil
        isUnlocked = false
    }

    func requireEncKey() throws -> SymmetricKey {
        guard let encKey, isUnlocked else { throw VaultCryptoError.vaultLocked }
        return encKey
    }

    func requireSearchKey() throws -> SymmetricKey {
        guard let searchKey, isUnlocked else { throw VaultCryptoError.vaultLocked }
        return searchKey
    }

    func wipeVault() {
        lock()
        VaultKeychainStore.resetVaultSecrets()
    }
}
