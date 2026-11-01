package alibarznji.qasa.screens.auth.pair

import alibarznji.qasa.R
import alibarznji.qasa.device.DeviceRegistrationService
import alibarznji.qasa.vault.VaultSecureStore
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * State for the Link Device flow (`PairExistingVaultView.swift:8-14`). Pairing only adopts
 * the account id; the vault itself is created afterwards on the normal Create screen,
 * where `VaultSession.createVault` keeps an existing `store.accountId`.
 */
@HiltViewModel
class PairViewModel @Inject constructor(
    private val devices: DeviceRegistrationService,
    private val store: VaultSecureStore,
) : ViewModel() {
    var step by mutableStateOf(PairStep.Intro)
        private set
    var code by mutableStateOf("")
        private set
    var accountIdText by mutableStateOf("")
        private set

    /** A string resource id, never server text. */
    var errorMessage by mutableStateOf<Int?>(null)
        private set
    var isChecking by mutableStateOf(false)
        private set

    fun updateCode(value: String) {
        // iOS uses a number pad; keep the field to the six digits a code can have.
        code = value.filter(Char::isDigit).take(6)
    }

    fun updateAccountId(value: String) {
        accountIdText = value
    }

    fun showEnterCode() {
        step = PairStep.EnterCode
    }

    /** System back. Returns false when the flow should close (Cancel). */
    fun back(): Boolean {
        if (isChecking) return true
        val previous = step.previous() ?: return false
        errorMessage = null
        step = previous
        return true
    }

    /**
     * The activity-scoped view model outlives a single visit (Pair is shown inline by the
     * Create screen, not a nav destination), so clear it whenever the flow closes.
     */
    fun reset() {
        step = PairStep.Intro
        code = ""
        accountIdText = ""
        errorMessage = null
        isChecking = false
    }

    /** `applyPairing()` (`PairExistingVaultView.swift:221-256`). */
    fun applyPairing() {
        if (isChecking) return
        errorMessage = null
        val accountId = resolvePairTarget(accountIdText, code, PairingSessionStore.shared)
        if (accountId == null) {
            errorMessage = R.string.pair_invalidcode
            return
        }
        isChecking = true
        viewModelScope.launch {
            try {
                when (val outcome = classifyLinkCheck(devices.checkLinkEligibility(accountId))) {
                    LinkOutcome.Linked -> {
                        store.accountId = accountId
                        step = PairStep.Success
                    }
                    LinkOutcome.NeedsPremium -> step = PairStep.NeedsPremium
                    is LinkOutcome.Error -> errorMessage = outcome.message
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Any transport or decoding failure is one message, as on iOS; the raw
                // exception text is never shown.
                errorMessage = R.string.pair_checkfailed
            } finally {
                isChecking = false
            }
        }
    }
}
