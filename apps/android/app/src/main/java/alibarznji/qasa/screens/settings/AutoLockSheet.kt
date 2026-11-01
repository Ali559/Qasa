package alibarznji.qasa.screens.settings

import alibarznji.qasa.R
import alibarznji.qasa.prefs.AppPreferences
import alibarznji.qasa.theme.CardContainer
import alibarznji.qasa.theme.QasaDimens
import alibarznji.qasa.theme.QasaModalBottomSheet
import alibarznji.qasa.theme.QasaPressable
import alibarznji.qasa.theme.SectionLabel
import alibarznji.qasa.theme.SheetChromeHeader
import alibarznji.qasa.theme.qasaColors
import alibarznji.qasa.theme.qasaText
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource

/** iOS `AutoLockSettingsView.presets`. */
private val AutoLockPresets = listOf(1, 2, 5, 10, 15, 30, 45, 60)
private val AutoLockRange = 1..60

/**
 * Port of iOS `AutoLockSettingsView`: a 1–60 minute stepper with the help footer, then a
 * Presets list with a check on the current value. Changes apply immediately; Done closes.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutoLockSheet(prefs: AppPreferences, onDismiss: () -> Unit) {
    val c = qasaColors
    val minutes by prefs.autoLockMinutes.collectAsState()
    QasaModalBottomSheet(onDismissRequest = onDismiss) {
        SheetChromeHeader(
            title = stringResource(R.string.settings_autolock),
            onDismiss = onDismiss,
            dismissLabel = stringResource(R.string.settings_done),
        )
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = QasaDimens.screenPadding)
                .padding(bottom = QasaDimens.scrollTail)
                .navigationBarsPadding(),
        ) {
            CardContainer {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.settings_autolockminutesfmt, minutes),
                        color = c.textPrimary,
                        style = qasaText.bodyLarge,
                        modifier = Modifier.weight(1f),
                    )
                    // iOS `Stepper(in: 1...60)`: each half disables at its bound.
                    IconButton(
                        onClick = { prefs.setAutoLockMinutes(minutes - 1) },
                        enabled = minutes > AutoLockRange.first,
                    ) {
                        Icon(Icons.Filled.Remove, stringResource(R.string.common_decrease), tint = if (minutes > AutoLockRange.first) c.accent else c.textTertiary)
                    }
                    IconButton(
                        onClick = { prefs.setAutoLockMinutes(minutes + 1) },
                        enabled = minutes < AutoLockRange.last,
                    ) {
                        Icon(Icons.Filled.Add, stringResource(R.string.common_increase), tint = if (minutes < AutoLockRange.last) c.accent else c.textTertiary)
                    }
                }
            }
            Spacer(Modifier.height(QasaDimens.contentGap))
            Text(stringResource(R.string.settings_autolockhelp), color = c.textSecondary, style = qasaText.bodySmall)

            Spacer(Modifier.height(QasaDimens.sectionGap))
            SectionLabel(stringResource(R.string.settings_autolockpresets))
            Spacer(Modifier.height(QasaDimens.contentGap))
            CardContainer {
                AutoLockPresets.forEachIndexed { index, preset ->
                    if (index > 0) HorizontalDivider(color = c.stroke)
                    QasaPressable(onClick = { prefs.setAutoLockMinutes(preset) }, modifier = Modifier.fillMaxWidth()) {
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = QasaDimens.gapSmall),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                stringResource(R.string.settings_autolockminutesfmt, preset),
                                color = c.textPrimary,
                                style = qasaText.bodyLarge,
                                modifier = Modifier.weight(1f),
                            )
                            if (minutes == preset) Icon(Icons.Filled.Check, null, tint = c.accent)
                        }
                    }
                }
            }
        }
    }
}
