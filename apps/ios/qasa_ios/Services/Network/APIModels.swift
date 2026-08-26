import Foundation

struct RemoteDevice: Codable, Identifiable, Hashable {
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
