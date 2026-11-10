import Foundation

/// Legacy compatibility shim — delegates to encrypted vault session.
enum VaultAuthStore {
    static var vaultExists: Bool {
        VaultSession.shared.vaultExists
    }

    static func createVault(masterPassword: String, biometricsEnabled: Bool = true) throws {
        try VaultSession.shared.createVault(password: masterPassword, biometricsEnabled: biometricsEnabled)
    }

    /// `typedByUser: false` for a password released by Face ID / Touch ID, so the
    /// biometric interval (`BiometricPolicy`) isn't restarted by a biometric unlock.
    static func verifyMasterPassword(_ password: String, typedByUser: Bool = true) -> Bool {
        VaultSession.shared.unlock(password: password, recordsMasterPasswordUse: typedByUser)
    }

    static func resetVaultAuth() {
        VaultSession.shared.wipeVault()
        LicenseManager.shared.clear()
        // The backup file itself stays where the user put it.
        BackupPreferences.shared.clear()
    }
}
