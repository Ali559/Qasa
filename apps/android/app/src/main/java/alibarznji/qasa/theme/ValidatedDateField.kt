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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import java.text.DateFormat
import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Port of iOS `Components/ValidatedDateField.swift`: a free-text date field that shows
 * "Use e.g. Nov 6 2000" when what was typed doesn't parse, plus a "Pick date" button.
 *
 * iOS expands an inline graphical `DatePicker` with a "Done" button; the Android idiom is
 * the Material date-picker dialog, whose confirm button carries the same "Done" copy. A
 * picked date is written back in the locale's `MMMdyyyy` skeleton, as iOS does.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ValidatedDateField(
    title: String,
    text: String,
    onTextChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = qasaColors
    val locale = LocalConfiguration.current.locales[0] ?: Locale.getDefault()
    var showPicker by remember { mutableStateOf(false) }
    // iOS validates on change only, so a stored-but-unparseable value shows no error
    // until the user edits it.
    var parseError by remember { mutableStateOf(false) }

    Column(modifier.fillMaxWidth()) {
        SectionLabel(title)
        Spacer(Modifier.height(QasaDimens.contentGap))
        ThemedTextField(
            value = text,
            onValueChange = {
                onTextChange(it)
                parseError = it.isNotBlank() && DateFieldParser.parse(it, locale) == null
            },
            placeholder = title,
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
            if (parseError) {
                Text(
                    stringResource(R.string.date_invalidformat),
                    color = c.danger,
                    style = qasaText.errorDetail,
                    textAlign = TextAlign.End,
                )
            }
        }
    }

    if (showPicker) {
        val initialUtc = remember(text) { DateFieldParser.parse(text, locale)?.let(::localDateToUtcMidnight) }
        val state = rememberDatePickerState(initialSelectedDateMillis = initialUtc ?: todayUtcMidnight())
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { utc ->
                        onTextChange(DateFieldParser.display(utcMidnightToLocalDate(utc), locale))
                        parseError = false
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

/** iOS `ValidatedDateField.parse` / `isValid`, reusable outside the composable. */
object DateFieldParser {
    // Same strict formats, same order, as iOS `parsers` (en_US_POSIX, non-lenient).
    private val formats = listOf("MMM d yyyy", "MMM d, yyyy", "yyyy-MM-dd", "dd/MM/yyyy", "MM/dd/yyyy")

    fun parse(raw: String, locale: Locale = Locale.getDefault()): Date? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        for (pattern in formats) {
            strict(SimpleDateFormat(pattern, Locale.US), trimmed)?.let { return it }
        }
        // Locale-aware fallback for typed dates in ckb/ar (iOS `dateStyle = .medium`).
        return strict(DateFormat.getDateInstance(DateFormat.MEDIUM, locale), trimmed)
    }

    fun isValid(raw: String, locale: Locale = Locale.getDefault()): Boolean =
        raw.isBlank() || parse(raw, locale) != null

    /** iOS `displayFormatter`: `setLocalizedDateFormatFromTemplate("MMMdyyyy")`. */
    fun display(date: Date, locale: Locale): String {
        val pattern = android.text.format.DateFormat.getBestDateTimePattern(locale, "MMMdyyyy")
        return SimpleDateFormat(pattern, locale).format(date)
    }

    /** Non-lenient and must consume the whole string, like `DateFormatter.date(from:)`. */
    private fun strict(format: DateFormat, text: String): Date? {
        format.isLenient = false
        val pos = ParsePosition(0)
        val date = runCatching { format.parse(text, pos) }.getOrNull() ?: return null
        return if (pos.index == text.length) date else null
    }
}

// Material's DatePicker speaks UTC-midnight millis; payloads hold local calendar dates.
private fun localDateToUtcMidnight(date: Date): Long {
    val local = Calendar.getInstance().apply { time = date }
    return Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
        clear()
        set(local.get(Calendar.YEAR), local.get(Calendar.MONTH), local.get(Calendar.DAY_OF_MONTH))
    }.timeInMillis
}

private fun todayUtcMidnight(): Long = localDateToUtcMidnight(Date())

private fun utcMidnightToLocalDate(utc: Long): Date {
    val u = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = utc }
    return Calendar.getInstance().apply {
        clear()
        set(u.get(Calendar.YEAR), u.get(Calendar.MONTH), u.get(Calendar.DAY_OF_MONTH))
    }.time
}
