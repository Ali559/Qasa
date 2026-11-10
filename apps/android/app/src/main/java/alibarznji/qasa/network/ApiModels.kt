package alibarznji.qasa.network

import kotlinx.serialization.Serializable

@Serializable
data class RemoteDevice(
    val id: String,
    val device_name: String,
    val platform: String,
    val location: String? = null,
    val created_at: String? = null,
    val last_seen_at: String? = null,
    val is_primary: Boolean? = null,
)

@Serializable
data class RegisterDeviceRequest(
    val account_id: String,
    val device_fingerprint: String,
    val device_name: String,
    val platform: String,
    val location: String? = null,
    /** Base64 of the raw 32-byte key from a restored backup; omitted when none. */
    val restore_key: String? = null,
    /** With [restore_key]: unlink the device that made the backup and take its slot. */
    val replace_device: Boolean? = null,
)

/** The device a `replace_device` registration unlinked. */
@Serializable
data class ReplacedDevice(val id: String, val device_name: String)

@Serializable
data class RegisterDeviceResponse(
    val device_id: String,
    val license_token: String,
    val devices_used: Int,
    val device_limit: Int,
    val tier: String,
    val replaced_device: ReplacedDevice? = null,
)

@Serializable
data class DeviceLinkCheckRequest(val account_id: String)

@Serializable
data class DeviceLinkCheckResponse(
    val eligible: Boolean,
    val tier: String,
    val devices_used: Int,
    val device_limit: Int,
    val error: String? = null,
)

@Serializable
data class DeviceListResponse(val devices: List<RemoteDevice>)

@Serializable
data class LicenseRefreshResponse(
    val license_token: String,
    val tier: String,
    val device_limit: Int,
)

/** `GET /.well-known/qasa-license-key` — the Ed25519 verifying key. */
@Serializable
data class WellKnownLicenseKey(
    val algorithm: String,
    val public_key: String,
)

@Serializable
data class PaymentIntentRequest(
    val gateway: String,
    val plan_id: String,
    val auto_renew: Boolean,
    val amount_iqd: Int? = null,
    val idempotency_key: String? = null,
)

@Serializable
data class PaymentIntentResponse(
    val payment_id: String,
    val gateway: String,
    val gateway_order_id: String,
    val deep_link: String,
    val amount_iqd: Int,
    val plan_id: String? = null,
    val auto_renew: Boolean? = null,
    val status: String,
    /** `checkout` or `renewal`; older servers omit it. */
    val kind: String? = null,
)

@Serializable
data class PaymentStatusResponse(
    val payment_id: String,
    val status: String,
    val gateway: String,
    val gateway_order_id: String,
    val amount_iqd: Int,
    val completed_at: String? = null,
    val kind: String? = null,
)

@Serializable
data class SubscriptionDTO(
    val id: String,
    val status: String,
    val plan_id: String,
    val amount_iqd: Int,
    val auto_renew: Boolean,
    val started_at: String,
    val expires_at: String,
    val cancelled_at: String? = null,
    val cancellation_reason: String? = null,
    /** Null on servers without automatic renewals. */
    val renewal: RenewalDTO? = null,
)

/**
 * How the next period gets paid. [mode] is `charge` (the saved wallet in [gateway] is
 * charged), `link` (a payment link is sent a few days before `expires_at`) or `none`;
 * older servers omit it and only [will_renew] says whether a saved wallet is charged.
 * [link] is set once a renewal link is out, and stays payable even if auto-renew was
 * turned off since.
 */
@Serializable
data class RenewalDTO(
    val will_renew: Boolean,
    val gateway: String? = null,
    val amount_iqd: Int? = null,
    val mode: String? = null,
    val link: RenewalLinkDTO? = null,
)

/** An unpaid renewal link; confirmed like a checkout by polling [payment_id]. */
@Serializable
data class RenewalLinkDTO(
    val payment_id: String,
    val url: String,
    val amount_iqd: Int,
    val pay_by: String,
)

@Serializable
data class SubscriptionResponse(val subscription: SubscriptionDTO? = null)

@Serializable
data class SetAutoRenewRequest(val auto_renew: Boolean, val subscription_id: String? = null)

/** `DELETE /v1/payment-method`. */
@Serializable
data class RemovePaymentMethodResponse(val removed: Boolean)

@Serializable
data class CancelSubscriptionRequest(val reason: String? = null, val subscription_id: String? = null)

@Serializable
data class PricingPlanDTO(
    val id: String,
    val title: String,
    val interval: String,
    val amount_iqd: Int,
    val days: Int,
    val vpn_quota_gb: Int? = null,
    val active: Boolean? = null,
    val sort_order: Int? = null,
)

/**
 * [auto_renew] maps a gateway id (`wayl`; older servers send `fib`, `fastpay`, `superqi`)
 * to the renewal mode that leaving auto-renew on gives it (`charge`, `link` or `none`).
 * It, or the entry, may be missing: the app then says renewal is by payment link.
 */
@Serializable
data class PricingResponse(
    val plans: List<PricingPlanDTO>,
    val auto_renew: Map<String, String>? = null,
)

