package alibarznji.qasa.screens.tools.health

import alibarznji.qasa.screens.PasswordHealthAnalyzer
import alibarznji.qasa.screens.PasswordHealthReport
import alibarznji.qasa.vault.EphemeralClipboard
import alibarznji.qasa.vault.VaultPayload
import alibarznji.qasa.vault.VaultRepository
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@HiltViewModel
class HealthViewModel @Inject constructor(
    val repo: VaultRepository,
    val clipboard: EphemeralClipboard,
) : ViewModel() {
    /**
     * iOS `refreshReport()` (`PasswordHealthView.swift:81`) runs on appear; here it re-runs
     * on every row change, so editing or deleting an item from the issues screen updates
     * the gauge and the lists when the user comes back. `null` until the first pass, so
     * the gauge does not count up to the empty report's 100 first.
     */
    val report: StateFlow<PasswordHealthReport?> = repo.observeRows()
        .map { rows ->
            val logins = rows.mapNotNull { row ->
                val p = runCatching { repo.getPayload(row.id) }.getOrNull() as? VaultPayload.Login
                    ?: return@mapNotNull null
                // iOS trims and skips blank passwords, so empty logins are neither weak nor reused.
                val trimmed = p.password.trim()
                if (trimmed.isEmpty()) null else row to trimmed
            }
            PasswordHealthAnalyzer.analyze(logins)
        }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    suspend fun loadPayload(id: String) = withContext(Dispatchers.Default) { repo.getPayload(id) }

    /** iOS stamps `lastUsedAt` after every row copy (`VaultItemRow.swift:139`). */
    fun markUsed(id: String) {
        viewModelScope.launch { runCatching { repo.markUsed(id) } }
    }

    fun delete(id: String) {
        viewModelScope.launch { runCatching { repo.delete(id) } }
    }
}
