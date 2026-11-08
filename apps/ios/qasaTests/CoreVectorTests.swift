import CryptoKit
import Foundation
import Testing
@testable import qasa

/// Checks the iOS vault crypto against the golden vectors shared with the desktop core and
/// Android (`core/qasa-vault/tests/vectors.json`). If one of these fails, the platforms can
/// no longer read each other's vaults: fix the code, never the vectors.
struct CoreVectorTests {
    struct Vectors: Decodable {
        let pbkdf2_iterations: UInt32
        let kdf: [KdfCase]
        let aead: [AeadCase]
        let search: SearchVectors
    }

    struct KdfCase: Decodable {
        let name, password, salt_hex, root_key_hex, verifier_hex, enc_key_hex, search_key_hex: String
    }

    struct AeadCase: Decodable {
        let name, key_hex, nonce_hex, plaintext, blob_hex: String
    }

    struct SearchVectors: Decodable {
        let key_hex: String
        let cases: [SearchCase]
    }

    struct SearchCase: Decodable {
        let name, text: String
        let tokens, hashes: [String]
    }

    static let vectors: Vectors = {
        // qasaTests/ → apps/ios → apps → repository root.
        let root = URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent().deletingLastPathComponent()
            .deletingLastPathComponent().deletingLastPathComponent()
        let url = root.appendingPathComponent("core/qasa-vault/tests/vectors.json")
        let data = try! Data(contentsOf: url)
        return try! JSONDecoder().decode(Vectors.self, from: data)
    }()

    @Test func iterationCountMatches() {
        #expect(Self.vectors.pbkdf2_iterations == VaultCrypto.pbkdf2Iterations)
    }

    @Test func keyDerivation() throws {
        for c in Self.vectors.kdf {
            let root = try MasterKeyDeriver.deriveRootKey(password: c.password, salt: hex(c.salt_hex))
            #expect(root == hex(c.root_key_hex), "root key: \(c.name)")
            #expect(MasterKeyDeriver.passwordVerifier(rootKey: root) == hex(c.verifier_hex), "verifier: \(c.name)")
            let keys = MasterKeyDeriver.deriveSubkeys(rootKey: root)
            #expect(bytes(keys.encKey) == hex(c.enc_key_hex), "enc key: \(c.name)")
            #expect(bytes(keys.searchKey) == hex(c.search_key_hex), "search key: \(c.name)")
        }
    }

    @Test func aesGcmBlobs() throws {
        for c in Self.vectors.aead {
            let key = SymmetricKey(data: hex(c.key_hex))
            let plain = try VaultCrypto.decrypt(hex(c.blob_hex), key: key)
            #expect(String(decoding: plain, as: UTF8.self) == c.plaintext, "decrypt: \(c.name)")

            // Sealing with the same nonce must give the same bytes as the core.
            let sealed = try AES.GCM.seal(
                Data(c.plaintext.utf8), using: key, nonce: try AES.GCM.Nonce(data: hex(c.nonce_hex))
            )
            #expect(sealed.combined == hex(c.blob_hex), "encrypt: \(c.name)")
        }
    }

    @Test func searchTokensAndHashes() {
        let key = SymmetricKey(data: hex(Self.vectors.search.key_hex))
        for c in Self.vectors.search.cases {
            #expect(Set(VaultSearchIndex.tokenize(c.text)) == Set(c.tokens), "tokens: \(c.name)")
            #expect(Set(VaultSearchIndex.tokenHashes(for: c.text, searchKey: key)) == Set(c.hashes), "hashes: \(c.name)")
        }
    }

    private func hex(_ string: String) -> Data {
        var data = Data(capacity: string.count / 2)
        var index = string.startIndex
        while index < string.endIndex {
            let next = string.index(index, offsetBy: 2)
            data.append(UInt8(string[index..<next], radix: 16)!)
            index = next
        }
        return data
    }

    private func bytes(_ key: SymmetricKey) -> Data {
        key.withUnsafeBytes { Data($0) }
    }
}
