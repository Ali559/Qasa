package alibarznji.qasa.screens.settings

import alibarznji.qasa.R
import alibarznji.qasa.license.LicenseManager
import alibarznji.qasa.network.SubscriptionDTO
import alibarznji.qasa.payments.SubscriptionService
import alibarznji.qasa.screens.AppSession
import alibarznji.qasa.screens.settings.subscription.AutoRenewCard
import alibarznji.qasa.screens.settings.subscription.CancelSubscriptionButton
import alibarznji.qasa.screens.settings.subscription.SubscriptionDetailsCard
import alibarznji.qasa.screens.settings.subscription.appLocale
import alibarznji.qasa.screens.settings.subscription.formatSubscriptionDate
import alibarznji.qasa.theme.BackChromeHeader
import alibarznji.qasa.theme.PrimaryButton
import alibarznji.qasa.theme.QasaConfirmDialog
import alibarznji.qasa.theme.QasaDimens
import alibarznji.qasa.theme.QasaModalBottomSheet
import alibarznji.qasa.theme.ScreenScaffold
import alibarznji.qasa.theme.ThemedTextField
import alibarznji.qasa.theme.qasaColors
import alibarznji.qasa.theme.qasaText
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch

@HiltViewModel
class SubViewModel @Inject constructor(
    val service: SubscriptionService,
    private val license: LicenseManager,
    private val appSession: AppSession,
) : ViewModel() {
    /** iOS `session.syncFromLicense()` after a cancel (ManageSubscriptionView.swift:329). */
    fun syncFromLicense() = appSession.syncFromLicense(license)
}

