package alibarznji.qasa.screens.vault

import alibarznji.qasa.credentials.AutofillIndexStore
import alibarznji.qasa.screens.nav.VaultRoutes
import alibarznji.qasa.screens.vault.list.VaultListContent
import alibarznji.qasa.theme.QasaModalBottomSheet
import alibarznji.qasa.theme.rememberSheetDismiss
import alibarznji.qasa.vault.EphemeralClipboard
import alibarznji.qasa.vault.VaultItemKind
import alibarznji.qasa.vault.VaultRepository
import alibarznji.qasa.vault.VaultSession
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private sealed interface VaultSheet {
    /** "Add New" for the list's current tab (`null` = All). */
    data class Add(val tab: VaultItemKind?) : VaultSheet
    data object Edit : VaultSheet
}

@HiltViewModel
class VaultListViewModel @Inject constructor(
    val repo: VaultRepository,
    val clipboard: EphemeralClipboard,
    val session: VaultSession,
    autofillIndex: AutofillIndexStore,
    val appSession: alibarznji.qasa.screens.AppSession,
) : ViewModel() {
    val rows = repo.observeRows().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * The autofill index iOS `VaultSuggestionEngine` ranks Suggested with. Re-read whenever
     * the rows change, since the repository rebuilds it after every save/delete.
     */
    val autofillEntries = repo.observeRows()
        .map { autofillIndex.read() }
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    suspend fun loadPayload(id: String) = withContext(Dispatchers.Default) { repo.getPayload(id) }

    /** iOS stamps `lastUsedAt` after every row copy (`VaultItemRow.swift:139`). */
    fun markUsed(id: String) {
        viewModelScope.launch { runCatching { repo.markUsed(id) } }
    }

    fun delete(id: String) {
        viewModelScope.launch { runCatching { repo.delete(id) } }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultListScreen(
    onOpenMenu: (() -> Unit)? = null,
    vm: VaultListViewModel = hiltViewModel(),
    editorHolder: VaultEditorHolder = hiltViewModel(),
) {
    val navController = rememberNavController()
    var sheet by remember { mutableStateOf<VaultSheet?>(null) }
    // The detail screen re-reads its item when the row's updatedAt changes, so closing the
    // editor doesn't rebuild it (a rebuild flashed the whole screen).
    fun dismissSheet() {
        sheet = null
        editorHolder.clear()
    }

    BackHandler(enabled = sheet != null) {
        dismissSheet()
    }

    // A login autofill offered to save: open the new-login form with it.
    val pendingSave by vm.appSession.pendingLoginSave.collectAsState()
    LaunchedEffect(pendingSave) {
        vm.appSession.consumeLoginSave()?.let { login ->
            editorHolder.beginCreate(login)
            sheet = VaultSheet.Edit
        }
    }

    Box(Modifier.fillMaxSize()) {
        NavHost(
            navController = navController,
            startDestination = VaultRoutes.LIST,
            modifier = Modifier.fillMaxSize(),
        ) {
            composable(VaultRoutes.LIST) {
                VaultListContent(
                    onOpenMenu = onOpenMenu,
                    onOpenDetail = { id -> navController.navigate(VaultRoutes.detail(id)) },
                    onAdd = { tab ->
                        // iOS `handleAddTapped`: Logins / Notes open their editor directly.
                        val direct = tab?.directAddPayload()
                        if (direct != null) {
                            editorHolder.beginCreate(direct)
                            sheet = VaultSheet.Edit
                        } else {
                            sheet = VaultSheet.Add(tab)
                        }
                    },
                    onEdit = { id, payload ->
                        editorHolder.beginEdit(id, payload)
                        sheet = VaultSheet.Edit
                    },
                    vm = vm,
                )
            }
            composable(
                route = VaultRoutes.DETAIL,
                arguments = listOf(navArgument("itemId") { type = NavType.StringType }),
            ) { entry ->
                val itemId = entry.arguments?.getString("itemId").orEmpty()
                // The editor sheet opens over the detail screen (iOS presents it from the
                // pushed detail view), so save / cancel land back here, not on the list.
                VaultItemDetailScreen(
                    itemId = itemId,
                    onBack = { navController.popBackStack() },
                    onEdit = { payload ->
                        editorHolder.beginEdit(itemId, payload)
                        sheet = VaultSheet.Edit
                    },
                )
            }
        }

        when (val s = sheet) {
            is VaultSheet.Add -> {
                QasaModalBottomSheet(onDismissRequest = ::dismissSheet) {
                    AddItemMenuSheetContent(
                        tab = s.tab,
                        onSelect = { payload ->
                            editorHolder.beginCreate(payload)
                            sheet = VaultSheet.Edit
                        },
                        onCancel = rememberSheetDismiss(::dismissSheet),
                    )
                }
            }
            VaultSheet.Edit -> {
                val draft = editorHolder.draft
                if (draft != null) {
                    QasaModalBottomSheet(onDismissRequest = ::dismissSheet) {
                        VaultItemEditorSheetContent(
                            initial = draft,
                            existingId = editorHolder.existingId,
                            onDone = rememberSheetDismiss(::dismissSheet),
                        )
                    }
                }
            }
            null -> Unit
        }
    }
}
