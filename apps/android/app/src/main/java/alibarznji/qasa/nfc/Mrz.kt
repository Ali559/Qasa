package alibarznji.qasa.nfc

import java.time.LocalDate

/** What the sheet fills from an eMRTD's DG1. */
data class MrzData(
    /** `P`, `I`, `A`, `C`, … (first one or two MRZ characters, fillers dropped). */
    val documentCode: String,
    /** ISO 3166-1 alpha-3 (or ICAO code such as `D` or `GBD`), fillers dropped. */
    val issuingState: String,
    val surname: String,
    val givenNames: String,
    val documentNumber: String,
    val nationality: String,
    /** YYMMDD as printed. */
    val birthDate: String,
    /** YYMMDD as printed. */
    val expiryDate: String,
) {
    /** "Given Surname", in title case (the MRZ is all capitals). */
    val displayName: String
        get() = listOf(givenNames, surname).filter { it.isNotBlank() }.joinToString(" ") { titleCase(it) }

    /** Expiry as `YYYY-MM-DD` (the vault's date form), or null if it does not parse. */
    val expiryIso: String? get() = Mrz.yymmddToDate(expiryDate, pivotFuture = true)?.toString()

    /** ISO 3166-1 alpha-2 for the vault's country picker: issuing state, else nationality. */
    val countryCodeIso2: String? get() = Mrz.iso2(issuingState) ?: Mrz.iso2(nationality)

    private fun titleCase(s: String) = s.split(' ').filter { it.isNotEmpty() }.joinToString(" ") { word ->
        word.lowercase().replaceFirstChar { it.uppercaseChar() }
    }
}

/** ICAO 9303 Part 3 (check digits) and Parts 4–6 (TD3 / TD2 / TD1 machine readable zones). */
object Mrz {
    /** Check digit: weights 7, 3, 1; digits as is, A–Z as 10–35, `<` as 0. */
    fun checkDigit(s: String): Char {
        var sum = 0
        s.forEachIndexed { i, ch ->
            val v = when (ch) {
                in '0'..'9' -> ch - '0'
                in 'A'..'Z' -> ch - 'A' + 10
                else -> 0
            }
            sum += v * WEIGHTS[i % 3]
        }
        return '0' + sum % 10
    }

    private val WEIGHTS = intArrayOf(7, 3, 1)

    /**
     * The BAC key seed input (9303 Part 11 §4.3.2.1 / §9.7.1): document number (padded with
     * `<` to 9; a longer TD1 number is used whole) + check digit, birth date YYMMDD + check
     * digit, expiry YYMMDD + check digit.
     */
    fun bacKeyInfo(documentNumber: String, birthDate: LocalDate, expiryDate: LocalDate): String {
        val number = normalizeDocumentNumber(documentNumber).padEnd(9, '<')
        val dob = yymmdd(birthDate)
        val exp = yymmdd(expiryDate)
        return number + checkDigit(number) + dob + checkDigit(dob) + exp + checkDigit(exp)
    }

    /** Upper case, spaces and dashes removed: what is printed in the MRZ. */
    fun normalizeDocumentNumber(raw: String): String =
        raw.uppercase().filter { it in 'A'..'Z' || it in '0'..'9' }

    fun yymmdd(date: LocalDate): String = "%02d%02d%02d".format(date.year % 100, date.monthValue, date.dayOfMonth)

    /**
     * YYMMDD to a date. Expiry dates pivot into the future (20YY unless that is more than
     * 50 years ahead); birth dates into the past (19YY when 20YY would be after today).
     */
    fun yymmddToDate(s: String, pivotFuture: Boolean, today: LocalDate = LocalDate.now()): LocalDate? {
        if (s.length != 6 || !s.all(Char::isDigit)) return null
        val yy = s.substring(0, 2).toInt()
        val century2000 = 2000 + yy
        val year = if (pivotFuture) {
            if (century2000 > today.year + 50) 1900 + yy else century2000
        } else {
            if (century2000 > today.year) 1900 + yy else century2000
        }
        return runCatching { LocalDate.of(year, s.substring(2, 4).toInt(), s.substring(4, 6).toInt()) }.getOrNull()
    }

