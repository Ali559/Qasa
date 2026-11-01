package alibarznji.qasa.screens.vault

import alibarznji.qasa.R
import alibarznji.qasa.screens.vault.detail.DetailCard
import alibarznji.qasa.screens.vault.detail.DetailField
import alibarznji.qasa.screens.vault.detail.DetailFieldRow
import alibarznji.qasa.screens.vault.detail.HistoryCard
import alibarznji.qasa.screens.vault.detail.OrganizationCard
import alibarznji.qasa.screens.vault.detail.detailFields
import alibarznji.qasa.theme.BackChromeHeader
import alibarznji.qasa.theme.QasaConfirmDialog
import alibarznji.qasa.theme.QasaDimens
import alibarznji.qasa.theme.QasaIconFrame
import alibarznji.qasa.theme.QasaIconSize
import alibarznji.qasa.theme.qasaColors
import alibarznji.qasa.theme.qasaText
import alibarznji.qasa.vault.BiometricGateEffect
import alibarznji.qasa.vault.EphemeralClipboard
import alibarznji.qasa.vault.VaultListRow
import alibarznji.qasa.vault.VaultPayload
import alibarznji.qasa.vault.VaultRepository
import alibarznji.qasa.vault.VaultSession
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch

@HiltViewModel
class DetailViewModel @Inject constructor(
    val repo: VaultRepository,
    val session: VaultSession,
    val clipboard: EphemeralClipboard,
) : ViewModel()

/** Port of iOS `Views/Vault/VaultItemDetailView.swift`. */
@Composable
fun VaultItemDetailScreen(
    itemId: String,
    onBack: () -> Unit,
    onEdit: (VaultPayload) -> Unit,
    vm: DetailViewModel = hiltViewModel(),
) {
    val c = qasaColors
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    var payload by remember { mutableStateOf<VaultPayload?>(null) }
    var gateItemId by remember { mutableStateOf<String?>(null) }
    var revealed by remember { mutableStateOf(false) }
    val protectedNotePrompt = stringResource(R.string.vault_unlockprotectednote)
    // The row a locked tap asked to copy; copied once the gate succeeds.
    var pendingCopy by remember { mutableStateOf<DetailField?>(null) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    // Secret rows the user has unmasked with the eye toggle (iOS `revealedSecrets`).
    var shownSecrets by remember { mutableStateOf(setOf<Int>()) }

    // iOS reads `cache.row(for:)` and reloads on `row?.updatedAt` change
    // (`VaultItemDetailView.swift:88`): the editor sheet now opens over this screen, so a
    // save must refresh the payload in place rather than relying on a re-navigation.
    val rows by remember { vm.repo.observeRows() }.collectAsState(initial = emptyList())
    val row: VaultListRow? = rows.firstOrNull { it.id == itemId }
    val collections by remember { vm.repo.observeCollections() }.collectAsState(initial = emptyList())

    LaunchedEffect(itemId, row?.updatedAt) {
        payload = vm.repo.getPayload(itemId)
    }
    // iOS stamps "last used" on appear as well as on every copy (`:85`).
    LaunchedEffect(itemId) { vm.repo.markUsed(itemId) }

    fun copy(field: DetailField) {
        val label = context.getString(field.label)
        // Card and bank fields go through the 30s ephemeral clipboard (iOS
        // `PaymentClipboard`): a card number in the system clipboard outlives the vault.
        if (field.ephemeral) {
            vm.clipboard.copy(label, field.value)
        } else {
            val clip = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clip.setPrimaryClip(ClipData.newPlainText(label, field.value))
        }
        // iOS `VaultClipboard.copy` fires a light impact.
        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        Toast.makeText(context, context.getString(R.string.vault_copiedfmt, label), Toast.LENGTH_SHORT).show()
        scope.launch { vm.repo.markUsed(itemId) }
    }

    // A protected note is gated per item, per unlock: the session records the decision so
    // re-composing (scroll, edit round-trip, rotation) does not re-prompt, and `lock()`
    // clears it. Fails closed — no hardware, cancel, or lockout leaves the note masked.
    val protectedNote = payload?.isProtectedNote == true
    val alreadyRevealed = vm.session.isProtectedNoteRevealed(itemId)
    val unlocked = !protectedNote || revealed || alreadyRevealed
    LaunchedEffect(itemId, protectedNote, alreadyRevealed) {
        if (protectedNote && !alreadyRevealed) gateItemId = itemId
    }
    BiometricGateEffect(
        enabled = gateItemId != null,
        title = protectedNotePrompt,
        onAuthenticated = {
            gateItemId = null
            if (protectedNote) {
                vm.session.markProtectedNoteRevealed(itemId)
                revealed = true
            }
            // iOS copies immediately after the gate succeeds (`VaultItemDetailView.swift:296`).
            pendingCopy?.let { copy(it) }
            pendingCopy = null
        },
        onDismissed = {
            gateItemId = null
            revealed = false
            pendingCopy = null
        },
    )

    val untitled = stringResource(R.string.vault_untitled)
    val name = payload?.displayName()?.takeUnless { it.isBlank() || it == "Untitled" } ?: untitled

    Column(Modifier.fillMaxSize().background(c.background)) {
        // iOS: inline nav bar with no title (the name sits under the hero icon) and
        // Edit / Delete as trailing toolbar icons (`:49`). No bottom action buttons.
        BackChromeHeader(
            title = "",
            onBack = onBack,
            trailing = payload?.let { p ->
                {
                    IconButton(onClick = { onEdit(p) }) {
                        Icon(Icons.Default.Edit, stringResource(R.string.common_edit), tint = c.accent)
                    }
                    IconButton(onClick = { showDeleteConfirm = true }) {
                        Icon(Icons.Default.Delete, stringResource(R.string.common_delete), tint = c.danger)
                    }
                }
            },
        )
        val p = payload
        if (p == null) {
            Text(
                stringResource(R.string.vault_loading),
                color = c.textSecondary,
                modifier = Modifier.padding(QasaDimens.screenPadding),
            )
            return@Column
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = QasaDimens.screenPadding)
                .padding(top = QasaDimens.contentGap, bottom = QasaDimens.scrollTail),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(QasaDimens.sectionGap),
        ) {
            HeroIcon(p)
            Text(name, color = c.textPrimary, style = qasaText.sectionHeadline, textAlign = TextAlign.Center)
            DetailsCard(
                fields = p.detailFields(context::getString),
                unlocked = unlocked,
                shownSecrets = shownSecrets,
                onToggle = { field, locked ->
                    // A locked row's eye answers the biometric gate first.
                    if (locked) {
                        gateItemId = itemId
                    } else {
                        shownSecrets = if (field.label in shownSecrets) shownSecrets - field.label else shownSecrets + field.label
                    }
                },
                onCopy = { field, locked ->
                    // A masked protected note must not copy plaintext before its gate.
                    if (locked) {
                        pendingCopy = field
                        gateItemId = itemId
                    } else {
                        copy(field)
                    }
                },
            )
            val collectionId = p.collectionId() ?: row?.collectionId
            OrganizationCard(p, collections.firstOrNull { it.id == collectionId }?.name)
            HistoryCard(row?.createdAt, row?.updatedAt, row?.lastUsedAt)
        }
    }

    if (showDeleteConfirm && payload != null) {
        QasaConfirmDialog(
            title = stringResource(R.string.vault_deleteitemtitle),
            message = stringResource(R.string.vault_deleteitemmessage, name),
            confirmLabel = stringResource(R.string.common_delete),
            onConfirm = {
                showDeleteConfirm = false
                scope.launch {
                    vm.repo.delete(itemId)
                    onBack()
                }
            },
            onDismiss = { showDeleteConfirm = false },
        )
    }
}