@Serializable
data class AppNotificationDTO(
    val id: String,
    val title_en: String,
    val title_ckb: String? = null,
    val title_ar: String? = null,
    val body_en: String,
    val body_ckb: String? = null,
    val body_ar: String? = null,
    val created_at: String,
    val read_at: String? = null,
)

/** `GET /v1/notifications`: newest first. [next_before] is null on the last page. */
@Serializable
data class NotificationsListResponse(
    val notifications: List<AppNotificationDTO>,
    val unread_count: Int? = null,
    val next_before: String? = null,
)

/** `GET /v1/notifications/unread-count`. */
@Serializable
data class NotificationsUnreadCountResponse(val unread_count: Int)

/** `POST /v1/notifications/{id}/read` and `POST /v1/notifications/read-all`. */
@Serializable
data class NotificationsReadResponse(
    val ok: Boolean? = null,
    val unread_count: Int? = null,
    val marked: Int? = null,
)

@Serializable
data class NotificationDetailResponse(val notification: AppNotificationDTO)

@Serializable
data class DevicePrefsRequest(
    val locale: String? = null,
    val push_token: String? = null,
    val notifications_enabled: Boolean? = null,
)

@Serializable
data class BreachPrefixResponse(
    val prefix: String,
    val count: Int,
    val suffixes: List<BreachSuffixMatch>,
)

@Serializable
data class BreachSuffixMatch(val suffix: String, val source: String)

@Serializable
data class BreachMonitorRegisterResponse(
    val ok: Boolean? = null,
    val matched: Boolean? = null,
    val alerted: Boolean? = null,
)

@Serializable
data class BreachMonitorBody(val email_hash: String, val label_hint: String? = null)

@Serializable
data class VPNConnectRequest(
    val client_public_key: String? = null,
    /** A node from `GET /v1/vpn/nodes`; null lets the server pick the least loaded one. */
    val node_id: String? = null,
)

@Serializable
data class VPNConnectResponse(
    val config: VPNWireGuardConfig? = null,
    val node: VPNNodeRef? = null,
    val reused: Boolean? = null,
    val usage: VPNUsage? = null,
)

/** `qasa_vpn::WireGuardConfig`: everything the device needs to bring the tunnel up. */
@Serializable
data class VPNWireGuardConfig(
    val session_id: String? = null,
    /** `host:port` of the node. */
    val endpoint: String,
    val server_public_key: String,
    val client_public_key: String? = null,
    /** Bare IPv4 address inside the node's pool; the interface uses it as a /32. */
    val client_ip: String,
    val dns: List<String> = emptyList(),
    val allowed_ips: List<String> = listOf("0.0.0.0/0", "::/0"),
    val persistent_keepalive: Int = 25,
)

@Serializable
data class VPNNodeRef(val id: String, val name: String, val city: String)

/** A row of `GET /v1/vpn/nodes`: online and under 80% load. */
@Serializable
data class VPNNode(val id: String, val name: String, val city: String, val load_pct: Int = 0)

@Serializable
data class VPNNodesResponse(val nodes: List<VPNNode> = emptyList())

/** Monthly data against the plan's quota; past it the node limits speed to [throttle_mbps]. */
@Serializable
data class VPNUsage(
    val bytes_used: Long = 0,
    val quota_bytes: Long = 0,
    val quota_gb: Int = 0,
    val throttled: Boolean = false,
    val throttle_mbps: Int? = null,
    val period_start: String? = null,
)

@Serializable
data class VPNStatusResponse(
    /** A session exists on the server. */
    val connected: Boolean,
    val session_id: String? = null,
    val node: String? = null,
    val node_id: String? = null,
    val city: String? = null,
    val assigned_ip: String? = null,
    val connected_at: String? = null,
    val last_handshake_at: String? = null,
    /** The node saw a WireGuard handshake in the last 3 minutes. */
    val tunnel_up: Boolean = false,
    val usage: VPNUsage? = null,
)

@Serializable
data class VPNDisconnectResponse(val disconnected: Boolean, val session_id: String? = null)

@Serializable
data class DeviceLimitErrorBody(
    val error: String,
    val message: String,
    val devices_used: Int? = null,
    val device_limit: Int? = null,
    val devices: List<RemoteDevice>? = null,
    /**
     * What the sent restore key can do when there is no free slot: null (none sent),
     * `unknown`, `not_enough` or `replaceable`. See [restoreKeyStatus].
     */
    val restore_key: String? = null,
    /** With `restore_key == "replaceable"`: the device a replace would unlink. */
    val replaceable_device: RemoteDevice? = null,
) {
    val restoreKeyStatus: RestoreKeyStatus?
        get() = when (restore_key) {
            null -> null
            "replaceable" -> if (replaceable_device != null) RestoreKeyStatus.Replaceable else RestoreKeyStatus.Unknown
            "not_enough" -> RestoreKeyStatus.NotEnough
            // "unknown" and anything a newer server adds.
            else -> RestoreKeyStatus.Unknown
        }
}

/** The server's verdict on a restore key sent with a registration that hit the limit. */
enum class RestoreKeyStatus { Replaceable, Unknown, NotEnough }

@Serializable
data class StubWebhookBody(val order_id: String, val status: String)

@Serializable
data class OkResponse(val ok: Boolean? = null)
