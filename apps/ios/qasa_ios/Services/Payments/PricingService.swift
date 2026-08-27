import Foundation

@Observable
final class PricingService {
    static let shared = PricingService()

    private(set) var plans: [PricingPlanDTO] = []
    private(set) var lastError: String?

    private init() {}

    @discardableResult
    func fetchPlans() async -> [PricingPlanDTO] {
        do {
            let response: PricingResponse = try await QasaAPIClient.shared.request(
                QasaConfig.pricingURL,
                authorized: false
            )
            let sorted = response.plans.sorted { ($0.sort_order ?? 0) < ($1.sort_order ?? 0) }
            plans = sorted
            lastError = nil
            return sorted
        } catch {
            lastError = error.localizedDescription
            if plans.isEmpty {
                plans = Self.fallbackPlans
            }
            return plans
        }
    }

    func plan(id: String) -> PricingPlanDTO? {
        plans.first { $0.id == id }
    }

    static let fallbackPlans: [PricingPlanDTO] = [
        PricingPlanDTO(
            id: "premium_monthly",
            title: "Premium Monthly",
            interval: "monthly",
            amount_iqd: 10_000,
            days: 30,
            vpn_quota_gb: 100,
            active: true,
            sort_order: 1,
            features_json: .strings(["5 devices", "Breach monitoring", "VPN"])
        ),
        PricingPlanDTO(
            id: "premium_yearly",
            title: "Premium Yearly",
            interval: "yearly",
            amount_iqd: 100_000,
            days: 365,
            vpn_quota_gb: 100,
            active: true,
            sort_order: 2,
            features_json: .strings(["5 devices", "Breach monitoring", "VPN", "Save vs monthly"])
        ),
    ]
}

extension PricingPlanDTO {
    var periodLabel: String {
        interval == "yearly" ? "IQD / year" : "IQD / mo"
    }

    var displayAmount: String {
        amount_iqd.formatted(.number.grouping(.automatic))
    }
}