/** Mirrors `I/Views/ManageSubscriptionView.swift` (sheet variant with the "Manage Subscription" header). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageSubscriptionScreen(onDone: () -> Unit, vm: SubViewModel = hiltViewModel()) {
    val c = qasaColors
    val locale = appLocale()
    var sub by remember { mutableStateOf<SubscriptionDTO?>(null) }
    var autoRenew by remember { mutableStateOf(true) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var reason by remember { mutableStateOf("") }
    var isCancelling by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }
    var showCancelConfirm by remember { mutableStateOf(false) }
    var showCancelReason by remember { mutableStateOf(false) }
    var showRemovePaymentConfirm by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val isCancelled = sub?.status?.lowercase() == "cancelled"

    val cancelledUntilFmt = stringResource(R.string.sub_cancelleduntil)
    val autoRenewEnabledMsg = stringResource(R.string.sub_autorenewenabled)
    val autoRenewDisabledMsg = stringResource(R.string.sub_autorenewdisabled)
    val paymentMethodRemovedMsg = stringResource(R.string.sub_paymentmethodremoved)

    LaunchedEffect(Unit) {
        try {
            val fetched = vm.service.current()
            sub = fetched
            autoRenew = fetched?.auto_renew ?: true
        } catch (e: Exception) {
            errorMessage = e.message
        } finally {
            isLoading = false
        }
    }

    fun confirmCancel() {
        val s = sub ?: return
        scope.launch {
            isCancelling = true
            errorMessage = null
            statusMessage = null
            try {
                val updated = vm.service.cancel(reason, s.id)
                sub = updated
                autoRenew = updated.auto_renew
                showCancelReason = false
                statusMessage = cancelledUntilFmt.format(formatSubscriptionDate(updated.expires_at, locale))
                vm.syncFromLicense()
            } catch (e: Exception) {
                errorMessage = e.message
            } finally {
                isCancelling = false
            }
        }
    }

    fun updateAutoRenew(enabled: Boolean) {
        val s = sub ?: return
        if (isCancelled || isSaving) return
        // Optimistic like iOS, rolled back to the server value if the request fails.
        autoRenew = enabled
        scope.launch {
            isSaving = true
            errorMessage = null
            statusMessage = null
            try {
                val updated = vm.service.setAutoRenew(enabled, s.id)
                sub = updated
                autoRenew = updated.auto_renew
                statusMessage = if (enabled) autoRenewEnabledMsg else autoRenewDisabledMsg
            } catch (e: Exception) {
                autoRenew = sub?.auto_renew ?: true
                errorMessage = e.message
            } finally {
                isSaving = false
            }
        }
    }

    fun removePaymentMethod() {
        if (isSaving) return
        scope.launch {
            isSaving = true
            errorMessage = null
            statusMessage = null
            try {
                vm.service.removePaymentMethod()
                // Reload so the card drops the renewal line and the remove action.
                val fetched = vm.service.current()
                sub = fetched
                autoRenew = fetched?.auto_renew ?: autoRenew
                statusMessage = paymentMethodRemovedMsg
            } catch (e: Exception) {
                errorMessage = e.message
            } finally {
                isSaving = false
            }
        }
    }

    ScreenScaffold(
        header = {
            BackChromeHeader(
                title = stringResource(R.string.sub_managetitle),
                onBack = onDone,
                applyStatusBars = false,
            )
        },
    ) {
        val s = sub
        when {
            isLoading -> Row(
                Modifier.fillMaxWidth().padding(top = QasaDimens.scrollTail),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) { CircularProgressIndicator(color = c.accent) }
            s == null -> Text(
                stringResource(R.string.sub_noneactive),
                color = c.textSecondary,
                style = qasaText.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = QasaDimens.scrollTail),
            )
            else -> {
                SubscriptionDetailsCard(s, isCancelled)
                Spacer(Modifier.height(QasaDimens.gap))
                AutoRenewCard(
                    sub = s,
                    checked = autoRenew,
                    enabled = !isCancelled && !isSaving,
                    isCancelled = isCancelled,
                    onChange = ::updateAutoRenew,
                    onRemovePaymentMethod = { showRemovePaymentConfirm = true },
                )
                if (!isCancelled) {
                    Spacer(Modifier.height(QasaDimens.gap))
                    CancelSubscriptionButton(enabled = !isCancelling, onClick = { showCancelConfirm = true })
                }
            }
        }

        statusMessage?.let {
            Spacer(Modifier.height(QasaDimens.gap))
            Text(it, color = c.accent, style = qasaText.bodySmall)
        }
        errorMessage?.let {
            Spacer(Modifier.height(QasaDimens.gap))
            Text(it, color = c.danger, style = qasaText.bodySmall)
        }
    }

    if (showCancelConfirm) {
        QasaConfirmDialog(
            title = stringResource(R.string.sub_cancelconfirmtitle),
            message = stringResource(R.string.sub_cancelconfirmmessage, formatSubscriptionDate(sub?.expires_at, locale)),
            confirmLabel = stringResource(R.string.sub_continue),
            dismissLabel = stringResource(R.string.sub_keeppremium),
            onConfirm = {
                showCancelConfirm = false
                reason = ""
                showCancelReason = true
            },
            onDismiss = { showCancelConfirm = false },
        )
    }

    if (showRemovePaymentConfirm) {
        QasaConfirmDialog(
            title = stringResource(R.string.sub_removepaymentmethod),
            message = stringResource(R.string.sub_removepaymentmethodconfirm),
            confirmLabel = stringResource(R.string.sub_removepaymentmethod),
            onConfirm = {
                showRemovePaymentConfirm = false
                removePaymentMethod()
            },
            onDismiss = { showRemovePaymentConfirm = false },
        )
    }

    if (showCancelReason) {
        QasaModalBottomSheet(onDismissRequest = { if (!isCancelling) showCancelReason = false }) {
            Column(Modifier.padding(horizontal = QasaDimens.screenPadding)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.sub_cancelreasontitle),
                        color = c.textPrimary,
                        style = qasaText.rowTitle,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { confirmCancel() }, enabled = !isCancelling) {
                        Text(stringResource(R.string.sub_skip), color = c.accent)
                    }
                }
                Spacer(Modifier.height(QasaDimens.contentGap))
                Text(stringResource(R.string.sub_cancelreasonprompt), color = c.textSecondary, style = qasaText.bodyLegal)
                Spacer(Modifier.height(QasaDimens.contentGap))
                ThemedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    placeholder = stringResource(R.string.sub_cancelreasontitle),
                )
                Spacer(Modifier.height(QasaDimens.spaceSmall))
                Text(stringResource(R.string.sub_cancelreasonoptional), color = c.textSecondary, style = qasaText.bodySmall)
                Spacer(Modifier.height(QasaDimens.screenPadding))
                PrimaryButton(
                    title = stringResource(
                        if (isCancelling) R.string.sub_cancellingprogress else R.string.sub_confirmcancel,
                    ),
                    enabled = !isCancelling,
                    icon = Icons.Outlined.Cancel,
                    onClick = { confirmCancel() },
                )
                Spacer(Modifier.height(QasaDimens.screenPadding))
            }
        }
    }
}
