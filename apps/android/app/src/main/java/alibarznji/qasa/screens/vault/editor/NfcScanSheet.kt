package alibarznji.qasa.screens.vault.editor

import alibarznji.qasa.R
import alibarznji.qasa.nfc.EmvCard
import alibarznji.qasa.nfc.Mrz
import alibarznji.qasa.nfc.MrzData
import alibarznji.qasa.nfc.NfcAvailability
import alibarznji.qasa.nfc.NfcFailure
import alibarznji.qasa.nfc.NfcReadException
import alibarznji.qasa.nfc.NfcTagReader
import alibarznji.qasa.nfc.findActivity
import alibarznji.qasa.nfc.nfcAvailability
import alibarznji.qasa.screens.vault.VaultDates
import alibarznji.qasa.theme.QasaDimens
import alibarznji.qasa.theme.QasaModalBottomSheet
import alibarznji.qasa.theme.SectionLabel
import alibarznji.qasa.theme.ThemedTextField
import alibarznji.qasa.theme.ValidatedDateField
import alibarznji.qasa.theme.qasaColors
import alibarznji.qasa.theme.qasaSheetMaxHeight
import alibarznji.qasa.theme.qasaText
import alibarznji.qasa.theme.rememberSheetDismiss
import android.content.Intent
import android.nfc.Tag
import android.os.Build
import android.provider.Settings
import android.view.HapticFeedbackConstants
import androidx.annotation.StringRes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/** What a successful scan hands back to the editor. */
sealed interface NfcScanResult {
    data class Card(val card: EmvCard) : NfcScanResult
    data class Document(val mrz: MrzData) : NfcScanResult
}

/** Where the scan stands; one line of copy each. */
private sealed interface ScanStatus {
    /** ID / passport: waiting for the details that unlock the chip. */
    data object NeedsDetails : ScanStatus
    data object Ready : ScanStatus
    data object Reading : ScanStatus
    data object Done : ScanStatus
    data class Failed(val failure: NfcFailure) : ScanStatus
}

