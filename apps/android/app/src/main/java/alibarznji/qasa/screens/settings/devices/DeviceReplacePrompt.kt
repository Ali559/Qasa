package alibarznji.qasa.screens.settings.devices

import alibarznji.qasa.R
import alibarznji.qasa.device.DeviceRegistrationService
import alibarznji.qasa.services.AppBootstrap
import alibarznji.qasa.theme.QasaConfirmSheet
import alibarznji.qasa.theme.QasaDimens
import alibarznji.qasa.theme.QasaIconSize
import alibarznji.qasa.theme.qasaColors
import alibarznji.qasa.theme.qasaText
import alibarznji.qasa.theme.LocalQasaToast
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch

@HiltViewModel
class DeviceReplaceViewModel @Inject constructor(
    val devices: DeviceRegistrationService,
    private val bootstrap: AppBootstrap,
) : ViewModel() {
    var working by mutableStateOf(false)
        private set
    var failed by mutableStateOf(false)
        private set

    fun confirm() {
        if (working) return
        working = true
        failed = false
        viewModelScope.launch {
            val ok = try {
                bootstrap.replaceOldDevice()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Exception) {
                false
            }
            working = false
            failed = !ok
        }
    }

    fun dismiss() {
        if (working) return
        failed = false
        devices.dismissReplacePrompt()
    }
}

/**
 * App-wide while unlocked: the "Replace your old device?" confirmation (after a setup
 * restore hits the device limit, or from the Linked Devices banner) and the one-time
 * notice once a replace went through.
 */
@Composable
fun DeviceReplaceHost(vm: DeviceReplaceViewModel = hiltViewModel()) {
    val context = LocalContext.current
    // The app's own toast (iOS `ToastCenter` look), not the system one.
    val qasaToast = LocalQasaToast.current
    val prompt by vm.devices.replacePrompt.collectAsState()
    val replaced by vm.devices.replacedNotice.collectAsState()

    replaced?.let { name ->
        val text = stringResource(R.string.backup_replace_donefmt, name)
        LaunchedEffect(name) {
            qasaToast.show(text)
            vm.devices.consumeReplacedNotice()
        }
    }

    val old = prompt ?: return
    val c = qasaColors
    QasaConfirmSheet(
        title = stringResource(R.string.backup_replace_title),
        message = stringResource(R.string.backup_replace_bodyfmt, old.device_name),
        confirmLabel = stringResource(R.string.backup_replace_button),
        onConfirm = vm::confirm,
        onDismiss = vm::dismiss,
        dismissLabel = stringResource(R.string.backup_reminder_dismiss),
        enabled = !vm.working,
    ) {
        if (vm.working) {
            CircularProgressIndicator(Modifier.size(QasaIconSize.xl), color = c.accent, strokeWidth = QasaDimens.ringStroke)
        }
        if (vm.failed) {
            Text(stringResource(R.string.backup_replace_failed), color = c.danger, style = qasaText.bodySmallMedium)
        }
    }
}
