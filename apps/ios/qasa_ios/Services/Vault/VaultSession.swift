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
        let root = try MasterKeyDeriver.deriveRootKey(password: password, salt: salt)
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
        VaultKeychainStore.kdfVersion = VaultCrypto.kdfVersion
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
        // A vault written by a newer build is rejected as an unsupported-version failure
        // rather than silently failing the verifier check, which would look like a wrong
        // password and send the user into a reset loop.
        guard isKdfVersionSupported else { return false }

        guard let root = try? MasterKeyDeriver.deriveRootKey(password: password, salt: salt),
              VaultCrypto.constantTimeEquals(MasterKeyDeriver.passwordVerifier(rootKey: root), expected)
        else {
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

    /// `nil` means a legacy vault written before versioning, which is still v1.
    var isKdfVersionSupported: Bool {
        guard let version = VaultKeychainStore.kdfVersion else { return true }
        return version <= VaultCrypto.kdfVersion
    }

    /// Lets the UI distinguish "wrong password" from "this vault needs a newer build".
    var unsupportedKdfVersion: Int? {
        guard let version = VaultKeychainStore.kdfVersion, version > VaultCrypto.kdfVersion else {
            return nil
        }
        return version
    }

    func unlockWithBiometrics() -> String? {
        guard let password = VaultKeychainStore.loadBiometricPassword() else { return nil }
        return unlock(password: password) ? password : nil
    }

    #if DEBUG
    /// Unit tests: an unlocked session without touching the Keychain.
    func installKeysForTesting(encKey: SymmetricKey, searchKey: SymmetricKey) {
        self.encKey = encKey
        self.searchKey = searchKey
        isUnlocked = true
    }
    #endif

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
        guard isKdfVersionSupported else {
            throw VaultCryptoError.unsupportedKdfVersion(VaultKeychainStore.kdfVersion ?? 0)
        }

        let currentRoot = try MasterKeyDeriver.deriveRootKey(password: current, salt: salt)
        guard VaultCrypto.constantTimeEquals(
            MasterKeyDeriver.passwordVerifier(rootKey: currentRoot),
            expected
        ) else {
            throw VaultCryptoError.wrongPassword
        }

        let trimmed = newPassword.trimmingCharacters(in: .whitespacesAndNewlines)
        guard trimmed.count >= 8 else {
            throw VaultCryptoError.weakPassword
        }

        let newSalt = MasterKeyDeriver.generateSalt()
        let newRoot = try MasterKeyDeriver.deriveRootKey(password: trimmed, salt: newSalt)
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
        VaultKeychainStore.kdfVersion = VaultCrypto.kdfVersion
        encKey = newEnc
        searchKey = newSearch
        if VaultKeychainStore.biometricsEnabled {
            VaultKeychainStore.storeBiometricPassword(newPassword)
        }
    }
}
