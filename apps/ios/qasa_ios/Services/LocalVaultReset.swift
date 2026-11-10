import Foundation
import SwiftData

/// Wipes on-device vault files and secrets. Used for a fresh first-run (debug launch arg).
enum LocalVaultReset {
    static var shouldWipeOnLaunch: Bool {
        ProcessInfo.processInfo.arguments.contains("-wipeLocalVault")
    }

    static func wipeStoreFiles() {
        let config = ModelConfiguration(isStoredInMemoryOnly: false)
        let url = config.url
        let fm = FileManager.default
        let candidates = [
            url,
            URL(fileURLWithPath: url.path + "-wal"),
            URL(fileURLWithPath: url.path + "-shm")
        ]
        for file in candidates {
            try? fm.removeItem(at: file)
        }
        let parent = url.deletingLastPathComponent()
        if let items = try? fm.contentsOfDirectory(at: parent, includingPropertiesForKeys: nil) {
            for item in items where item.lastPathComponent.hasPrefix("default.store") {
                try? fm.removeItem(at: item)
            }
        }
        if let group = AppGroup.containerURL {
            try? fm.removeItem(at: group.appendingPathComponent("autofill-index.json"))
        }
    }

    static func wipeSecretsAndWelcome() {
        VaultAuthStore.resetVaultAuth()
        SharedVaultKeychain.resetAll()
        AutofillIndexStore.clear()
        // Logins of the erased vault must not stay in the QuickType bar.
        AutofillIdentitySync.clear()
        EphemeralSecretStore.clear()
        AppPreferences.shared.resetWelcome()
    }
}
