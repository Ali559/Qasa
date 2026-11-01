import Foundation

@Observable
final class NotificationService {
    static let shared = NotificationService()

    private(set) var items: [AppNotificationDTO] = []
    private(set) var lastError: String?

    private init() {}

    @discardableResult
    func fetchList() async -> [AppNotificationDTO] {
        do {
            let response: NotificationsListResponse = try await QasaAPIClient.shared.request(
                QasaConfig.notificationsURL
            )
            items = response.notifications
            lastError = nil
            return items
        } catch {
            lastError = error.localizedDescription
            return items
        }
    }

    func fetchDetail(id: String) async throws -> AppNotificationDTO {
        let response: NotificationDetailResponse = try await QasaAPIClient.shared.request(
            QasaConfig.notificationURL(id: id)
        )
        if let idx = items.firstIndex(where: { $0.id == id }) {
            items[idx] = response.notification
        }
        return response.notification
    }
}
