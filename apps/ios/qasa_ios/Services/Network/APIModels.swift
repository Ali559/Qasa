import Foundation

nonisolated struct RemoteDevice: Codable, Identifiable, Hashable, Sendable {
    let id: String
    let device_name: String
    let platform: String
}

struct RegisterDeviceRequest: Encodable {
    let account_id: String
    let device_fingerprint: String
    let device_name: String
    let platform: String
}

struct RegisterDeviceResponse: Decodable {
    let device_id: String
    let license_token: String
    let devices_used: Int
    let device_limit: Int
    let tier: String
}

struct DeviceListResponse: Decodable {
    let devices: [RemoteDevice]
}

struct LicenseRefreshResponse: Decodable {
    let license_token: String
    let tier: String
    let device_limit: Int
}

struct PaymentIntentRequest: Encodable {
    let gateway: String
    let plan_id: String
    let auto_renew: Bool
    let amount_iqd: Int?
    let idempotency_key: String?
}

struct PaymentIntentResponse: Decodable {
    let payment_id: String
    let gateway: String
    let gateway_order_id: String
    let deep_link: String
    let amount_iqd: Int
    let plan_id: String?
    let auto_renew: Bool?
    let status: String
}

struct PaymentStatusResponse: Decodable {
    let payment_id: String
    let status: String
    let gateway: String
    let gateway_order_id: String
    let amount_iqd: Int
    let completed_at: String?
}

struct SubscriptionDTO: Decodable, Identifiable {
    let id: String
    let status: String
    let plan_id: String
    let amount_iqd: Int
    let auto_renew: Bool
    let started_at: String
    let expires_at: String
    let cancelled_at: String?
    let cancellation_reason: String?
}

struct SubscriptionResponse: Decodable {
    let subscription: SubscriptionDTO?
}

struct SetAutoRenewRequest: Encodable {
    let auto_renew: Bool
    let subscription_id: String?
}

struct CancelSubscriptionRequest: Encodable {
    let reason: String?
    let subscription_id: String?
}

struct PricingPlanDTO: Decodable, Identifiable, Hashable {
    let id: String
    let title: String
    let interval: String
    let amount_iqd: Int
    let days: Int
    let vpn_quota_gb: Int?
    let active: Bool?
    let sort_order: Int?
    let features_json: FeaturesJSON?

    var featureList: [String] {
        features_json?.values ?? []
    }
}

enum FeaturesJSON: Decodable, Hashable {
    case strings([String])
    case empty

    init(from decoder: Decoder) throws {
        let container = try decoder.singleValueContainer()
        if let arr = try? container.decode([String].self) {
            self = .strings(arr)
        } else {
            self = .empty
        }
    }

    var values: [String] {
        switch self {
        case .strings(let v): return v
        case .empty: return []
        }
    }
}

struct PricingResponse: Decodable {
    let plans: [PricingPlanDTO]
}

struct AppNotificationDTO: Decodable, Identifiable {
    let id: String
    let title_en: String
    let title_ckb: String?
    let title_ar: String?
    let body_en: String
    let body_ckb: String?
    let body_ar: String?
    let created_at: String
    let read_at: String?

    func title(locale: String) -> String {
        switch locale {
        case "ckb": return title_ckb ?? title_en
        case "ar": return title_ar ?? title_en
        default: return title_en
        }
    }

    func body(locale: String) -> String {
        switch locale {
        case "ckb": return body_ckb ?? body_en
        case "ar": return body_ar ?? body_en
        default: return body_en
        }
    }
}

struct NotificationsListResponse: Decodable {
    let notifications: [AppNotificationDTO]
}

struct NotificationDetailResponse: Decodable {
    let notification: AppNotificationDTO
}

struct DevicePrefsRequest: Encodable {
    let locale: String?
    /// FCM registration token (iOS via Firebase Messaging; Android same field later).
    let push_token: String?
    let notifications_enabled: Bool?
}

struct BreachPrefixResponse: Decodable {
    let prefix: String
    let count: Int
    let suffixes: [BreachSuffixMatch]
}

struct BreachSuffixMatch: Decodable {
    let suffix: String
    let source: String
}

struct VPNConnectRequest: Encodable {
    let client_public_key: String?
}

struct VPNConnectResponse: Decodable {
    let config: VPNWireGuardConfig?
    let reused: Bool?
}

struct VPNWireGuardConfig: Decodable {
    let session_id: String?
    let endpoint: String?
    let assigned_ip: String?
}

struct VPNStatusResponse: Decodable {
    let connected: Bool
    let session_id: String?
    let node: String?
    let city: String?
    let assigned_ip: String?
    let connected_at: String?
}

struct VPNDisconnectResponse: Decodable {
    let disconnected: Bool
    let session_id: String?
}
