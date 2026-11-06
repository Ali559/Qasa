package alibarznji.qasa

import android.app.Application
import android.util.Log
import alibarznji.qasa.push.PushChannels
import alibarznji.qasa.push.PushTokenRegistrar
import alibarznji.qasa.vault.db.VaultStoreHolder
import alibarznji.qasa.vpn.VpnAutoConnector
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class QasaApp : Application() {
    @Inject lateinit var pushTokenRegistrar: PushTokenRegistrar
    @Inject lateinit var vaultStore: VaultStoreHolder
    /** Also creates `VPNService`, which registers the Always-on VPN callback. */
    @Inject lateinit var vpnAutoConnector: VpnAutoConnector

    override fun onCreate() {
        super.onCreate()
        PushChannels.ensureCreated(this)
        pushTokenRegistrar.refreshTokenIfAvailable()
        vpnAutoConnector.start()
        // Open the store before the first frame so a migration failure is reported by
        // `VaultStoreErrorView` rather than crashing a repository call. The database is
        // left untouched: this never deletes anything.
        vaultStore.open()?.let { Log.i(TAG, "Vault store opened") }
            ?: Log.e(TAG, "Vault store unavailable: ${vaultStore.failure.value?.underlying}")
    }

    private companion object {
        const val TAG = "QasaApp"
    }
}
