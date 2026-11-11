package alibarznji.qasa.screens.breach

import alibarznji.qasa.R
import alibarznji.qasa.breach.BreachCheckResult
import alibarznji.qasa.theme.AppMenuHeader
import alibarznji.qasa.theme.FilterChipButton
import alibarznji.qasa.theme.PremiumGate
import alibarznji.qasa.theme.PrimaryButton
import alibarznji.qasa.theme.QasaConfirmDialog
import alibarznji.qasa.theme.QasaDimens
import alibarznji.qasa.theme.ScreenScaffold
import alibarznji.qasa.theme.SectionLabel
import alibarznji.qasa.theme.StatusPill
import alibarznji.qasa.theme.QasaFieldGroup
import alibarznji.qasa.theme.QasaTextRow
import alibarznji.qasa.theme.qasaColors
import alibarznji.qasa.theme.qasaText
import alibarznji.qasa.vault.db.MonitoredEmailEntity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.launch

/**
 * Port of `I/Views/BreachMonitorView.swift`: free tier gets the shared [PremiumGate]; Premium gets
 * the slots header, add card, monitored list, Unresolved/Resolved chips and [BreachCard] results,
 * with a rescan of every monitored email whenever the screen appears (iOS `.onAppear { rescanAll }`).
 */
@Composable
fun BreachMonitorScreen(
    onOpenMenu: (() -> Unit)? = null,
    onUpgrade: () -> Unit,
    vm: BreachViewModel = hiltViewModel(),
) {
    val c = qasaColors
    val isPremium by vm.appSession.isPremium.collectAsState()
    val emails by vm.emails.collectAsState()
    val scanResults by vm.scanResults.collectAsState()
    val isChecking by vm.isChecking.collectAsState()
    val errorMessage by vm.errorMessage.collectAsState()
    val statusMessage by vm.statusMessage.collectAsState()
    var email by rememberSaveable { mutableStateOf("") }
    var selectedTab by rememberSaveable { mutableStateOf(BreachStatus.Unresolved) }
    var showLimitAlert by remember { mutableStateOf(false) }
    var pendingRemove by remember { mutableStateOf<MonitoredEmailEntity?>(null) }
    /** Email whose Rotate Password sheet is open (rotate_spec). */
    var rotateEmail by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    // iOS rescans on every appear; keyed on premium so an upgrade mid-session scans straight away.
    LaunchedEffect(isPremium) { vm.rescanAll() }

    ScreenScaffold(
        header = { AppMenuHeader(title = stringResource(R.string.nav_breachmonitoring), onMenu = onOpenMenu, applyStatusBars = false) },
    ) {
        if (!isPremium) {
            PremiumGate(hint = stringResource(R.string.breach_upgradehint), onUpgrade = onUpgrade)
            return@ScreenScaffold
        }

        Column(verticalArrangement = Arrangement.spacedBy(QasaDimens.sectionGap)) {
            SlotsHeader(used = emails.size)
            AddEmailCard(
                email = email,
                onEmailChange = { email = it },
                isChecking = isChecking,
                onAdd = {
                    scope.launch {
                        when (vm.addEmail(email)) {
                            AddEmailOutcome.LimitReached -> showLimitAlert = true
                            AddEmailOutcome.Added, AddEmailOutcome.Duplicate -> email = ""
                            AddEmailOutcome.Invalid, AddEmailOutcome.Failed -> Unit
                        }
                    }
                },
            )
            MonitoredList(emails = emails, onRemove = { pendingRemove = it })
            BreachTabs(selected = selectedTab, onSelect = { selectedTab = it })
            BreachList(results = scanResults, selected = selectedTab, onRotate = { rotateEmail = it })
            statusMessage?.let { Text(it, color = c.success, style = qasaText.bodySmall) }
            errorMessage?.let { Text(it, color = c.danger, style = qasaText.bodySmall) }
        }
    }

    rotateEmail?.let { target ->
        RotatePasswordSheet(
            email = target,
            loadCandidates = vm::rotateCandidates,
            onSave = vm::rotatePassword,
            onDismiss = { rotateEmail = null },
        )
    }

    pendingRemove?.let { e ->
        QasaConfirmDialog(
            title = stringResource(R.string.breach_removeemailtitle),
            message = stringResource(R.string.breach_removeemailmessage, e.email),
            confirmLabel = stringResource(R.string.common_remove),
            onConfirm = {
                pendingRemove = null
                vm.remove(e)
            },
            onDismiss = { pendingRemove = null },
        )
    }

    if (showLimitAlert) {
        QasaConfirmDialog(
            title = stringResource(R.string.breach_slotlimittitle),
            message = stringResource(R.string.breach_slotlimitmessage, BREACH_MAX_SLOTS),
            confirmLabel = stringResource(R.string.common_ok),
            dismissLabel = null,
            destructive = false,
            onConfirm = { showLimitAlert = false },
            onDismiss = { showLimitAlert = false },
        )
    }
}

