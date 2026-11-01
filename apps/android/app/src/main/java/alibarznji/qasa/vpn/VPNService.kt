package alibarznji.qasa.vpn

import alibarznji.qasa.config.QasaConfig
import alibarznji.qasa.network.QasaApiClient
import alibarznji.qasa.network.VPNConnectRequest
import alibarznji.qasa.network.VPNConnectResponse
import alibarznji.qasa.network.VPNStatusResponse
import alibarznji.qasa.network.get
import alibarznji.qasa.network.post
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

@Singleton
class VPNService @Inject constructor(
    private val api: QasaApiClient,
) {
    private val _connected = MutableStateFlow(false)
    val connected = _connected.asStateFlow()
    // StateFlows, not plain vars: the screen redraws when the node, IP or error changes
    // even if `connected` itself did not.
    private val _nodeName = MutableStateFlow<String?>(null)
    val nodeName = _nodeName.asStateFlow()
    private val _city = MutableStateFlow<String?>(null)
    val city = _city.asStateFlow()
    private val _assignedIP = MutableStateFlow<String?>(null)
    val assignedIP = _assignedIP.asStateFlow()
    private val _lastError = MutableStateFlow<String?>(null)
    val lastError = _lastError.asStateFlow()
    /** iOS `isBusy`: the dial shows a spinner and ignores taps while a call is in flight. */
    private val _isBusy = MutableStateFlow(false)
    val isBusy = _isBusy.asStateFlow()

    suspend fun refreshStatus() = withContext(Dispatchers.IO) {
        try {
            val status: VPNStatusResponse = api.get(QasaConfig.vpnStatusUrl)
            _connected.value = status.connected
            _nodeName.value = status.node
            _city.value = status.city
            _assignedIP.value = status.assigned_ip
            _lastError.value = null
        } catch (e: Exception) {
            _lastError.value = e.message
        }
    }

    suspend fun connect() = withContext(Dispatchers.IO) {
        _isBusy.value = true
        try {
            api.post<VPNConnectResponse, VPNConnectRequest>(
                QasaConfig.vpnConnectUrl,
                VPNConnectRequest(null),
            )
            refreshStatus()
        } catch (e: Exception) {
            _lastError.value = e.message
            _connected.value = false
        } finally {
            _isBusy.value = false
        }
    }

    suspend fun disconnect() = withContext(Dispatchers.IO) {
        _isBusy.value = true
        try {
            api.postRaw(QasaConfig.vpnDisconnectUrl, "{}", authorized = true)
            _connected.value = false
            _nodeName.value = null
            _city.value = null
            _assignedIP.value = null
            _lastError.value = null
        } catch (e: Exception) {
            _lastError.value = e.message
        } finally {
            _isBusy.value = false
        }
    }
}
