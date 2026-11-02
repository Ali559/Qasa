package alibarznji.qasa.payments

import alibarznji.qasa.BuildConfig
import alibarznji.qasa.config.QasaConfig
import alibarznji.qasa.license.LicenseManager
import alibarznji.qasa.network.CancelSubscriptionRequest
import alibarznji.qasa.network.PaymentIntentRequest
import alibarznji.qasa.network.PaymentIntentResponse
import alibarznji.qasa.network.PaymentStatusResponse
import alibarznji.qasa.network.PricingPlanDTO
import alibarznji.qasa.network.PricingResponse
import alibarznji.qasa.network.QasaApiClient
import alibarznji.qasa.network.SetAutoRenewRequest
import alibarznji.qasa.network.StubWebhookBody
import alibarznji.qasa.network.SubscriptionDTO
import alibarznji.qasa.network.SubscriptionResponse
import alibarznji.qasa.network.get
import alibarznji.qasa.network.patch
import alibarznji.qasa.network.post
import android.util.Log
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Mirrors `I/Services/Payments/PricingService.swift`: plans are sorted by `sort_order`, the
 * last good list is kept in memory, and a failed refetch returns that list (or the fallback
 * plans when nothing was ever fetched) instead of an empty carousel.
 */
@Singleton
class PricingService @Inject constructor(private val api: QasaApiClient) {
    @Volatile
    var plans: List<PricingPlanDTO> = emptyList()
        private set

    suspend fun fetchPlans(): List<PricingPlanDTO> = withContext(Dispatchers.IO) {
        try {
            val sorted = api.get<PricingResponse>(QasaConfig.pricingUrl, authorized = false)
                .plans
                .sortedBy { it.sort_order ?: 0 }
            plans = sorted
            sorted
        } catch (_: Exception) {
            if (plans.isEmpty()) plans = FALLBACK_PLANS
            plans
        }
    }

    companion object {
        /** `PricingService.fallbackPlans` (PricingService.swift:38); iOS interval raw values. */
        val FALLBACK_PLANS: List<PricingPlanDTO> = listOf(
            PricingPlanDTO(
                id = "premium_monthly",
                title = "Premium Monthly",
                interval = "monthly",
                amount_iqd = 5_000,
                days = 30,
                vpn_quota_gb = 100,
                active = true,
                sort_order = 1,
            ),
            PricingPlanDTO(
                id = "premium_yearly",
                title = "Premium Yearly",
                interval = "yearly",
                amount_iqd = 50_000,
                days = 365,
                vpn_quota_gb = 100,
                active = true,
                sort_order = 2,
            ),
        )
    }
}

@Singleton
class PaymentService @Inject constructor(
    private val api: QasaApiClient,
    private val license: LicenseManager,
) {
    private companion object {
        const val TAG = "QasaPayment"
    }
    suspend fun createIntent(gateway: String, planId: String, amountIqd: Int, autoRenew: Boolean): PaymentIntentResponse =
        withContext(Dispatchers.IO) {
            api.post(
                QasaConfig.paymentsUrl,
                PaymentIntentRequest(
                    gateway = gateway,
                    plan_id = planId,
                    auto_renew = autoRenew,
                    amount_iqd = amountIqd,
                    idempotency_key = UUID.randomUUID().toString().lowercase(),
                ),
            )
        }

    /**
     * Marks an order paid **without any money moving**, by posting the gateway webhook
     * body directly and unauthenticated.
     *
     * This existed for local development, but the production flow reached it whenever
     * the wallet deep link failed to open — so any user without the app installed, or
     * with the intent blocked, got premium for free. It is now a no-op in release; the
     * caller must surface a real error instead.
     */
    suspend fun simulateStubPayment(gateway: String, gatewayOrderId: String): Boolean {
        if (!BuildConfig.DEBUG) {
            Log.e(
                TAG,
                "Refusing to simulate payment for $gatewayOrderId: debug-only, and an " +
                    "unauthenticated success webhook is a free-premium bypass.",
            )
            return false
        }
        return withContext(Dispatchers.IO) {
            val body = api.json.encodeToString(StubWebhookBody.serializer(), StubWebhookBody(gatewayOrderId, "success"))
            runCatching { api.postRaw(QasaConfig.paymentWebhookUrl(gateway), body, authorized = false) }.isSuccess
        }
    }

    suspend fun pollUntilComplete(paymentId: String, maxAttempts: Int = 30): Boolean = withContext(Dispatchers.IO) {
        repeat(maxAttempts) {
            try {
                val status: PaymentStatusResponse = api.get(QasaConfig.paymentUrl(paymentId))
                when (status.status.lowercase()) {
                    "success" -> {
                        license.refresh()
                        return@withContext true
                    }
                    "failed" -> return@withContext false
                }
            } catch (_: Exception) {
            }
            delay(2000)
        }
        false
    }
}

/** Server answered 2xx without a subscription body (iOS `SubscriptionServiceError.notFound`). */
class SubscriptionNotFoundException : Exception("No active subscription found.")

/**
 * Mirrors `I/Services/Payments/SubscriptionService.swift`: mutations return the updated
 * subscription the server sends back, so the screen never needs a second fetch.
 */
@Singleton
class SubscriptionService @Inject constructor(private val api: QasaApiClient) {
    suspend fun current(): SubscriptionDTO? = withContext(Dispatchers.IO) {
        api.get<SubscriptionResponse>(QasaConfig.subscriptionUrl).subscription
    }

    suspend fun setAutoRenew(enabled: Boolean, subscriptionId: String?): SubscriptionDTO = withContext(Dispatchers.IO) {
        api.patch<SubscriptionResponse, SetAutoRenewRequest>(
            QasaConfig.subscriptionAutoRenewUrl,
            SetAutoRenewRequest(enabled, subscriptionId),
        ).subscription ?: throw SubscriptionNotFoundException()
    }

    /** Blank reasons are sent as null, like iOS `cancel(reason:)`. */
    suspend fun cancel(reason: String?, subscriptionId: String?): SubscriptionDTO = withContext(Dispatchers.IO) {
        api.post<SubscriptionResponse, CancelSubscriptionRequest>(
            QasaConfig.subscriptionCancelUrl,
            CancelSubscriptionRequest(reason?.trim()?.ifEmpty { null }, subscriptionId),
        ).subscription ?: throw SubscriptionNotFoundException()
    }
}
