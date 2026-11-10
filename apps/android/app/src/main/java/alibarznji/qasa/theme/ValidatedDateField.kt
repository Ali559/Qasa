package alibarznji.qasa.theme

import alibarznji.qasa.R
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import java.time.LocalDate

/**
 * Port of iOS `Components/ValidatedDateField.swift`: a free-text date field plus a "Pick
 * date" button.
 *
 * Stored dates are ISO `YYYY-MM-DD` (the shared field rules, `validate.rs`): the picker
 * writes that form, and [localized] (the stored date in the user's locale, when it parses)
 * is shown beside the button. Typed text in another form Qasa reads is accepted and
 * rewritten to ISO by the repository's normalize on save. [error] is the shared rule's
 * message (danger colour), shown in place of the hint.
 *
 * iOS expands an inline graphical `DatePicker` with a "Done" button; the Android idiom is
 * the Material date-picker dialog, whose confirm button carries the same "Done" copy.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ValidatedDateField(
    title: String,
    text: String,
    onTextChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = title,
    error: String? = null,
    /** [text] as a date, for the picker's initial selection. */
    date: LocalDate? = null,
    localized: String? = null,
) {
    val c = qasaColors
    var showPicker by remember { mutableStateOf(false) }

    Column(modifier.fillMaxWidth()) {
        SectionLabel(title)
        Spacer(Modifier.height(QasaDimens.contentGap))
        ThemedTextField(
            value = text,
            onValueChange = onTextChange,
            placeholder = placeholder,
            label = title,
        )
        Spacer(Modifier.height(QasaDimens.contentGap))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier.clickable { showPicker = true }.padding(vertical = QasaDimens.spaceSmall),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.CalendarMonth, null, tint = c.accent, modifier = Modifier.size(QasaIconSize.md))
                Spacer(Modifier.width(QasaDimens.spaceSmall))
                Text(stringResource(R.string.date_pick), color = c.accent, style = qasaText.bodySmallSemibold)
            }
            Spacer(Modifier.weight(1f))
            when {
                error != null -> Text(
                    error,
                    color = c.danger,
                    style = qasaText.errorDetail,
                    textAlign = TextAlign.End,
                )
                localized != null && localized != text.trim() -> Text(
                    localized,
                    color = c.textSecondary,
                    style = qasaText.bodySmall,
                    textAlign = TextAlign.End,
                )
            }
        }
    }

    if (showPicker) {
        val initialUtc = remember(date) { (date ?: LocalDate.now()).toEpochDay() * MILLIS_PER_DAY }
        val state = rememberDatePickerState(initialSelectedDateMillis = initialUtc)
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    // Material's DatePicker speaks UTC-midnight millis: the picked calendar day.
                    state.selectedDateMillis?.let { utc ->
                        onTextChange(LocalDate.ofEpochDay(Math.floorDiv(utc, MILLIS_PER_DAY)).toString())
                    }
                    showPicker = false
                }) { Text(stringResource(R.string.date_done), color = c.accent) }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) {
                    Text(stringResource(R.string.common_cancel), color = c.accent)
                }
            },
        ) {
            DatePicker(state = state)
        }
    }
}

private const val MILLIS_PER_DAY = 86_400_000L
