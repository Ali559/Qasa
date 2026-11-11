package alibarznji.qasa.screens.settings.main

import alibarznji.qasa.R
import alibarznji.qasa.theme.CardContainer
import alibarznji.qasa.theme.QasaDimens
import alibarznji.qasa.theme.QasaIconSize
import alibarznji.qasa.theme.QasaIcons
import alibarznji.qasa.theme.QasaPressable
import alibarznji.qasa.theme.qasaColors
import alibarznji.qasa.theme.qasaText
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource

/** iOS placeholder username (`AppSession.displayName`): shown localized, never verbatim. */
private const val DefaultUsername = "Qasa User"

/**
 * iOS `SettingsView.profileCard` (`SettingsView.swift:300-352`): 56pt accent ring with a
 * person glyph, name, optional email, outlined tier pill with trophy, then a full-width
 * Manage Subscription / Upgrade button.
 *
 * Android's `AppSession` has no email yet (iOS's is empty by default too), so that line is
 * only drawn when there is one.
 */
@Composable
internal fun SettingsProfileCard(
    displayName: String,
    isPremium: Boolean,
    onCta: () -> Unit,
    email: String = "",
) {
    val c = qasaColors
    val name = if (displayName.isBlank() || displayName == DefaultUsername) {
        stringResource(R.string.session_qasauser)
    } else {
        displayName
    }
    val tint = if (isPremium) c.accent else c.textSecondary
    CardContainer {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(QasaDimens.gapSmall)) {
            Box(
                Modifier.size(QasaIconSize.x8l).border(QasaDimens.ringStroke, c.accent, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(QasaIcons.Person, null, tint = c.textSecondary, modifier = Modifier.size(QasaIconSize.x4l))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(QasaDimens.spaceSmall)) {
                Text(name, color = c.textPrimary, style = qasaText.rowTitle)
                if (email.isNotBlank()) {
                    Text(email, color = c.textSecondary, style = qasaText.bodyMedium)
                }
                Row(
                    modifier = Modifier
                        .border(QasaDimens.stroke, if (isPremium) c.accent else c.stroke, CircleShape)
                        .padding(horizontal = QasaDimens.contentGap, vertical = QasaDimens.spaceTight),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(QasaDimens.spaceSmall),
                ) {
                    Icon(
                        if (isPremium) QasaIcons.BestValue else QasaIcons.Person,
                        null,
                        tint = tint,
                        modifier = Modifier.size(QasaIconSize.xxs),
                    )
                    Text(
                        stringResource(if (isPremium) R.string.settings_premiumaccount else R.string.settings_freeaccount),
                        color = tint,
                        style = qasaText.sectionLabel,
                    )
                }
            }
        }
        Spacer(Modifier.height(QasaDimens.gapSmall))
        QasaPressable(onClick = onCta, modifier = Modifier.fillMaxWidth()) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(c.cardBackgroundLight, RoundedCornerShape(QasaDimens.radiusSmall))
                    .padding(vertical = QasaDimens.gapMedium),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    stringResource(if (isPremium) R.string.settings_managesubscription else R.string.settings_upgradepremium),
                    color = c.textPrimary,
                    style = qasaText.bodyMediumMedium,
                )
            }
        }
    }
}