/**
 * "Scan with NFC": a sheet that reads a payment card (number, expiry) or the chip of an ID
 * card / passport (name, number, country, expiry) and hands it to the editor. The layout
 * follows the approved preview (`nfc-preview.html`): title and subtitle, the animated
 * picture ([NfcScanArt]), three numbered steps (for ID / passport the chip key inputs sit
 * right under them), a live status line and Cancel.
 *
 * Reader mode is on for as long as this sheet is in composition and NFC is on, so a card
 * on the phone never reaches the system dispatcher; tags are only read while the scan is
 * armed. Leaving composition always turns it off. Tags are read on the reader's binder
 * thread; results come back to the main thread through the composition's scope.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NfcScanSheet(
    document: NfcDocument,
    initialNumber: String,
    initialExpiry: String,
    onDismiss: () -> Unit,
    onResult: (NfcScanResult) -> Unit,
) {
    QasaModalBottomSheet(onDismissRequest = onDismiss) {
        NfcScanContent(document, initialNumber, initialExpiry, onDismiss, onResult)
    }
}

@Composable
private fun NfcScanContent(
    document: NfcDocument,
    initialNumber: String,
    initialExpiry: String,
    onDismiss: () -> Unit,
    onResult: (NfcScanResult) -> Unit,
) {
    val c = qasaColors
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val view = LocalView.current
    val focus = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    val dismiss = rememberSheetDismiss(onDismiss)
    val result by rememberUpdatedState(onResult)
    val isCard = document == NfcDocument.Card

    var availability by remember { mutableStateOf(nfcAvailability(context)) }
    // NFC can be switched on in Settings (our button) or the quick settings tile: re-check.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) availability = nfcAvailability(context)
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    var docNumber by rememberSaveable { mutableStateOf(Mrz.normalizeDocumentNumber(initialNumber)) }
    var birthDate by rememberSaveable { mutableStateOf("") }
    var expiry by rememberSaveable { mutableStateOf(initialExpiry) }
    val mrzInfo = remember(docNumber, birthDate, expiry) {
        val dob = VaultDates.parse(birthDate)
        val exp = VaultDates.parse(expiry)
        if (docNumber.isNotEmpty() && dob != null && exp != null) Mrz.bacKeyInfo(docNumber, dob, exp) else null
    }

    var status by remember { mutableStateOf(if (isCard) ScanStatus.Ready else ScanStatus.NeedsDetails) }
    // Bumped by "Try again": re-enabling reader mode makes a card still in the field show up again.
    var session by remember { mutableIntStateOf(0) }
    val busy = remember { AtomicBoolean(false) }

    // What a tag arriving now should be read with: null swallows it (details missing, done).
    // After a dropped link the next tag reads again, so lifting the card and putting it
    // back is enough. Written on the main thread, read on the reader's binder thread.
    val armedKey = if (isCard) "card" else mrzInfo
    val armed = status == ScanStatus.Ready || status == ScanStatus.Reading ||
        (status as? ScanStatus.Failed)?.failure == NfcFailure.Lost
    val readKey = remember { AtomicReference<String?>(null) }
    SideEffect { readKey.set(if (armed) armedKey else null) }

    val readerOn = activity != null && availability == NfcAvailability.Ready
    DisposableEffect(readerOn, session, lifecycle) {
        val host = activity
        if (!readerOn || host == null) return@DisposableEffect onDispose { }
        val onTag: (Tag) -> Unit = onTag@{ tag ->
            val key = readKey.get() ?: return@onTag
            if (!busy.compareAndSet(false, true)) return@onTag
            scope.launch { status = ScanStatus.Reading }
            val outcome = runCatching {
                if (isCard) {
                    NfcScanResult.Card(NfcTagReader.readCard(tag))
                } else {
                    NfcScanResult.Document(NfcTagReader.readMrtd(tag, key))
                }
            }
            scope.launch {
                busy.set(false)
                outcome
                    .onSuccess { r ->
                        status = ScanStatus.Done
                        confirmHaptic(view)
                        result(r)
                        delay(SUCCESS_BEAT_MS)
                        dismiss()
                    }
                    .onFailure { e ->
                        status = ScanStatus.Failed((e as? NfcReadException)?.failure ?: NfcFailure.Unsupported)
                    }
            }
        }
        // Reader mode belongs to the resumed activity: apply it now and again after every
        // resume (back from Settings, the lock screen, a system dialog), and retry shortly
        // when the activity was not quite in the foreground yet.
        val retry = scope.launch {
            repeat(ENABLE_ATTEMPTS) {
                if (NfcTagReader.enable(host, onTag)) return@launch
                delay(ENABLE_RETRY_MS)
            }
        }
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) NfcTagReader.enable(host, onTag)
        }
        lifecycle.addObserver(observer)
        onDispose {
            retry.cancel()
            lifecycle.removeObserver(observer)
            NfcTagReader.disable(host)
        }
    }

    val transition = rememberInfiniteTransition(label = "nfc")
    val loop = rememberNfcLoop(transition)
    val blink = rememberNfcBlink(transition)

    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(max = qasaSheetMaxHeight())
            .imePadding(),
    ) {
        Column(
            Modifier
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = QasaDimens.screenPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                stringResource(titleRes(document)),
                color = c.textPrimary,
                style = qasaText.cardTitleBold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(QasaDimens.spaceSmall))
            Text(
                stringResource(if (isCard) R.string.nfc_card_subtitle else R.string.nfc_id_subtitle),
                color = c.textSecondary,
                style = qasaText.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(QasaDimens.gapTiny))
            NfcScanArt(
                document,
                loop,
                contentDescription = stringResource(titleRes(document)),
                modifier = Modifier.padding(vertical = QasaDimens.gapSmall),
            )
            Steps(document, current = currentStep(status))
            if (!isCard) {
                Spacer(Modifier.height(QasaDimens.gap))
                ChipKeyInputs(
                    docNumber = docNumber,
                    birthDate = birthDate,
                    expiry = expiry,
                    onDocNumber = { docNumber = Mrz.normalizeDocumentNumber(it).take(MAX_DOC_NUMBER); status = disarmed(status) },
                    onBirthDate = { birthDate = it; status = disarmed(status) },
                    onExpiry = { expiry = it; status = disarmed(status) },
                )
            }
            Spacer(Modifier.height(QasaDimens.gap))
            StatusLine(status, availability, blink)
            Spacer(Modifier.height(QasaDimens.gapSmall))
        }
        Actions(
            status = status,
            availability = availability,
            canStart = mrzInfo != null,
            onCancel = dismiss,
            onStart = {
                focus.clearFocus()
                status = ScanStatus.Ready
            },
            onTryAgain = {
                status = if (isCard || mrzInfo != null) ScanStatus.Ready else ScanStatus.NeedsDetails
                session++
            },
            onOpenSettings = {
                runCatching {
                    context.startActivity(Intent(Settings.ACTION_NFC_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            },
            focus = focus,
        )
    }
}

/** Editing the chip key while armed (or after a failure) goes back to "enter details". */
private fun disarmed(status: ScanStatus): ScanStatus =
    if (status == ScanStatus.Ready || status is ScanStatus.Failed) ScanStatus.NeedsDetails else status

