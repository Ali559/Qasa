import Foundation
import UserNotifications
import UIKit

/// Posts card/bank copy UI into the OS notification tray (not an in-app overlay).
@MainActor
final class EphemeralPaymentNotifier {
    static let shared = EphemeralPaymentNotifier()

    private var expiryWork: DispatchWorkItem?
    private var expiryTimer: Timer?
    private var registeredCategories = false

    private init() {}

    func present(secrets: AutofillSecrets, copyFieldId: String? = nil) {
        let built = makePayload(secrets: secrets)
        guard let built else { return }

        if let copyFieldId,
           let field = built.fields.first(where: { $0.id == copyFieldId }) {
            EphemeralPaymentNotificationSupport.copyField(field)
        }

        Task {
            await ensureAuthorization()
            registerCategoriesIfNeeded()
            await post(title: built.title, subtitle: built.subtitle, fields: built.fields)
        }
    }

    private struct Built {
        let title: String
        let subtitle: String
        let fields: [EphemeralPaymentNotificationSupport.FieldPayload]
    }

    private func makePayload(secrets: AutofillSecrets) -> Built? {
        switch secrets {
        case .login:
            return nil
        case .card(let c):
            let title = c.itemName.isEmpty ? AutofillL10n.tr("autofill.card", "Card") : c.itemName
            let fields: [EphemeralPaymentNotificationSupport.FieldPayload] = [
                .init(id: "holder", label: AutofillL10n.tr("autofill.nameOnCard", "Name on card"), value: c.accountHolder),
                .init(id: "number", label: AutofillL10n.tr("autofill.cardNumber", "Card number"), value: c.number),
                .init(id: "cvv", label: AutofillL10n.tr("autofill.cvv", "CVV"), value: c.securityCode),
                .init(id: "expiry", label: AutofillL10n.tr("autofill.expiryDate", "Expiry date"), value: c.expiryDate)
            ].filter { !$0.value.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty }
            return Built(title: title, subtitle: maskedLast4(c.number), fields: fields)
        case .bank(let b):
            let fallback = b.bankName.isEmpty ? AutofillL10n.tr("autofill.bank", "Bank") : b.bankName
            let title = b.itemName.isEmpty ? fallback : b.itemName
            let fields: [EphemeralPaymentNotificationSupport.FieldPayload] = [
                .init(id: "account", label: AutofillL10n.tr("field.accountNumber", "Account Number"), value: b.accountNumber),
                .init(id: "routing", label: AutofillL10n.tr("field.routingNumber", "Routing Number"), value: b.routingNumber),
                .init(id: "holder", label: AutofillL10n.tr("field.accountHolder", "Account Holder"), value: b.accountHolder),
                .init(id: "bank", label: AutofillL10n.tr("field.bankName", "Bank Name"), value: b.bankName)
            ].filter { !$0.value.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty }
            return Built(title: title, subtitle: maskedLast4(b.accountNumber), fields: fields)
        }
    }

    private func ensureAuthorization() async {
        let center = UNUserNotificationCenter.current()
        let settings = await center.notificationSettings()
        switch settings.authorizationStatus {
        case .authorized, .provisional, .ephemeral:
            return
        case .notDetermined:
            _ = try? await center.requestAuthorization(options: [.alert, .sound, .badge])
        default:
            break
        }
    }

    private func registerCategoriesIfNeeded() {
        guard !registeredCategories else { return }
        registeredCategories = true
        let category = UNNotificationCategory(
            identifier: EphemeralPaymentNotificationSupport.categoryId,
            actions: [],
            intentIdentifiers: [],
            options: [.customDismissAction]
        )
        UNUserNotificationCenter.current().setNotificationCategories([category])
    }

    private func post(
        title: String,
        subtitle: String,
        fields: [EphemeralPaymentNotificationSupport.FieldPayload]
    ) async {
        let center = UNUserNotificationCenter.current()
        EphemeralPaymentNotificationSupport.removeDeliveredEphemeralNotification()

        let ttl = EphemeralPasteboardSession.ttlSeconds
        let expiresAt = Date().addingTimeInterval(ttl)
        let content = UNMutableNotificationContent()
        content.title = title
        content.subtitle = subtitle
        content.body = AutofillL10n.format(
            "autofill.expiresIn",
            "Expires in %ds",
            Int(ttl)
        )
        content.categoryIdentifier = EphemeralPaymentNotificationSupport.categoryId
        content.sound = .default
        content.userInfo = EphemeralPaymentNotificationSupport.userInfo(
            title: title,
            subtitle: subtitle,
            fields: fields,
            expiresAt: expiresAt
        )
        if #available(iOS 15.0, *) {
            content.interruptionLevel = .timeSensitive
        }

        let request = UNNotificationRequest(
            identifier: EphemeralPaymentNotificationSupport.requestId,
            content: content,
            trigger: nil
        )
        try? await center.add(request)

        scheduleExpiry(at: expiresAt)
    }

    private func scheduleExpiry(at expiresAt: Date) {
        expiryWork?.cancel()
        expiryTimer?.invalidate()
        expiryTimer = nil

        let delay = max(0.05, expiresAt.timeIntervalSinceNow)
        let work = DispatchWorkItem { [weak self] in
            Task { @MainActor in
                self?.expire()
            }
        }
        expiryWork = work
        DispatchQueue.main.asyncAfter(deadline: .now() + delay, execute: work)

        // Backup timer on the main run loop (survives some DispatchWorkItem edge cases).
        let timer = Timer(timeInterval: delay, repeats: false) { [weak self] _ in
            Task { @MainActor in
                self?.expire()
            }
        }
        RunLoop.main.add(timer, forMode: .common)
        expiryTimer = timer
    }

    func expire() {
        expiryWork?.cancel()
        expiryWork = nil
        expiryTimer?.invalidate()
        expiryTimer = nil
        EphemeralPaymentNotificationSupport.removeDeliveredEphemeralNotification()
        EphemeralPaymentNotificationSupport.clearOwnedPasteboardIfNeeded()
    }

    private func maskedLast4(_ raw: String) -> String {
        EphemeralPaymentNotificationSupport.maskedLast4(raw)
    }
}
