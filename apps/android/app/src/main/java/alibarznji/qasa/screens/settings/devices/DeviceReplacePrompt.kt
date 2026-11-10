package alibarznji.qasa.screens.settings.devices

import alibarznji.qasa.R
import alibarznji.qasa.device.DeviceRegistrationService
import alibarznji.qasa.services.AppBootstrap
import alibarznji.qasa.theme.QasaDimens
import alibarznji.qasa.theme.qasaColors
import alibarznji.qasa.theme.qasaText
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
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
    val prompt by vm.devices.replacePrompt.collectAsState()
    val replaced by vm.devices.replacedNotice.collectAsState()

    replaced?.let { name ->
        val text = stringResource(R.string.backup_replace_donefmt, name)
        LaunchedEffect(name) {
            Toast.makeText(context, text, Toast.LENGTH_LONG).show()
            vm.devices.consumeReplacedNotice()
        }
    }

    val old = prompt ?: return
    val c = qasaColors
    AlertDialog(
        onDismissRequest = vm::dismiss,
        containerColor = c.cardBackground,
        titleContentColor = c.textPrimary,
        textContentColor = c.textSecondary,
        confirmButton = {
            TextButton(onClick = vm::confirm, enabled = !vm.working) {
                Text(stringResource(R.string.backup_replace_button), color = c.danger)
            }
        },
        dismissButton = {
            TextButton(onClick = vm::dismiss, enabled = !vm.working) {
                Text(stringResource(R.string.backup_reminder_dismiss), color = c.accent)
            }
        },
        title = { Text(stringResource(R.string.backup_replace_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(QasaDimens.gap)) {
                Text(stringResource(R.string.backup_replace_bodyfmt, old.device_name))
                if (vm.working) {
                    CircularProgressIndicator(Modifier.size(20.dp), color = c.accent, strokeWidth = 2.dp)
                }
                if (vm.failed) {
                    Text(stringResource(R.string.backup_replace_failed), color = c.danger, style = qasaText.bodySmallMedium)
                }
            }
        },
    )
}
