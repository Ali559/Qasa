import CryptoKit
import Foundation
import Observation
import SwiftData
import UIKit

/// Encrypted vault backups (`core/qasa-backup` through `QasaFFI`). The file format, the
/// merge and the schedule live in the core; this writes the bytes to the folder the user
/// picked, runs automatic backups and restores. Reuses the Wi‑Fi sync store
/// (`VaultSyncStore`), so a backup holds exactly what a new device would get over Wi‑Fi.
@Observable
final class BackupService {
    static let shared = BackupService()

    /// One backup at a time, manual or automatic.
    private(set) var isBackingUp = false

    @ObservationIgnored private let prefs: BackupPreferences
    @ObservationIgnored private let restoreKeys: RestoreKeySecrets

    init(prefs: BackupPreferences = .shared, restoreKeys: RestoreKeySecrets = KeychainRestoreKeySecrets()) {
        self.prefs = prefs
        self.restoreKeys = restoreKeys
    }

    enum Outcome: Equatable {
        case saved
        case noLocation
        case busy
        case failed
    }

    /// Builds the backup off the main thread and replaces this device's previous file.
    /// Records the time on success and the failure flag otherwise. Rotates the restore key
    /// first when due (`RestoreKeyRotation`); that upload never fails the backup.
    @discardableResult
    func backUp(container: ModelContainer) async -> Outcome {
        guard !isBackingUp else { return .busy }
        guard let bookmark = prefs.folderBookmark else { return .noLocation }
        guard let encKey = try? VaultSession.shared.requireEncKey() else {
            prefs.recordFailure()
            return .failed
        }
        isBackingUp = true
        defer { isBackingUp = false }

        let keyData = encKey.withUnsafeBytes { Data($0) }
        let deviceName = UIDevice.current.name
        let fileName = backupFileName(deviceName: deviceName)
        let store = VaultSyncStore(container: container, deviceName: deviceName, onApplied: {})
        let restoreKey = await RestoreKeyRotation.keyForBackup(
            secrets: restoreKeys,
            upload: { await RestoreKeyRotation.uploadHash($0) }
        )

        let result: Result<Data?, Error> = await Self.offMain {
            do {
                let bytes = try backupCreate(encKey: keyData, restoreKey: restoreKey, store: store)
                return .success(try BackupFileIO.write(bytes, named: fileName, inFolder: bookmark))
            } catch {
                return .failure(error)
            }
        }
        switch result {
        case .success(let refreshedBookmark):
            if let refreshedBookmark { prefs.folderBookmark = refreshedBookmark }
            RestoreKeyRotation.backupWritten(secrets: restoreKeys, key: restoreKey)
            prefs.recordSuccess(atMs: BackupPreferences.nowMs())
            return .saved
        case .failure:
            prefs.recordFailure()
            return .failed
        }
    }

    /// On unlock and on return to the foreground while unlocked: a silent backup when one
    /// is due. Failures only set the flag shown on the Backups screen.
    func runIfDue(container: ModelContainer) {
        guard VaultSession.shared.isUnlocked, !isBackingUp,
              prefs.isAutomaticBackupDue(nowMs: BackupPreferences.nowMs()) else { return }
        Task { await backUp(container: container) }
    }

    // MARK: restore

    enum RestoreError: Error, Equatable {
        /// A `backup.error.*` (or `backup.failed`) string key.
        case message(String)
    }

    /// The header of a picked file, readable without the password.
    static func readInfo(_ file: Data) -> Result<FfiBackupInfo, RestoreError> {
        do {
            return .success(try backupInfo(file: file))
        } catch {
            return .failure(.message(messageKey(error)))
        }
    }

    /// Restores off the main thread (PBKDF2 takes a few seconds). On a device with no vault
    /// this adopts the backup's vault and account; otherwise it merges. An adopted backup's
    /// restore key is kept for the registration that follows, so this device may take the
    /// place of the one that made the backup.
    static func restore(
        file: Data,
        password: String,
        container: ModelContainer,
        restoreKeys: RestoreKeySecrets = KeychainRestoreKeySecrets()
    ) async -> Result<FfiRestoreOutcome, RestoreError> {
        let store = VaultSyncStore(container: container, deviceName: UIDevice.current.name)
        let result: Result<FfiRestoreOutcome, RestoreError> = await offMain {
            do {
                return .success(try backupRestore(file: file, password: password, store: store))
            } catch {
                return .failure(.message(messageKey(error)))
            }
        }
        if case .success(let outcome) = result, outcome.adopted {
            RestoreKeyRotation.rememberRestored(outcome, secrets: restoreKeys)
            DeviceRegistrationService.shared.noteRestoredBackup()
        }
        return result
    }

