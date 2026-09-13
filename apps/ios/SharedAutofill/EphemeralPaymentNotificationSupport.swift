import Foundation
import UIKit
import UserNotifications

/// Keys / helpers for ephemeral payment secret notifications (OS notification tray).
nonisolated enum EphemeralPaymentNotificationSupport: Sendable {
    nonisolated static let categoryId = "qasa.ephemeral.payment"
    nonisolated static let requestId = "qasa.ephemeral.payment.request"
    nonisolated static let userInfoFlag = "qasa.ephemeral"
    nonisolated static let userInfoTitle = "qasa.title"
    nonisolated static let userInfoSubtitle = "qasa.subtitle"
    nonisolated static let userInfoFields = "qasa.fields"
    nonisolated static let userInfoExpiresAt = "qasa.expiresAt"
    nonisolated static let pasteboardChangeCountKey = "ephemeral.pasteboardChangeCount"

    struct FieldPayload: Codable, Equatable, Sendable {
        let id: String
        let label: String
        let value: String
    }

    nonisolated static func userInfo(
        title: String,
        subtitle: String,
        fields: [FieldPayload],
        expiresAt: Date
    ) -> [AnyHashable: Any] {
        let encoded: String
        if let data = try? JSONEncoder().encode(fields),
           let string = String(data: data, encoding: .utf8) {
            encoded = string
        } else {
            encoded = "[]"
        }
        return [
            userInfoFlag: true,
            userInfoTitle: title,
            userInfoSubtitle: subtitle,
            userInfoFields: encoded,
            userInfoExpiresAt: expiresAt.timeIntervalSince1970
        ]
    }

    nonisolated static func fields(from userInfo: [AnyHashable: Any]) -> [FieldPayload] {
        guard let raw = userInfo[userInfoFields] as? String,
              let data = raw.data(using: .utf8),
              let decoded = try? JSONDecoder().decode([FieldPayload].self, from: data) else {
            return []
        }
        return decoded
    }

    nonisolated static func title(from userInfo: [AnyHashable: Any]) -> String {
        userInfo[userInfoTitle] as? String ?? ""
    }

    nonisolated static func subtitle(from userInfo: [AnyHashable: Any]) -> String {
        userInfo[userInfoSubtitle] as? String ?? ""
    }

    nonisolated static func expiresAt(from userInfo: [AnyHashable: Any]) -> Date? {
        guard let raw = userInfo[userInfoExpiresAt] as? TimeInterval else { return nil }
        return Date(timeIntervalSince1970: raw)
    }

    nonisolated static func isEphemeral(_ userInfo: [AnyHashable: Any]) -> Bool {
        if let flag = userInfo[userInfoFlag] as? Bool { return flag }
        if let flag = userInfo[userInfoFlag] as? NSNumber { return flag.boolValue }
        return false
    }

    @MainActor
    static func markOwnedPasteboard() {
        let defaults = UserDefaults(suiteName: AppGroup.identifier)
        defaults?.set(UIPasteboard.general.changeCount, forKey: pasteboardChangeCountKey)
    }

    @MainActor
    static func clearOwnedPasteboardIfNeeded() {
        let defaults = UserDefaults(suiteName: AppGroup.identifier)
        let owned = defaults?.integer(forKey: pasteboardChangeCountKey) ?? 0
        guard owned > 0 else { return }
        if UIPasteboard.general.changeCount == owned {
            UIPasteboard.general.string = ""
        }
        defaults?.removeObject(forKey: pasteboardChangeCountKey)
    }

    @MainActor
    static func copyField(_ field: FieldPayload) {
        UIPasteboard.general.string = field.value
        markOwnedPasteboard()
        UIImpactFeedbackGenerator(style: .light).impactOccurred()
    }

    /// Display-only masking. Copy still uses the full `value`.
    nonisolated static func displayValue(for field: FieldPayload) -> String {
        switch field.id {
        case "holder", "expiry", "bank":
            return field.value
        case "number", "account":
            return maskedLast4(field.value)
        default:
            // CVV, routing, and any other secret — password-style bullets.
            let count = max(field.value.count, 3)
            return String(repeating: "•", count: min(count, 8))
        }
    }

    nonisolated static func maskedLast4(_ raw: String) -> String {
        let digits = raw.filter(\.isNumber)
        let last4 = String(digits.suffix(4))
        return last4.isEmpty ? String(repeating: "•", count: 4) : "•••• \(last4)"
    }

    /// Removes the ephemeral payment notification from Notification Center (app + content extension).
    nonisolated static func removeDeliveredEphemeralNotification() {
        let center = UNUserNotificationCenter.current()
        center.removePendingNotificationRequests(withIdentifiers: [requestId])
        center.removeDeliveredNotifications(withIdentifiers: [requestId])
    }
}
