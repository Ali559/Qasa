package alibarznji.qasa.screens.auth

import alibarznji.qasa.R
import alibarznji.qasa.theme.BrandHeader
import alibarznji.qasa.theme.CardContainer
import alibarznji.qasa.theme.PrimaryButton
import alibarznji.qasa.theme.QasaDimens
import alibarznji.qasa.theme.QasaIconSize
import alibarznji.qasa.theme.ScreenScaffold
import alibarznji.qasa.theme.SectionLabel
import alibarznji.qasa.theme.StatusPill
import alibarznji.qasa.theme.StickyBottomBar
import alibarznji.qasa.theme.ThemedTextField
import alibarznji.qasa.theme.qasaColors
import alibarznji.qasa.theme.qasaText
import alibarznji.qasa.vault.BiometricGateEffect
import alibarznji.qasa.vault.VaultSecureStore
import alibarznji.qasa.vault.VaultSession
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.material.icons.filled.Shield
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class UnlockViewModel @Inject constructor(
    val session: VaultSession,
    val store: VaultSecureStore,
) : ViewModel()

/**
 * One in-flight gate attempt. [cipher] is the `CryptoObject` the prompt must authorize:
 * a DECRYPT cipher to read the sealed master password, or an ENCRYPT cipher to seal it.
 * A `null` cipher means this device has no usable gate, and no prompt is shown.
 */
private sealed interface UnlockPrompt {
    val cipher: javax.crypto.Cipher?

    data class Unlock(override val cipher: javax.crypto.Cipher?) : UnlockPrompt

    data class Capture(
        override val cipher: javax.crypto.Cipher,
        val password: String,
    ) : UnlockPrompt
}

@Composable
fun UnlockScreen(
    onUnlocked: () -> Unit,
    vm: UnlockViewModel = hiltViewModel(),
) {
    val c = qasaColors
    var password by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var prompt by remember { mutableStateOf<UnlockPrompt?>(null) }
    val canUnlock = password.isNotBlank()
    val failedMessage = stringResource(R.string.auth_unlock_biometricsfailed)
    val wrongPasswordMessage = stringResource(R.string.auth_unlock_wrongpassword)

    fun unlockWithPassword(candidate: String) {
        if (vm.session.unlockMatching(candidate) != null) {
            password = ""
            onUnlocked()
        } else {
            error = wrongPasswordMessage
        }
    }

    BiometricGateEffect(
        enabled = prompt != null,
        title = stringResource(R.string.auth_unlock_biometrics),
        subtitle = stringResource(R.string.auth_unlock_biometricshint),
        cipher = prompt?.cipher,
        onAuthenticated = { cipher ->
            when (val attempt = prompt) {
                is UnlockPrompt.Unlock -> {
                    val ok = cipher != null && vm.session.unlockWithBiometricCipher(cipher)
                    if (ok) {
                        password = ""
                        onUnlocked()
                    } else {
                        error = failedMessage
                    }
                }
                is UnlockPrompt.Capture -> {
                    // Unlock first so only the variant that verified gets sealed (sealing
                    // the password requires the same authorization as reading it). Sealing
                    // is optional, so a failed seal still lets the user in.
                    val used = vm.session.unlockMatching(attempt.password)
                    if (used == null) {
                        error = wrongPasswordMessage
                    } else {
                        if (cipher != null) vm.session.captureBiometricSecret(cipher, used)
                        password = ""
                        onUnlocked()
                    }
                }
                null -> Unit
            }
            prompt = null
        },
        onDismissed = {
            val attempt = prompt
            prompt = null
            when (attempt) {
                // Sealing is an optional convenience, not the unlock itself: a declined
                // or failed prompt must not lock the user out of a password they know.
                is UnlockPrompt.Capture -> unlockWithPassword(attempt.password)
                is UnlockPrompt.Unlock -> error = failedMessage
                null -> Unit
            }
        },
    )

    ScreenScaffold(
        header = { BrandHeader(applyStatusBars = false) { StatusPill(stringResource(R.string.brand_localoffline)) } },
        stickyBottom = {
            StickyBottomBar {
                PrimaryButton(
                    title = stringResource(R.string.auth_unlock_button),
                    icon = Icons.Default.LockOpen,
                    enabled = canUnlock,
                    onClick = {
                        // The secret is sealed at unlock time, not at create time: a
                        // password can only be cached behind a cipher the hardware
                        // authorized, and the user is holding the password right now.
                        val captureCipher = if (vm.session.needsBiometricCapture) {
                            vm.session.biometricCaptureCipher()
                        } else {
                            null
                        }
                        if (captureCipher != null) {
                            prompt = UnlockPrompt.Capture(captureCipher, password)
                        } else {
                            unlockWithPassword(password)
                        }
                    },
                )
            }
        },
    ) {
        Text(stringResource(R.string.auth_unlock_title), color = c.textPrimary, style = qasaText.displayLarge)
        Spacer(Modifier.height(QasaDimens.contentGap))
        Text(
            stringResource(R.string.auth_unlock_subtitle),
            color = c.textSecondary,
            style = qasaText.bodyMedium
        )
        Spacer(Modifier.height(QasaDimens.screenPadding))
        SectionLabel(stringResource(R.string.auth_unlock_masterpassword))
        Spacer(Modifier.height(QasaDimens.contentGap))
        ThemedTextField(
            value = password,
            onValueChange = { password = it; error = null },
            placeholder = stringResource(R.string.auth_unlock_placeholder),
            leadingIcon = Icons.Default.Lock,
            isSecure = true,
            passwordVisible = visible,
            onToggleVisibility = { visible = !visible },
        )
        error?.let {
            Spacer(Modifier.height(QasaDimens.contentGap))
            Text(it, color = c.danger, style = qasaText.bodySmallMedium)
        }
        if (vm.session.biometricUnlockAvailable()) {
            Spacer(Modifier.height(QasaDimens.gap))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(QasaDimens.cornerRadius))
                    .background(c.cardBackground)
                    .border(QasaDimens.stroke, c.stroke, RoundedCornerShape(QasaDimens.cornerRadius))
                    .clickable {
                        if (prompt == null) {
                            prompt = UnlockPrompt.Unlock(vm.session.biometricUnlockCipher())
                        }
                    }
                    .padding(QasaDimens.gap),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(QasaIconSize.x6l)
                        .clip(CircleShape)
                        .background(c.cardBackgroundLight),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Default.Fingerprint,
                        contentDescription = null,
                        tint = c.accent,
                        modifier = Modifier.size(QasaIconSize.xxl),
                    )
                }
                Spacer(Modifier.width(QasaDimens.gapSmall))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.auth_unlock_biometrics), color = c.textPrimary, style = qasaText.rowTitle)
                    Text(stringResource(R.string.auth_unlock_biometricshint), color = c.textSecondary, style = MaterialTheme.typography.labelMedium)
                }
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = c.textTertiary)
            }
        }
        Spacer(Modifier.height(QasaDimens.gap))
        // iOS: shield + "Local unlock only" title above the body.
        CardContainer(background = c.cardBackgroundLight) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Shield, null, tint = c.accent)
                Spacer(Modifier.width(QasaDimens.contentGap))
                Text(stringResource(R.string.auth_unlock_localonly), color = c.textPrimary, style = qasaText.rowTitle)
            }
            Spacer(Modifier.height(QasaDimens.contentGap))
            Text(
                stringResource(R.string.auth_unlock_localonlybody),
                color = c.textSecondary,
                style = qasaText.bodyMedium
            )
        }
        Spacer(Modifier.height(QasaDimens.heroArtHeight))
    }
}