    /**
     * Parses DG1 (9303 Part 10 §4.7.1: `61 { 5F1F mrz }`) or a bare MRZ. 90 characters is
     * TD1 (3×30, ID cards), 72 is TD2 (2×36), 88 is TD3 (2×44, passports). Line breaks are
     * ignored. Null when the length matches none of them.
     */
    fun parseDg1(dg1: ByteArray): MrzData? {
        val mrz = Tlv.find(dg1, 0x5F1F)?.value?.toString(Charsets.US_ASCII) ?: return null
        return parse(mrz)
    }

    fun parse(raw: String): MrzData? {
        val s = raw.filterNot { it == '\n' || it == '\r' || it == ' ' }.uppercase()
        return when (s.length) {
            90 -> parseTd1(s)
            72 -> parseTd2or3(s, lineLength = 36)
            88 -> parseTd2or3(s, lineLength = 44)
            else -> null
        }
    }

    private fun parseTd1(s: String): MrzData {
        val l1 = s.substring(0, 30)
        val l2 = s.substring(30, 60)
        val l3 = s.substring(60, 90)
        var number = l1.substring(5, 14)
        // 9303 Part 5 §4.2.2 note j: a number longer than 9 characters puts `<` in the check
        // digit position and continues in the optional data, ending with its check digit.
        if (l1[14] == '<') {
            val rest = l1.substring(15).substringBefore('<')
            if (rest.length >= 2) number += rest.dropLast(1)
        }
        val (surname, given) = parseName(l3)
        return MrzData(
            documentCode = clean(l1.substring(0, 2)),
            issuingState = clean(l1.substring(2, 5)),
            surname = surname,
            givenNames = given,
            documentNumber = clean(number),
            nationality = clean(l2.substring(15, 18)),
            birthDate = l2.substring(0, 6),
            expiryDate = l2.substring(8, 14),
        )
    }

    private fun parseTd2or3(s: String, lineLength: Int): MrzData {
        val l1 = s.substring(0, lineLength)
        val l2 = s.substring(lineLength, lineLength * 2)
        val (surname, given) = parseName(l1.substring(5))
        return MrzData(
            documentCode = clean(l1.substring(0, 2)),
            issuingState = clean(l1.substring(2, 5)),
            surname = surname,
            givenNames = given,
            documentNumber = clean(l2.substring(0, 9)),
            nationality = clean(l2.substring(10, 13)),
            birthDate = l2.substring(13, 19),
            expiryDate = l2.substring(21, 27),
        )
    }

    /** `SURNAME<<GIVEN<NAMES<<<<` → ("SURNAME", "GIVEN NAMES"). */
    private fun parseName(field: String): Pair<String, String> {
        val trimmed = field.trimEnd('<')
        val surname = trimmed.substringBefore("<<").replace('<', ' ').trim()
        val given = if (trimmed.contains("<<")) trimmed.substringAfter("<<").replace('<', ' ').trim() else ""
        return surname to given.replace(Regex(" +"), " ")
    }

    private fun clean(s: String) = s.replace("<", "")

    /**
     * ICAO 9303 Part 3 three-letter codes for the countries in `vault/Country.kt`, plus the
     * ICAO-only variants (Germany prints `D`, the UK its territories' citizen codes).
     */
    private val ISO3_TO_ISO2 = mapOf(
        "AUS" to "AU", "BHR" to "BH", "BRA" to "BR", "CAN" to "CA", "CHN" to "CN",
        "EGY" to "EG", "FRA" to "FR", "DEU" to "DE", "D" to "DE", "IND" to "IN",
        "IRN" to "IR", "IRQ" to "IQ", "ITA" to "IT", "JPN" to "JP", "JOR" to "JO",
        "KWT" to "KW", "MEX" to "MX", "NLD" to "NL", "NOR" to "NO", "PAK" to "PK",
        "POL" to "PL", "QAT" to "QA", "SAU" to "SA", "KOR" to "KR", "ESP" to "ES",
        "SWE" to "SE", "CHE" to "CH", "TUR" to "TR", "ARE" to "AE", "GBR" to "GB",
        "GBD" to "GB", "GBN" to "GB", "GBO" to "GB", "GBP" to "GB", "GBS" to "GB",
        "USA" to "US",
    )

    /** The vault country code for an MRZ state code, or null when the vault does not list it. */
    fun iso2(code: String): String? = ISO3_TO_ISO2[code.replace("<", "").uppercase()]
}
