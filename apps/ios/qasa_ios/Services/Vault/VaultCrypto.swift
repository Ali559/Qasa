import CryptoKit
import Foundation

enum VaultCrypto {
    private static let nonceLength = 12
    private static let tagLength = 16

    /// KDF parameters, versioned so a future change (e.g. moving to Argon2id) can
    /// migrate existing vaults instead of bricking them. Persisted alongside the salt;
    /// see `VaultKeychainStore.kdfVersion`.
    static let kdfVersion = 1
    static let pbkdf2Iterations: UInt32 = 310_000
    static let kdfSaltLength = 32
    static let rootKeyLength = 32

    /// Compares two secrets without leaking how many leading bytes matched.
    ///
    /// `Data`'s `==` short-circuits on the first differing byte, so comparing a stored
    /// password verifier with `==` is a measurable timing side channel.
    static func constantTimeEquals(_ lhs: Data, _ rhs: Data) -> Bool {
        guard lhs.count == rhs.count else { return false }
        let a = [UInt8](lhs)
        let b = [UInt8](rhs)
        var diff: UInt8 = 0
        for i in 0..<a.count {
            diff |= a[i] ^ b[i]
        }
        return diff == 0
    }

    static func encrypt(_ plaintext: Data, key: SymmetricKey) throws -> Data {
        let nonce = AES.GCM.Nonce()
        let sealed = try AES.GCM.seal(plaintext, using: key, nonce: nonce)
        guard let combined = sealed.combined else {
            throw VaultCryptoError.encryptionFailed
        }
        // combined = nonce || ciphertext || tag
        return combined
    }

    static func decrypt(_ blob: Data, key: SymmetricKey) throws -> Data {
        guard blob.count >= nonceLength + tagLength else {
            throw VaultCryptoError.invalidCiphertext
        }
        let sealed = try AES.GCM.SealedBox(combined: blob)
        return try AES.GCM.open(sealed, using: key)
    }
}

enum VaultCryptoError: Error, LocalizedError {
    case encryptionFailed
    case invalidCiphertext
    case vaultLocked
    case itemNotFound
    case wrongPassword
    case weakPassword
    case keyDerivationFailed(Int32)
    case unsupportedKdfVersion(Int)
    case invalidSalt

    var errorDescription: String? {
        switch self {
        // Shown to the user, so in the app's language; the case name says what went wrong.
        case .wrongPassword: return L10n.tr("auth.unlock.wrongPassword")
        case .weakPassword: return L10n.tr("settings.weakMasterPassword")
        case .unsupportedKdfVersion(let version):
            return String(format: L10n.tr("error.vaultNewerKdf"), version)
        case .encryptionFailed, .invalidCiphertext, .vaultLocked, .itemNotFound,
             .keyDerivationFailed, .invalidSalt:
            return L10n.tr("error.generic")
        }
    }
}
