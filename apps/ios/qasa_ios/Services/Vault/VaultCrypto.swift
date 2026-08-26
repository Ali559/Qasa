import CryptoKit
import Foundation

enum VaultCrypto {
    private static let nonceLength = 12
    private static let tagLength = 16

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

enum VaultCryptoError: Error {
    case encryptionFailed
    case invalidCiphertext
    case vaultLocked
    case itemNotFound
}
