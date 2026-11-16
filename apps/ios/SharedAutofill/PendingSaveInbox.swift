import CryptoKit
import Foundation

/// Logins the AutoFill extension was asked to save (iOS 26.2 `ASSavePasswordRequest`,
/// design rule 6c). The extension cannot write the vault, so after the user unlocks it there
/// it seals each login with the vault key into a file in the App Group; the app imports and
/// clears them on its next unlock (`VaultRepository.importPendingSaves`). Nothing is stored
/// in the clear.
nonisolated enum PendingSaveInbox {
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

    enum InboxError: Error {
        /// No App Group container: nothing can be saved, and the extension must not say it was.
        case noAppGroup
    }

    static var url: URL? {
        AppGroup.containerURL?.appendingPathComponent("pending-saves.json", isDirectory: false)
    }

    /// Seals `login` and adds it to the file, coordinated with the app reading it.
    static func append(_ login: Login, key: SymmetricKey, at url: URL? = url) throws {
        guard let url else { throw InboxError.noAppGroup }
        let encoder = JSONEncoder()
        encoder.dateEncodingStrategy = .iso8601
        let blob = try SharedVaultCrypto.encrypt(try encoder.encode(login), key: key)
        try coordinatedWrite(url) { url in
            var file = read(url) ?? File(sealed: [])
            file.sealed.append(blob)
            try write(file, to: url)
        }
    }

    /// Hands each waiting login to `handle`, which returns whether it was imported. Imported
    /// entries and unreadable ones (another vault's) leave the file; the rest stay for the
    /// next unlock. The file is removed once nothing is left.
    static func drain(key: SymmetricKey, at url: URL? = url, handle: (Login) -> Bool) throws {
        guard let url else { throw InboxError.noAppGroup }
        let decoder = JSONDecoder()
        decoder.dateDecodingStrategy = .iso8601
        try coordinatedWrite(url) { url in
            guard let file = read(url) else { return }
            var kept: [Data] = []
            for blob in file.sealed {
                guard let plain = try? SharedVaultCrypto.decrypt(blob, key: key),
                      let login = try? decoder.decode(Login.self, from: plain) else { continue }
                if !handle(login) { kept.append(blob) }
            }
            if kept.isEmpty {
                try FileManager.default.removeItem(at: url)
            } else {
                try write(File(sealed: kept), to: url)
            }
        }
    }

    /// Everything waiting, decrypted with `key`, and removed from the file.
    static func drain(key: SymmetricKey, at url: URL? = url) throws -> [Login] {
        var out: [Login] = []
        try drain(key: key, at: url) { out.append($0); return true }
        return out
    }

    static func hasPending(at url: URL? = url) -> Bool {
        guard let url else { return false }
        return FileManager.default.fileExists(atPath: url.path)
    }

    private static func read(_ url: URL) -> File? {
        guard let data = try? Data(contentsOf: url) else { return nil }
        return try? JSONDecoder().decode(File.self, from: data)
    }

    private static func write(_ file: File, to url: URL) throws {
        try JSONEncoder().encode(file).write(to: url, options: [.atomic, .completeFileProtection])
    }

    /// The app and the extension both write this file: one at a time.
    private static func coordinatedWrite(_ url: URL, _ body: (URL) throws -> Void) throws {
        var coordinationError: NSError?
        var bodyError: Error?
        NSFileCoordinator(filePresenter: nil).coordinate(writingItemAt: url, options: .forMerging, error: &coordinationError) { url in
            do { try body(url) } catch { bodyError = error }
        }
        if let coordinationError { throw coordinationError }
        if let bodyError { throw bodyError }
    }
}
