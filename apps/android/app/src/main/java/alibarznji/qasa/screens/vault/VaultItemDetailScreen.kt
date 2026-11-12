package alibarznji.qasa.screens.vault

import alibarznji.qasa.R
import alibarznji.qasa.screens.vault.detail.DetailCard
import alibarznji.qasa.screens.vault.detail.DetailField
import alibarznji.qasa.screens.vault.detail.DetailFieldRow
import alibarznji.qasa.screens.vault.detail.HistoryCard
import alibarznji.qasa.screens.vault.detail.OrganizationCard
import alibarznji.qasa.screens.vault.detail.TotpDetailRow
import alibarznji.qasa.screens.vault.detail.detailFields
import alibarznji.qasa.theme.BackChromeHeader
import alibarznji.qasa.theme.QasaConfirmDialog
import alibarznji.qasa.theme.QasaDimens
import alibarznji.qasa.theme.QasaIconFrame
import alibarznji.qasa.theme.QasaIconSize
import alibarznji.qasa.theme.QasaIcons
import alibarznji.qasa.theme.SiteIcons
import alibarznji.qasa.theme.qasaColors
import alibarznji.qasa.theme.qasaText
import alibarznji.qasa.vault.ProtectedItemGate
import alibarznji.qasa.screens.vault.detail.ProtectedItemCover
import alibarznji.qasa.screens.vault.detail.ProtectedUnlockSheet
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import alibarznji.qasa.vault.EphemeralClipboard
import alibarznji.qasa.vault.VaultListRow
import alibarznji.qasa.vault.VaultPayload
import alibarznji.qasa.vault.VaultRepository
import alibarznji.qasa.vault.VaultSession
import alibarznji.qasa.vault.VaultValidation
import alibarznji.qasa.vault.totp.TotpClipboard
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import alibarznji.qasa.theme.LocalQasaToast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.Color
import alibarznji.qasa.screens.vault.detail.organizationColor

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
    // The app's own toast (iOS `ToastCenter` look), not the system one.
    val qasaToast = LocalQasaToast.current
    val haptics = LocalHapticFeedback.current
    var payload by remember { mutableStateOf<VaultPayload?>(null) }
    // Rule 6b: what a protected note may show. Null until the payload is read, so nothing
    // is drawn before the gate knows the item.
    var gate by remember(itemId) { mutableStateOf<ProtectedItemGate?>(null) }
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
        val loaded = vm.repo.getPayload(itemId)
        payload = loaded
        // A first read, or the note's protection changed in the editor: decide again.
        val isProtected = loaded?.isProtectedNote == true
        if (gate?.isProtected != isProtected) gate = ProtectedItemGate(isProtected)
    }
    // Going to the background locks a protected note again; leaving forgets reveals.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) gate = gate?.lock()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            gate = gate?.leave()
        }
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
        qasaToast.show(context.getString(R.string.vault_copiedfmt, label))
        scope.launch { vm.repo.markUsed(itemId) }
    }

    fun copyTotp(code: String) {
        // Same as the password row's copy, flagged sensitive (see TotpClipboard).
        TotpClipboard.copy(context, code)
        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        qasaToast.show(context.getString(R.string.totp_copied))
        scope.launch { vm.repo.markUsed(itemId) }
    }

    val currentGate = gate
    if (payload == null || currentGate == null || !currentGate.showsItem) {
        // iOS: the plain cover, and the unlock sheet over it; cancelling goes back.
        Column(Modifier.fillMaxSize().background(c.background)) {
            BackChromeHeader(title = "", onBack = onBack)
            ProtectedItemCover()
        }
        if (currentGate != null && !currentGate.showsItem) {
            ProtectedUnlockSheet(
                biometricsEnabled = vm.session.biometricsEnabled,
                verify = vm.session::verify,
                onUnlocked = { gate = gate?.unlock() },
                onCancel = onBack,
            )
        }
        return
    }

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
                        Icon(QasaIcons.Edit, stringResource(R.string.common_edit), tint = c.accent)
                    }
                    IconButton(onClick = { showDeleteConfirm = true }) {
                        Icon(QasaIcons.Delete, stringResource(R.string.common_delete), tint = c.danger)
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
            // Stored before the shared field rules (or synced in): editing will ask for fixes.
            val needsAttention = remember(p) {
                runCatching { VaultValidation.issuesAfterNormalize(p).isNotEmpty() }.getOrDefault(false)
            }
            if (needsAttention) NeedsAttentionBanner()
            val locale = LocalConfiguration.current.locales[0] ?: java.util.Locale.getDefault()
            DetailsCard(
                fields = p.detailFields(context::getString) { VaultDates.display(it, locale) },
                totp = (p as? VaultPayload.Login)?.totp?.ifEmpty { null },
                onCopyTotp = ::copyTotp,
                gate = currentGate,
                shownSecrets = shownSecrets,
                onToggle = { field ->
                    if (currentGate.isProtected && field.hiddenWhenProtected) {
                        gate = currentGate.toggle(field.label.toString())
                    } else {
                        shownSecrets = if (field.label in shownSecrets) shownSecrets - field.label else shownSecrets + field.label
                    }
                },
                // Copying works without revealing: the note is already unlocked to be drawn.
                onCopy = ::copy,
            )
            val collectionId = p.collectionId() ?: row?.collectionId
            OrganizationCard(p, collections.firstOrNull { it.id.equals(collectionId, ignoreCase = true) }?.name)
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
    // A note or card takes its colour: the glyph in the colour on its tile fill, no stroke.
    val itemColor = p.organizationColor()
    Box(
        modifier = Modifier
            .padding(top = QasaDimens.gapSmall)
            .size(QasaIconFrame.shield)
            .clip(shape)
            .background(itemColor?.tile() ?: c.chrome)
            .border(QasaDimens.stroke, if (itemColor == null) c.strokeChrome else Color.Transparent, shape),
        contentAlignment = Alignment.Center,
    ) {
        val siteIcon = (p as? VaultPayload.Login)?.let { SiteIcons.forWebsite(it.website) }
        Icon(
            siteIcon ?: p.subtypeIcon,
            contentDescription = null,
            tint = itemColor?.swatch() ?: c.textPrimary,
            modifier = Modifier.size(QasaIconSize.x5l),
        )
    }
}

