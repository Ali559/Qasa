import Foundation
import UIKit

@Observable
final class PaymentService {
    static let shared = PaymentService()

    private(set) var isProcessing = false
    private(set) var lastError: String?
    private(set) var pendingPaymentId: String?

    private init() {}

    func createIntent(
        gateway: String,
        plan: SubscriptionPlan,
        autoRenew: Bool
    ) async throws -> PaymentIntentResponse {
        isProcessing = true
        defer { isProcessing = false }
        let body = PaymentIntentRequest(
            gateway: gateway,
            plan_id: plan.rawValue,
            auto_renew: autoRenew,
            amount_iqd: plan.amountIQD,
            idempotency_key: UUID().uuidString.lowercased()
        )
        let response: PaymentIntentResponse = try await QasaAPIClient.shared.request(
            QasaConfig.paymentsURL,
            method: "POST",
            body: body
        )
        pendingPaymentId = response.payment_id
        lastError = nil
        return response
    }

    /// Returns whether the OS handed off to another app (wallet).
    @MainActor
    func openDeepLink(_ deepLink: String) async -> Bool {
        guard let url = URL(string: deepLink) else { return false }
        return await UIApplication.shared.open(url)
    }

    /// Completes a stub gateway payment by posting the success webhook (dev / Simulator).
    func simulateStubPayment(gateway: String, gatewayOrderId: String) async throws {
        struct Body: Encodable {
            let order_id: String
            let status: String
        }
        _ = try await QasaAPIClient.shared.rawRequest(
            QasaConfig.paymentWebhookURL(gateway: gateway),
            method: "POST",
            body: Body(order_id: gatewayOrderId, status: "success"),
            authorized: false
        )
    }

    func pollUntilComplete(paymentId: String, maxAttempts: Int = 30, intervalSeconds: UInt64 = 2) async -> Bool {
        for _ in 0..<maxAttempts {
            do {
                let status: PaymentStatusResponse = try await QasaAPIClient.shared.request(
                    QasaConfig.paymentURL(id: paymentId)
                )
                switch status.status.lowercased() {
                case "success":
                    pendingPaymentId = nil
                    _ = await LicenseManager.shared.refresh()
                    return true
                case "failed":
                    lastError = "Payment failed."
                    pendingPaymentId = nil
                    return false
                default:
                    break
                }
            } catch {
                lastError = error.localizedDescription
            }
            try? await Task.sleep(nanoseconds: intervalSeconds * 1_000_000_000)
        }
        lastError = "Payment timed out. Check your wallet and try again."
        return false
    }
}
