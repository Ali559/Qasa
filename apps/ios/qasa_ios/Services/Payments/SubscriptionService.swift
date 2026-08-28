import Foundation

enum SubscriptionServiceError: LocalizedError {
    case notFound

    var errorDescription: String? {
        switch self {
        case .notFound: return "No subscription found."
        }
    }
}

enum SubscriptionPlan: String, CaseIterable, Identifiable {
    case monthly = "premium_monthly"
    case yearly = "premium_yearly"

    var id: String { rawValue }

    var title: String {
        switch self {
        case .monthly: return "Monthly"
        case .yearly: return "Yearly"
        }
    }

    var amountIQD: Int {
        switch self {
        case .monthly: return 10_000
        case .yearly: return 100_000
        }
    }

    var periodLabel: String {
        switch self {
        case .monthly: return "IQD / mo"
        case .yearly: return "IQD / year"
        }
    }

    var savingsHint: String? {
        switch self {
        case .monthly: return nil
        case .yearly: return "Save 2 months vs monthly"
        }
    }

    var displayName: String {
        switch self {
        case .monthly: return L10n.tr("sub.planMonthly")
        case .yearly: return L10n.tr("sub.planYearly")
        }
    }

    static func from(planId: String) -> SubscriptionPlan {
        planId == yearly.rawValue ? .yearly : .monthly
    }
}

@Observable
final class SubscriptionService {
    static let shared = SubscriptionService()

    private(set) var current: SubscriptionDTO?
    private(set) var lastError: String?
    private(set) var isLoading = false

    private init() {}

    @discardableResult
    func fetchCurrent() async throws -> SubscriptionDTO? {
        isLoading = true
        defer { isLoading = false }
        let response: SubscriptionResponse = try await QasaAPIClient.shared.request(
            QasaConfig.subscriptionURL
        )
        current = response.subscription
        lastError = nil
        return response.subscription
    }

    @discardableResult
    func setAutoRenew(_ enabled: Bool, subscriptionId: String? = nil) async throws -> SubscriptionDTO {
        let body = SetAutoRenewRequest(
            auto_renew: enabled,
            subscription_id: subscriptionId ?? current?.id
        )
        let response: SubscriptionResponse = try await QasaAPIClient.shared.request(
            QasaConfig.subscriptionAutoRenewURL,
            method: "PATCH",
            body: body
        )
        guard let sub = response.subscription else {
            throw SubscriptionServiceError.notFound
        }
        current = sub
        lastError = nil
        return sub
    }

    @discardableResult
    func cancel(reason: String?, subscriptionId: String? = nil) async throws -> SubscriptionDTO {
        let trimmed = reason?.trimmingCharacters(in: .whitespacesAndNewlines)
        let body = CancelSubscriptionRequest(
            reason: (trimmed?.isEmpty == false) ? trimmed : nil,
            subscription_id: subscriptionId ?? current?.id
        )
        let response: SubscriptionResponse = try await QasaAPIClient.shared.request(
            QasaConfig.subscriptionCancelURL,
            method: "POST",
            body: body
        )
        guard let sub = response.subscription else {
            throw SubscriptionServiceError.notFound
        }
        current = sub
        lastError = nil
        return sub
    }
}
