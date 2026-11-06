package alibarznji.qasa.screens.tools.health

import alibarznji.qasa.breach.PwnedPasswordsService
import alibarznji.qasa.screens.AppSession
import alibarznji.qasa.screens.PasswordHealthAnalyzer
import alibarznji.qasa.screens.PasswordHealthReport
import alibarznji.qasa.vault.EphemeralClipboard
import alibarznji.qasa.vault.VaultPayload
import alibarznji.qasa.vault.VaultRepository
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@HiltViewModel
class HealthViewModel @Inject constructor(
    val repo: VaultRepository,
    val clipboard: EphemeralClipboard,
    private val pwned: PwnedPasswordsService,
    appSession: AppSession,
) : ViewModel() {
    /** Bumped by [recheckBreaches] to re-run the Pwned Passwords check. */
    private val recheck = MutableStateFlow(0)

    /** True while the Pwned Passwords check runs (the Breached tab shows "Checking…"). */
    private val _breachChecking = MutableStateFlow(true)
    val breachChecking: StateFlow<Boolean> = _breachChecking.asStateFlow()

    /** Last successful counts, so a row edit does not blink Breached to 0 while re-checking. */
    private var lastCounts: Map<String, Int>? = null

    /**
     * iOS `refreshReport()` (`PasswordHealthView.swift:81`) runs on appear; here it re-runs
     * on every row change, so editing or deleting an item from the issues screen updates
     * the gauge and the lists when the user comes back. `null` until the first pass, so
     * the gauge does not count up to the empty report's 100 first. Each pass also runs the
     * Pwned Passwords check (only 5-char SHA-1 prefixes leave the device; ranges are cached
     * for the process) and emits a second report with the breached logins.
     *
     * The breach check is Premium. A Free account never calls HIBP: the report is marked
     * [PasswordHealthReport.breachLocked], scored without the breach deduction, and any
     * counts or ranges from an earlier Premium period are dropped. The tier is part of the
     * key, so buying Premium runs the check and a lapse locks it again straight away.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val report: StateFlow<PasswordHealthReport?> =
        combine(repo.observeRows(), recheck, appSession.isPremium) { rows, _, premium -> rows to premium }
        .transformLatest { (rows, premium) ->
            val logins = rows.mapNotNull { row ->
                val p = runCatching { repo.getPayload(row.id) }.getOrNull() as? VaultPayload.Login
                    ?: return@mapNotNull null
                // iOS trims and skips blank passwords, so empty logins are neither weak nor reused.
                val trimmed = p.password.trim()
                if (trimmed.isEmpty()) null else row to trimmed
            }
            if (!premium) {
                lastCounts = null
                pwned.clearCache()
                _breachChecking.value = false
                emit(PasswordHealthAnalyzer.analyze(logins).copy(breachLocked = true))
                return@transformLatest
            }
            // Local report straight away, then again once the Pwned Passwords check is back.
            emit(PasswordHealthAnalyzer.analyze(logins, lastCounts))
            if (logins.isEmpty()) {
                _breachChecking.value = false
                return@transformLatest
            }
            _breachChecking.value = true
            try {
                val counts = pwned.breachCounts(logins.map { it.second })
                lastCounts = counts
                emit(PasswordHealthAnalyzer.analyze(logins, counts))
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                emit(PasswordHealthAnalyzer.analyze(logins).copy(breachCheckFailed = true))
            } finally {
                _breachChecking.value = false
            }
        }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /** "Check again" after a failed (or finished) breach check. */
    fun recheckBreaches() {
        recheck.value++
    }

    suspend fun loadPayload(id: String) = withContext(Dispatchers.Default) { repo.getPayload(id) }

    /** iOS stamps `lastUsedAt` after every row copy (`VaultItemRow.swift:139`). */
    fun markUsed(id: String) {
        viewModelScope.launch { runCatching { repo.markUsed(id) } }
    }

    fun delete(id: String) {
        viewModelScope.launch { runCatching { repo.delete(id) } }
    }
}
