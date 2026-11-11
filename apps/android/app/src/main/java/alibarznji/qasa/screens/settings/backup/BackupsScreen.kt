package alibarznji.qasa.screens.settings.backup

import alibarznji.qasa.backup.BackupPlace
import alibarznji.qasa.theme.SecondaryButton
import alibarznji.qasa.R
import alibarznji.qasa.backup.BackupFrequency
import alibarznji.qasa.backup.BackupRunResult
import alibarznji.qasa.backup.BackupService
import alibarznji.qasa.theme.AppMenuHeader
import alibarznji.qasa.theme.CardContainer
import alibarznji.qasa.theme.NavChevronRow
import alibarznji.qasa.theme.PrimaryButton
import alibarznji.qasa.theme.QasaDimens
import alibarznji.qasa.theme.QasaPressable
import alibarznji.qasa.theme.ScreenScaffold
import alibarznji.qasa.theme.SectionLabel
import alibarznji.qasa.theme.QasaSheetHeader
import alibarznji.qasa.theme.StickyBottomBar
import alibarznji.qasa.theme.qasaColors
import alibarznji.qasa.theme.qasaText
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.launch

/** Medium date, short time in the app's language (as the notification and item dates). */
internal fun formatBackupDate(millis: Long, locale: Locale): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, locale).format(Date(millis))

@Composable
internal fun backupLocale(): Locale = LocalConfiguration.current.locales[0] ?: Locale.getDefault()

@HiltViewModel
class BackupsViewModel @Inject constructor(
    val backups: BackupService,
) : ViewModel() {
    /** The last Back up now result line, a string resource. */
    @get:StringRes
    var status by mutableStateOf<Int?>(null)
        private set
    var statusIsError by mutableStateOf(false)
        private set

    /** Back up now was tapped with no location: the picker is open for it. */
    var pendingBackup = false

    fun backUpNow() {
        viewModelScope.launch { report(backups.backUpNow()) }
    }

    /** A new location: kept, then written right away so the picked file isn't left empty. */
    fun locationPicked(uri: Uri) {
        pendingBackup = false
        viewModelScope.launch {
            backups.setLocation(uri)
            report(backups.backUpNow())
        }
    }

    fun locationCancelled() {
        if (pendingBackup) {
            pendingBackup = false
            status = R.string.backup_chooselocationfirst
            statusIsError = true
        }
    }

    fun setFrequency(frequency: BackupFrequency) {
        backups.setFrequency(frequency)
        // Turning automatic backups on with an old (or no) backup runs one now.
        backups.runIfDue()
    }

    private fun report(result: BackupRunResult) {
        when (result) {
            BackupRunResult.Done -> {
                status = R.string.backup_done
                statusIsError = false
            }
            BackupRunResult.NoLocation -> {
                status = R.string.backup_chooselocationfirst
                statusIsError = true
            }
            BackupRunResult.Failed, BackupRunResult.Locked -> {
                status = R.string.backup_failed
                statusIsError = true
            }
            // An automatic backup is already writing; its result shows in the last-backup line.
            BackupRunResult.Busy -> Unit
        }
    }
}

private val FrequencyOptions = listOf(
    BackupFrequency.Off to R.string.backup_freq_off,
    BackupFrequency.Daily to R.string.backup_freq_daily,
    BackupFrequency.Weekly to R.string.backup_freq_weekly,
    BackupFrequency.Monthly to R.string.backup_freq_monthly,
)

/**
 * Settings → Backups: back up now, the last backup, where it is kept, automatic backups
 * and restore. Free on every plan. [inSheet] is the Settings sheet (Close); otherwise it
 * is a page opened from the Home reminder (back arrow).
 */
