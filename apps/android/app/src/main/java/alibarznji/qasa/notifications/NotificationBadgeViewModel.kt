package alibarznji.qasa.notifications

import alibarznji.qasa.vault.VaultRepository
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * The Home bell's badge: the server's `unread_count` (whole inbox, not only loaded pages)
 * plus unread "On this device" notifications. Same sum on iOS `NotificationService.badgeCount`.
 */
@HiltViewModel
class NotificationBadgeViewModel @Inject constructor(
    private val service: NotificationService,
    repo: VaultRepository,
) : ViewModel() {
    val count: StateFlow<Int> = combine(
        service.unreadCount,
        repo.observeLocalNotifications(),
    ) { remote, local -> remote + local.count { !it.isRead } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    fun refresh() {
        viewModelScope.launch { service.refreshUnreadCount() }
    }
}
