import Foundation

/// The three restore-key secrets of this device. The Keychain in the app
/// (`KeychainRestoreKeySecrets`), memory in tests.
protocol RestoreKeySecrets: AnyObject {
    /// This device's current key, set only once the server confirmed its hash.
    var restoreKey: Data? { get set }
    /// A backup file holding `restoreKey` was written successfully.
    var restoreKeyWritten: Bool { get set }
    /// The key of a backup this device adopted, until a registration uses it.
    var pendingRestoreKey: Data? { get set }
    /// Whether the device has a license token (the hash upload needs one).
    var hasLicense: Bool { get }
}

final class KeychainRestoreKeySecrets: RestoreKeySecrets {
    var restoreKey: Data? {
        get { VaultKeychainStore.restoreKey }
        set { VaultKeychainStore.restoreKey = newValue }
    }

    var restoreKeyWritten: Bool {
        get { VaultKeychainStore.restoreKeyWritten }
        set { VaultKeychainStore.restoreKeyWritten = newValue }
    }

    var pendingRestoreKey: Data? {
        get { VaultKeychainStore.pendingRestoreKey }
        set { VaultKeychainStore.pendingRestoreKey = newValue }
    }

    var hasLicense: Bool { VaultKeychainStore.licenseToken != nil }
}

/// Body of `PUT /v1/devices/me/restore-key`.
nonisolated struct RestoreKeyHashRequest: Encodable, Sendable {
    /// Base64 of the 32-byte SHA-256 of the key.
    let key_hash: String
}

/// The backup restore key: each backup carries a secret whose SHA-256 this device sent
/// to the server, so a phone restoring that backup may take this device's place when the
/// account has no free device slot.
///
/// Rotation before every backup: with a license token and either no key yet or a key a
/// backup already holds, make a new key and upload its hash; only a confirmed upload
/// replaces the stored key. The server accepts the current and the previous hash, and the
/// file on disk always holds one of them.
enum RestoreKeyRotation {
    /// How long a backup waits for the hash upload at most.
    static let uploadTimeout: TimeInterval = 8

    /// Rotates when due and returns the key the backup should carry (`nil`: none yet).
    /// Never fails: a failed upload keeps the previous state.
    static func keyForBackup(
        secrets: RestoreKeySecrets,
        newKey: (() -> Data)? = nil,
        upload: (_ keyHash: Data) async -> Bool
    ) async -> Data? {
        if secrets.hasLicense, secrets.restoreKey == nil || secrets.restoreKeyWritten {
            let candidate = newKey?() ?? backupNewRestoreKey()
            if await upload(backupRestoreKeyHash(key: candidate)) {
                secrets.restoreKey = candidate
                secrets.restoreKeyWritten = false
            }
        }
        return secrets.restoreKey
    }

    /// After a backup file carrying `key` was written.
    static func backupWritten(secrets: RestoreKeySecrets, key: Data?) {
        guard let key, key == secrets.restoreKey else { return }
        secrets.restoreKeyWritten = true
    }

    /// After a restore: keep the adopted backup's key for the registration that follows.
    static func rememberRestored(_ outcome: FfiRestoreOutcome, secrets: RestoreKeySecrets) {
        guard outcome.adopted, let key = outcome.restoreKey else { return }
        secrets.pendingRestoreKey = key
    }

    /// `PUT /v1/devices/me/restore-key` with license auth; true on 2xx.
    static func uploadHash(
        _ keyHash: Data,
        api: QasaAPIClient = .shared,
        url: URL = QasaConfig.restoreKeyURL
    ) async -> Bool {
        do {
            _ = try await api.rawRequest(
                url,
                method: "PUT",
                body: RestoreKeyHashRequest(key_hash: keyHash.base64EncodedString()),
                authorized: true,
                timeout: uploadTimeout
            )
            return true
        } catch {
            #if DEBUG
            print("Restore key upload failed: \(error)")
            #endif
            return false
        }
    }
}
