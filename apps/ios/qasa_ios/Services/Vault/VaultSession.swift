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

    /// Verifies the current master password and derives new key material without committing yet.
    func derivePasswordChange(current: String, newPassword: String) throws -> (
        oldEnc: SymmetricKey,
        newSalt: Data,
        newVerifier: Data,
        newPassword: String,
        newEnc: SymmetricKey,
        newSearch: SymmetricKey
    ) {
        guard isUnlocked,
              let salt = VaultKeychainStore.kdfSalt,
              let expected = VaultKeychainStore.passwordVerifier,
              let oldEnc = encKey else {
            throw VaultCryptoError.vaultLocked
        }

        let currentRoot = MasterKeyDeriver.deriveRootKey(password: current, salt: salt)
        guard MasterKeyDeriver.passwordVerifier(rootKey: currentRoot) == expected else {
            throw VaultCryptoError.wrongPassword
        }

        let trimmed = newPassword.trimmingCharacters(in: .whitespacesAndNewlines)
        guard trimmed.count >= 8 else {
            throw VaultCryptoError.weakPassword
        }

        let newSalt = MasterKeyDeriver.generateSalt()
        let newRoot = MasterKeyDeriver.deriveRootKey(password: trimmed, salt: newSalt)
        let newVerifier = MasterKeyDeriver.passwordVerifier(rootKey: newRoot)
        let newKeys = MasterKeyDeriver.deriveSubkeys(rootKey: newRoot)
        return (oldEnc, newSalt, newVerifier, trimmed, newKeys.encKey, newKeys.searchKey)
    }

    func commitPasswordChange(
        newSalt: Data,
        newVerifier: Data,
        newPassword: String,
        newEnc: SymmetricKey,
        newSearch: SymmetricKey
    ) {
        VaultKeychainStore.kdfSalt = newSalt
        VaultKeychainStore.passwordVerifier = newVerifier
        encKey = newEnc
        searchKey = newSearch
        if VaultKeychainStore.biometricsEnabled {
            VaultKeychainStore.storeBiometricPassword(newPassword)
        }
    }
}
