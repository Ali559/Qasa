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
        case .encryptionFailed: return "Encryption failed"
        case .invalidCiphertext: return "Invalid ciphertext"
        case .vaultLocked: return "Vault is locked"
        case .itemNotFound: return "Item not found"
        case .wrongPassword: return "Wrong master password"
        case .weakPassword: return "Password must be at least 8 characters"
        case .keyDerivationFailed: return "Key derivation failed"
        case .unsupportedKdfVersion: return "Vault was created by a newer version of Qasa. Update the app to open it."
        case .invalidSalt: return "Vault key material is corrupt"
        }
    }
}
