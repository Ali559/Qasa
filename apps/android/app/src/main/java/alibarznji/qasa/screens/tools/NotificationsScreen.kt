package alibarznji.qasa.screens.tools

import alibarznji.qasa.R
import alibarznji.qasa.network.AppNotificationDTO
import alibarznji.qasa.notifications.NotificationService
import alibarznji.qasa.prefs.AppPreferences
import alibarznji.qasa.screens.tools.notifications.NotificationDetailSheetContent
import alibarznji.qasa.screens.tools.notifications.NotificationRow
import alibarznji.qasa.screens.tools.notifications.SwipeToDeleteRow
import alibarznji.qasa.screens.tools.notifications.formatNotificationDate
import alibarznji.qasa.screens.tools.notifications.localizedBody
import alibarznji.qasa.screens.tools.notifications.localizedTitle
import alibarznji.qasa.theme.AppMenuHeader
import alibarznji.qasa.theme.EmptyState
import alibarznji.qasa.theme.QasaDimens
import alibarznji.qasa.theme.QasaIcons
import alibarznji.qasa.theme.QasaModalBottomSheet
import alibarznji.qasa.theme.ScreenScaffold
import alibarznji.qasa.theme.SectionLabel
import alibarznji.qasa.theme.qasaColors
import alibarznji.qasa.theme.qasaText
import alibarznji.qasa.vault.VaultRepository
import alibarznji.qasa.vault.db.AppNotificationEntity
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.remember
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import alibarznji.qasa.theme.QasaIconSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class NotificationsViewModel @Inject constructor(
    private val service: NotificationService,
    private val repo: VaultRepository,
    val prefs: AppPreferences,
) : ViewModel() {
    val remote = service.items
    val remoteUnread = service.unreadCount
    val hasMore = service.hasMore
    val local = repo.observeLocalNotifications()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    var selected by mutableStateOf<AppNotificationDTO?>(null)
        private set

    /** Set when opening a remote notification fails (iOS `errorMessage`). */
    var openFailed by mutableStateOf(false)
        private set

    /** Set when Mark All Read or a delete of a server notification failed and was rolled back. */
    var actionFailed by mutableStateOf(false)
        private set

    /** iOS `.task`: fetch the list, then open `deepLinkId` when one was passed in. */
    fun load(deepLinkId: String?) {
        viewModelScope.launch {
            service.fetchAll()
            if (deepLinkId != null) {
                runCatching { service.fetchDetail(deepLinkId) }
                    .onSuccess {
                        selected = it
                        service.markRead(it.id)
                    }
                    .onFailure { openFailed = true }
            }
        }
    }

    /** The end of the "From Qasa" list scrolled into view. */
    fun loadMore() {
        viewModelScope.launch { service.loadMore() }
    }

    /** iOS `openRemote`: show the row we already have and mark it read on the server. */
    fun openRemote(note: AppNotificationDTO) {
        openFailed = false
        selected = note
        viewModelScope.launch { service.markRead(note.id) }
    }

    fun closeDetail() {
        selected = null
    }

    fun markLocalRead(id: String) {
        viewModelScope.launch { runCatching { repo.markLocalNotificationRead(id) } }
    }

    /** Both sections: local rows in Room, server rows through `read-all`. */
    fun markAllRead(includeRemote: Boolean) {
        viewModelScope.launch { runCatching { repo.markAllLocalNotificationsRead() } }
        if (includeRemote) {
            viewModelScope.launch { actionFailed = !service.markAllRead() }
        }
    }

    fun deleteRemote(id: String) {
        if (selected?.id == id) selected = null
        viewModelScope.launch { actionFailed = !service.delete(id) }
    }

    fun deleteLocal(id: String) {
        viewModelScope.launch { runCatching { repo.deleteLocalNotification(id) } }
    }
}

