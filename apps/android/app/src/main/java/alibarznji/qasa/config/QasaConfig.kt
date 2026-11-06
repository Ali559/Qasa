package alibarznji.qasa.config

import alibarznji.qasa.BuildConfig

object QasaConfig {
    val apiBaseUrl: String
        get() = BuildConfig.QASA_API_URL.trimEnd('/')

    /**
     * Mirrors iOS `QasaConfig.isBaseURLValid`: a release build must carry an explicit
     * `https` API URL, or it reaches nothing (and would send its bearer token over
     * cleartext). Debug builds talk to a local `http` server and are always valid.
     */
    val isBaseUrlValid: Boolean
        get() {
            if (BuildConfig.DEBUG) return true
            val scheme = runCatching { java.net.URI(BuildConfig.QASA_API_URL).scheme }.getOrNull()
            return scheme?.lowercase() == "https"
        }

    /** The host shown in the misconfigured-API banner, or null when the URL is fine (iOS `AppSession.configurationError`). */
    val configurationError: String?
        get() = if (isBaseUrlValid) null else apiBaseUrl

    val registerUrl get() = "$apiBaseUrl/v1/devices/register"
    val devicesLinkCheckUrl get() = "$apiBaseUrl/v1/devices/link-check"
    val devicesUrl get() = "$apiBaseUrl/v1/devices"
    val devicesOthersUrl get() = "$apiBaseUrl/v1/devices/others"
    val licenseRefreshUrl get() = "$apiBaseUrl/v1/license"
    val licenseKeyUrl get() = "$apiBaseUrl/.well-known/qasa-license-key"
    val paymentsUrl get() = "$apiBaseUrl/v1/payments/intents"
    fun paymentUrl(id: String) = "$apiBaseUrl/v1/payments/$id"
    fun paymentWebhookUrl(gateway: String) = "$apiBaseUrl/v1/payments/webhook/$gateway"
    val subscriptionUrl get() = "$apiBaseUrl/v1/subscription"
    val subscriptionAutoRenewUrl get() = "$apiBaseUrl/v1/subscription/auto-renew"
    val paymentMethodUrl get() = "$apiBaseUrl/v1/payment-method"
    val subscriptionCancelUrl get() = "$apiBaseUrl/v1/subscription/cancel"
    val pricingUrl get() = "$apiBaseUrl/v1/pricing"
    val notificationsUrl get() = "$apiBaseUrl/v1/notifications"
    fun notificationUrl(id: String) = "$apiBaseUrl/v1/notifications/$id"
    /** One inbox page; [before] is the previous page's `next_before` (RFC 3339). */
    fun notificationsPageUrl(limit: Int, before: String?): String =
        "$apiBaseUrl/v1/notifications?limit=$limit" +
            (before?.let { "&before=" + java.net.URLEncoder.encode(it, "UTF-8") } ?: "")
    val notificationsUnreadCountUrl get() = "$apiBaseUrl/v1/notifications/unread-count"
    val notificationsReadAllUrl get() = "$apiBaseUrl/v1/notifications/read-all"
    fun notificationReadUrl(id: String) = "$apiBaseUrl/v1/notifications/$id/read"
    val devicePrefsUrl get() = "$apiBaseUrl/v1/devices/prefs"
    val breachMonitorUrl get() = "$apiBaseUrl/v1/breach/monitor"
    fun breachPrefixUrl(prefix: String) = "$apiBaseUrl/v1/breach/prefix/$prefix"
    val vpnConnectUrl get() = "$apiBaseUrl/v1/vpn/connect"
    val vpnStatusUrl get() = "$apiBaseUrl/v1/vpn/status"
    val vpnDisconnectUrl get() = "$apiBaseUrl/v1/vpn/disconnect"
    val vpnNodesUrl get() = "$apiBaseUrl/v1/vpn/nodes"
    val healthUrl get() = "$apiBaseUrl/health"
}
