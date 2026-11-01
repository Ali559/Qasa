import CommonCrypto
import CryptoKit
import Foundation
import Security

/// Must stay byte-for-byte equivalent to the extension's `SharedMasterKeyDeriver`
/// (`apps/ios/SharedAutofill/SharedMasterKeyDeriver.swift`). A divergence in the KDF
/// parameters makes the credential provider derive a different root key than the app,
/// which breaks autofill for every user while the main app keeps working — silently.
enum MasterKeyDeriver {
    private static let saltLength = VaultCrypto.kdfSaltLength
    private static let rootKeyLength = VaultCrypto.rootKeyLength
    private static let pbkdf2Iterations = VaultCrypto.pbkdf2Iterations

    static func generateSalt() -> Data {
        var bytes = [UInt8](repeating: 0, count: saltLength)
        guard SecRandomCopyBytes(kSecRandomDefault, saltLength, &bytes) == errSecSuccess else {
            // A weak salt silently destroys the whole key hierarchy. Fail loudly at
            // creation time rather than deriving keys from predictable bytes.
            fatalError("Failed to generate vault salt: CSPRNG unavailable")
        }
        return Data(bytes)
    }

    /// Throws instead of trapping. `fatalError` in the KDF is reachable on a
    /// low-memory device, and the extension runs this in a host process.
    static func deriveRootKey(password: String, salt: Data) throws -> Data {
        guard salt.count >= 16 else { throw VaultCryptoError.invalidSalt }
        let passwordData = Data(password.utf8)
        var derived = [UInt8](repeating: 0, count: rootKeyLength)
        let status = passwordData.withUnsafeBytes { passwordBytes in
            salt.withUnsafeBytes { saltBytes in
                CCKeyDerivationPBKDF(
                    CCPBKDFAlgorithm(kCCPBKDF2),
                    passwordBytes.baseAddress?.assumingMemoryBound(to: Int8.self),
                    passwordData.count,
                    saltBytes.baseAddress?.assumingMemoryBound(to: UInt8.self),
                    salt.count,
                    CCPseudoRandomAlgorithm(kCCPRFHmacAlgSHA256),
                    pbkdf2Iterations,
                    &derived,
                    rootKeyLength
                )
            }
        }
        guard status == kCCSuccess else {
            throw VaultCryptoError.keyDerivationFailed(status)
        }
        return Data(derived)
    }

    static func deriveSubkeys(rootKey: Data) -> (encKey: SymmetricKey, searchKey: SymmetricKey) {
        let material = SymmetricKey(data: rootKey)
        let encKey = HKDF<SHA256>.deriveKey(
            inputKeyMaterial: material,
            salt: Data(),
            info: Data("qasa-enc".utf8),
            outputByteCount: 32
        )
        let searchKey = HKDF<SHA256>.deriveKey(
            inputKeyMaterial: material,
            salt: Data(),
            info: Data("qasa-search".utf8),
            outputByteCount: 32
        )
        return (encKey, searchKey)
    }

    static func passwordVerifier(rootKey: Data) -> Data {
        Data(SHA256.hash(data: rootKey))
    }
}
