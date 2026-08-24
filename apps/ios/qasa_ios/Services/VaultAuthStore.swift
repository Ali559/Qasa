import Foundation

/// Lightweight local auth stub for vault unlock / create flows.
enum VaultAuthStore {
    private static let existsKey = "kaso.vaultExists"
    private static let passwordKey = "kaso.masterPassword"

    static var vaultExists: Bool {
        get { UserDefaults.standard.bool(forKey: existsKey) }
        set { UserDefaults.standard.set(newValue, forKey: existsKey) }
    }

    static func createVault(masterPassword: String) {
        UserDefaults.standard.set(masterPassword, forKey: passwordKey)
        vaultExists = true
    }

    static func verifyMasterPassword(_ password: String) -> Bool {
        guard vaultExists else { return false }
        let stored = UserDefaults.standard.string(forKey: passwordKey)
        // Demo fallback: if no stored password (legacy installs), accept any non-empty unlock.
        guard let stored, !stored.isEmpty else { return !password.isEmpty }
        return stored == password
    }

    static func resetVaultAuth() {
        UserDefaults.standard.removeObject(forKey: passwordKey)
        vaultExists = false
    }
}
