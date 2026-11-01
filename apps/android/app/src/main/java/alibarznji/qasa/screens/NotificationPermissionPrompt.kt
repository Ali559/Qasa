package alibarznji.qasa.screens

import alibarznji.qasa.R
import alibarznji.qasa.prefs.AppPreferences
import alibarznji.qasa.push.NotificationPermissionAction
import alibarznji.qasa.push.notificationPermissionAction
import alibarznji.qasa.theme.QasaConfirmDialog
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat

/**
 * iOS `requestAuthorizationAndRegister` after unlock: on Android 13+ ask for
 * POST_NOTIFICATIONS once; if it was declined, offer system settings at most every
 * 30 days ([notificationPermissionAction]). Below Android 13 this does nothing.
 * [onResult] runs after the prompt answers so the server learns the new state.
 */
@Composable
fun NotificationPermissionPrompt(prefs: AppPreferences, onResult: () -> Unit) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val context = LocalContext.current
    var offerSettings by rememberSaveable { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        onResult()
    }

    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        val now = System.currentTimeMillis()
        when (
            notificationPermissionAction(
                sdkInt = Build.VERSION.SDK_INT,
                granted = granted,
                alreadyRequested = prefs.notificationPermissionRequested,
                lastPromptAtMs = prefs.notificationPromptedAt,
                nowMs = now,
            )
        ) {
            NotificationPermissionAction.Request -> {
                // Recorded before launching, so a crash or recreate never re-prompts.
                prefs.notificationPermissionRequested = true
                prefs.notificationPromptedAt = now
                launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            NotificationPermissionAction.OfferSettings -> {
                prefs.notificationPromptedAt = now
                offerSettings = true
            }
            NotificationPermissionAction.None -> Unit
        }
    }

    if (offerSettings) {
        QasaConfirmDialog(
            title = stringResource(R.string.notifications_permissiontitle),
            message = stringResource(R.string.notifications_permissionmessage),
            confirmLabel = stringResource(R.string.notifications_permissionopensettings),
            dismissLabel = stringResource(R.string.notifications_permissionnotnow),
            destructive = false,
            onConfirm = {
                offerSettings = false
                // The resume that follows the return from settings syncs the new state.
                context.startActivity(
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
                )
            },
            onDismiss = { offerSettings = false },
        )
    }
}
