import Foundation
import SwiftData

enum AppBootstrap {
    @MainActor
    static func onVaultUnlocked(context: ModelContext, session: AppSession) async {
        await VaultRepository.shared.configure(context: context)
        await DeviceRegistrationService.shared.registerIfNeeded()
        if VaultKeychainStore.licenseToken != nil {
            _ = await LicenseManager.shared.refresh()
        }
        session.syncFromLicense()
        VaultSeedData.seedIfNeeded(context: context)
    }

    @MainActor
    static func onLogout() {
        VaultSession.shared.lock()
        VaultRepository.shared.cache.clear()
        VaultRepository.shared.resetContext()
    }
}
