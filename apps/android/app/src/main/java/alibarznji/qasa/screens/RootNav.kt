package alibarznji.qasa.screens


import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBars
import androidx.compose.ui.Alignment
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.invisibleToUser
import androidx.compose.ui.semantics.semantics
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import alibarznji.qasa.config.QasaConfig
import alibarznji.qasa.prefs.AppPreferences
import alibarznji.qasa.services.AppBootstrap
import alibarznji.qasa.services.AutoLockMonitor
import alibarznji.qasa.screens.auth.CreateVaultScreen
import alibarznji.qasa.screens.auth.UnlockScreen
import alibarznji.qasa.screens.auth.WelcomePreferencesScreen
import alibarznji.qasa.screens.settings.devices.DeviceReplaceHost
import alibarznji.qasa.screens.shell.AppShellScreen
import alibarznji.qasa.theme.ApiMisconfiguredBanner
import alibarznji.qasa.theme.LocalQasaToast
import alibarznji.qasa.theme.QasaToastHost
import alibarznji.qasa.theme.QasaToastState
import androidx.compose.runtime.CompositionLocalProvider
import alibarznji.qasa.theme.QasaMotion
import alibarznji.qasa.theme.SecuringVaultCard
import alibarznji.qasa.theme.VaultPrivacyShield
import alibarznji.qasa.theme.VaultStoreErrorView
import alibarznji.qasa.vault.VaultSession
import alibarznji.qasa.vault.db.VaultStoreHolder
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class RootViewModel @Inject constructor(
    val session: VaultSession,
    val prefs: AppPreferences,
    val bootstrap: AppBootstrap,
    val autoLock: AutoLockMonitor,
    val store: VaultStoreHolder,
) : ViewModel() {
    private val _bootstrapping = MutableStateFlow(false)

    /** iOS `isBootstrapping`: true while post-unlock bootstrap (licence, devices) runs. */
    val bootstrapping: StateFlow<Boolean> = _bootstrapping.asStateFlow()

    /** Whether [onUnlocked] ran for the current unlock. */
    private var started = false

    /**
     * The vault was unlocked outside this screen (autofill's unlock sheet, while the app
     * was in the background): run the post-unlock work and the auto-lock now.
     */
    fun ensureStarted() {
        if (!started && session.isUnlocked) onUnlocked()
    }

    fun onUnlocked() {
        // Once per unlock: the unlock screen's callback and `ensureStarted` can both arrive.
        if (started) return
        started = true
        viewModelScope.launch {
            // iOS `handleCreate` / `handleUnlock` show "Securing vault…" around
            // `AppBootstrap.onVaultUnlocked`; this is the same span on Android.
            _bootstrapping.value = true
            try {
                bootstrap.onVaultUnlocked()
            } finally {
                _bootstrapping.value = false
            }
            autoLock.start { onLocked() }
        }
    }

    fun onLocked() {
        started = false
        bootstrap.onLogout()
        autoLock.stop()
    }
}

@Composable
fun RootNav(vm: RootViewModel = hiltViewModel()) {
    val unlocked by vm.session.unlocked.collectAsState()
    val storeFailure by vm.store.failure.collectAsState()
    val shielded by vm.autoLock.shielded.collectAsState()
    var welcomeDone by remember { mutableStateOf(vm.prefs.hasCompletedWelcome) }
    // Observed, so a vault adopted over Wi‑Fi on the Link Device flow leads to Unlock —
    // once that flow (still showing its success step) closes.
    val vaultExists by vm.session.vaultExistsFlow.collectAsState()
    var pairOpen by remember { mutableStateOf(false) }

    val bootstrapping by vm.bootstrapping.collectAsState()
    LaunchedEffect(unlocked) { if (unlocked) vm.ensureStarted() }
    val configurationError = remember { QasaConfig.configurationError }
    // Root-owned, so a toast raised inside a sheet outlives the sheet.
    val toast = remember { QasaToastState() }
    // A toast can name an item: it must not survive onto the unlock screen.
    LaunchedEffect(unlocked) { if (!unlocked) toast.clear() }

    CompositionLocalProvider(LocalQasaToast provides toast) {
        Box(Modifier.fillMaxSize()) {
            Column(
                Modifier
                    .fillMaxSize()
                    // While covered, the content underneath must leave the accessibility
                    // tree, or TalkBack reads vault rows through an opaque overlay. Its
                    // semantics are preserved so they come back when the shield lifts.
                    .then(
                        if (shielded) Modifier.semantics { invisibleToUser() } else Modifier,
                    ),
            ) {
                // iOS `.safeAreaInset(edge: .top)`: the banner takes the status-bar inset and
                // pushes the app down. The store error screen is its own window on iOS, so
                // the banner is not shown there.
                val banner = configurationError?.takeIf { storeFailure == null }
                if (banner != null) ApiMisconfiguredBanner(banner)
                Box(
                    Modifier
                        .fillMaxSize()
                        .then(
                            if (banner != null) {
                                Modifier.consumeWindowInsets(WindowInsets.statusBars)
                            } else {
                                Modifier
                            },
                        ),
                ) {
                    when {
                        // The store is the user's data, not a cache: when it will not open we
                        // show the error screen and never offer to delete it.
                        storeFailure != null -> VaultStoreErrorView(storeFailure!!)
                        unlocked -> AppShellScreen(onLock = { vm.onLocked() })
                        vaultExists && !pairOpen -> UnlockScreen(onUnlocked = { vm.onUnlocked() })
                        !welcomeDone -> WelcomePreferencesScreen(onContinue = { welcomeDone = true })
                        else -> CreateVaultScreen(
                            onCreated = { vm.onUnlocked() },
                            onPairOpenChanged = { pairOpen = it },
                        )
                    }
                    if (bootstrapping) {
                        SecuringVaultCard(Modifier.align(Alignment.Center))
                    } else if (unlocked && storeFailure == null) {
                        // iOS asks right after the unlock bootstrap; so does Android 13+.
                        NotificationPermissionPrompt(
                            prefs = vm.prefs,
                            onResult = vm.bootstrap::onNotificationStateMayHaveChanged,
                        )
                        // A restored backup's device-replace confirmation and its done notice.
                        DeviceReplaceHost()
                    }
                    QasaToastHost(toast, Modifier.align(Alignment.BottomCenter))
                }
            }
            // iOS `privacyShield` uses `.transition(.opacity)`. FLAG_SECURE (MainActivity)
            // already blanks the Recents snapshot, so the short fade-in exposes nothing.
            AnimatedVisibility(
                visible = shielded,
                enter = fadeIn(tween(QasaMotion.FAST)),
                exit = fadeOut(tween(QasaMotion.FAST)),
            ) {
                VaultPrivacyShield(onReveal = vm.autoLock::dismissShield)
            }
        }
    }
}
