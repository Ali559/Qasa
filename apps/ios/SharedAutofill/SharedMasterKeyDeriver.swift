import CommonCrypto
import CryptoKit
import Foundation

enum SharedMasterKeyDeriver {
    private static let saltLength = 32
    private static let rootKeyLength = 32
    private static let pbkdf2Iterations: UInt32 = 310_000

    static func deriveRootKey(password: String, salt: Data) -> Data {
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
            fatalError("PBKDF2 key derivation failed with status \(status)")
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
