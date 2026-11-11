package alibarznji.qasa.screens.vault.editor

import uniffi.qasa_ffi.FfiField
import alibarznji.qasa.R
import alibarznji.qasa.theme.FieldIconButton
import alibarznji.qasa.theme.QasaGroupRow
import alibarznji.qasa.theme.QasaRowCaption
import alibarznji.qasa.theme.QasaTextInputSheet
import alibarznji.qasa.theme.qasaColors
import alibarznji.qasa.theme.qasaText
import alibarznji.qasa.vault.totp.Totp
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.LayoutDirection
import kotlinx.coroutines.delay
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning

/** Where the "2FA code" row is while no code is set. */
private enum class TotpEntry { Closed, Choosing }

/**
 * The login editor's "2FA code" group row. Unset, the value line is "Add 2FA code" with the
 * help hint as a caption; tapped, it offers a QR scan (Google's code scanner, which needs no
 * camera permission) or "Enter setup key", which opens the one-field prompt sheet. Set: the live code, who it is for as a
 * caption, and a trailing ✕ that removes it. [value] is the stored URI; only valid, normalised URIs are passed to [onChange],
 * and the secret itself is never shown.
 */
@Composable
internal fun TotpFormField(value: String, onChange: (String) -> Unit) {
    val c = qasaColors
    val context = LocalContext.current
    val label = stringResource(R.string.field_totp)
    var entry by rememberSaveable { mutableStateOf(TotpEntry.Closed) }
    var enteringKey by rememberSaveable { mutableStateOf(false) }
    var error by remember { mutableStateOf<Int?>(null) }
    // The scanner answers after its own activity closes; save into the latest payload.
    val latestOnChange by rememberUpdatedState(onChange)
    val rowError = error?.let { stringResource(it) } ?: fieldError(FfiField.TOTP)

    if (value.isNotEmpty()) {
        val params = remember(value) { Totp.parse(value) }
        var now by remember { mutableLongStateOf(System.currentTimeMillis() / 1000) }
        LaunchedEffect(params) {
            while (params != null) {
                val millis = System.currentTimeMillis()
                now = millis / 1000
                // Wake on the next whole second so the rollover lands on time.
                delay(1000 - millis % 1000)
            }
        }
        val code = params?.let { remember(it, now / it.period) { Totp.code(it, now) } }
        QasaGroupRow(
            label = label,
            error = fieldError(FfiField.TOTP),
            trailing = {
                FieldIconButton(
                    icon = Icons.Outlined.Close,
                    contentDescription = stringResource(R.string.totp_remove),
                    onClick = { onChange("") },
                )
            },
        ) {
            // The live code (never the secret), Latin digits kept LTR; who it is for under it.
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Text(
                    code?.let(Totp::grouped) ?: "—",
                    color = c.textPrimary,
                    style = qasaText.groupValue,
                    maxLines = 1,
                )
            }
            params?.let(::accountName)?.let { QasaRowCaption(it) }
        }
        return
    }

    fun accept(raw: String, scanned: Boolean) {
        if (scanned && !Totp.isOtpauthUri(raw)) {
            error = R.string.totp_notqr
            return
        }
        val normalized = Totp.normalize(raw, requireUri = scanned)
        if (normalized == null) {
            error = R.string.totp_invalid
            return
        }
        error = null
        entry = TotpEntry.Closed
        latestOnChange(normalized)
    }

    fun scan() {
        error = null
        val options = GmsBarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .build()
        GmsBarcodeScanning.getClient(context, options).startScan()
            .addOnSuccessListener { barcode -> accept(barcode.rawValue.orEmpty(), scanned = true) }
            // The scanner module can be missing or still downloading; typing the key
            // always works, so fall through to it.
            .addOnFailureListener { enteringKey = true }
    }

    val hint = stringResource(R.string.totp_hint)
    when (entry) {
        TotpEntry.Closed -> QasaGroupRow(
            label = label,
            error = rowError,
            onTap = { entry = TotpEntry.Choosing },
            role = Role.Button,
        ) {
            RowAction(stringResource(R.string.totp_add))
            QasaRowCaption(hint)
        }
        TotpEntry.Choosing -> QasaGroupRow(label = label, error = rowError) {
            RowAction(stringResource(R.string.totp_scan), onClick = ::scan)
            RowAction(stringResource(R.string.totp_enterkey), onClick = { error = null; enteringKey = true })
            QasaRowCaption(hint)
        }
    }

    // iOS / desktop: the setup key is typed in the one-field prompt sheet, not in the row.
    if (enteringKey) {
        QasaTextInputSheet(
            title = stringResource(R.string.totp_enterkey),
            message = hint,
            initial = "",
            placeholder = stringResource(R.string.totp_keyplaceholder),
            confirmLabel = stringResource(R.string.common_add),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Ascii,
                capitalization = KeyboardCapitalization.Characters,
                autoCorrectEnabled = false,
            ),
            onConfirm = { typed ->
                enteringKey = false
                accept(typed, scanned = false)
            },
            onDismiss = { enteringKey = false },
        )
    }
}

/** An accent action on a row's value line ("Scan QR code"). Without [onClick] it is the
 *  row's own tap that acts (the whole row is the button). */
@Composable
private fun RowAction(title: String, onClick: (() -> Unit)? = null) {
    Text(
        title,
        color = qasaColors.accent,
        style = qasaText.groupValue,
        modifier = if (onClick == null) Modifier else Modifier.clickable(role = Role.Button, onClick = onClick),
    )
}

/** "GitHub · ali@example.com": the issuer and account the code is for, never the secret. */
internal fun accountName(params: Totp.Params): String? =
    listOfNotNull(params.issuer, params.label?.substringAfter(':')?.trim())
        .filter { it.isNotBlank() }
        .distinct()
        .joinToString(" · ")
        .ifEmpty { null }
