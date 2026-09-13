import CryptoKit
import Foundation

enum AutofillUnlock {
    struct Session {
        let encKey: SymmetricKey
    }

    static func unlock(password: String) -> Session? {
        guard SharedVaultKeychain.vaultExists,
              let salt = SharedVaultKeychain.kdfSalt,
              let expected = SharedVaultKeychain.passwordVerifier else {
            return nil
        }
        let root = SharedMasterKeyDeriver.deriveRootKey(password: password, salt: salt)
        guard SharedMasterKeyDeriver.passwordVerifier(rootKey: root) == expected else {
            return nil
        }
        let subkeys = SharedMasterKeyDeriver.deriveSubkeys(rootKey: root)
        return Session(encKey: subkeys.encKey)
    }

    static func unlockWithBiometrics() -> Session? {
        guard let password = SharedVaultKeychain.loadBiometricPassword() else { return nil }
        return unlock(password: password)
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
