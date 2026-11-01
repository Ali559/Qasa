package alibarznji.qasa.screens.breach

import alibarznji.qasa.breach.BreachCheckResult
import alibarznji.qasa.breach.BreachMonitorService
import alibarznji.qasa.screens.AppSession
import alibarznji.qasa.vault.VaultItemKind
import alibarznji.qasa.vault.VaultPayload
import alibarznji.qasa.vault.VaultRepository
import alibarznji.qasa.vault.db.MonitoredEmailEntity
import alibarznji.qasa.vault.db.VaultDao
import alibarznji.qasa.vault.db.VaultStoreHolder
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

const val STATUS_AT_RISK = "atRisk"
const val STATUS_SECURE = "secure"

/** iOS `BreachMonitorView.maxSlots`. */
const val BREACH_MAX_SLOTS = 10

/** Outcome of [BreachViewModel.addEmail] the screen has to react to. */
enum class AddEmailOutcome { Added, Duplicate, Invalid, LimitReached, Failed }

/**
 * State + actions behind `BreachMonitorView.swift`. Scan results are in-memory only, exactly like
 * iOS `@State scanResults`: "resolved" is not persisted anywhere on iOS — a card is Resolved when the
 * latest scan of that email found no match. Only the monitored row's status is stored.
 */
@HiltViewModel
class BreachViewModel @Inject constructor(
    private val breach: BreachMonitorService,
    private val store: VaultStoreHolder,
    private val vault: VaultRepository,
    val appSession: AppSession,
) : ViewModel() {
    private val dao: VaultDao get() = store.requireDao()

    // iOS `@Query(sort: \MonitoredEmail.createdAt)` is oldest first; the DAO returns newest first.
    val emails: StateFlow<List<MonitoredEmailEntity>> = dao.observeMonitoredEmails()
        .map { list -> list.sortedBy { it.createdAt } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _scanResults = MutableStateFlow<List<BreachCheckResult>>(emptyList())
    val scanResults: StateFlow<List<BreachCheckResult>> = _scanResults.asStateFlow()

    /** iOS `isChecking`: shared by add and rescan, drives the "Saving…" label. */
    private val _isChecking = MutableStateFlow(false)
    val isChecking: StateFlow<Boolean> = _isChecking.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    /** Success line after a password rotation ("Password updated for …"). */
    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    /** iOS `rescanAll` (BreachMonitorView.swift:291), run from `.onAppear`. */
    fun rescanAll() {
        viewModelScope.launch {
            if (!appSession.isPremium.value) return@launch
            val monitored = dao.getMonitoredEmails()
            if (monitored.isEmpty()) return@launch
            _isChecking.value = true
            try {
                val results = breach.checkEmails(monitored.map { it.email })
                _scanResults.value = results
                for (result in results) {
                    val item = monitored.firstOrNull { it.email.equals(result.email, ignoreCase = true) } ?: continue
                    val status = if (result.breached) STATUS_AT_RISK else STATUS_SECURE
                    if (item.statusRaw != status) dao.upsertMonitoredEmail(item.copy(statusRaw = status))
                }
            } finally {
                _isChecking.value = false
            }
        }
    }

    /** iOS `addEmail` (BreachMonitorView.swift:262): slot limit, shape check, dedupe, register, check. */
    suspend fun addEmail(raw: String): AddEmailOutcome {
        val current = emails.value
        if (current.size >= BREACH_MAX_SLOTS) return AddEmailOutcome.LimitReached
        val trimmed = raw.trim().lowercase()
        if (!trimmed.contains("@") || !trimmed.contains(".")) return AddEmailOutcome.Invalid
        if (current.any { it.email.lowercase() == trimmed }) return AddEmailOutcome.Duplicate
        _isChecking.value = true
        return try {
            breach.registerMonitor(trimmed)
            val result = breach.checkEmail(trimmed)
            _scanResults.value = _scanResults.value.filterNot { it.email == trimmed } + result
            dao.upsertMonitoredEmail(
                MonitoredEmailEntity(
                    id = UUID.randomUUID().toString(),
                    email = trimmed,
                    statusRaw = if (result.breached) STATUS_AT_RISK else STATUS_SECURE,
                    createdAt = System.currentTimeMillis(),
                ),
            )
            _errorMessage.value = null
            AddEmailOutcome.Added
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _errorMessage.value = e.message
            AddEmailOutcome.Failed
        } finally {
            _isChecking.value = false
        }
    }

    fun remove(entity: MonitoredEmailEntity) {
        viewModelScope.launch {
            dao.deleteMonitoredEmail(entity.id)
            // iOS keeps the stale card until the next rescan; drop it now so a removed email
            // never shows a breach the user can no longer act on.
            _scanResults.value = _scanResults.value.filterNot { it.email.equals(entity.email, ignoreCase = true) }
            breach.unregisterMonitor(entity.email)
        }
    }

    /** Rotate sheet: vault Logins matching the card's email ([matchRotateCandidates]). */
    suspend fun rotateCandidates(email: String): List<RotateCandidate> {
        val logins = vault.getAllRows()
            .filter { it.kind == VaultItemKind.Login }
            .mapNotNull { row -> vault.getPayload(row.id)?.let { row.id to it } }
        return matchRotateCandidates(email, logins)
    }

    /**
     * Rotate sheet, after the user confirmed: replaces ONLY the Login's password and saves through
     * the normal repository path (same id, every other field kept). The breach card state is not
     * touched — it follows the latest scan. Returns null on success, else the error message.
     */
    suspend fun rotatePassword(candidate: RotateCandidate, newPassword: String, savedMessage: String): String? =
        try {
            val login = vault.getPayload(candidate.id) as? VaultPayload.Login
                ?: error("Login not found")
            vault.save(candidate.id, login.copy(password = newPassword))
            _statusMessage.value = savedMessage
            null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            e.message ?: e.toString()
        }
}