    /// The `backup.*` string for a core error.
    nonisolated static func messageKey(_ error: Error) -> String {
        guard let error = error as? FfiBackupError else { return "backup.error.unreadable" }
        switch error {
        case .NotABackup: return "backup.error.notABackup"
        case .NewerVersion: return "backup.error.newerVersion"
        case .WrongPassword: return "backup.error.wrongPassword"
        case .DifferentVault: return "backup.error.differentVault"
        case .NoVault: return "backup.error.noVault"
        case .Store: return "backup.error.unreadable"
        }
    }

    /// Runs a blocking FFI call on a background thread.
    nonisolated static func offMain<T: Sendable>(_ work: @escaping @Sendable () -> T) async -> T {
        await withCheckedContinuation { continuation in
            DispatchQueue.global(qos: .userInitiated).async { continuation.resume(returning: work()) }
        }
    }

    /// "Last backup: <medium date, short time>" or "No backup yet".
    static func lastBackupText(ms: Int64?, locale: Locale) -> String {
        guard let ms else { return L10n.tr("backup.never", locale: locale) }
        return String(format: L10n.tr("backup.lastBackupFmt", locale: locale), formattedDate(ms: ms, locale: locale))
    }

    static func formattedDate(ms: Int64, locale: Locale) -> String {
        let formatter = DateFormatter()
        formatter.locale = locale
        formatter.dateStyle = .medium
        formatter.timeStyle = .short
        return formatter.string(from: Date(timeIntervalSince1970: Double(ms) / 1000))
    }
}

/// Reading and writing backup files at places the user picked with the document picker
/// (iCloud Drive, Google Drive, On My iPhone…). Every access is security scoped and
/// coordinated, so file providers see a whole file, never a half-written one.
nonisolated enum BackupFileIO {
    enum IOError: Error { case unreadable }

    /// A bookmark for a folder the picker just returned, and its display name.
    static func bookmark(forPickedFolder folder: URL) throws -> (bookmark: Data, name: String) {
        let scoped = folder.startAccessingSecurityScopedResource()
        defer { if scoped { folder.stopAccessingSecurityScopedResource() } }
        let data = try folder.bookmarkData(options: [], includingResourceValuesForKeys: nil, relativeTo: nil)
        let name = (try? folder.resourceValues(forKeys: [.localizedNameKey]).localizedName) ?? folder.lastPathComponent
        return (data, name)
    }

    /// Writes `bytes` as `fileName` inside the bookmarked folder, replacing the previous
    /// backup atomically. Returns a fresh bookmark when the stored one went stale.
    static func write(_ bytes: Data, named fileName: String, inFolder bookmark: Data) throws -> Data? {
        var stale = false
        let folder = try URL(resolvingBookmarkData: bookmark, options: [], relativeTo: nil, bookmarkDataIsStale: &stale)
        let scoped = folder.startAccessingSecurityScopedResource()
        defer { if scoped { folder.stopAccessingSecurityScopedResource() } }

        let target = folder.appendingPathComponent(fileName, isDirectory: false)
        var coordinationError: NSError?
        var writeError: Error?
        NSFileCoordinator(filePresenter: nil).coordinate(
            writingItemAt: target,
            options: .forReplacing,
            error: &coordinationError
        ) { url in
            do {
                try bytes.write(to: url, options: [.atomic])
            } catch {
                writeError = error
            }
        }
        if let error = coordinationError ?? writeError { throw error }
        return stale ? try? folder.bookmarkData(options: [], includingResourceValuesForKeys: nil, relativeTo: nil) : nil
    }

    /// Reads a file the picker returned (downloading it first if the provider must).
    static func read(pickedFile url: URL) throws -> Data {
        let scoped = url.startAccessingSecurityScopedResource()
        defer { if scoped { url.stopAccessingSecurityScopedResource() } }
        var coordinationError: NSError?
        var result: Result<Data, Error> = .failure(IOError.unreadable)
        NSFileCoordinator(filePresenter: nil).coordinate(readingItemAt: url, options: [], error: &coordinationError) { readURL in
            result = Result { try Data(contentsOf: readURL) }
        }
        if let coordinationError { throw coordinationError }
        return try result.get()
    }
}
