import CryptoKit
import Foundation

/// Logins the AutoFill extension was asked to save (iOS 26.2 `ASSavePasswordRequest`,
/// design rule 6c). The extension cannot write the vault, so after the user unlocks it there
/// it seals each login with the vault key into a file in the App Group; the app imports and
/// clears them on its next unlock (`VaultRepository.importPendingSaves`). Nothing is stored
/// in the clear.
enum PendingSaveInbox {
    struct Login: Codable, Equatable {
        var title: String
        var website: String
        var username: String
        var password: String
        var savedAt: Date
    }

    private struct File: Codable {
        var sealed: [Data]
    }

    static var url: URL? {
        AppGroup.containerURL?.appendingPathComponent("pending-saves.json", isDirectory: false)
    }

    static func append(_ login: Login, key: SymmetricKey, at url: URL? = url) throws {
        guard let url else { return }
        var file = read(url) ?? File(sealed: [])
        let encoder = JSONEncoder()
        encoder.dateEncodingStrategy = .iso8601
        file.sealed.append(try SharedVaultCrypto.encrypt(try encoder.encode(login), key: key))
        try JSONEncoder().encode(file).write(to: url, options: [.atomic, .completeFileProtection])
    }

    /// Everything waiting, decrypted with `key`; unreadable entries (another vault's) are
    /// dropped. The file is removed.
    static func drain(key: SymmetricKey, at url: URL? = url) -> [Login] {
        guard let url, let file = read(url) else { return [] }
        try? FileManager.default.removeItem(at: url)
        let decoder = JSONDecoder()
        decoder.dateDecodingStrategy = .iso8601
        return file.sealed.compactMap { blob in
            guard let plain = try? SharedVaultCrypto.decrypt(blob, key: key) else { return nil }
            return try? decoder.decode(Login.self, from: plain)
        }
    }

    static func hasPending(at url: URL? = url) -> Bool {
        guard let url else { return false }
        return FileManager.default.fileExists(atPath: url.path)
    }

    private static func read(_ url: URL) -> File? {
        guard let data = try? Data(contentsOf: url) else { return nil }
        return try? JSONDecoder().decode(File.self, from: data)
    }
}
