package alibarznji.qasa.screens.breach

import alibarznji.qasa.R
import alibarznji.qasa.theme.CardContainer
import alibarznji.qasa.theme.PrimaryButton
import alibarznji.qasa.theme.QasaConfirmDialog
import alibarznji.qasa.theme.QasaDimens
import alibarznji.qasa.theme.QasaIconSize
import alibarznji.qasa.theme.QasaModalBottomSheet
import alibarznji.qasa.theme.SecondaryButton
import alibarznji.qasa.theme.SectionLabel
import alibarznji.qasa.theme.qasaColors
import alibarznji.qasa.theme.qasaText
import alibarznji.qasa.vault.crypto.PasswordGenerator
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.PersistableBundle
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.LayoutDirection
import kotlinx.coroutines.launch

/** App generator defaults (16 chars, every class, one of each guaranteed) — rotate_spec step 3. */
private const val ROTATE_PASSWORD_LENGTH = 16

private fun newRotatePassword() = PasswordGenerator.generate(length = ROTATE_PASSWORD_LENGTH)

/**
 * Rotate Password sheet for one breached email (rotate_spec, shared with iOS): pick the matching
 * vault Login, review a freshly generated password, confirm, then only that Login's password is
 * replaced. Nothing is written before the confirmation alert's "Replace".
 *
 * The password lives in plain `remember` (not `rememberSaveable`) so it never lands in the saved
 * instance state Bundle; the spec allows it to persist only in the vault item.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RotatePasswordSheet(
    email: String,
    loadCandidates: suspend (String) -> List<RotateCandidate>,
    /** Returns null on success, otherwise the error message to show. */
    onSave: suspend (RotateCandidate, String, String) -> String?,
    onDismiss: () -> Unit,
) {
    val c = qasaColors
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    var candidates by remember { mutableStateOf<List<RotateCandidate>?>(null) }
    var selected by remember { mutableStateOf<RotateCandidate?>(null) }
    var password by remember { mutableStateOf(newRotatePassword()) }
    var confirming by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(email) {
        val found = try {
            loadCandidates(email)
        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
            throw e
        } catch (e: Exception) {
            error = e.message
            emptyList()
        }
        candidates = found
        // One match is preselected; several require an explicit pick.
        selected = found.singleOrNull()
    }

    QasaModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = QasaDimens.screenPadding, vertical = QasaDimens.gap),
            verticalArrangement = Arrangement.spacedBy(QasaDimens.gap),
        ) {
            Text(stringResource(R.string.breach_rotatetitle), color = c.textPrimary, style = qasaText.sectionHeadline)
            val list = candidates
            when {
                list == null -> CircularProgressIndicator(color = c.accent, modifier = Modifier.align(Alignment.CenterHorizontally))
                list.isEmpty() -> {
                    Text(stringResource(R.string.breach_rotatenologins), color = c.textSecondary, style = qasaText.bodyMedium)
                    error?.let { Text(it, color = c.danger, style = qasaText.bodySmall) }
                    SecondaryButton(title = stringResource(R.string.common_close), onClick = onDismiss)
                }
                else -> {
                    if (list.size > 1) SectionLabel(stringResource(R.string.breach_rotatepicklogin))
                    Column(verticalArrangement = Arrangement.spacedBy(QasaDimens.contentGap)) {
                        list.forEach { item ->
                            CandidateRow(item, isSelected = item.id == selected?.id, onClick = { selected = item })
                        }
                    }
                    PasswordCard(
                        password = password,
                        onRegenerate = { password = newRotatePassword() },
                        onCopy = {
                            copyPassword(context, password)
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            Toast.makeText(context, context.getString(R.string.gen_copied), Toast.LENGTH_SHORT).show()
                        },
                    )
                    Text(stringResource(R.string.breach_rotatehint), color = c.textSecondary, style = qasaText.bodySmall)
                    error?.let { Text(it, color = c.danger, style = qasaText.bodySmall) }
                    PrimaryButton(
                        title = stringResource(R.string.breach_rotatesave),
                        enabled = selected != null && !saving,
                        onClick = { confirming = true },
                    )
                }
            }
        }
    }

    val target = selected
    if (confirming && target != null) {
        val savedMessage = stringResource(R.string.breach_rotatesaved, target.name)
        QasaConfirmDialog(
            title = stringResource(R.string.breach_rotateconfirmtitle),
            message = stringResource(R.string.breach_rotateconfirmmessage, target.name),
            confirmLabel = stringResource(R.string.breach_rotatereplace),
            onConfirm = {
                confirming = false
                saving = true
                scope.launch {
                    val failure = onSave(target, password, savedMessage)
                    saving = false
                    if (failure == null) onDismiss() else error = failure
                }
            },
            onDismiss = { confirming = false },
        )
    }
}

/** Selectable Login row: name + email/username; the selected one is mint-highlighted. */
@Composable
private fun CandidateRow(item: RotateCandidate, isSelected: Boolean, onClick: () -> Unit) {
    val c = qasaColors
    val shape = RoundedCornerShape(QasaDimens.radiusSmall)
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (isSelected) c.accent.copy(alpha = 0.12f) else c.cardBackground, shape)
            .border(QasaDimens.stroke, if (isSelected) c.accent else c.stroke, shape)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(QasaDimens.padTight),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(item.name, color = c.textPrimary, style = qasaText.bodyMedium)
            if (item.subtitle.isNotBlank()) {
                Text(item.subtitle, color = c.textSecondary, style = qasaText.bodySmall)
            }
        }
        if (isSelected) {
            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = c.accent, modifier = Modifier.size(QasaIconSize.md))
        }
    }
}

/** "New password" label, monospace value, Regenerate + Copy actions. */
@Composable
private fun PasswordCard(password: String, onRegenerate: () -> Unit, onCopy: () -> Unit) {
    val c = qasaColors
    CardContainer(background = c.cardBackgroundLight) {
        SectionLabel(stringResource(R.string.breach_rotatenewpassword))
        Spacer(Modifier.size(QasaDimens.contentGap))
        // A password is Latin text; keep it LTR so RTL locales don't reorder symbols.
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Text(password, color = c.textPrimary, style = qasaText.bodyMono, modifier = Modifier.fillMaxWidth())
        }
        Spacer(Modifier.size(QasaDimens.contentGap))
        Row(horizontalArrangement = Arrangement.spacedBy(QasaDimens.gap)) {
            ActionLink(Icons.Default.Refresh, stringResource(R.string.gen_regenerate), onRegenerate)
            ActionLink(Icons.Default.ContentCopy, stringResource(R.string.gen_copy), onCopy)
        }
    }
}

@Composable
private fun ActionLink(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    val c = qasaColors
    Row(
        Modifier.clickable(role = Role.Button, onClick = onClick).padding(vertical = QasaDimens.spaceSmall),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = c.accent, modifier = Modifier.size(QasaIconSize.sm))
        Spacer(Modifier.width(QasaDimens.spaceSmall))
        Text(label, color = c.accent, style = qasaText.bodyMedium)
    }
}

/** Same plain clipboard copy as the generator; flagged sensitive so Android 13+ hides the preview. */
private fun copyPassword(context: Context, password: String) {
    val clip = ClipData.newPlainText("password", password)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        clip.description.extras = PersistableBundle().apply { putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true) }
    }
    (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(clip)
}
