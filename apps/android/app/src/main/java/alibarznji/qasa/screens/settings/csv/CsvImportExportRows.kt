package alibarznji.qasa.screens.settings.csv

import alibarznji.qasa.R
import alibarznji.qasa.theme.NavChevronRow
import alibarznji.qasa.theme.qasaColors
import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** iOS `CSVDocument.spillURL` / `SharePreview` filename. */
private const val EXPORT_FILE_NAME = "qasa-vault-backup.csv"

// iOS `fileImporter(allowedContentTypes: [.commaSeparatedText, .plainText])`, plus "any"
// because many Android file providers label .csv files as application/octet-stream.
private val IMPORT_MIME_TYPES = arrayOf("text/csv", "text/comma-separated-values", "text/plain", "*/*")

/**
 * The "Import CSV" and "Export Vault CSV" rows of Settings → Data & Account (iOS
 * `SettingsView` import/export). Renders both rows with a divider between them; the
 * caller places them inside its card. Results go to [onStatus] (iOS `statusMessage`).
 *
 * Export goes to a file the user picks (SAF `CreateDocument`), never the clipboard: as on
 * iOS (`CSVDocument.swift`), a plaintext vault on the clipboard is readable by other apps.
 */
@Composable
fun CsvImportExportRows(onStatus: (String) -> Unit) {
    val vm: CsvViewModel = hiltViewModel()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val c = qasaColors

    fun report(status: CsvStatus) = onStatus(status.message(context))

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            val text = try {
                withContext(Dispatchers.IO) { readUtf8(context, uri) }
            } catch (_: CharacterCodingException) {
                report(CsvStatus.ImportNotUtf8)
                return@launch
            } catch (e: Exception) {
                report(CsvStatus.ImportFailed(e.message ?: e.javaClass.simpleName))
                return@launch
            }
            report(withContext(Dispatchers.Default) { vm.import(text) })
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv"),
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult // user cancelled the picker
        scope.launch {
            val status = withContext(Dispatchers.IO) {
                vm.export { csv ->
                    val out = context.contentResolver.openOutputStream(uri, "wt")
                        ?: error("Cannot open $uri")
                    out.use { it.write(csv.toByteArray(Charsets.UTF_8)) }
                }
            }
            report(status)
        }
    }

    NavChevronRow(
        title = stringResource(R.string.settings_importcsv),
        icon = Icons.Outlined.FileDownload,
        onClick = { importLauncher.launch(IMPORT_MIME_TYPES) },
    )
    HorizontalDivider(color = c.stroke)
    NavChevronRow(
        title = stringResource(R.string.settings_exportcsv),
        icon = Icons.Outlined.FileUpload,
        onClick = {
            scope.launch {
                // iOS checks for an empty vault before offering the file.
                if (withContext(Dispatchers.IO) { vm.hasExportable() }) {
                    exportLauncher.launch(EXPORT_FILE_NAME)
                } else {
                    report(CsvStatus.ExportEmpty)
                }
            }
        },
    )
}

/** iOS rejects non-UTF-8 files (`String(data:encoding: .utf8)` returns nil); so do we. */
private fun readUtf8(context: Context, uri: Uri): String {
    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        ?: error("Cannot open $uri")
    return Charsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT)
        .decode(ByteBuffer.wrap(bytes))
        .toString()
}

/** The iOS `statusMessage` strings (SettingsView.swift:376-450). */
private fun CsvStatus.message(ctx: Context): String = when (this) {
    CsvStatus.ExportEmpty -> ctx.getString(R.string.settings_exportempty)
    is CsvStatus.Exported -> ctx.getString(R.string.settings_exportprepared, count)
    is CsvStatus.ExportPartial -> ctx.getString(R.string.settings_exportpartial, count, failures)
    is CsvStatus.ExportFailed -> ctx.getString(R.string.settings_exportfailed, reason)
    CsvStatus.ImportNothing -> ctx.getString(R.string.settings_importnothing)
    CsvStatus.ImportEmptyFile ->
        ctx.getString(R.string.settings_importfailed, ctx.getString(R.string.settings_importnothing))
    // Wrapped in importFailed exactly like iOS (`VaultCSVError` → `settings.importFailed`).
    is CsvStatus.ImportBadHeader ->
        ctx.getString(R.string.settings_importfailed, ctx.getString(R.string.settings_importbadheader, missing))
    is CsvStatus.Imported -> ctx.getString(R.string.settings_parsedcsv, count)
    is CsvStatus.ImportSkipped -> ctx.getString(R.string.settings_importskipped, added, skipped)
    is CsvStatus.ImportFailed -> ctx.getString(R.string.settings_importfailed, reason)
    CsvStatus.ImportNotUtf8 ->
        ctx.getString(R.string.settings_importfailed, ctx.getString(R.string.error_decoding))
}
