package alibarznji.qasa.screens.auth

import alibarznji.qasa.R
import alibarznji.qasa.screens.auth.pair.PairEnterCodeBody
import alibarznji.qasa.screens.auth.pair.PairIntroBody
import alibarznji.qasa.screens.auth.pair.PairPremiumBody
import alibarznji.qasa.screens.auth.pair.PairStep
import alibarznji.qasa.screens.auth.pair.PairSuccessBody
import alibarznji.qasa.screens.auth.pair.PairViewModel
import alibarznji.qasa.theme.BackChromeHeader
import alibarznji.qasa.theme.PrimaryButton
import alibarznji.qasa.theme.ScreenScaffold
import alibarznji.qasa.theme.StickyBottomBar
import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel

/**
 * Link Device — the iOS 4-step flow from `PairExistingVaultView.swift`:
 * intro → pairing code (or account id) → Premium required / Account linked.
 *
 * Pairing only adopts the other device's account id into `VaultSecureStore.accountId`.
 * "Continue to vault" then returns to the normal Create screen, which creates the local
 * vault under that id (iOS `qasaApp.swift:146-153` does the same: `onPaired` just closes
 * the cover and leaves `CreateVaultView` showing).
 *
 * The Create screen shows this inline in place of itself, so it is full screen like the
 * iOS `fullScreenCover`. System back walks to the previous step and otherwise cancels;
 * this handler is registered after Create's, so it takes precedence.
 *
 * @param onDone closes the flow (Cancel, or "Continue to vault" after linking).
 * @param onCreated unused: a vault is never created here any more. Kept so the Create
 *   screen's existing call site still compiles; it can be dropped there.
 */
@Composable
fun PairExistingVaultScreen(
    onDone: () -> Unit,
    vm: PairViewModel = hiltViewModel(),
) {
    val close = {
        vm.reset()
        onDone()
    }

    BackHandler {
        if (!vm.back()) close()
    }

    // iOS hides the bottom bar on the Premium card (`PairExistingVaultView.swift:46`).
    val bottomBar: (@Composable () -> Unit)? =
        if (vm.step == PairStep.NeedsPremium) null else ({ PairBottomBar(vm, onFinish = close) })

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
            PairStep.Intro -> PairIntroBody()
            PairStep.EnterCode -> PairEnterCodeBody(
                code = vm.code,
                onCodeChange = vm::updateCode,
                accountId = vm.accountIdText,
                onAccountIdChange = vm::updateAccountId,
                errorMessage = vm.errorMessage?.let { stringResource(it) },
            )
            PairStep.NeedsPremium -> PairPremiumBody()
            PairStep.Success -> PairSuccessBody()
        }
    }
}

/** `bottomBar` (`PairExistingVaultView.swift:181`). */
@Composable
private fun PairBottomBar(vm: PairViewModel, onFinish: () -> Unit) {
    StickyBottomBar {
        when (vm.step) {
            PairStep.Intro -> PrimaryButton(
                title = stringResource(R.string.pair_entercode),
                icon = Icons.Default.QrCode,
                onClick = vm::showEnterCode,
            )
            // iOS calls `onPaired(id)` or, with no id, `onCancel()`; both just close the
            // cover, and the id is already stored, so one close covers both.
            PairStep.Success -> PrimaryButton(
                title = stringResource(R.string.pair_done),
                icon = Icons.Default.Check,
                onClick = onFinish,
            )
            else -> PrimaryButton(
                title = stringResource(if (vm.isChecking) R.string.pair_checking else R.string.pair_continue),
                icon = if (vm.isChecking) null else Icons.Default.Link,
                enabled = !vm.isChecking,
                onClick = vm::applyPairing,
            )
        }
    }
}
