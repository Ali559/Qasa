import Foundation

enum QasaConfig {
    /// Last-resort fallback. Release builds set `QASA_API_URL` explicitly; see
    /// `project.pbxproj`. A Release build left pointing at localhost cannot reach
    /// anything, so a misconfigured build surfaces as a clear error rather than a
    /// silent force-unwrap crash at first use.
    private static let defaultBaseURL = "http://localhost:8080"

    /// Sentinel used when the Info.plist value is missing or unparseable. Requests
    /// against it fail immediately instead of hanging against a dead host.
    static let misconfiguredBaseURL = URL(string: "qasa-misconfigured://invalid")!

    static var baseURLString: String {
        if let raw = Bundle.main.object(forInfoDictionaryKey: "QASA_API_URL") as? String,
           !raw.isEmpty,
           let url = URL(string: raw),
           url.scheme != nil {
            return raw
        }
        return defaultBaseURL
    }

    /// A Release build must carry an explicit `https` `QASA_API_URL`. Shipping without
    /// one leaves the app unable to reach the API at all — and, worse, free to send its
    /// bearer token and vault metadata over cleartext if pointed at an `http` host.
    static var isBaseURLValid: Bool {
        #if DEBUG
        return true
        #else
        guard let raw = Bundle.main.object(forInfoDictionaryKey: "QASA_API_URL") as? String,
              let url = URL(string: raw),
              let scheme = url.scheme?.lowercased() else { return false }
        return scheme == "https"
        #endif
    }

    static var apiBaseURL: URL {
        URL(string: baseURLString) ?? misconfiguredBaseURL
    }

    static var registerURL: URL { apiBaseURL.appending(path: "/v1/devices/register") }
    static var devicesLinkCheckURL: URL { apiBaseURL.appending(path: "/v1/devices/link-check") }
    static var devicesURL: URL { apiBaseURL.appending(path: "/v1/devices") }
    static var devicesOthersURL: URL { apiBaseURL.appending(path: "/v1/devices/others") }
    static var licenseRefreshURL: URL { apiBaseURL.appending(path: "/v1/license") }
    static var licenseKeyURL: URL { apiBaseURL.appending(path: "/.well-known/qasa-license-key") }
    static var paymentsURL: URL { apiBaseURL.appending(path: "/v1/payments/intents") }
    static func paymentURL(id: String) -> URL { apiBaseURL.appending(path: "/v1/payments/\(id)") }
    static func paymentWebhookURL(gateway: String) -> URL {
        apiBaseURL.appending(path: "/v1/payments/webhook/\(gateway)")
    }
    static var subscriptionURL: URL { apiBaseURL.appending(path: "/v1/subscription") }
    static var subscriptionAutoRenewURL: URL { apiBaseURL.appending(path: "/v1/subscription/auto-renew") }
    static var subscriptionCancelURL: URL { apiBaseURL.appending(path: "/v1/subscription/cancel") }
    static var pricingURL: URL { apiBaseURL.appending(path: "/v1/pricing") }
    static var notificationsURL: URL { apiBaseURL.appending(path: "/v1/notifications") }
    static func notificationURL(id: String) -> URL {
        apiBaseURL.appending(path: "/v1/notifications/\(id)")
    }
    static var devicePrefsURL: URL { apiBaseURL.appending(path: "/v1/devices/prefs") }
    static var breachMonitorURL: URL { apiBaseURL.appending(path: "/v1/breach/monitor") }
    static func breachPrefixURL(_ prefix: String) -> URL {
        apiBaseURL.appending(path: "/v1/breach/prefix/\(prefix)")
    }
    static var vpnConnectURL: URL { apiBaseURL.appending(path: "/v1/vpn/connect") }
    static var vpnStatusURL: URL { apiBaseURL.appending(path: "/v1/vpn/status") }
    static var vpnDisconnectURL: URL { apiBaseURL.appending(path: "/v1/vpn/disconnect") }
}
