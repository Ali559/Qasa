package alibarznji.qasa.screens.vault

import alibarznji.qasa.theme.qasaSheetMaxHeight
import alibarznji.qasa.R
import alibarznji.qasa.screens.vault.editor.EditorEnv
import alibarznji.qasa.screens.vault.editor.EditorFields
import alibarznji.qasa.screens.vault.editor.PasswordGeneratorSheet
import alibarznji.qasa.screens.vault.editor.PhoneOption
import alibarznji.qasa.screens.vault.editor.EditorValidation
import alibarznji.qasa.screens.vault.editor.LocalEditorValidation
import alibarznji.qasa.screens.vault.editor.typeLabelRes
import alibarznji.qasa.theme.PrimaryButton
import alibarznji.qasa.theme.QasaConfirmDialog
import alibarznji.qasa.theme.QasaDimens
import alibarznji.qasa.theme.SheetChromeHeader
import alibarznji.qasa.theme.LocalQasaToast
import alibarznji.qasa.theme.StickyBottomBar
import androidx.compose.ui.platform.LocalContext
import alibarznji.qasa.vault.VaultItemKind
import alibarznji.qasa.vault.VaultPayload
import alibarznji.qasa.vault.VaultRepository
import alibarznji.qasa.vault.VaultCollection
import alibarznji.qasa.vault.VaultValidation
import alibarznji.qasa.vault.VaultValidationException
import alibarznji.qasa.theme.qasaColors
import alibarznji.qasa.theme.qasaText
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.text.style.TextAlign
import uniffi.qasa_ffi.FfiField
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class EditorViewModel @Inject constructor(val repo: VaultRepository) : ViewModel() {
    /** iOS `CollectionPickerField`'s `@Query` over `VaultCollection`. */
    val collections: StateFlow<List<VaultCollection>> =
        repo.observeCollections().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** iOS `AddressPhonePicker.phones`: every row whose decrypted payload is a Phone. */
    suspend fun loadPhones(): List<PhoneOption> =
        repo.getAllRows()
            .filter { it.kind == VaultItemKind.PersonalInfo }
            .mapNotNull { row ->
                val payload = runCatching { repo.getPayload(row.id) }.getOrNull()
                if (payload is VaultPayload.Phone) PhoneOption(row.id, row.displayName) else null
            }

    fun createCollection(name: String, onCreated: (String) -> Unit) {
        viewModelScope.launch {
            runCatching { repo.saveCollection(null, name) }.onSuccess(onCreated)
        }
    }
}

/**
 * Port of iOS `VaultItemEditor` + `VaultFormChrome` (Views/Vault/VaultItemEditor.swift,
 * FormHelpers.swift:156). Per-kind fields live in `editor/`.
 */
