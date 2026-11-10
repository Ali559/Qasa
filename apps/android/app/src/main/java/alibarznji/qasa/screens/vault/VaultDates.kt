package alibarznji.qasa.screens.vault

import alibarznji.qasa.vault.VaultValidation
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Stored item dates (ISO `YYYY-MM-DD`, or a legacy form Qasa still reads) shown in the
 * user's locale: the `MMMdyyyy` skeleton iOS uses for its date fields.
 */
object VaultDates {
    fun parse(raw: String): LocalDate? =
        if (raw.isBlank()) null else runCatching { VaultValidation.parseDate(raw) }.getOrNull()

    fun format(date: LocalDate, locale: Locale): String {
        val pattern = android.text.format.DateFormat.getBestDateTimePattern(locale, "MMMdyyyy")
        return DateTimeFormatter.ofPattern(pattern, locale).format(date)
    }

    /** [raw] localized when it parses, else as stored. */
    fun display(raw: String, locale: Locale): String = parse(raw)?.let { format(it, locale) } ?: raw
}
