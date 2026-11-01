package alibarznji.qasa.vault

/**
 * Port of iOS `Models/Country.swift`: the same 30 countries, English names and dial
 * codes, sorted by name. Payloads store the ISO code (`countryCode`), never the name.
 */
data class Country(val code: String, val name: String, val dialCode: String) {
    /** Country name with dial code — for phone number forms. */
    val displayNameWithDialCode: String get() = "$name ($dialCode)"

    fun pickerLabel(showsDialCode: Boolean): String = if (showsDialCode) displayNameWithDialCode else name

    companion object {
        val all: List<Country> = listOf(
            Country("IQ", "Iraq", "+964"),
            Country("US", "United States", "+1"),
            Country("GB", "United Kingdom", "+44"),
            Country("TR", "Turkey", "+90"),
            Country("DE", "Germany", "+49"),
            Country("FR", "France", "+33"),
            Country("AE", "United Arab Emirates", "+971"),
            Country("SA", "Saudi Arabia", "+966"),
            Country("JO", "Jordan", "+962"),
            Country("KW", "Kuwait", "+965"),
            Country("QA", "Qatar", "+974"),
            Country("BH", "Bahrain", "+973"),
            Country("EG", "Egypt", "+20"),
            Country("IR", "Iran", "+98"),
            Country("CA", "Canada", "+1"),
            Country("AU", "Australia", "+61"),
            Country("NL", "Netherlands", "+31"),
            Country("SE", "Sweden", "+46"),
            Country("NO", "Norway", "+47"),
            Country("CH", "Switzerland", "+41"),
            Country("IN", "India", "+91"),
            Country("PK", "Pakistan", "+92"),
            Country("CN", "China", "+86"),
            Country("JP", "Japan", "+81"),
            Country("KR", "South Korea", "+82"),
            Country("BR", "Brazil", "+55"),
            Country("MX", "Mexico", "+52"),
            Country("ES", "Spain", "+34"),
            Country("IT", "Italy", "+39"),
            Country("PL", "Poland", "+48"),
        ).sortedBy { it.name }

        fun dialCode(code: String): String = all.firstOrNull { it.code == code }?.dialCode ?: "+$code"

        fun name(code: String): String = all.firstOrNull { it.code == code }?.name ?: code
    }
}
