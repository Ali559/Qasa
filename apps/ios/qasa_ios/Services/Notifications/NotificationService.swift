import Foundation

/// The server inbox ("From Qasa"), its unread count for the badge, and paging.
///
/// Opening a notification calls `POST /v1/notifications/{id}/read` explicitly; the detail
/// GET still marks read server-side but only for older app versions. Mark All Read and
/// delete update the list optimistically and roll back when the call fails.
/// Mirrors Android `notifications/NotificationService.kt`.
@Observable
final class NotificationService {
    static let shared = NotificationService()

    /// The pages loaded so far, newest first, with local read/delete changes applied.
    private(set) var items: [AppNotificationDTO] = []
    /// Server unread count (the whole inbox, not only the loaded pages).
    private(set) var unreadCount = 0
    private(set) var hasMore = false
    private(set) var lastError: String?

    /// `next_before` of the last page loaded; nil once the end is reached.
    private var nextBefore: String?
    private var isLoadingPage = false
    private static let pageSize = 50

    private init() {}

    /// First page. A failure keeps the cached list rather than blanking the screen.
    @discardableResult
    func fetchList() async -> [AppNotificationDTO] {
        isLoadingPage = true
        defer { isLoadingPage = false }
        do {
            let response: NotificationsListResponse = try await QasaAPIClient.shared.request(
                QasaConfig.notificationsPageURL(limit: Self.pageSize, before: nil)
            )
            items = response.notifications
            if let count = response.unread_count { unreadCount = count }
            nextBefore = response.next_before
            hasMore = response.next_before != nil
            lastError = nil
            return items
        } catch {
            lastError = L10n.message(for: error)
            return items
        }
    }

    /// Appends the next page (the list's end scrolled into view). No-op on the last page.
    func loadMore() async {
        guard !isLoadingPage, let cursor = nextBefore else { return }
        isLoadingPage = true
        defer { isLoadingPage = false }
        do {
            let response: NotificationsListResponse = try await QasaAPIClient.shared.request(
                QasaConfig.notificationsPageURL(limit: Self.pageSize, before: cursor)
            )
            let known = Set(items.map(\.id))
            items.append(contentsOf: response.notifications.filter { !known.contains($0.id) })
            if let count = response.unread_count { unreadCount = count }
            nextBefore = response.next_before
            hasMore = response.next_before != nil
        } catch {
            // Keep the cursor: the next time the end appears, it retries.
        }
    }

    /// Badge refresh on foreground; silent without a license token or network.
    func refreshUnreadCount() async {
        guard VaultKeychainStore.licenseToken != nil else { return }
        if let response: NotificationsUnreadCountResponse = try? await QasaAPIClient.shared.request(
            QasaConfig.notificationsUnreadCountURL
        ) {
            unreadCount = response.unread_count
        }
    }

    /// Deep link: throws so the screen can show the error.
    func fetchDetail(id: String) async throws -> AppNotificationDTO {
        let response: NotificationDetailResponse = try await QasaAPIClient.shared.request(
            QasaConfig.notificationURL(id: id)
        )
        if let idx = items.firstIndex(where: { $0.id == id }) {
            items[idx] = response.notification
        }
        return response.notification
    }

    /// A notification was shown: mark it read now, then take the server's count.
    func markRead(id: String) async {
        let wasUnread = items.contains { $0.id == id && $0.read_at == nil }
        if wasUnread {
            setReadAt([id], Self.nowString())
            unreadCount = max(0, unreadCount - 1)
        }
        do {
            let response: NotificationsReadResponse = try await QasaAPIClient.shared.request(
                QasaConfig.notificationReadURL(id: id),
                method: "POST"
            )
            if let count = response.unread_count { unreadCount = count }
        } catch QasaAPIError.httpStatus(404, _) {
            // No longer in this inbox (deleted elsewhere): nothing to roll back to.
        } catch {
            if wasUnread {
                setReadAt([id], nil)
                unreadCount += 1
            }
        }
    }

    /// Mark All Read. Returns false (after rolling back) when the server call failed.
    @discardableResult
    func markAllRead() async -> Bool {
        let unreadIds = Set(items.filter { $0.read_at == nil }.map(\.id))
        let previousCount = unreadCount
        setReadAt(unreadIds, Self.nowString())
        unreadCount = 0
        do {
            let response: NotificationsReadResponse = try await QasaAPIClient.shared.request(
                QasaConfig.notificationsReadAllURL,
                method: "POST"
            )
            unreadCount = response.unread_count ?? 0
            return true
        } catch {
            setReadAt(unreadIds, nil)
            unreadCount = previousCount
            return false
        }
    }

    /// Swipe to delete (per-user). The row leaves `items` synchronously, as `List.onDelete`
    /// expects; `completion(false)` follows (after restoring the row) when the call failed.
    func delete(id: String, completion: @escaping (Bool) -> Void = { _ in }) {
        guard let index = items.firstIndex(where: { $0.id == id }) else {
            completion(true)
            return
        }
        let removed = items.remove(at: index)
        if removed.read_at == nil { unreadCount = max(0, unreadCount - 1) }
        Task {
            do {
                try await QasaAPIClient.shared.requestEmpty(QasaConfig.notificationURL(id: id))
                completion(true)
            } catch QasaAPIError.httpStatus(404, _) {
                // Already gone from the inbox, which is what the user asked for.
                completion(true)
            } catch {
                if !items.contains(where: { $0.id == id }) {
                    items.insert(removed, at: min(index, items.count))
                }
                if removed.read_at == nil { unreadCount += 1 }
                completion(false)
            }
        }
    }

    private func setReadAt(_ ids: Set<String>, _ readAt: String?) {
        guard !ids.isEmpty else { return }
        for i in items.indices where ids.contains(items[i].id) {
            items[i].read_at = readAt
        }
    }

    private static func nowString() -> String {
        ISO8601DateFormatter().string(from: Date())
    }
}
