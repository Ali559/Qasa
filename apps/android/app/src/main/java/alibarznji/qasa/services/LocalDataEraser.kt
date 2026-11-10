package alibarznji.qasa.services

import alibarznji.qasa.backup.BackupService
import alibarznji.qasa.backup.clearRestoreKeys
import alibarznji.qasa.license.LicenseManager
import alibarznji.qasa.screens.AppSession
import alibarznji.qasa.vault.VaultRepository
import alibarznji.qasa.vault.VaultSecureStore
import alibarznji.qasa.vault.VaultSession
import alibarznji.qasa.vpn.VPNService
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Erases this phone's vault and identity: Settings "Clear Local Data" and the end of
 * "Delete account". The caller then locks, and the root screen falls back to Create Vault.
 */
@Singleton
class LocalDataEraser @Inject constructor(
    private val vpn: VPNService,
    private val repo: VaultRepository,
    private val session: VaultSession,
    private val license: LicenseManager,
    private val appSession: AppSession,
    private val backups: BackupService,
    private val secrets: VaultSecureStore,
) {
    suspend fun eraseAll() {
        // Tunnel off and the device's WireGuard key gone with the account.
        vpn.forget()
        repo.clearAll()
        // Clears the whole secure store: salt, verifier, account id, device fingerprint,
        // license token, the biometric secret and the backup restore keys (current,
        // written flag and a pending one from a restored backup).
        session.wipeVault()
        clearRestoreKeys(secrets)
        // Same scope as iOS `VaultAuthStore.resetVaultAuth`: the licence goes too,
        // so a cleared device does not keep premium attached to a deleted vault.
        license.clear()
        appSession.syncFromLicense(license)
        // Backup location, schedule and reminder go; the backup file itself stays where it is.
        backups.clearSettings()
    }
}
