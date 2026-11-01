package alibarznji.qasa.notifications

import alibarznji.qasa.config.QasaConfig
import alibarznji.qasa.network.AppNotificationDTO
import alibarznji.qasa.network.NotificationDetailResponse
import alibarznji.qasa.network.NotificationsListResponse
import alibarznji.qasa.network.NotificationsReadResponse
import alibarznji.qasa.network.NotificationsUnreadCountResponse
import alibarznji.qasa.network.QasaApiClient
import alibarznji.qasa.network.QasaApiException
import alibarznji.qasa.network.get
import alibarznji.qasa.network.post
import alibarznji.qasa.vault.VaultSecureStore
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Port of iOS `Services/Notifications/NotificationService.swift`: the server inbox
 * ("From Qasa"), its unread count for the badge, and paging.
 *
 * Opening a notification calls `POST /v1/notifications/{id}/read` explicitly; the
 * detail GET still marks read server-side but only for older app versions. Mark all
 * read and delete update the list optimistically and roll back when the call fails.
 */
@Singleton
class NotificationService @Inject constructor(
    private val api: QasaApiClient,
    private val store: VaultSecureStore,
) {
    private val _items = MutableStateFlow<List<AppNotificationDTO>>(emptyList())

    /** The pages loaded so far, newest first, with local read/delete changes applied. */
    val items: StateFlow<List<AppNotificationDTO>> = _items.asStateFlow()

    private val _unreadCount = MutableStateFlow(0)

    /** Server unread count (the whole inbox, not only the loaded pages). */
    val unreadCount: StateFlow<Int> = _unreadCount.asStateFlow()

    private val _hasMore = MutableStateFlow(false)
    val hasMore: StateFlow<Boolean> = _hasMore.asStateFlow()

    /** `next_before` of the last page loaded; null once the end is reached. */
    @Volatile private var nextBefore: String? = null
    private val pageLock = Mutex()

    /** iOS `fetchList`: first page. A failure keeps the cached list rather than blanking the screen. */
    suspend fun fetchAll(): List<AppNotificationDTO> = withContext(Dispatchers.IO) {
        pageLock.withLock {
            try {
                val page = api.get<NotificationsListResponse>(QasaConfig.notificationsPageUrl(PAGE_SIZE, null))
                _items.value = page.notifications
                page.unread_count?.let { _unreadCount.value = it }
                nextBefore = page.next_before
                _hasMore.value = page.next_before != null
            } catch (_: Exception) {
            }
            _items.value
        }
    }

    /** Appends the next page (the list's end scrolled into view). No-op on the last page. */
    suspend fun loadMore() = withContext(Dispatchers.IO) {
        if (pageLock.isLocked) return@withContext
        pageLock.withLock {
            val cursor = nextBefore ?: return@withLock
            try {
                val page = api.get<NotificationsListResponse>(QasaConfig.notificationsPageUrl(PAGE_SIZE, cursor))
                _items.update { list ->
                    val known = list.mapTo(HashSet()) { it.id }
                    list + page.notifications.filter { it.id !in known }
                }
                page.unread_count?.let { _unreadCount.value = it }
                nextBefore = page.next_before
                _hasMore.value = page.next_before != null
            } catch (_: Exception) {
                // Keep the cursor: the next scroll to the end retries.
            }
        }
    }

    /** Badge refresh on foreground; silent without a license token or network. */
    suspend fun refreshUnreadCount() = withContext(Dispatchers.IO) {
        if (store.licenseToken == null) return@withContext
        runCatching { api.get<NotificationsUnreadCountResponse>(QasaConfig.notificationsUnreadCountUrl) }
            .onSuccess { _unreadCount.value = it.unread_count }
    }

    /** iOS `fetchDetail` (deep link): throws so the screen can show the error. */
    suspend fun fetchDetail(id: String): AppNotificationDTO = withContext(Dispatchers.IO) {
        val detail = api.get<NotificationDetailResponse>(QasaConfig.notificationUrl(id)).notification
        _items.update { list -> list.map { if (it.id == id) detail else it } }
        detail
    }

    /** A notification was shown: mark it read now, then take the server's count. */
    suspend fun markRead(id: String) = withContext(Dispatchers.IO) {
        val wasUnread = _items.value.any { it.id == id && it.read_at == null }
        if (wasUnread) {
            setReadAt(setOf(id), Instant.now().toString())
            _unreadCount.update { (it - 1).coerceAtLeast(0) }
        }
        try {
            val res = api.post<NotificationsReadResponse, Map<String, String>>(QasaConfig.notificationReadUrl(id), emptyMap())
            res.unread_count?.let { _unreadCount.value = it }
        } catch (e: QasaApiException.HttpStatus) {
            // 404: no longer in this inbox (deleted elsewhere) — nothing to roll back to.
            if (e.code != 404 && wasUnread) rollBackRead(setOf(id), 1)
        } catch (_: Exception) {
            if (wasUnread) rollBackRead(setOf(id), 1)
        }
    }

    /** Mark All Read. Returns false (after rolling back) when the server call failed. */
    suspend fun markAllRead(): Boolean = withContext(Dispatchers.IO) {
        val unreadIds = _items.value.filter { it.read_at == null }.mapTo(HashSet()) { it.id }
        val previousCount = _unreadCount.value
        setReadAt(unreadIds, Instant.now().toString())
        _unreadCount.value = 0
        try {
            val res = api.post<NotificationsReadResponse, Map<String, String>>(QasaConfig.notificationsReadAllUrl, emptyMap())
            _unreadCount.value = res.unread_count ?: 0
            true
        } catch (_: Exception) {
            setReadAt(unreadIds, null)
            _unreadCount.value = previousCount
            false
        }
    }

    /** Swipe to delete (per-user). Returns false (after restoring the row) when it failed. */
    suspend fun delete(id: String): Boolean = withContext(Dispatchers.IO) {
        val before = _items.value
        val index = before.indexOfFirst { it.id == id }
        if (index < 0) return@withContext true
        val removed = before[index]
        _items.update { list -> list.filterNot { it.id == id } }
        if (removed.read_at == null) _unreadCount.update { (it - 1).coerceAtLeast(0) }
        try {
            api.deleteEmpty(QasaConfig.notificationUrl(id))
            true
        } catch (e: Exception) {
            // 404: already gone from the inbox, which is what the user asked for.
            if (e is QasaApiException.HttpStatus && e.code == 404) return@withContext true
            _items.update { list ->
                if (list.any { it.id == id }) list
                else list.toMutableList().apply { add(index.coerceAtMost(size), removed) }
            }
            if (removed.read_at == null) _unreadCount.update { it + 1 }
            false
        }
    }

    private fun setReadAt(ids: Set<String>, readAt: String?) {
        if (ids.isEmpty()) return
        _items.update { list -> list.map { if (it.id in ids) it.copy(read_at = readAt) else it } }
    }

    private fun rollBackRead(ids: Set<String>, count: Int) {
        setReadAt(ids, null)
        _unreadCount.update { it + count }
    }

    private companion object {
        const val PAGE_SIZE = 50
    }
}