/** iOS `detailsSection` (`VaultItemDetailView.swift:124`). */
@Composable
private fun DetailsCard(
    fields: List<DetailField>,
    totp: String?,
    onCopyTotp: (String) -> Unit,
    gate: ProtectedItemGate,
    shownSecrets: Set<Int>,
    onToggle: (DetailField) -> Unit,
    onCopy: (DetailField) -> Unit,
) {
    val c = qasaColors
    DetailCard(stringResource(R.string.vault_details)) {
        if (fields.isEmpty() && totp == null) {
            Text(stringResource(R.string.vault_nodetails), color = c.textSecondary, style = qasaText.bodyMedium)
        }
        // The live 2FA code sits under the password, or last when there is none.
        val totpAfter = fields.indexOfFirst { it.label == R.string.field_password }.takeIf { it >= 0 } ?: fields.lastIndex
        if (totp != null && totpAfter < 0) TotpDetailRow(totp, onCopyTotp)
        fields.forEachIndexed { index, field ->
            // A protected note hides every value (rule 6b); other items hide their secrets.
            val guarded = gate.isProtected && field.hiddenWhenProtected
            DetailFieldRow(
                field = field,
                maskable = guarded || field.secret,
                hidden = if (guarded) gate.isHidden(field.label.toString()) else field.secret && field.label !in shownSecrets,
                onToggleShown = { onToggle(field) },
                onCopy = { onCopy(field) },
            )
            if (totp != null && index == totpAfter) {
                HorizontalDivider(color = c.stroke, thickness = QasaDimens.stroke)
                TotpDetailRow(totp, onCopyTotp)
            }
            if (index < fields.lastIndex) {
                HorizontalDivider(color = c.stroke, thickness = QasaDimens.stroke)
            }
        }
    }
}

/** `validation.needsAttention`: the stored item has fields the shared rules flag. */
@Composable
private fun NeedsAttentionBanner() {
    val c = qasaColors
    val shape = RoundedCornerShape(QasaDimens.cornerRadius)
    Row(
        Modifier
            .fillMaxWidth()
            .background(c.gold.copy(alpha = 0.1f), shape)
            .border(QasaDimens.stroke, c.gold.copy(alpha = 0.3f), shape)
            .padding(QasaDimens.gap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(QasaIcons.Warning, contentDescription = null, tint = c.gold, modifier = Modifier.size(QasaIconSize.lg))
        Spacer(Modifier.width(QasaDimens.contentGap))
        Text(stringResource(R.string.validation_needsattention), color = c.textPrimary, style = qasaText.bodyMedium)
    }
}
