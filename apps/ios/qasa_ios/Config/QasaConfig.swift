import Foundation

enum QasaConfig {
    private static let defaultBaseURL = "http://localhost:8080"

    static var apiBaseURL: URL {
        if let raw = Bundle.main.object(forInfoDictionaryKey: "QASA_API_URL") as? String,
           let url = URL(string: raw), !raw.isEmpty {
            return url
        }
        return URL(string: defaultBaseURL)!
    }

    static var registerURL: URL { apiBaseURL.appending(path: "/v1/devices/register") }
    static var devicesURL: URL { apiBaseURL.appending(path: "/v1/devices") }
    static var licenseRefreshURL: URL { apiBaseURL.appending(path: "/v1/license") }
    static var paymentsURL: URL { apiBaseURL.appending(path: "/v1/payments/intents") }
    static func paymentURL(id: String) -> URL { apiBaseURL.appending(path: "/v1/payments/\(id)") }
    static func paymentWebhookURL(gateway: String) -> URL {
        apiBaseURL.appending(path: "/v1/payments/webhook/\(gateway)")
    }
    static var subscriptionURL: URL { apiBaseURL.appending(path: "/v1/subscription") }
    static var subscriptionAutoRenewURL: URL { apiBaseURL.appending(path: "/v1/subscription/auto-renew") }
    static var subscriptionCancelURL: URL { apiBaseURL.appending(path: "/v1/subscription/cancel") }
    static func breachPrefixURL(_ prefix: String) -> URL {
        apiBaseURL.appending(path: "/v1/breach/prefix/\(prefix)")
    }
    static var vpnConnectURL: URL { apiBaseURL.appending(path: "/v1/vpn/connect") }
    static var vpnStatusURL: URL { apiBaseURL.appending(path: "/v1/vpn/status") }
    static var vpnDisconnectURL: URL { apiBaseURL.appending(path: "/v1/vpn/disconnect") }
}
