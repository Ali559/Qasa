import Foundation
import SwiftData

/// Body of `DELETE /v1/account`: the server only deletes when this matches the account
/// in the caller's license token.
nonisolated struct DeleteAccountRequest: Encodable, Sendable {
    let confirm_account_id: String
}

/// In-app account deletion (App Store guideline 5.1.1(v)), as the desktop's
/// `account_delete`: delete on the server first, and only then forget the account and
/// erase the vault on this phone. A failed call leaves everything local untouched.
enum AccountDeletion {
    /// Whether this phone has an account the server knows about: an account id plus a
    /// license token from registration. Without a token the delete call cannot be
    /// authenticated, so the Settings row stays hidden.
    static var isAvailable: Bool {
        VaultKeychainStore.accountId != nil && VaultKeychainStore.licenseToken != nil
    }

    /// `DELETE /v1/account`, then `eraseLocal`. A 401 (expired or revoked token) runs
    /// `reauthorize` (re-registration issues a fresh token) and retries once.
    static func deleteAccount(
        accountId: String,
        api: QasaAPIClient = .shared,
        url: URL = QasaConfig.accountURL,
        reauthorize: (() async -> Void)? = nil,
        eraseLocal: () -> Void
    ) async throws {
        let body = DeleteAccountRequest(confirm_account_id: accountId)
        do {
            _ = try await api.rawRequest(url, method: "DELETE", body: body, authorized: true)
        } catch QasaAPIError.unauthorized where reauthorize != nil {
            await reauthorize?()
            _ = try await api.rawRequest(url, method: "DELETE", body: body, authorized: true)
        }
        eraseLocal()
    }

    /// Deletes every row of every vault model (items, search tokens, folders,
    /// notifications, monitored emails). Real deletes, not sync tombstones: the account
    /// is gone, so there is nothing left to sync them to.
    @MainActor
    static func eraseVaultRecords(context: ModelContext) throws {
        try context.delete(model: VaultSearchToken.self)
        try context.delete(model: VaultItemRecord.self)
        try context.delete(model: VaultCollection.self)
        try context.delete(model: AppNotification.self)
        try context.delete(model: MonitoredEmail.self)
        try context.save()
    }

    /// Forgets the vault and account identity in `meta` (the Keychain in the app).
    static func forgetIdentity(_ meta: SyncVaultMetaStore) {
        meta.vaultExists = false
        meta.kdfSalt = nil
        meta.passwordVerifier = nil
        meta.kdfVersion = nil
        meta.accountId = nil
        meta.deviceFingerprint = nil
    }

    /// The whole local erase after a successful delete, the same end state as
    /// Settings > Clear local data plus the vault rows: vault rows, every Keychain
    /// secret (salt, verifier, account id, device fingerprint, license token, biometric
    /// password), the AutoFill copies, and the license in memory. The caller then runs
    /// the logout path so the root shows Create vault.
    @MainActor
    static func eraseThisDevice(context: ModelContext?) {
        if let context {
            try? eraseVaultRecords(context: context)
        }
        VaultRepository.shared.cache.clear()
        // Locks the session, clears all Keychain keys and the license.
        VaultAuthStore.resetVaultAuth()
        SharedVaultKeychain.resetAll()
        AutofillIndexStore.clear()
        AutofillIdentitySync.clear()
        EphemeralSecretStore.clear()
    }
}
