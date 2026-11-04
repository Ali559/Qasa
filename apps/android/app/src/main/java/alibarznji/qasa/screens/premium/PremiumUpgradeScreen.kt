package alibarznji.qasa.screens.premium

import alibarznji.qasa.BuildConfig
import alibarznji.qasa.R
import alibarznji.qasa.license.LicenseManager
import alibarznji.qasa.payments.PaymentService
import alibarznji.qasa.payments.PricingService
import alibarznji.qasa.screens.AppSession
import alibarznji.qasa.theme.AppMenuHeader
import alibarznji.qasa.theme.PrimaryButton
import alibarznji.qasa.theme.QasaDimens
import alibarznji.qasa.theme.ScreenScaffold
import alibarznji.qasa.theme.StickyBottomBar
import alibarznji.qasa.theme.qasaColors
import alibarznji.qasa.theme.qasaText
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch

@HiltViewModel
class PremiumViewModel @Inject constructor(
    val pricing: PricingService,
    val payments: PaymentService,
    val license: LicenseManager,
    val appSession: AppSession,
) : ViewModel()

@Composable
fun PremiumUpgradeScreen(
    onOpenMenu: (() -> Unit)? = null,
    onDone: () -> Unit,
    // iOS `embedded`: inside the shell the header is "Upgrade" + menu; as a sheet it is
    // "Qasa Premium" with an X (PremiumUpgradeView.swift:58).
    asSheet: Boolean = false,
    vm: PremiumViewModel = hiltViewModel(),
) {
    val c = qasaColors
    // iOS starts from the fallback plans so the carousel never renders empty.
    var plans by remember { mutableStateOf(vm.pricing.plans.ifEmpty { PricingService.FALLBACK_PLANS }) }
    var gateway by remember { mutableStateOf("fib") }
    var autoRenew by remember { mutableStateOf(true) }
    var autoRenewModes by remember { mutableStateOf(vm.pricing.autoRenewModes) }
    var status by remember { mutableStateOf<String?>(null) }
    var isError by remember { mutableStateOf(false) }
    // A second tap while an intent is in flight used to create a second order.
    var isPaying by remember { mutableStateOf(false) }
    val isPremium by vm.appSession.isPremium.collectAsState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        // Sorted by sort_order; a failed refetch returns the last good list (PricingService).
        val fetched = vm.pricing.fetchPlans()
        if (fetched.isNotEmpty()) plans = fetched
        autoRenewModes = vm.pricing.autoRenewModes
    }

    val pagerState = rememberPagerState(pageCount = { plans.size.coerceAtLeast(1) })
    val selected = plans.getOrNull(pagerState.currentPage) ?: plans.firstOrNull()
    val selectedGateway = premiumGateways.firstOrNull { it.id == gateway } ?: premiumGateways.first()
    val locale = appLocale()

    ScreenScaffold(
        header = {
            if (asSheet) {
                PremiumSheetHeader(title = stringResource(R.string.premium_title), onClose = onDone)
            } else {
                // iOS embedded header has no trailing action; the drawer is the way out.
                AppMenuHeader(title = stringResource(R.string.nav_upgrade), onMenu = onOpenMenu, applyStatusBars = false)
            }
        },
        stickyBottom = {
            StickyBottomBar {
                Column(verticalArrangement = Arrangement.spacedBy(QasaDimens.gapSmall)) {
                    PrimaryButton(
                        title = if (isPaying) {
                            context.getString(R.string.premium_processing)
                        } else {
                            context.getString(
                                R.string.premium_payvia,
                                selected?.displayAmount(locale) ?: "—",
                                selectedGateway.badge,
                            )
                        },
                        icon = Icons.Filled.Lock,
                        enabled = !isPaying && !isPremium && selected != null,
                                onClick = {
                                    val plan = selected ?: return@PrimaryButton
                                    if (isPaying || isPremium) return@PrimaryButton
                                    isPaying = true
                                    isError = false
                                    status = null
                                    scope.launch {
                                        try {
                                            val intent = vm.payments.createIntent(gateway, plan.id, plan.amount_iqd, autoRenew)
                                            // `deep_link` is a wallet scheme or, behind Wayl, an
                                            // https hosted page; ACTION_VIEW opens either one.
                                            val opened = try {
                                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(intent.deep_link)))
                                                true
                                            } catch (_: Exception) {
                                                false
                                            }
                                            if (!opened) {
                                                // Do not fall back to a simulated payment here. The old
                                                // fallback POSTed the stub webhook, which granted premium
                                                // for free to anyone whose wallet app was missing or
                                                // blocked the intent. The real order stays unpaid and the
                                                // user is told to install the wallet app.
                                                isError = true
                                                status = context.getString(
                                                    R.string.premium_installwalletapp,
                                                    selectedGateway.badge,
                                                )
                                                if (BuildConfig.DEBUG) {
                                                    Log.w("QasaPayment", "Deep link failed; payment left pending")
                                                }
                                                return@launch
                                            }
                                            status = context.getString(
                                                R.string.premium_completeinwallet,
                                                selectedGateway.badge,
                                            )
                                            val ok = vm.payments.pollUntilComplete(intent.payment_id)
                                            if (ok && !vm.license.isPremium) vm.license.refresh()
                                            vm.appSession.syncFromLicense(vm.license)
                                            // Same as iOS: only close once the licence actually says premium.
                                            when {
                                                ok && vm.license.isPremium -> {
                                                    status = context.getString(R.string.premium_activated)
                                                    // iOS dismisses only when presented as a sheet; on the
                                                    // drawer page it stays and shows "Premium activated".
                                                    if (asSheet) onDone()
                                                }
                                                ok -> {
                                                    isError = true
                                                    status = context.getString(R.string.premium_licenserefreshfailed)
                                                }
                                                else -> {
                                                    isError = true
                                                    status = context.getString(R.string.premium_paymentfailed)
                                                }
                                            }
                                        } catch (e: Exception) {
                                            isError = true
                                            status = e.message
                                        } finally {
                                            isPaying = false
                                        }
                                    }
                                },
                    )
                    // iOS swaps the wallet hint for the already-premium note (success, centred mono 12).
                    Text(
                        stringResource(if (isPremium) R.string.premium_alreadypremium else R.string.premium_openswallet),
                        color = if (isPremium) c.success else c.textSecondary,
                        style = qasaText.sectionLabel,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
    ) {
        // iOS VStack(spacing: 16): carousel, auto-renew, payment methods, status, error.
        Column(verticalArrangement = Arrangement.spacedBy(QasaDimens.gap)) {
            if (plans.isNotEmpty()) PremiumPlanCarousel(plans, pagerState)
            PremiumAutoRenewRow(
                checked = autoRenew,
                // Unknown or missing means link mode, the default with Wayl.
                chargesWallet = autoRenewModes?.get(gateway) == "charge",
                onCheckedChange = { autoRenew = it },
            )
            PremiumPaymentSection(selectedId = gateway, onSelect = { gateway = it })
            status?.let {
                // iOS: status in accent, errors in danger.
                Text(it, color = if (isError) c.danger else c.accent, style = qasaText.bodySmall)
            }
        }
    }
}
