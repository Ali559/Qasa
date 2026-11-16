package alibarznji.qasa.vault.crypto

/**
 * A site's `passwordrules` attribute (Apple's syntax, https://developer.apple.com/password-rules/):
 *
 *     required: upper; required: digit; allowed: lower, [-_]; max-consecutive: 2; minlength: 8; maxlength: 20;
 *
 * Each `required` property is one set the password needs at least one character from
 * (`required: upper, lower` = one upper or lower). `allowed` adds to what may appear. Unknown
 * properties are ignored; empty rules are no rules (R2, docs/autofill-hardening.md).
 */
data class PasswordRules(
    val required: List<Set<Char>> = emptyList(),
    /** Null when the rules name no `allowed` property. */
    val allowed: Set<Char>? = null,
    val minLength: Int? = null,
    val maxLength: Int? = null,
    val maxConsecutive: Int? = null,
) {
    val isEmpty: Boolean get() = required.isEmpty() && allowed == null && minLength == null && maxLength == null && maxConsecutive == null

    companion object {
        val UPPER: Set<Char> = ('A'..'Z').toSet()
        val LOWER: Set<Char> = ('a'..'z').toSet()
        val DIGIT: Set<Char> = ('0'..'9').toSet()
        /** Apple's `special`: ASCII printable minus letters and digits (space included). */
        val SPECIAL: Set<Char> = "-~!@#\$%^&*_+=`|(){}[:;\"'<>,.? ]/\\".toSet()
        val ASCII_PRINTABLE: Set<Char> = (0x20..0x7e).map { it.toChar() }.toSet()

        /** Null for a blank or absent attribute. Never throws: what it can't read it skips. */
        fun parse(text: String?): PasswordRules? {
            if (text.isNullOrBlank()) return null
            val required = mutableListOf<Set<Char>>()
            var allowed: MutableSet<Char>? = null
            var minLength: Int? = null
            var maxLength: Int? = null
            var maxConsecutive: Int? = null
            for (property in splitProperties(text)) {
                val colon = property.indexOf(':')
                if (colon < 0) continue
                val name = property.substring(0, colon).trim().lowercase()
                val value = property.substring(colon + 1).trim()
                when (name) {
                    "required" -> parseClasses(value).takeIf { it.isNotEmpty() }?.let { required += it }
                    "allowed" -> parseClasses(value).let { (allowed ?: mutableSetOf<Char>().also { s -> allowed = s }).addAll(it) }
                    "minlength" -> value.toIntOrNull()?.takeIf { it > 0 }?.let { minLength = maxOf(minLength ?: 0, it) }
                    "maxlength" -> value.toIntOrNull()?.takeIf { it > 0 }?.let { maxLength = minOf(maxLength ?: Int.MAX_VALUE, it) }
                    "max-consecutive" -> value.toIntOrNull()?.takeIf { it > 0 }?.let { maxConsecutive = minOf(maxConsecutive ?: Int.MAX_VALUE, it) }
                }
            }
            return PasswordRules(required, allowed, minLength, maxLength, maxConsecutive).takeUnless { it.isEmpty }
        }

        /** `;` separates properties, except inside a `[...]` set. */
        private fun splitProperties(text: String): List<String> {
            val out = mutableListOf<String>()
            val current = StringBuilder()
            var inSet = false
            var i = 0
            while (i < text.length) {
                val c = text[i]
                when {
                    !inSet && c == '[' -> { inSet = true; current.append(c) }
                    inSet && c == ']' && closesSet(text, i) -> { inSet = false; current.append(c) }
                    !inSet && c == ';' -> { out += current.toString(); current.clear() }
                    else -> current.append(c)
                }
                i++
            }
            if (current.isNotBlank()) out += current.toString()
            return out
        }

        /** A `]` ends a set unless the next one does (`[abc]]` holds `]`). */
        private fun closesSet(text: String, at: Int): Boolean = at + 1 >= text.length || text[at + 1] != ']'

        /** `upper, digit, [-_.]` → the union of those characters. */
        private fun parseClasses(value: String): Set<Char> {
            val out = mutableSetOf<Char>()
            var i = 0
            while (i < value.length) {
                val c = value[i]
                when {
                    c == '[' -> {
                        var j = i + 1
                        while (j < value.length && !(value[j] == ']' && closesSet(value, j))) j++
                        value.substring(i + 1, minOf(j, value.length)).forEach { ch -> if (ch in ASCII_PRINTABLE) out += ch }
                        i = j + 1
                    }
                    c.isLetter() -> {
                        var j = i
                        while (j < value.length && (value[j].isLetterOrDigit() || value[j] == '-')) j++
                        when (value.substring(i, j).lowercase()) {
                            "upper" -> out += UPPER
                            "lower" -> out += LOWER
                            "digit" -> out += DIGIT
                            "special" -> out += SPECIAL
                            // Qasa generates ASCII only, so "unicode" allows what ASCII allows.
                            "ascii-printable", "unicode" -> out += ASCII_PRINTABLE
                        }
                        i = j
                    }
                    else -> i++
                }
            }
            return out
        }
    }
}