/** iOS `heroIcon` (`VaultItemDetailView.swift:110`): 88pt chrome tile, per-subtype glyph. */
@Composable
private fun HeroIcon(p: VaultPayload) {
    val c = qasaColors
    val shape = RoundedCornerShape(QasaDimens.cornerRadiusLarge)
    Box(
        modifier = Modifier
            .padding(top = QasaDimens.gapSmall)
            .size(QasaIconFrame.shield)
            .clip(shape)
            .background(c.chrome)
            .border(QasaDimens.stroke, c.strokeChrome, shape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(p.subtypeIcon, contentDescription = null, tint = c.textPrimary, modifier = Modifier.size(QasaIconSize.x5l))
    }
}

/** iOS `detailsSection` (`VaultItemDetailView.swift:124`). */
@Composable
private fun DetailsCard(
    fields: List<DetailField>,
    unlocked: Boolean,
    shownSecrets: Set<Int>,
    onToggle: (DetailField, Boolean) -> Unit,
    onCopy: (DetailField, Boolean) -> Unit,
) {
    val c = qasaColors
    DetailCard(stringResource(R.string.vault_details)) {
        if (fields.isEmpty()) {
            Text(stringResource(R.string.vault_nodetails), color = c.textSecondary, style = qasaText.bodyMedium)
        }
        fields.forEachIndexed { index, field ->
            val locked = field.secret && !unlocked
            DetailFieldRow(
                field = field,
                locked = locked,
                shown = field.label in shownSecrets,
                onToggleShown = { onToggle(field, locked) },
                onCopy = { onCopy(field, locked) },
            )
            if (index < fields.lastIndex) {
                HorizontalDivider(color = c.stroke, thickness = QasaDimens.stroke)
            }
        }
    }
}
