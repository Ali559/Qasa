package alibarznji.qasa.screens.tools

import alibarznji.qasa.screens.tools.collections.CollectionDetailContent
import alibarznji.qasa.screens.tools.collections.CollectionsListContent
import alibarznji.qasa.screens.vault.VaultEditorHolder
import alibarznji.qasa.screens.vault.VaultItemDetailScreen
import alibarznji.qasa.screens.vault.VaultItemEditorSheetContent
import alibarznji.qasa.screens.vault.VaultListViewModel
import alibarznji.qasa.theme.QasaModalBottomSheet
import alibarznji.qasa.vault.VaultRepository
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class CollectionsViewModel @Inject constructor(private val repo: VaultRepository) : ViewModel() {
    val collections = repo.observeCollections().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val rows = repo.observeRows().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun create(name: String) {
        viewModelScope.launch { runCatching { repo.saveCollection(null, name) } }
    }

    fun rename(id: String, name: String) {
        viewModelScope.launch { runCatching { repo.renameCollection(id, name) } }
    }

    /** Un-assigns the collection's items first (`VaultRepository.deleteCollection`). */
    fun delete(id: String) {
        viewModelScope.launch { runCatching { repo.deleteCollection(id) } }
    }
}

/** Destinations of the Collections page's own stack (iOS `NavigationLink`s inside it). */
private object CollectionRoutes {
    const val LIST = "collections/list"
    const val DETAIL = "collections/detail/{collectionId}"
    const val ITEM = "collections/item/{itemId}"
    fun detail(id: String) = "collections/detail/$id"
    fun item(id: String) = "collections/item/$id"
}

/**
 * Port of iOS `CollectionsView` + `CollectionDetailView`.
 *
 * iOS pushes `CollectionDetailView` and then `VaultItemDetailView` inside the page's own
 * `NavigationStack`, with the editor as a sheet. Android does the same as `VaultListScreen`:
 * a page-local `NavHost` (list → collection → item) reusing `VaultItemDetailScreen`, and the
 * editor sheet over whichever screen opened it. System back pops this stack first.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollectionsScreen(
    onOpenMenu: (() -> Unit)? = null,
    vm: CollectionsViewModel = hiltViewModel(),
    editorHolder: VaultEditorHolder = hiltViewModel(),
) {
    val navController = rememberNavController()
    val collections by vm.collections.collectAsState()
    val rows by vm.rows.collectAsState()
    var editing by remember { mutableStateOf(false) }
    // Bumped when the editor closes over the item detail so it re-reads the saved item.
    var detailVersion by remember { mutableIntStateOf(0) }

    fun dismissEditor() {
        if (editorHolder.existingId != null) detailVersion++
        editing = false
        editorHolder.clear()
    }

    BackHandler(enabled = editing) { dismissEditor() }

    Box(Modifier.fillMaxSize()) {
        NavHost(
            navController = navController,
            startDestination = CollectionRoutes.LIST,
            modifier = Modifier.fillMaxSize(),
        ) {
            composable(CollectionRoutes.LIST) {
                CollectionsListContent(
                    collections = collections,
                    rows = rows,
                    onOpenMenu = onOpenMenu,
                    onOpen = { navController.navigate(CollectionRoutes.detail(it.id)) },
                    onCreate = vm::create,
                    onRename = vm::rename,
                    onDelete = vm::delete,
                )
            }
            composable(
                route = CollectionRoutes.DETAIL,
                arguments = listOf(navArgument("collectionId") { type = NavType.StringType }),
            ) { entry ->
                val collectionId = entry.arguments?.getString("collectionId").orEmpty()
                val collection = collections.firstOrNull { it.id.equals(collectionId, ignoreCase = true) }
                // If the collection disappears while open (e.g. a vault reset), go back to the list.
                var seen by remember { mutableStateOf(false) }
                LaunchedEffect(collection) {
                    if (collection != null) seen = true else if (seen) navController.popBackStack()
                }
                val listVm: VaultListViewModel = hiltViewModel()
                CollectionDetailContent(
                    collectionId = collectionId,
                    collectionName = collection?.name.orEmpty(),
                    onBack = { navController.popBackStack() },
                    onOpenItem = { navController.navigate(CollectionRoutes.item(it)) },
                    onEdit = { id, payload ->
                        editorHolder.beginEdit(id, payload)
                        editing = true
                    },
                    vm = listVm,
                )
            }
            composable(
                route = CollectionRoutes.ITEM,
                arguments = listOf(navArgument("itemId") { type = NavType.StringType }),
            ) { entry ->
                val itemId = entry.arguments?.getString("itemId").orEmpty()
                key(detailVersion) {
                    VaultItemDetailScreen(
                        itemId = itemId,
                        onBack = { navController.popBackStack() },
                        onEdit = { payload ->
                            editorHolder.beginEdit(itemId, payload)
                            editing = true
                        },
                    )
                }
            }
        }

        val draft = editorHolder.draft
        if (editing && draft != null) {
            QasaModalBottomSheet(onDismissRequest = ::dismissEditor) {
                VaultItemEditorSheetContent(
                    initial = draft,
                    existingId = editorHolder.existingId,
                    onDone = ::dismissEditor,
                )
            }
        }
    }
}