/** iOS `slotsHeader`: "Email Monitoring" 22 bold, slots line, success-dot "Premium" pill. */
@Composable
private fun SlotsHeader(used: Int) {
    val c = qasaColors
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.breach_emailmonitoring), color = c.textPrimary, style = qasaText.sectionHeadline)
            Spacer(Modifier.height(QasaDimens.spaceSmall))
            Text(
                stringResource(R.string.breach_slotsused, used, BREACH_MAX_SLOTS),
                color = c.textSecondary,
                style = qasaText.bodyMedium,
            )
        }
        StatusPill(
            text = stringResource(R.string.breach_premium),
            dotColor = c.success,
            textColor = c.success,
            background = c.cardBackground,
        )
    }
}

/** iOS `addEmailCard`: a one-row "Add Email" group, then "+ Add to Monitoring" / "Saving…". */
@Composable
private fun AddEmailCard(
    email: String,
    onEmailChange: (String) -> Unit,
    isChecking: Boolean,
    onAdd: () -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        QasaFieldGroup {
            QasaTextRow(
                label = stringResource(R.string.breach_addemail),
                value = email,
                onValueChange = onEmailChange,
                placeholder = stringResource(R.string.field_emailplaceholder),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, autoCorrectEnabled = false),
            )
        }
        Spacer(Modifier.height(QasaDimens.formGap))
        PrimaryButton(
            title = stringResource(if (isChecking) R.string.breach_saving else R.string.breach_addtomonitoring),
            icon = Icons.Default.Add,
            // Android keeps a blank field disabled (iOS no-ops the tap); the slot-limit alert stays
            // reachable on Android, whereas iOS disables the button at the limit.
            enabled = email.isNotBlank() && !isChecking,
            onClick = onAdd,
        )
    }
}

/** iOS `breachTabs`: "Breaches" label + Unresolved / Resolved chips. */
@Composable
private fun BreachTabs(selected: BreachStatus, onSelect: (BreachStatus) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(QasaDimens.gapSmall)) {
        SectionLabel(stringResource(R.string.breach_breaches))
        Row(horizontalArrangement = Arrangement.spacedBy(QasaDimens.contentGap)) {
            FilterChipButton(
                title = stringResource(R.string.breach_unresolved),
                selected = selected == BreachStatus.Unresolved,
                onClick = { onSelect(BreachStatus.Unresolved) },
            )
            FilterChipButton(
                title = stringResource(R.string.breach_resolved),
                selected = selected == BreachStatus.Resolved,
                onClick = { onSelect(BreachStatus.Resolved) },
            )
        }
    }
}

/**
 * iOS `breachList`: one card per scanned email. The prefix API only returns source names, so —
 * exactly like iOS — the date is "—", discovery is "Recent scan" and the only field is "Emails".
 */
@Composable
private fun BreachList(results: List<BreachCheckResult>, selected: BreachStatus, onRotate: (String) -> Unit) {
    val c = qasaColors
    val filtered = results.filter { if (selected == BreachStatus.Unresolved) it.breached else !it.breached }
    Column(verticalArrangement = Arrangement.spacedBy(QasaDimens.gapSmall)) {
        if (filtered.isEmpty()) {
            Text(
                stringResource(if (selected == BreachStatus.Unresolved) R.string.breach_nounresolved else R.string.breach_noresolved),
                color = c.textSecondary,
                style = qasaText.bodyMedium,
            )
        } else {
            val detected = stringResource(R.string.breach_detected)
            val recentScan = stringResource(R.string.breach_recentscan)
            val emailsField = CompromisedField(stringResource(R.string.breach_emailsfield), EmailsFieldIcon)
            filtered.forEach { result ->
                BreachCard(
                    Breach(
                        title = result.sources.firstOrNull() ?: detected,
                        breachDate = "—",
                        discoveredDate = recentScan,
                        compromisedFields = listOf(emailsField),
                        status = if (result.breached) BreachStatus.Unresolved else BreachStatus.Resolved,
                    ),
                    onRotatePassword = { onRotate(result.email) },
                )
            }
        }
    }
}

/** iOS `monitoredList` + `monitoredRow`. */
@Composable
private fun MonitoredList(emails: List<MonitoredEmailEntity>, onRemove: (MonitoredEmailEntity) -> Unit) {
    val c = qasaColors
    Column(verticalArrangement = Arrangement.spacedBy(QasaDimens.gapSmall)) {
        SectionLabel(stringResource(R.string.breach_monitoredemails))
        if (emails.isEmpty()) {
            Text(
                stringResource(R.string.breach_nonemonitored),
                color = c.textSecondary,
                style = qasaText.bodyMedium,
                modifier = Modifier.padding(vertical = QasaDimens.contentGap),
            )
        } else {
            emails.forEach { MonitoredEmailRow(it, onRemove = { onRemove(it) }) }
        }
    }
}
