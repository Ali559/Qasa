package alibarznji.qasa.theme

import alibarznji.qasa.R
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

/**
 * Opaque cover drawn whenever the app is not frontmost — `VaultPrivacyShield.swift:12`.
 *
 * Android takes the system snapshot for Recents and for the task switcher as the activity
 * is backgrounded, so key material has to be gone from the window before that happens;
 * [alibarznji.qasa.services.AutoLockMonitor.shielded] is raised in `onPause`, not when
 * the lock timer fires. `FLAG_SECURE` in `MainActivity` covers the cases a Compose
 * overlay cannot: screenshots, screen recording, and the thumbnail itself.
 *
 * The shield is opaque rather than translucent on purpose — a translucent scrim still
 * encodes legible edges of the content underneath. `RootNav` fades it in and out like
 * iOS `.transition(.opacity)`.
 */
/** iOS `VStack(spacing: 14)`. */
private val ShieldGap = 14.dp

@Composable
fun VaultPrivacyShield(
    onReveal: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val c = qasaColors
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(c.background),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(ShieldGap, Alignment.CenterVertically),
    ) {
        Icon(
            painterResource(R.drawable.ic_qasa_mark),
            contentDescription = null,
            tint = c.textSecondary,
            modifier = Modifier.size(QasaIconSize.x7l),
        )
        Text(
            stringResource(R.string.security_hidden),
            color = c.textSecondary,
            style = qasaText.bodyMedium15,
        )
        if (onReveal != null) {
            // Deliberately reachable: iOS marks this overlay `.accessibilityHidden(true)`
            // and thereby hides the OK button from VoiceOver (§13). A screen reader user
            // gets the same way out as everyone else. 14 semibold, 18/8 padding, radius 8.
            Text(
                text = stringResource(R.string.common_ok),
                color = c.textPrimary,
                style = qasaText.bodyMediumSemibold,
                modifier = Modifier
                    .clip(RoundedCornerShape(QasaDimens.radiusSmall))
                    .background(c.cardBackground)
                    .clickable(onClick = onReveal)
                    .padding(horizontal = QasaDimens.gapXLarge, vertical = QasaDimens.contentGap),
            )
        }
    }
}
