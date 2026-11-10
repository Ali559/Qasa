import CryptoKit
import Foundation

enum AutofillUnlock {
    struct Session {
        let encKey: SymmetricKey
    }

    /// A typed master password: restarts the biometric interval (`BiometricPolicy`).
    static func unlock(password: String) -> Session? {
        guard let session = deriveSession(password: password) else { return nil }
        BiometricPolicy.recordMasterPasswordUnlock()
        return session
    }

    private static func deriveSession(password: String) -> Session? {
        guard SharedVaultKeychain.vaultExists,
              let salt = SharedVaultKeychain.kdfSalt,
              let expected = SharedVaultKeychain.passwordVerifier else {
            return nil
        }
        // Refuse rather than silently reject a vault written by a newer KDF version.
        if let version = SharedVaultKeychain.kdfVersion, version > SharedVaultCrypto.kdfVersion {
            return nil
        }
        guard let root = try? SharedMasterKeyDeriver.deriveRootKey(password: password, salt: salt),
              SharedVaultCrypto.constantTimeEquals(
                  SharedMasterKeyDeriver.passwordVerifier(rootKey: root),
                  expected
              ) else {
            return nil
        }
        let subkeys = SharedMasterKeyDeriver.deriveSubkeys(rootKey: root)
        return Session(encKey: subkeys.encKey)
    }

    /// Whether Face ID / Touch ID may be offered now: on, usable, and the master password
    /// isn't due (`BiometricPolicy`, shared with the app).
    static var canOfferBiometricUnlock: Bool {
        SharedVaultKeychain.biometricsEnabled
            && !BiometricPolicy.isMasterPasswordDue
            && BiometricPolicy.isBiometryAvailable
    }

    /// Refused once the master password is due; never restarts the interval.
    static func unlockWithBiometrics() -> Session? {
        guard !BiometricPolicy.isMasterPasswordDue,
              let password = SharedVaultKeychain.loadBiometricPassword() else { return nil }
        return deriveSession(password: password)
    }

    static func decryptSecrets(_ entry: AutofillIndexEntry, session: Session) throws -> AutofillSecrets {
        let data = try SharedVaultCrypto.decrypt(entry.encryptedSecrets, key: session.encKey)
        return try JSONDecoder().decode(AutofillSecrets.self, from: data)
    }

    static func decryptSecrets(_ candidate: AutofillCandidate, session: Session) throws -> AutofillSecrets {
        let data = try SharedVaultCrypto.decrypt(candidate.encryptedSecrets, key: session.encKey)
        return try JSONDecoder().decode(AutofillSecrets.self, from: data)
    }
}
