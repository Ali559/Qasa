import Foundation
import SwiftData

enum AppBootstrap {
    @MainActor
    static func onVaultUnlocked(context: ModelContext, session: AppSession) async {
        await VaultRepository.shared.configure(context: context)
        // The license token is only trusted once its Ed25519 signature checks out, and
        // that needs a verifying key. Fetch it first so registration/payment responses
        // are not discarded simply because no key was cached yet.
        _ = await LicenseManager.shared.bootstrapVerifyingKey()
        await DeviceRegistrationService.shared.registerIfNeeded()
        if VaultKeychainStore.licenseToken != nil {
            _ = await LicenseManager.shared.refresh()
        }
        session.syncFromLicense()
        #if DEBUG
        // Sample credentials must never reach a shipping vault. On every unlock this
        // re-injected them whenever the item count hit zero — i.e. right after a user
        // purged compromised logins — including a live card PAN that autofill would
        // then offer to real merchants.
        VaultSeedData.seedIfNeeded(context: context)
        #endif
        await PushNotificationService.shared.requestAuthorizationAndRegister()
        await AppPreferences.shared.syncLocaleToServer()
    }

    @MainActor
    static func onLogout() {
        VaultSession.shared.lock()
        VaultRepository.shared.cache.clear()
        VaultRepository.shared.resetContext()
    }
}