@Composable
fun VaultItemEditorSheetContent(
    initial: VaultPayload,
    existingId: String?,
    onDone: () -> Unit,
    vm: EditorViewModel = hiltViewModel(),
) {
    val isEditing = existingId != null
    // An edit opens with normalize applied (legacy dates → ISO, card numbers grouped…), so
    // only what normalize can't fix shows as an issue.
    var payload by remember(initial) {
        mutableStateOf(if (isEditing) VaultValidation.normalizeOrSelf(initial) else initial)
    }
    var touched by remember(initial) { mutableStateOf(setOf<FfiField>()) }
    var saveAttempted by remember(initial) { mutableStateOf(false) }
    // Live: the issues of the payload as a save would store it.
    val issues = remember(payload) {
        runCatching { VaultValidation.byField(VaultValidation.issuesAfterNormalize(payload)) }.getOrDefault(emptyMap())
    }
    val validation = EditorValidation(
        issues = issues,
        touched = touched,
        showAll = saveAttempted,
        onTouch = { field -> if (field !in touched) touched = touched + field },
    )
    var saving by remember { mutableStateOf(false) }
    var saveError by remember { mutableStateOf<String?>(null) }
    var showGenerator by remember { mutableStateOf(false) }
    var phones by remember { mutableStateOf<List<PhoneOption>?>(null) }
    val collections by vm.collections.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val maxHeight = qasaSheetMaxHeight()
    val toast = LocalQasaToast.current
    val context = LocalContext.current

    // Only the address form links a phone; skip decrypting the vault for other kinds.
    val needsPhones = payload is VaultPayload.Address
    LaunchedEffect(needsPhones) {
        if (needsPhones && phones == null) phones = runCatching { vm.loadPhones() }.getOrDefault(emptyList())
    }

    val env = EditorEnv(
        collections = collections,
        phones = phones,
        onCreateCollection = vm::createCollection,
        onGeneratePassword = { showGenerator = true },
    )

    Column(Modifier.fillMaxWidth().heightIn(max = maxHeight)) {
        SheetChromeHeader(
            title = stringResource(
                if (isEditing) R.string.vault_editfmt else R.string.vault_newfmt,
                stringResource(initial.typeLabelRes()),
            ),
            onDismiss = onDone,
            dismissLabel = stringResource(R.string.common_cancel),
        )
        Column(
            Modifier
                .weight(1f, fill = false)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = QasaDimens.screenPadding)
                .padding(top = QasaDimens.contentGap, bottom = QasaDimens.gap),
        ) {
            CompositionLocalProvider(LocalEditorValidation provides validation) {
                EditorFields(payload = payload, env = env, onChange = { payload = it })
            }
        }
        StickyBottomBar { Column(Modifier.fillMaxWidth()) {
            if (saveAttempted && issues.isNotEmpty()) {
                Text(
                    stringResource(R.string.validation_fixtosave),
                    color = qasaColors.danger,
                    style = qasaText.errorDetail,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(bottom = QasaDimens.contentGap),
                )
            }
            PrimaryButton(
                title = stringResource(if (isEditing) R.string.common_update else R.string.common_save),
                // Always tappable: a Save with issues shows every field's message (a required
                // name included) instead of a silently disabled button.
                enabled = !saving,
                onClick = {
                    saveAttempted = true
                    if (issues.isNotEmpty()) return@PrimaryButton
                    saving = true
                    scope.launch {
                        try {
                            val saved = payload
                            vm.repo.save(existingId, saved)
                            // Root-owned toast, so it stays up once this sheet has closed.
                            toast.show(context.savedToastText(saved, isEditing))
                            onDone()
                        } catch (e: CancellationException) {
                            throw e
                        } catch (_: VaultValidationException) {
                            // The repository's own check (same rules): the messages show inline.
                        } catch (e: Exception) {
                            saveError = e.localizedMessage ?: e.toString()
                        } finally {
                            saving = false
                        }
                    }
                },
            )
        } }
    }

    if (showGenerator) {
        PasswordGeneratorSheet(
            onDismiss = { showGenerator = false },
            onUsePassword = { generated ->
                (payload as? VaultPayload.Login)?.let { payload = it.copy(password = generated) }
                showGenerator = false
            },
        )
    }

    saveError?.let { message ->
        // iOS `.alert("vault.couldNotSave")` with a single OK.
        QasaConfirmDialog(
            title = stringResource(R.string.vault_couldnotsave),
            message = message,
            confirmLabel = stringResource(R.string.common_ok),
            onConfirm = { saveError = null },
            onDismiss = { saveError = null },
            dismissLabel = null,
            destructive = false,
        )
    }
}

/** "Gmail added" / "Gmail updated", or the untitled forms when the item has no name. */
private fun android.content.Context.savedToastText(payload: VaultPayload, isEditing: Boolean): String {
    val title = payload.toastTitle()
    return when {
        isEditing && title != null -> getString(R.string.toast_itemupdatedfmt, title)
        isEditing -> getString(R.string.toast_itemupdated)
        title != null -> getString(R.string.toast_itemcreatedfmt, title)
        else -> getString(R.string.toast_itemcreated)
    }
}

/**
 * The name the user gave the item, or null. `displayName()` falls back to a literal
 * "Untitled" for list rows; a toast says "Item added" instead.
 */
private fun VaultPayload.toastTitle(): String? =
    displayName().trim().takeIf { it.isNotEmpty() && it != UNTITLED_FALLBACK }

private const val UNTITLED_FALLBACK = "Untitled"
