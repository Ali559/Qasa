package alibarznji.qasa.screens.home

import alibarznji.qasa.R
import androidx.compose.ui.res.painterResource
import alibarznji.qasa.credentials.AutofillSettings
import alibarznji.qasa.prefs.AppPreferences
import alibarznji.qasa.theme.PrimaryButton
import alibarznji.qasa.theme.QasaDimens
import alibarznji.qasa.theme.QasaIconSize
import alibarznji.qasa.theme.qasaColors
import alibarznji.qasa.theme.qasaText
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Whether Home asks the user to make Qasa the autofill service (docs/autofill-plan.md):
 * shown while another service (or none) is selected, until the user turns Qasa on or
 * taps Not now, which hides it for [SNOOZE_MS]. Rechecked every time Home resumes, so it
 * goes away as soon as the user comes back from the system picker.
 */
@HiltViewModel
class AutofillPromptViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prefs: AppPreferences,
) : ViewModel() {
    private val _show = MutableStateFlow(false)
    val show: StateFlow<Boolean> = _show.asStateFlow()

    fun refresh() {
        val snoozed = System.currentTimeMillis() < prefs.autofillPromptSnoozedUntil
        _show.value = !snoozed && !AutofillSettings.isQasaEnabled(context)
    }

    fun notNow() {
        prefs.autofillPromptSnoozedUntil = System.currentTimeMillis() + SNOOZE_MS
        _show.value = false
    }

    companion object {
        const val SNOOZE_MS = 3L * 24 * 60 * 60 * 1000
    }
}

/** "Fill passwords with Qasa": the Qasa mark, a title, why, and Activate / Not now. */
@Composable
internal fun AutofillPromptCard(onNotNow: () -> Unit) {
    val c = qasaColors
    val context = LocalContext.current
    val shape = RoundedCornerShape(QasaDimens.cornerRadius)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(c.cardBackground, shape)
            .padding(QasaDimens.cardPadding),
        verticalArrangement = Arrangement.spacedBy(QasaDimens.contentGap),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(QasaIconSize.x6l).clip(CircleShape).background(c.accent),
                contentAlignment = Alignment.Center,
            ) {
                Icon(painterResource(R.drawable.ic_qasa_mark), contentDescription = null, tint = c.onAccent, modifier = Modifier.size(QasaIconSize.xl))
            }
            Spacer(Modifier.width(QasaDimens.gapSmall))
            Text(
                stringResource(R.string.autofill_prompt_title),
                color = c.textPrimary,
                style = qasaText.rowTitle,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
        }
        Text(stringResource(R.string.autofill_prompt_body), color = c.textSecondary, style = qasaText.bodySmall)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(QasaDimens.gapSmall)) {
            PrimaryButton(
                title = stringResource(R.string.autofill_prompt_activate),
                onClick = { AutofillSettings.openPicker(context) },
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onNotNow) {
                Text(stringResource(R.string.autofill_prompt_notnow), color = c.textSecondary, fontWeight = FontWeight.Medium)
            }
        }
    }
}
