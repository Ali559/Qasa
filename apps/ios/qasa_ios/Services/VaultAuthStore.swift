import Foundation

/// Legacy compatibility shim — delegates to encrypted vault session.
enum VaultAuthStore {
    static var vaultExists: Bool {
        VaultSession.shared.vaultExists
    }

    static func createVault(masterPassword: String, biometricsEnabled: Bool = true) throws {
        try VaultSession.shared.createVault(password: masterPassword, biometricsEnabled: biometricsEnabled)
    }

    static func verifyMasterPassword(_ password: String) -> Bool {
        VaultSession.shared.unlock(password: password)
    }

    static func resetVaultAuth() {
        VaultSession.shared.wipeVault()
        LicenseManager.shared.clear()
    }
}