private const val SUCCESS_BEAT_MS = 700L
private const val ENABLE_ATTEMPTS = 5
private const val ENABLE_RETRY_MS = 300L

/** Long enough for a TD1 number with its overflow (9303 Part 5 allows up to 22 + check). */
private const val MAX_DOC_NUMBER = 22

@StringRes
private fun titleRes(document: NfcDocument) = when (document) {
    NfcDocument.Card -> R.string.nfc_card_title
    NfcDocument.IdCard -> R.string.nfc_id_title
    NfcDocument.Passport -> R.string.nfc_passport_title
}

/** 0-based index of the highlighted step. */
private fun currentStep(status: ScanStatus) = when (status) {
    ScanStatus.NeedsDetails -> 0
    ScanStatus.Ready, is ScanStatus.Failed -> 1
    ScanStatus.Reading, ScanStatus.Done -> 2
}

private fun confirmHaptic(view: android.view.View) {
    val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.LONG_PRESS
    view.performHapticFeedback(type)
}

@Composable
private fun Steps(document: NfcDocument, current: Int) {
    val steps = when (document) {
        NfcDocument.Card -> listOf(R.string.nfc_card_step1, R.string.nfc_card_step2, R.string.nfc_card_step3)
        NfcDocument.IdCard -> listOf(R.string.nfc_id_step1, R.string.nfc_id_step2, R.string.nfc_id_step3)
        NfcDocument.Passport -> listOf(R.string.nfc_id_step1, R.string.nfc_passport_step2, R.string.nfc_id_step3)
    }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(QasaDimens.gapMedium)) {
        steps.forEachIndexed { i, res -> StepRow(number = i + 1, text = stringResource(res), now = i == current) }
    }
}

@Composable
private fun StepRow(number: Int, text: String, now: Boolean) {
    val c = qasaColors
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        // The disc grows with the font so a large text size never clips the numeral.
        Box(
            Modifier
                .heightIn(min = STEP_DISC)
                .width(STEP_DISC)
                .background(if (now) c.accent else c.cardMuted, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                number.toString(),
                color = if (now) c.onAccent else c.accent,
                style = qasaText.bodySmallSemibold,
            )
        }
        Spacer(Modifier.width(QasaDimens.gapSmall))
        Text(
            text,
            color = c.textPrimary,
            style = qasaText.bodyMedium,
            modifier = Modifier
                .weight(1f)
                .padding(top = QasaDimens.spaceSmall),
        )
    }
}

private val STEP_DISC = 28.dp

@Composable
private fun ChipKeyInputs(
    docNumber: String,
    birthDate: String,
    expiry: String,
    onDocNumber: (String) -> Unit,
    onBirthDate: (String) -> Unit,
    onExpiry: (String) -> Unit,
) {
    val placeholder = stringResource(R.string.field_dateplaceholder)
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(QasaDimens.gap)) {
        val numberLabel = stringResource(R.string.nfc_field_documentnumber)
        Column(Modifier.fillMaxWidth()) {
            SectionLabel(numberLabel)
            Spacer(Modifier.height(QasaDimens.contentGap))
            ThemedTextField(
                value = docNumber,
                onValueChange = onDocNumber,
                placeholder = numberLabel,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Characters,
                    autoCorrectEnabled = false,
                    keyboardType = KeyboardType.Ascii,
                ),
            )
        }
        val dob = VaultDates.parse(birthDate)
        ValidatedDateField(
            title = stringResource(R.string.nfc_field_birthdate),
            text = birthDate,
            onTextChange = onBirthDate,
            placeholder = placeholder,
            date = dob,
        )
        ValidatedDateField(
            title = stringResource(R.string.field_expirydate),
            text = expiry,
            onTextChange = onExpiry,
            placeholder = placeholder,
            date = VaultDates.parse(expiry),
        )
    }
}

