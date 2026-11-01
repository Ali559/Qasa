package alibarznji.qasa.vault.csv

/**
 * RFC 4180 primitives, a line-for-line port of iOS `VaultCSV.swift` ("RFC 4180 primitives").
 * Kept identical so a file written by either app splits the same way on the other.
 */
internal object CsvPrimitives {

    /** iOS `csvEscape`: quote only when the cell contains a comma, quote, LF or CR. */
    fun escape(value: String): String =
        if (value.contains(',') || value.contains('"') || value.contains('\n') || value.contains('\r')) {
            "\"" + value.replace("\"", "\"\"") + "\""
        } else {
            value
        }

    /**
     * iOS `parseRecords`: splits on newlines that are not inside a quoted field, keeping the
     * quotes in the record for [parseLine]. Swift iterates grapheme clusters, so "\r\n" is one
     * newline there; here it yields an extra empty record, which the caller filters out
     * exactly as iOS filters blank records.
     */
    fun parseRecords(csv: String): List<String> {
        val records = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < csv.length) {
            val ch = csv[i]
            if (ch == '"') {
                current.append(ch)
                if (inQuotes) {
                    // A doubled quote is a literal quote, not the field terminator.
                    if (i + 1 < csv.length && csv[i + 1] == '"') {
                        current.append('"')
                        i++
                    } else {
                        inQuotes = false
                    }
                } else {
                    inQuotes = true
                }
            } else if (isNewline(ch) && !inQuotes) {
                records.add(current.toString())
                current.clear()
            } else {
                current.append(ch)
            }
            i++
        }
        records.add(current.toString())
        return records
    }

    /** iOS `parseLine`: field splitter honouring quoted commas/newlines and `""` escapes. */
    fun parseLine(line: String): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val ch = line[i]
            if (inQuotes) {
                if (ch == '"') {
                    if (i + 1 < line.length && line[i + 1] == '"') {
                        current.append('"')
                        i++
                    } else {
                        inQuotes = false
                    }
                } else {
                    current.append(ch)
                }
            } else {
                when (ch) {
                    '"' -> inQuotes = true
                    ',' -> {
                        result.add(current.toString())
                        current.clear()
                    }
                    else -> current.append(ch)
                }
            }
            i++
        }
        result.add(current.toString())
        return result
    }

    /** Swift `Character.isNewline`. */
    private fun isNewline(ch: Char) =
        ch == '\n' || ch == '\r' || ch == '\u000B' || ch == '\u000C' ||
            ch == '\u0085' || ch == ' ' || ch == ' '
}

/**
 * Writes a flat JSON object the way iOS `JSONEncoder` with `.sortedKeys` does, so the CSV
 * `fields` column (and therefore [VaultCsv.fingerprint]) is byte-identical across apps:
 * keys sorted, no whitespace, `/` escaped as `\/` (Apple's default), non-ASCII left raw,
 * `null` values omitted (Swift `encodeIfPresent` for optional UUIDs).
 */
internal object IosJsonWriter {
    fun write(fields: Map<String, Any?>): String {
        val sb = StringBuilder("{")
        var first = true
        for (key in fields.keys.sorted()) {
            val value = fields[key] ?: continue
            if (!first) sb.append(',')
            first = false
            appendString(sb, key)
            sb.append(':')
            when (value) {
                is Boolean -> sb.append(if (value) "true" else "false")
                else -> appendString(sb, value.toString())
            }
        }
        return sb.append('}').toString()
    }

    private fun appendString(sb: StringBuilder, s: String) {
        sb.append('"')
        for (ch in s) {
            when (ch) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '/' -> sb.append("\\/")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                '\b' -> sb.append("\\b")
                '\u000C' -> sb.append("\\f")
                else -> if (ch < ' ') sb.append("\\u%04x".format(ch.code)) else sb.append(ch)
            }
        }
        sb.append('"')
    }
}
