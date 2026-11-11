package alibarznji.qasa.screens.settings

import alibarznji.qasa.R
import alibarznji.qasa.prefs.AppPreferences
import alibarznji.qasa.theme.QasaDimens
import alibarznji.qasa.theme.QasaModalBottomSheet
import alibarznji.qasa.theme.QasaRow
import alibarznji.qasa.theme.QasaRowPressable
import alibarznji.qasa.theme.RowDivider
import alibarznji.qasa.theme.SettingsSection
import alibarznji.qasa.theme.qasaColors
import alibarznji.qasa.theme.qasaText
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/** iOS `AutoLockSettingsView.presets`. */
private val AutoLockPresets = listOf(1, 2, 5, 10, 15, 30, 45, 60)

private val AutoLockRange = 1..60

/**
 * iOS `AutoLockSettingsView`: under the "Auto-lock" sheet header, a section with the
 * minutes row and its stepper, the help text 8dp below it, then 24dp to the Presets
 * section (a timer row per preset, accent badge and check on the current one). A pick
 * applies at once; the sheet closes by dragging it down (no Done).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutoLockSheet(prefs: AppPreferences, onDismiss: () -> Unit) {
    val c = qasaColors
    val minutes by prefs.autoLockMinutes.collectAsState()
    QasaModalBottomSheet(onDismissRequest = onDismiss, title = stringResource(R.string.settings_autolock)) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(QasaDimens.screenPadding),
            verticalArrangement = Arrangement.spacedBy(QasaDimens.sectionGap),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(QasaDimens.contentGap)) {
                SettingsSection {
                    QasaRow(
                        icon = Icons.Outlined.Schedule,
                        title = stringResource(R.string.settings_autolockminutesfmt, minutes),
                        trailing = {
                            MinutesStepper(
                                canDecrease = minutes > AutoLockRange.first,
                                canIncrease = minutes < AutoLockRange.last,
                                onDecrease = { prefs.setAutoLockMinutes(minutes - 1) },
                                onIncrease = { prefs.setAutoLockMinutes(minutes + 1) },
                            )
                        },
                    )
                }
                Text(
                    stringResource(R.string.settings_autolockhelp),
                    color = c.textSecondary,
                    style = qasaText.caption,
                    modifier = Modifier.padding(horizontal = QasaDimens.spaceSmall),
                )
            }
            SettingsSection(title = stringResource(R.string.settings_autolockpresets)) {
                AutoLockPresets.forEachIndexed { index, preset ->
                    if (index > 0) RowDivider()
                    val isCurrent = minutes == preset
                    QasaRowPressable(
                        onClick = { prefs.setAutoLockMinutes(preset) },
                        modifier = Modifier.semantics { selected = isCurrent },
                    ) {
                        QasaRow(
                            icon = Icons.Outlined.Timer,
                            iconColor = if (isCurrent) c.accent else c.textSecondary,
                            title = stringResource(R.string.settings_autolockminutesfmt, preset),
                            trailing = {
                                Icon(
                                    Icons.Filled.Check,
                                    contentDescription = null,
                                    tint = c.accent,
                                    modifier = Modifier.size(PresetCheckSize).alpha(if (isCurrent) 1f else 0f),
                                )
                            },
                        )
                    }
                }
            }
        }
    }
}

/** iOS checkmark `.font(.system(size: 15, weight: .bold))`. */
private val PresetCheckSize = 15.dp

/** iOS `Stepper` metrics: a 94 × 32 capsule-ish pill, radius 8, split in two halves. */
private val StepperWidth = 94.dp
private val StepperHeight = 32.dp
private val StepperRadius = 8.dp

/** iOS `Stepper(in: 1...60)`: − and + halves, each disabled at its bound. */
@Composable
private fun MinutesStepper(
    canDecrease: Boolean,
    canIncrease: Boolean,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
) {
    val c = qasaColors
    Row(
        Modifier
            .size(StepperWidth, StepperHeight)
            .clip(RoundedCornerShape(StepperRadius))
            .background(c.calm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StepperHalf(Icons.Filled.Remove, stringResource(R.string.common_decrease), canDecrease, onDecrease)
        Box(Modifier.width(QasaDimens.stroke).fillMaxHeight().padding(vertical = QasaDimens.contentGap).background(c.strokeChrome))
        StepperHalf(Icons.Filled.Add, stringResource(R.string.common_increase), canIncrease, onIncrease)
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.StepperHalf(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val c = qasaColors
    Box(
        Modifier
            .weight(1f)
            .fillMaxHeight()
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = if (enabled) c.textPrimary else c.textTertiary, modifier = Modifier.size(StepperGlyph))
    }
}

private val StepperGlyph = 17.dp
