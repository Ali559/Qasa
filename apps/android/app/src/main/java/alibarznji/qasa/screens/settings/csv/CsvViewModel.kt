package alibarznji.qasa.screens.settings.csv

import alibarznji.qasa.vault.VaultPayload
import alibarznji.qasa.vault.VaultRepository
import alibarznji.qasa.vault.csv.VaultCsv
import alibarznji.qasa.vault.csv.VaultCsvException
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/** Outcome of an import/export, resolved to the iOS `statusMessage` strings by the UI. */
sealed interface CsvStatus {
    data object ExportEmpty : CsvStatus
    data class Exported(val count: Int) : CsvStatus
    data class ExportPartial(val count: Int, val failures: Int) : CsvStatus
    data class ExportFailed(val reason: String) : CsvStatus
    data object ImportNothing : CsvStatus
    data class ImportBadHeader(val missing: String) : CsvStatus
    data class Imported(val count: Int) : CsvStatus
    data class ImportSkipped(val added: Int, val skipped: Int) : CsvStatus
    data class ImportFailed(val reason: String) : CsvStatus
    data object ImportNotUtf8 : CsvStatus

    /** iOS shows the thrown `VaultCSVError.empty` through `importFailed`. */
    data object ImportEmptyFile : CsvStatus
}

/** Logic of iOS `SettingsView.performExport` / `performImport` (SettingsView.swift:376-450). */
@HiltViewModel
class CsvViewModel @Inject constructor(
    private val repo: VaultRepository,
) : ViewModel() {

    /**
     * iOS `VaultRepository.allPayloads()`: every payload that decrypts, plus how many did
     * not. Rows whose envelope fails to decrypt are already dropped by `getAllRows`.
     */
    suspend fun allPayloads(): Pair<List<VaultPayload>, Int> {
        var failures = 0
        val payloads = repo.getAllRows().mapNotNull { row ->
            try {
                repo.getPayload(row.id)
            } catch (_: Exception) {
                failures++
                null
            }
        }
        return payloads to failures
    }

    /** True when there is something to export (the iOS `exportEmpty` guard). */
    suspend fun hasExportable(): Boolean = allPayloads().first.isNotEmpty()

    /**
     * Builds the CSV and hands it to [write] (the SAF stream). Re-decrypts here rather than
     * holding the plaintext while the system file picker is open.
     */
    suspend fun export(write: (String) -> Unit): CsvStatus {
        val (payloads, failures) = allPayloads()
        if (payloads.isEmpty()) return CsvStatus.ExportEmpty
        return try {
            write(VaultCsv.exportCsv(payloads))
            // Do not imply a complete backup when some rows could not be decrypted.
            if (failures > 0) CsvStatus.ExportPartial(payloads.size, failures) else CsvStatus.Exported(payloads.size)
        } catch (e: Exception) {
            CsvStatus.ExportFailed(e.message ?: e.javaClass.simpleName)
        }
    }

    suspend fun import(csv: String): CsvStatus {
        val candidates = try {
            VaultCsv.parseImport(csv)
        } catch (e: VaultCsvException.InvalidHeader) {
            return CsvStatus.ImportBadHeader(e.missing)
        } catch (_: VaultCsvException.Empty) {
            return CsvStatus.ImportEmptyFile
        }
        if (candidates.isEmpty()) return CsvStatus.ImportNothing

        // Skip anything already present so a repeated import is a no-op.
        val existingPrints = allPayloads().first.map(VaultCsv::fingerprint).toSet()
        var added = 0
        var skipped = 0
        var firstError: Exception? = null
        for (payload in candidates) {
            if (VaultCsv.fingerprint(payload) in existingPrints) {
                skipped++
                continue
            }
            try {
                repo.save(null, payload)
                added++
            } catch (e: Exception) {
                if (firstError == null) firstError = e
            }
        }
        return when {
            firstError != null && added == 0 ->
                CsvStatus.ImportFailed(firstError.message ?: firstError.javaClass.simpleName)
            skipped > 0 -> CsvStatus.ImportSkipped(added, skipped)
            else -> CsvStatus.Imported(added)
        }
    }
}
