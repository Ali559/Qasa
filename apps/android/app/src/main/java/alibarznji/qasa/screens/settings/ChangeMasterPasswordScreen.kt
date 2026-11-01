package alibarznji.qasa.screens.settings

import alibarznji.qasa.R
import alibarznji.qasa.vault.crypto.VaultCryptoException
import alibarznji.qasa.theme.BackChromeHeader
import alibarznji.qasa.theme.PrimaryButton
import alibarznji.qasa.theme.QasaDimens
import alibarznji.qasa.theme.ScreenScaffold
import alibarznji.qasa.theme.SectionLabel
import alibarznji.qasa.theme.StickyBottomBar
import alibarznji.qasa.theme.ThemedTextField
import alibarznji.qasa.theme.qasaColors
import alibarznji.qasa.theme.qasaText
import alibarznji.qasa.vault.BiometricGateEffect
import alibarznji.qasa.vault.VaultRepository
import alibarznji.qasa.vault.VaultSession
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.crypto.Cipher
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@HiltViewModel
class ChangePwViewModel @Inject constructor(
    val session: VaultSession,
    val repo: VaultRepository,
) : ViewModel()

@Composable
fun ChangeMasterPasswordScreen(onDone: () -> Unit, vm: ChangePwViewModel = hiltViewModel()) {
    val c = qasaColors
    var current by remember { mutableStateOf("") }
    var next by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var currentVisible by remember { mutableStateOf(false) }
    var nextVisible by remember { mutableStateOf(false) }
    var confirmVisible by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    // The sealed copy holds the old password, so the change drops it; re-sealing the new
    // one needs its own authorized cipher, which can only be requested after the
    // re-encryption succeeds.
    var resealCipher by remember { mutableStateOf<Cipher?>(null) }
    var pendingFinish by remember { mutableStateOf(false) }

    var didSucceed by remember { mutableStateOf(false) }
    val wrongPassword = stringResource(R.string.settings_wrongmasterpassword)
    val weakPassword = stringResource(R.string.settings_weakmasterpassword)
    val mismatch = stringResource(R.string.settings_passwordmismatch)

    // iOS `canSave`: at least 8 characters, and the new password must actually differ.
    val canSave = current.isNotEmpty() &&
        next.length >= 8 &&
        confirm.isNotEmpty() &&
        next != current &&
        !saving

    BiometricGateEffect(
        enabled = resealCipher != null,
        title = stringResource(R.string.auth_unlock_biometrics),
        subtitle = stringResource(R.string.auth_unlock_biometricshint),
        cipher = resealCipher,
        onAuthenticated = { cipher ->
            // Biometric unlock keeps working only if the new password is sealed; on
            // refusal the store already holds no cached copy and stays that way.
            if (cipher != null) vm.session.captureBiometricSecret(cipher, next)
            next = ""
            resealCipher = null
            if (pendingFinish) {
                pendingFinish = false
                onDone()
            }
        },
        onDismissed = {
            next = ""
            resealCipher = null
            if (pendingFinish) {
                pendingFinish = false
                onDone()
            }
        },
    )

    ScreenScaffold(
        header = {
            BackChromeHeader(
                title = stringResource(R.string.settings_changemasterpassword),
                onBack = onDone,
                applyStatusBars = false,
            )
        },
        stickyBottom = {
            StickyBottomBar {
                PrimaryButton(
                    title = if (saving) stringResource(R.string.settings_saving) else stringResource(R.string.settings_savemasterpassword),
                    enabled = canSave,
                    onClick = {
                        if (next != confirm) {
                            error = mismatch
                            return@PrimaryButton
                        }
                        saving = true
                        scope.launch {
                            try {
                                val material = vm.session.derivePasswordChange(current, next)
                                vm.repo.reencryptAll(material.oldEnc, material.newEnc, material.newSearch)
                                vm.session.commitPasswordChange(material)
                                didSucceed = true
                                // iOS leaves the success line up briefly before dismissing.
                                delay(700)
                                // Re-seal before leaving: the prompt keeps the screen
                                // alive, and every dismissal path still navigates.
                                val cipher = vm.session.biometricCaptureCipher()
                                if (vm.session.biometricsEnabled && cipher != null) {
                                    pendingFinish = true
                                    resealCipher = cipher
                                } else {
                                    onDone()
                                }
                            } catch (e: VaultCryptoException.WrongPassword) {
                                error = wrongPassword
                            } catch (e: VaultCryptoException.WeakPassword) {
                                error = weakPassword
                            } catch (e: Exception) {
                                error = e.message
                            } finally {
                                saving = false
                            }
                        }
                    },
                )
            }
        },
    ) {
        Text(
            stringResource(R.string.settings_changemastersubtitle),
            color = c.textSecondary,
            style = qasaText.bodyMedium,
        )
        Spacer(Modifier.height(QasaDimens.screenPadding))
        SectionLabel(stringResource(R.string.settings_currentmasterpassword))
        Spacer(Modifier.height(QasaDimens.contentGap))
        ThemedTextField(
            value = current,
            onValueChange = { current = it; error = null },
            placeholder = stringResource(R.string.settings_currentmasterpassword),
            isSecure = true,
            passwordVisible = currentVisible,
            onToggleVisibility = { currentVisible = !currentVisible },
        )
        Spacer(Modifier.height(QasaDimens.screenPadding))
        SectionLabel(stringResource(R.string.settings_newmasterpassword))
        Spacer(Modifier.height(QasaDimens.contentGap))
        ThemedTextField(
            value = next,
            onValueChange = { next = it; error = null },
            placeholder = stringResource(R.string.settings_newmasterpassword),
            isSecure = true,
            passwordVisible = nextVisible,
            onToggleVisibility = { nextVisible = !nextVisible },
        )
        Spacer(Modifier.height(QasaDimens.gapSmall))
        ThemedTextField(
            value = confirm,
            onValueChange = { confirm = it; error = null },
            placeholder = stringResource(R.string.settings_confirmmasterpassword),
            isSecure = true,
            passwordVisible = confirmVisible,
            onToggleVisibility = { confirmVisible = !confirmVisible },
        )
        error?.let {
            Spacer(Modifier.height(QasaDimens.gapSmall))
            Text(it, color = c.danger, style = qasaText.bodySmall)
        }
        if (didSucceed) {
            Spacer(Modifier.height(QasaDimens.gapSmall))
            Text(stringResource(R.string.settings_masterpasswordchanged), color = c.success, style = qasaText.bodySmall)
        }
    }
}