@Composable
private fun StatusLine(status: ScanStatus, availability: NfcAvailability, blink: State<Float>) {
    val c = qasaColors
    @StringRes val text: Int? = when {
        status == ScanStatus.Done -> R.string.nfc_status_done
        availability == NfcAvailability.Missing -> R.string.nfc_error_none
        availability == NfcAvailability.Off -> R.string.nfc_error_off
        status is ScanStatus.Failed -> when (status.failure) {
            NfcFailure.Lost -> R.string.nfc_error_lost
            NfcFailure.Unsupported -> R.string.nfc_error_unsupported
            NfcFailure.Access -> R.string.nfc_error_access
        }
        status == ScanStatus.Reading -> R.string.nfc_status_reading
        status == ScanStatus.Ready -> R.string.nfc_status_ready
        else -> null
    }
    if (text == null) return
    val done = status == ScanStatus.Done
    val error = !done && (status is ScanStatus.Failed || availability != NfcAvailability.Ready)
    val color = when {
        done -> c.success
        error -> c.danger
        else -> c.textSecondary
    }
    Row(
        Modifier
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Polite },
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(QasaDimens.contentGap)
                .graphicsLayer { alpha = if (done || error) 1f else blink.value }
                .background(color, CircleShape),
        )
        Spacer(Modifier.width(QasaDimens.contentGap))
        Text(
            stringResource(text),
            color = color,
            style = if (done) qasaText.bodyMediumMedium else qasaText.bodyMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f, fill = false),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Actions(
    status: ScanStatus,
    availability: NfcAvailability,
    canStart: Boolean,
    onCancel: () -> Unit,
    onStart: () -> Unit,
    onTryAgain: () -> Unit,
    onOpenSettings: () -> Unit,
    focus: FocusManager,
) {
    val primary: Pair<Int, () -> Unit>? = when {
        availability == NfcAvailability.Missing -> null
        availability == NfcAvailability.Off -> R.string.notifications_permissionopensettings to onOpenSettings
        status is ScanStatus.Failed -> R.string.nfc_tryagain to onTryAgain
        status == ScanStatus.NeedsDetails -> R.string.nfc_start to onStart
        else -> null
    }
    val enabled = !(status == ScanStatus.NeedsDetails && availability == NfcAvailability.Ready && !canStart)
    // Side by side as in the preview; the buttons grow in height (never clip) with the
    // font, and FlowRow drops the second one under the first when both labels cannot fit.
    FlowRow(
        Modifier
            .fillMaxWidth()
            .padding(start = QasaDimens.screenPadding, end = QasaDimens.screenPadding, bottom = QasaDimens.screenPadding),
        horizontalArrangement = Arrangement.spacedBy(QasaDimens.gapMedium),
        verticalArrangement = Arrangement.spacedBy(QasaDimens.gapMedium),
        maxItemsInEachRow = 2,
    ) {
        SheetButton(stringResource(R.string.common_cancel), primary = false, onClick = { focus.clearFocus(); onCancel() }, modifier = Modifier.weight(1f))
        if (primary != null) {
            SheetButton(
                stringResource(primary.first),
                primary = true,
                enabled = enabled,
                onClick = primary.second,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/**
 * The preview's two action buttons. Like the theme's Primary / Secondary buttons but with
 * a minimum instead of a fixed height, so a large font size wraps the label instead of
 * cutting it.
 */
@Composable
private fun SheetButton(title: String, primary: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val c = qasaColors
    val shape = RoundedCornerShape(QasaDimens.radiusMedium)
    val content: @Composable () -> Unit = {
        Text(title, style = qasaText.bodySemibold15, textAlign = TextAlign.Center)
    }
    val m = modifier.heightIn(min = QasaDimens.stickyButtonHeight)
    val padding = PaddingValues(horizontal = QasaDimens.gap, vertical = QasaDimens.padTight)
    if (primary) {
        Button(
            onClick = onClick,
            enabled = enabled,
            modifier = m,
            shape = shape,
            contentPadding = padding,
            colors = ButtonDefaults.buttonColors(
                containerColor = c.accent,
                contentColor = c.onAccent,
                disabledContainerColor = c.accent.copy(alpha = 0.45f),
                disabledContentColor = c.onAccent.copy(alpha = 0.7f),
            ),
        ) { content() }
    } else {
        OutlinedButton(
            onClick = onClick,
            modifier = m,
            shape = shape,
            contentPadding = padding,
            border = BorderStroke(QasaDimens.stroke, c.stroke),
            colors = ButtonDefaults.outlinedButtonColors(containerColor = c.cardBackground, contentColor = c.textPrimary),
        ) { content() }
    }
}
