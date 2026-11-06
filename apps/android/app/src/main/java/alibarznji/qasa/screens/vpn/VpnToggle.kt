package alibarznji.qasa.screens.vpn

import alibarznji.qasa.vpn.VPNService
import alibarznji.qasa.vpn.VpnConnectResult
import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch

/**
 * Tap handler for the VPN dial and the Home card: connect or disconnect, and on the first
 * connect show Android's "Connection request" dialog, then carry on if the user allows it.
 */
@Composable
fun rememberVpnToggle(vpn: VPNService): () -> Unit {
    val scope = rememberCoroutineScope()
    val consent = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            scope.launch { vpn.connect() }
        } else {
            vpn.reportPermissionDenied()
        }
    }
    return toggle@{
        if (vpn.isBusy.value) return@toggle
        scope.launch {
            if (vpn.connected.value) {
                vpn.disconnect()
            } else {
                val result = vpn.connect()
                if (result is VpnConnectResult.NeedsPermission) consent.launch(result.intent)
            }
        }
    }
}
