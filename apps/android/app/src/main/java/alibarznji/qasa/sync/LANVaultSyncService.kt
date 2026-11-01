package alibarznji.qasa.sync

import alibarznji.qasa.license.LicenseManager
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Placeholder LAN sync matching iOS stub — transfers qasa-vault-placeholder:{accountId} only.
 * Real encrypted vault blob sync is out of scope until both clients share a sync protocol.
 */
@Singleton
class LANVaultSyncService @Inject constructor(
    private val license: LicenseManager,
) {
    private val _status = MutableStateFlow("Idle")
    val statusMessage = _status.asStateFlow()

    fun startHost(pairCode: String, vaultBlob: ByteArray) {
        if (!license.isPremium) {
            _status.value = "Premium required"
            return
        }
        _status.value = "Hosting with code $pairCode (${vaultBlob.size} bytes placeholder)"
    }

    fun startBrowse(pairCode: String, onData: (ByteArray) -> Unit) {
        if (!license.isPremium) {
            _status.value = "Premium required"
            return
        }
        _status.value = "Browsing for code $pairCode (stub — no peers)"
    }

    fun stop() {
        _status.value = "Stopped"
    }
}
