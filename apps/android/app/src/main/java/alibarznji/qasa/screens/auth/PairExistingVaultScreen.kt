package alibarznji.qasa.screens.auth

import alibarznji.qasa.R
import alibarznji.qasa.screens.auth.pair.PairAccountIdBody
import alibarznji.qasa.screens.auth.pair.PairAdoptedBody
import alibarznji.qasa.screens.auth.pair.PairPremiumBody
import alibarznji.qasa.screens.auth.pair.PairStep
import alibarznji.qasa.screens.auth.pair.PairSuccessBody
import alibarznji.qasa.screens.auth.pair.PairViewModel
import alibarznji.qasa.screens.auth.pair.PairWifiBody
import alibarznji.qasa.screens.sync.WifiSyncViewModel
import alibarznji.qasa.theme.BackChromeHeader
import alibarznji.qasa.theme.PrimaryButton
import alibarznji.qasa.theme.QasaIcons
import alibarznji.qasa.theme.ScreenScaffold
import alibarznji.qasa.theme.StickyBottomBar
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel

/**
 * Link Device — "I already have Qasa on another device".
 *
 * Main path: this device joins the other one over Wi‑Fi (it hosts from Settings → Linked
 * Devices → Sync on this network). The vault's key material, every encrypted record and
 * the account id arrive inside the code-authenticated channel — never from a pasted id
 * alone — and "Continue to vault" leads to Unlock with the same master password.
 *
 * Advanced path (kept): paste the other device's account id, checked with the server;
 * the vault is then created on the normal Create screen under that id.
 *
 * The Create screen shows this inline in place of itself, so it is full screen like the
 * iOS `fullScreenCover`. System back walks to the previous step and otherwise cancels;
 * this handler is registered after Create's, so it takes precedence.
 *
 * @param onDone closes the flow (Cancel, or "Continue to vault").
 */
@Composable
fun PairExistingVaultScreen(
    onDone: () -> Unit,
    vm: PairViewModel = hiltViewModel(),
    sync: WifiSyncViewModel = hiltViewModel(),
) {
    val close = {
        vm.reset()
        sync.resetModes()
        onDone()
    }

    BackHandler {
        if (!vm.back()) close()
    }

    // A successful join on a device without a vault always adopts.
    val joined = sync.joinResult
    LaunchedEffect(joined) {
        if (joined != null && vm.step == PairStep.Intro) vm.adopted(joined.peerName)
    }

    // iOS hides the bottom bar on the Premium card (`PairExistingVaultView.swift:46`); the
    // Wi‑Fi step has its own Sync button.
    val bottomBar: (@Composable () -> Unit)? =
        if (vm.step == PairStep.NeedsPremium || vm.step == PairStep.Intro) null else ({ PairBottomBar(vm, onFinish = close) })

    ScreenScaffold(
        header = {
            BackChromeHeader(
                title = stringResource(R.string.pair_title),
                onBack = close,
                applyStatusBars = false,
            )
        },
        stickyBottom = bottomBar,
    ) {
        when (vm.step) {
            PairStep.Intro -> PairWifiBody(sync, onAdvanced = vm::showAccountId)
            PairStep.AccountId -> PairAccountIdBody(
                accountId = vm.accountIdText,
                onAccountIdChange = vm::updateAccountId,
                errorMessage = vm.errorMessage?.let { stringResource(it) },
            )
            PairStep.NeedsPremium -> PairPremiumBody()
            PairStep.Success -> PairSuccessBody()
            PairStep.Adopted -> PairAdoptedBody(vm.adoptedFrom)
        }
    }
}

@Composable
private fun PairBottomBar(vm: PairViewModel, onFinish: () -> Unit) {
    StickyBottomBar {
        when (vm.step) {
            PairStep.Success, PairStep.Adopted -> PrimaryButton(
                title = stringResource(R.string.pair_done),
                icon = QasaIcons.Check,
                onClick = onFinish,
            )
            else -> PrimaryButton(
                title = stringResource(if (vm.isChecking) R.string.pair_checking else R.string.pair_continue),
                icon = if (vm.isChecking) null else QasaIcons.Link,
                enabled = !vm.isChecking,
                onClick = vm::applyPairing,
            )
        }
    }
}
