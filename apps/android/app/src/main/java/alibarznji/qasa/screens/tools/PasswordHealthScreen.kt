package alibarznji.qasa.screens.tools

import alibarznji.qasa.R
import alibarznji.qasa.screens.PasswordHealthReport
import alibarznji.qasa.screens.vault.RowCopy
import alibarznji.qasa.screens.vault.VaultItemDetailScreen
import alibarznji.qasa.screens.vault.VaultItemEditorSheetContent
import alibarznji.qasa.screens.tools.health.HealthIssueTab
import alibarznji.qasa.screens.tools.health.HealthIssuesScreen
import alibarznji.qasa.screens.tools.health.HealthOverview
import alibarznji.qasa.screens.tools.health.HealthViewModel
import alibarznji.qasa.theme.QasaModalBottomSheet
import alibarznji.qasa.vault.VaultPayload
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import alibarznji.qasa.theme.LocalQasaToast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument

/** Routes of the Health page's own stack (iOS pushes inside the page's NavigationStack). */
private object HealthRoutes {
    const val OVERVIEW = "health/overview"
    const val ISSUES = "health/issues/{tab}"
    const val DETAIL = "health/detail/{itemId}"

    fun issues(tab: HealthIssueTab) = "health/issues/${tab.name}"
    fun detail(itemId: String) = "health/detail/$itemId"
}

/** The item being edited from the issues list or the detail screen. */
private data class HealthEdit(val itemId: String, val payload: VaultPayload)

/**
 * Port of iOS `PasswordHealthView` + `PasswordHealthIssuesView`.
 *
 * iOS pushes the issues screen and then `VaultItemDetailView` on the page's own
 * NavigationStack, and presents `VaultItemEditor` as a sheet from the issues screen. The
 * vault's NavHost lives inside `VaultListScreen` and the shell has no cross-page route,
 * so this page hosts the same three-level stack itself and reuses the vault's detail
 * screen and editor sheet: back from the item returns to the issues list, like iOS.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PasswordHealthScreen(
    onOpenMenu: (() -> Unit)? = null,
    onUpgrade: () -> Unit = {},
    vm: HealthViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    // The app's own toast (iOS `ToastCenter` look), not the system one.
    val qasaToast = LocalQasaToast.current
    val haptics = LocalHapticFeedback.current
    val navController = rememberNavController()
    val report by vm.report.collectAsState()
    var editing by remember { mutableStateOf<HealthEdit?>(null) }
    // Bumped when the editor closes over the detail screen so it re-reads the saved item.
    var detailVersion by remember { mutableIntStateOf(0) }

    fun closeEditor() {
        editing = null
        detailVersion++
    }

    // Same copy path as the vault list (`VaultListContent.copy`).
    fun copy(rowId: String, target: RowCopy) {
        if (target.ephemeral) {
            vm.clipboard.copy(target.label, target.value)
        } else {
            val clip = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clip.setPrimaryClip(ClipData.newPlainText(target.label, target.value))
        }
        vm.markUsed(rowId)
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        qasaToast.show(context.getString(R.string.vault_copiedfmt, context.getString(target.labelRes)))
    }

    BackHandler(enabled = editing != null) { closeEditor() }

    Box(Modifier.fillMaxSize()) {
        NavHost(
            navController = navController,
            startDestination = HealthRoutes.OVERVIEW,
            modifier = Modifier.fillMaxSize(),
        ) {
            composable(HealthRoutes.OVERVIEW) {
                HealthOverview(
                    report = report,
                    onOpenMenu = onOpenMenu,
                    onOpenIssues = { tab -> navController.navigate(HealthRoutes.issues(tab)) },
                    onUpgrade = onUpgrade,
                )
            }
            composable(
                route = HealthRoutes.ISSUES,
                arguments = listOf(navArgument("tab") { type = NavType.StringType }),
            ) { entry ->
                val tab = entry.arguments?.getString("tab")
                    ?.let { name -> HealthIssueTab.entries.firstOrNull { it.name == name } }
                    ?: HealthIssueTab.REUSED
                HealthIssuesScreen(
                    initialTab = tab,
                    report = report ?: PasswordHealthReport.empty,
                    onBack = { navController.popBackStack() },
                    onOpenItem = { id -> navController.navigate(HealthRoutes.detail(id)) },
                    onEdit = { id, payload -> editing = HealthEdit(id, payload) },
                    onCopy = ::copy,
                    onDelete = vm::delete,
                    loadPayload = vm::loadPayload,
                    onUpgrade = onUpgrade,
                )
            }
            composable(
                route = HealthRoutes.DETAIL,
                arguments = listOf(navArgument("itemId") { type = NavType.StringType }),
            ) { entry ->
                val itemId = entry.arguments?.getString("itemId").orEmpty()
                key(detailVersion) {
                    VaultItemDetailScreen(
                        itemId = itemId,
                        onBack = { navController.popBackStack() },
                        onEdit = { payload -> editing = HealthEdit(itemId, payload) },
                    )
                }
            }
        }

        editing?.let { edit ->
            QasaModalBottomSheet(onDismissRequest = ::closeEditor) {
                VaultItemEditorSheetContent(
                    initial = edit.payload,
                    existingId = edit.itemId,
                    onDone = ::closeEditor,
                )
            }
        }
    }
}