@Composable
fun BackupsScreen(
    onDone: () -> Unit,
    inSheet: Boolean = true,
    vm: BackupsViewModel = hiltViewModel(),
    restore: RestoreBackupViewModel = hiltViewModel(),
) {
    val c = qasaColors
    val locale = backupLocale()
    val settings by vm.backups.settings.collectAsState()
    val running by vm.backups.running.collectAsState()
    var restoring by rememberSaveable { mutableStateOf(false) }

    // SAF "create document": any provider (Drive included) can hold the file, and the
    // grant is persisted so every later backup overwrites the same document.
    val locationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream"),
    ) { uri ->
        if (uri == null) vm.locationCancelled() else vm.locationPicked(uri)
    }
    // Any file type: providers label `.qasabackup` files inconsistently; the core checks it.
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        restore.open(uri)
        restoring = true
    }
    val pickLocation = { locationLauncher.launch(vm.backups.suggestedFileName()) }
    val pickRestoreFile = { restoreLauncher.launch(arrayOf("*/*")) }

    if (restoring) {
        val close = {
            if (!restore.busy) {
                restore.reset()
                restoring = false
            }
        }
        BackHandler(onBack = close)
        RestoreBackupPage(
            vm = restore,
            setup = false,
            header = {
                // Pushed inside the Backups sheet: a back circle returns to Backups.
                QasaSheetHeader(stringResource(R.string.backup_restore_title), onBack = close)
            },
            onPickFile = pickRestoreFile,
            onClose = close,
        )
        return
    }

    ScreenScaffold(
        header = {
            // In a sheet this draws the shared sheet header (no Close).
            AppMenuHeader(title = stringResource(R.string.backup_title), onMenu = onDone, applyStatusBars = false)
        },
        stickyBottom = {
            StickyBottomBar {
                PrimaryButton(
                    title = stringResource(if (running) R.string.backup_backingup else R.string.backup_backupnow),
                    icon = if (running) null else Icons.Filled.Backup,
                    enabled = !running,
                    onClick = {
                        if (settings.locationUri == null) {
                            vm.pendingBackup = true
                            pickLocation()
                        } else {
                            vm.backUpNow()
                        }
                    },
                )
            }
        },
    ) {
        Text(stringResource(R.string.backup_intro), color = c.textSecondary, style = qasaText.bodyMedium)
        Spacer(Modifier.height(QasaDimens.contentGap))
        Text(
            settings.lastBackupMs?.let { stringResource(R.string.backup_lastbackupfmt, formatBackupDate(it, locale)) }
                ?: stringResource(R.string.backup_never),
            color = c.textSecondary,
            style = qasaText.bodySmall,
        )
        // The flag covers automatic runs too; a fresh manual result is shown instead.
        val status = vm.status
        if (status != null && !running) {
            Text(stringResource(status), color = if (vm.statusIsError) c.danger else c.success, style = qasaText.bodySmallMedium)
        } else if (settings.failed) {
            Text(stringResource(R.string.backup_failed), color = c.danger, style = qasaText.bodySmallMedium)
        }

        Spacer(Modifier.height(QasaDimens.sectionGap))
        SectionLabel(stringResource(R.string.backup_locationtitle))
        Spacer(Modifier.height(QasaDimens.contentGap))
        CardContainer {
            NavChevronRow(
                icon = Icons.Outlined.FolderOpen,
                title = settings.locationName ?: stringResource(R.string.backup_locationnone),
                subtitle = stringResource(
                    if (settings.locationUri == null) R.string.backup_chooselocation else R.string.backup_changelocation,
                ),
                onClick = { if (!running) pickLocation() },
            )
        }
        // Where the file is: Qasa never uploads it, so say whether it left the phone.
        val place = settings.locationUri?.let { BackupPlace.of(android.net.Uri.parse(it).authority) }
        when (place) {
            is BackupPlace.Cloud -> {
                Spacer(Modifier.height(QasaDimens.contentGap))
                Text(stringResource(R.string.backup_where_cloudfmt, place.name), color = c.success, style = qasaText.bodySmallMedium)
            }
            BackupPlace.ThisPhone -> {
                Spacer(Modifier.height(QasaDimens.contentGap))
                CardContainer(background = c.cardBackgroundLight) {
                    Text(stringResource(R.string.backup_where_device), color = c.textPrimary, style = qasaText.rowTitle)
                    Spacer(Modifier.height(QasaDimens.spaceSmall))
                    Text(stringResource(R.string.backup_where_devicebody), color = c.textSecondary, style = qasaText.bodySmall)
                    Spacer(Modifier.height(QasaDimens.contentGap))
                    SecondaryButton(
                        title = stringResource(R.string.backup_where_pick),
                        onClick = { if (!running) pickLocation() },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            else -> Unit
        }
        Spacer(Modifier.height(QasaDimens.contentGap))
        Text(stringResource(R.string.backup_locationhelp), color = c.textSecondary, style = qasaText.bodySmall)

        Spacer(Modifier.height(QasaDimens.sectionGap))
        SectionLabel(stringResource(R.string.backup_auto))
        Spacer(Modifier.height(QasaDimens.contentGap))
        CardContainer {
            FrequencyOptions.forEachIndexed { index, (frequency, label) ->
                if (index > 0) HorizontalDivider(color = c.stroke, thickness = QasaDimens.stroke)
                QasaPressable(onClick = { vm.setFrequency(frequency) }, modifier = Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = QasaDimens.gapSmall),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            stringResource(label),
                            color = c.textPrimary,
                            style = qasaText.bodyLarge,
                            modifier = Modifier.weight(1f),
                        )
                        if (settings.frequency == frequency) Icon(Icons.Filled.Check, null, tint = c.accent)
                    }
                }
            }
        }
        Spacer(Modifier.height(QasaDimens.contentGap))
        Text(stringResource(R.string.backup_autohelp), color = c.textSecondary, style = qasaText.bodySmall)

        Spacer(Modifier.height(QasaDimens.sectionGap))
        CardContainer {
            NavChevronRow(
                icon = Icons.Outlined.Restore,
                title = stringResource(R.string.backup_restore),
                subtitle = stringResource(R.string.backup_restoresubtitle),
                onClick = pickRestoreFile,
            )
        }
        Spacer(Modifier.height(QasaDimens.contentGap))
        Text(stringResource(R.string.backup_restorehelp), color = c.textSecondary, style = qasaText.bodySmall)
    }
}

/** First-run "Restore from a backup" option, under "I already have Qasa on another device". */
@Composable
fun RestoreFromBackupOption(onClick: () -> Unit) {
    val c = qasaColors
    // Same TextButton as the Link Device option above it, with the subtitle underneath.
    TextButton(onClick = onClick) {
        Column {
            Text(stringResource(R.string.backup_restore), color = c.accent, fontWeight = FontWeight.SemiBold)
            Text(stringResource(R.string.backup_restoresubtitle), color = c.textSecondary, style = qasaText.labelRegular)
        }
    }
}