/**
 * What one generated password must satisfy: its [length], the sets it needs one character
 * from each of, the characters it may use, and the longest run of one character.
 */
data class PasswordSpec(
    val length: Int = PasswordGenerator.DEFAULT_LENGTH,
    val classes: List<Set<Char>> = PasswordGenerator.DEFAULT_CLASSES,
    val maxConsecutive: Int? = null,
) {
    companion object {
        /**
         * R2: default 16, clamped to the smallest [maxLengths] when that is 8 or more (4–7:
         * used anyway); raised to [minLengths]/the rules' minlength; [numeric] fields get digits only.
         * Symbols only from the generator's own set, and only when the rules allow them.
         */
        fun forFields(
            maxLengths: List<Int?>,
            minLengths: List<Int?> = emptyList(),
            rules: PasswordRules? = null,
            numeric: Boolean = false,
        ): PasswordSpec {
            val max = (maxLengths.filterNotNull() + listOfNotNull(rules?.maxLength)).filter { it > 0 }.minOrNull()
            val min = (minLengths.filterNotNull() + listOfNotNull(rules?.minLength)).filter { it > 0 }.maxOrNull()
            var length = PasswordGenerator.DEFAULT_LENGTH
            if (max != null && max >= 4) length = minOf(length, max)
            if (min != null && min > length) length = if (max != null && max >= 4) minOf(min, max) else min
            length = length.coerceIn(1, PasswordGenerator.MAX_LENGTH)

            val classes: List<Set<Char>> = if (numeric) {
                listOf(PasswordRules.DIGIT)
            } else {
                narrow(rules)
            }
            return PasswordSpec(length = length, classes = classes.take(length), maxConsecutive = rules?.maxConsecutive)
        }

        /** The generator's classes, narrowed by [rules]; the site's required sets stay required. */
        private fun narrow(rules: PasswordRules?): List<Set<Char>> {
            if (rules == null || (rules.required.isEmpty() && rules.allowed == null)) return PasswordGenerator.DEFAULT_CLASSES
            val generatorChars = PasswordGenerator.DEFAULT_CLASSES.flatten().toSet()
            // Apple: with no `allowed`, the required sets are all that is allowed.
            val allowed = (rules.allowed.orEmpty() + rules.required.flatten()).toSet()
            val required = rules.required.mapNotNull { set ->
                // A site may require a symbol outside our set ("[~]"): take it from the site then.
                (set intersect generatorChars).ifEmpty { set - ' ' }.takeIf { it.isNotEmpty() }
            }
            val optional = PasswordGenerator.DEFAULT_CLASSES
                .map { it intersect allowed }
                .filter { cls -> cls.isNotEmpty() && required.none { it.containsAll(cls) } }
            val classes = required + optional
            return classes.ifEmpty { PasswordGenerator.DEFAULT_CLASSES }
        }
    }
}
