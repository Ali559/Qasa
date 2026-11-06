package alibarznji.qasa.screens.vault.editor

import alibarznji.qasa.R
import alibarznji.qasa.theme.DestructiveTextButton
import alibarznji.qasa.theme.PrimaryButton
import alibarznji.qasa.theme.QasaDimens
import alibarznji.qasa.theme.QasaIconSize
import alibarznji.qasa.theme.SecondaryButton
import alibarznji.qasa.theme.ThemedTextField
import alibarznji.qasa.theme.qasaColors
import alibarznji.qasa.theme.qasaText
import alibarznji.qasa.vault.totp.Totp
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning

/** Where the "2FA code" row is while no code is set. */
private enum class TotpEntry { Closed, Choosing, Manual }

/**
 * The login editor's "2FA code" row. Unset: "Add 2FA code" offers a QR scan (Google's code
 * scanner, which needs no camera permission) or a typed setup key. Set: who the code is
 * for, and Remove. [value] is the stored URI; only valid, normalised URIs are passed to
 * [onChange], and the secret itself is never shown.
 */
@Composable
internal fun TotpFormField(value: String, onChange: (String) -> Unit) {
    val c = qasaColors
    val context = LocalContext.current
    var entry by rememberSaveable { mutableStateOf(TotpEntry.Closed) }
    var key by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf<Int?>(null) }
    // The scanner answers after its own activity closes; save into the latest payload.
    val latestOnChange by rememberUpdatedState(onChange)

    FieldBlock(stringResource(R.string.field_totp)) {
        if (value.isNotEmpty()) {
            val params = remember(value) { Totp.parse(value) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.VerifiedUser, null, tint = c.success, modifier = Modifier.size(QasaIconSize.lg))
                Spacer(Modifier.width(QasaDimens.contentGap))
                Text(
                    params?.let(::accountName) ?: stringResource(R.string.field_totp),
                    color = c.textPrimary,
                    style = qasaText.bodyMedium15,
                    modifier = Modifier.weight(1f),
                )
                DestructiveTextButton(title = stringResource(R.string.totp_remove), onClick = { onChange("") })
            }
            return@FieldBlock
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
            key = ""
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
                .addOnFailureListener { entry = TotpEntry.Manual }
        }

        Column(verticalArrangement = Arrangement.spacedBy(QasaDimens.contentGap)) {
            when (entry) {
                TotpEntry.Closed -> SecondaryButton(
                    title = stringResource(R.string.totp_add),
                    onClick = { entry = TotpEntry.Choosing },
                )
                TotpEntry.Choosing -> {
                    PrimaryButton(
                        title = stringResource(R.string.totp_scan),
                        icon = Icons.Outlined.QrCodeScanner,
                        onClick = ::scan,
                    )
                    SecondaryButton(
                        title = stringResource(R.string.totp_enterkey),
                        onClick = { error = null; entry = TotpEntry.Manual },
                    )
                }
                TotpEntry.Manual -> {
                    ThemedTextField(
                        value = key,
                        onValueChange = { key = it; error = null },
                        placeholder = stringResource(R.string.totp_keyplaceholder),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Ascii,
                            capitalization = KeyboardCapitalization.Characters,
                            autoCorrectEnabled = false,
                        ),
                    )
                    PrimaryButton(
                        title = stringResource(R.string.totp_add),
                        enabled = key.isNotBlank(),
                        onClick = { accept(key, scanned = false) },
                    )
                    SecondaryButton(title = stringResource(R.string.totp_scan), onClick = ::scan)
                }
            }
            error?.let { Text(stringResource(it), color = c.danger, style = qasaText.bodySmall) }
            if (entry != TotpEntry.Closed) {
                Text(stringResource(R.string.totp_hint), color = c.textSecondary, style = qasaText.bodySmall)
            }
        }
    }
}

/** "GitHub · ali@example.com": the issuer and account the code is for, never the secret. */
internal fun accountName(params: Totp.Params): String? =
    listOfNotNull(params.issuer, params.label?.substringAfter(':')?.trim())
        .filter { it.isNotBlank() }
        .distinct()
        .joinToString(" · ")
        .ifEmpty { null }