/**
 * Port of iOS `Views/NotificationsView.swift`: "From Qasa" (server) and "On this device"
 * (local) sections, a detail sheet for server notifications, Mark All Read and swipe to
 * delete on both sections, and paging of the server list.
 *
 * Deep link: a push tap reaches this screen through `MainActivity` →
 * `AppSession.requestOpenNotifications()` → `AppShellScreen`, like iOS `.qasaOpenNotifications`
 * → `selectPage(.notifications)`. [deepLinkId] mirrors iOS `NotificationsView.deepLinkId`
 * (opens that notification's detail); neither app's push payload carries an id today.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(
    onOpenMenu: (() -> Unit)? = null,
    deepLinkId: String? = null,
    vm: NotificationsViewModel = hiltViewModel(),
) {
    val c = qasaColors
    val remote by vm.remote.collectAsState()
    val remoteUnread by vm.remoteUnread.collectAsState()
    val hasMore by vm.hasMore.collectAsState()
    val local by vm.local.collectAsState()
    val remoteHasUnread = remoteUnread > 0 || remote.any { it.read_at == null }
    val language by vm.prefs.language.collectAsState()
    val localeCode = language.code
    val locale = LocalConfiguration.current.locales[0]
    LaunchedEffect(deepLinkId) { vm.load(deepLinkId) }

    ScreenScaffold(
        header = {
            AppMenuHeader(
                title = stringResource(R.string.notifications_title),
                onMenu = onOpenMenu,
                applyStatusBars = false,
                // iOS `markAllButton`: both sections; hidden when nothing is unread.
                trailing = if (remoteHasUnread || local.any { !it.isRead }) {
                    {
                        TextButton(onClick = { vm.markAllRead(includeRemote = remoteHasUnread) }) {
                            Text(stringResource(R.string.notifications_markallread), color = c.accent, style = qasaText.bodySmallSemibold)
                        }
                    }
                } else {
                    null
                },
            )
        },
        stickyBottom = if (vm.openFailed || vm.actionFailed) {
            {
                Text(
                    stringResource(if (vm.openFailed) R.string.notifications_openfailed else R.string.notifications_actionfailed),
                    color = c.danger,
                    style = qasaText.bodySmall,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = QasaDimens.screenPadding, vertical = QasaDimens.contentGap),
                )
            }
        } else {
            null
        },
    ) {
        if (remote.isEmpty() && local.isEmpty()) {
            EmptyState(
                title = stringResource(R.string.notifications_empty),
                hint = stringResource(R.string.notifications_emptydesc),
                icon = QasaIcons.NotificationsOff,
            )
        } else {
            if (remote.isNotEmpty()) {
                NotificationSection(stringResource(R.string.notifications_fromqasa)) {
                    remote.forEachIndexed { i, note -> RemoteRow(note, showDivider = i > 0, vm = vm, localeCode = localeCode, locale = locale) }
                    if (hasMore) LoadMoreRow(itemCount = remote.size, onVisible = vm::loadMore)
                }
            }
            if (local.isNotEmpty()) {
                if (remote.isNotEmpty()) Spacer(Modifier.height(QasaDimens.sectionGap))
                NotificationSection(stringResource(R.string.notifications_ondevice)) {
                    local.forEachIndexed { i, note -> LocalRow(note, showDivider = i > 0, vm = vm, locale = locale) }
                }
            }
        }
    }

    vm.selected?.let { note ->
        QasaModalBottomSheet(onDismissRequest = vm::closeDetail) {
            NotificationDetailSheetContent(note, localeCode, locale, onClose = vm::closeDetail)
        }
    }
}

@Composable
private fun RemoteRow(note: AppNotificationDTO, showDivider: Boolean, vm: NotificationsViewModel, localeCode: String, locale: java.util.Locale) {
    key(note.id) {
        if (showDivider) HorizontalDivider(color = qasaColors.stroke)
        SwipeToDeleteRow(onDelete = { vm.deleteRemote(note.id) }) {
            NotificationRow(
                title = note.localizedTitle(localeCode),
                body = note.localizedBody(localeCode),
                date = formatNotificationDate(note.created_at, locale),
                unread = note.read_at == null,
                onClick = { vm.openRemote(note) },
            )
        }
    }
}

/**
 * Last row of "From Qasa" while the server has older pages. The body is a plain scrolling
 * `Column`, so "scrolled to the end" is this row becoming visible (its window bounds are
 * clipped by the scroll viewport); each new page re-arms it through [itemCount].
 */
@Composable
private fun LoadMoreRow(itemCount: Int, onVisible: () -> Unit) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(visible, itemCount) { if (visible) onVisible() }
    Box(
        Modifier
            .fillMaxWidth()
            .padding(vertical = QasaDimens.gapSmall)
            .onGloballyPositioned { visible = it.boundsInWindow().height > 0f },
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(
            color = qasaColors.accent,
            strokeWidth = QasaDimens.stroke * 2,
            modifier = Modifier.size(QasaIconSize.md),
        )
    }
}

@Composable
private fun LocalRow(note: AppNotificationEntity, showDivider: Boolean, vm: NotificationsViewModel, locale: java.util.Locale) {
    // Keyed so a deleted row's swipe state is not inherited by the row that moves up.
    key(note.id) {
        if (showDivider) HorizontalDivider(color = qasaColors.stroke)
        SwipeToDeleteRow(onDelete = { vm.deleteLocal(note.id) }) {
            // iOS: tapping a local notification only marks it read (no detail sheet).
            NotificationRow(
                title = note.title,
                body = note.body,
                date = formatNotificationDate(note.createdAt, locale),
                unread = !note.isRead,
                onClick = { vm.markLocalRead(note.id) },
            )
        }
    }
}

/** iOS inset-grouped `List` `Section`: label above a rounded card of rows. */
@Composable
private fun NotificationSection(title: String, rows: @Composable ColumnScope.() -> Unit) {
    val c = qasaColors
    val shape = RoundedCornerShape(QasaDimens.cornerRadius)
    SectionLabel(title, modifier = Modifier.padding(bottom = QasaDimens.contentGap))
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .border(QasaDimens.stroke, c.stroke, shape),
        horizontalAlignment = Alignment.Start,
        content = rows,
    )
}
