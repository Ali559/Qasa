package alibarznji.qasa.nfc

import java.time.LocalDate
import kotlin.random.Random

/** What a contactless payment card gives up: never the CVV, rarely the name. */
data class EmvCard(
    /** Digits only. */
    val pan: String,
    /** `MM/YY`, or null when the card does not say. */
    val expiry: String?,
)

/**
 * Reads the PAN and expiry of a contactless EMV card (EMV Book 1 §11–12, Book 3 §5–10,
 * Book C-2/C-3 for the contactless kernels):
 *
 * 1. SELECT the PPSE `2PAY.SYS.DDF01` and collect the application AIDs (tag 4F), in
 *    priority order (tag 87); fall back to the common AIDs when the card has no PPSE.
 * 2. SELECT each AID until one answers 9000.
 * 3. GET PROCESSING OPTIONS with the PDOL (tag 9F38) filled in, or `83 00` without one.
 * 4. Look for the PAN in the GPO answer (format 2 / tag 77 often carries Track 2), then
 *    READ RECORD every record the AFL (tag 94, or bytes 3.. of a format 1 / tag 80
 *    answer) lists, until the PAN shows up.
 *
 * Nothing is written to the card and no cryptogram is used: the GPO is the reader's half
 * of a zero-amount transaction that never goes online.
 */
class EmvReader(
    private val channel: CardChannel,
    private val today: () -> LocalDate = { LocalDate.now() },
    private val random: Random = Random.Default,
) {
    fun read(): EmvCard {
        val aids = selectPpse().ifEmpty { FALLBACK_AIDS.map(::hex) }
        for (aid in aids) {
            val fci = select(aid) ?: continue
            val card = readApplication(fci)
            if (card != null) return card
        }
        throw NfcReadException(NfcFailure.Unsupported, "no application gave a PAN")
    }

    /** AIDs from the PPSE directory, highest priority (lowest tag 87 value) first. */
    private fun selectPpse(): List<ByteArray> {
        val fci = select(PPSE) ?: return emptyList()
        return Tlv.findAll(fci, 0x61)
            .mapNotNull { entry ->
                val aid = entry.find(0x4F)?.value ?: return@mapNotNull null
                val priority = entry.find(0x87)?.value?.firstOrNull()?.toInt()?.and(0x0F)
                aid to (priority?.takeIf { it != 0 } ?: 15)
            }
            .sortedBy { it.second }
            .map { it.first }
    }

    private fun select(name: ByteArray): ByteArray? {
        val r = channel.send(bytes(0x00, 0xA4, 0x04, 0x00, name.size) + name + bytes(0x00))
        return if (r.ok) r.data else null
    }

    private fun readApplication(fci: ByteArray): EmvCard? {
        val pdol = Tlv.find(fci, 0x9F38)?.value
        val gpo = getProcessingOptions(pdol) ?: return null
        extract(listOf(gpo))?.let { return it }

        val records = mutableListOf<ByteArray>()
        for (entry in afl(gpo)) {
            for (record in entry.first..entry.last) {
                val p2 = (entry.sfi shl 3) or 0x04
                val r = channel.send(bytes(0x00, 0xB2, record, p2, 0x00))
                if (!r.ok) continue
                records += r.data
                extract(records)?.let { return it }
            }
        }
        return null
    }

    private fun getProcessingOptions(pdol: ByteArray?): ByteArray? {
        val dolData = pdol?.let { buildDol(it) } ?: ByteArray(0)
        val template = Tlv.encode(0x83, dolData)
        val r = channel.send(bytes(0x80, 0xA8, 0x00, 0x00, template.size) + template + bytes(0x00))
        return if (r.ok) r.data else null
    }

    /**
     * The PDOL's data: for each (tag, length) the terminal value, zero-filled for tags we
     * have no value for. The few tags qVSDC / PayPass kernels check before answering get
     * plausible values (an online-capable reader, a zero amount, today's date, a random
     * unpredictable number), since some Visa cards refuse a GPO whose TTQ is all zero.
     */
    internal fun buildDol(dol: ByteArray): ByteArray {
        var out = ByteArray(0)
        for ((tag, len) in parseDol(dol)) {
            val known = terminalValue(tag)
            val value = ByteArray(len)
            if (known != null) {
                // When the card asks for a different length: truncate or zero-pad on the right.
                known.copyInto(value, 0, 0, minOf(len, known.size))
            }
            out += value
        }
        return out
    }

    private fun terminalValue(tag: Int): ByteArray? = when (tag) {
        0x9F66 -> bytes(0x36, 0x00, 0x40, 0x00) // TTQ: qVSDC + contact + online PIN + signature
        0x9F1A, 0x5F2A -> bytes(0x08, 0x40) // terminal country / transaction currency
        0x9A -> today().let { bcd("%02d%02d%02d".format(it.year % 100, it.monthValue, it.dayOfMonth)) }
        0x9C -> bytes(0x00) // goods and services
        0x9F37 -> random.nextBytes(4) // unpredictable number
        0x9F35 -> bytes(0x22) // attended, offline with online capability
        0x9F33 -> bytes(0xE0, 0xF0, 0xC8) // terminal capabilities
        0x9F40 -> bytes(0x8E, 0x00, 0xB0, 0x50, 0x05)
        else -> null
    }

    internal data class AflEntry(val sfi: Int, val first: Int, val last: Int)

    /** AFL from a format 2 (tag 94) or format 1 (tag 80: AIP 2 bytes, then the AFL) answer. */
    internal fun afl(gpo: ByteArray): List<AflEntry> {
        val top = Tlv.parse(gpo).firstOrNull()
        val raw = when (top?.tag) {
            0x80 -> top.value.drop(2).toByteArray()
            else -> Tlv.find(gpo, 0x94)?.value ?: return emptyList()
        }
        return (0 until raw.size / 4).mapNotNull { i ->
            val sfi = (raw[i * 4].toInt() and 0xFF) shr 3
            val first = raw[i * 4 + 1].toInt() and 0xFF
            val last = raw[i * 4 + 2].toInt() and 0xFF
            if (sfi in 1..30 && first >= 1 && last >= first) AflEntry(sfi, first, last) else null
        }
    }

    companion object {
        private val PPSE = "2PAY.SYS.DDF01".toByteArray(Charsets.US_ASCII)

        /** Visa, Mastercard, Maestro, Amex, Discover, JCB, UnionPay, Mir, Interac. */
        private val FALLBACK_AIDS = listOf(
            "A0000000031010", "A0000000032010", "A0000000041010", "A0000000043060",
            "A00000002501", "A0000001523010", "A0000000651010", "A000000333010101",
            "A000000333010102", "A0000006581010", "A0000002771010",
        )

        /** (tag, length) pairs of a data object list. */
        internal fun parseDol(dol: ByteArray): List<Pair<Int, Int>> {
            val out = mutableListOf<Pair<Int, Int>>()
            var i = 0
            while (i < dol.size) {
                var tag = dol[i].toInt() and 0xFF
                i++
                if (tag and 0x1F == 0x1F) {
                    while (i < dol.size) {
                        val b = dol[i].toInt() and 0xFF
                        tag = (tag shl 8) or b
                        i++
                        if (b and 0x80 == 0) break
                    }
                }
                if (i >= dol.size) break
                out += tag to (dol[i].toInt() and 0xFF)
                i++
            }
            return out
        }

        /**
         * PAN and expiry from any of [responses]: tag 5A (PAN, BCD, F-padded) and 5F24
         * (expiry YYMMDD), else Track 2 Equivalent Data (57) or Mastercard's Track 2 (9F6B):
         * `PAN D YYMM service-code …`, F-padded.
         */
        fun extract(responses: List<ByteArray>): EmvCard? {
            val tlvs = responses.flatMap { runCatching { Tlv.parse(it) }.getOrDefault(emptyList()) }
            fun find(tag: Int) = tlvs.firstNotNullOfOrNull { it.find(tag) }?.value

            val track2 = (find(0x57) ?: find(0x9F6B))?.let(::parseTrack2)
            val pan = find(0x5A)?.let { nibbles(it).takeWhile(Char::isDigit) }?.takeIf(::plausiblePan)
                ?: track2?.first?.takeIf(::plausiblePan)
                ?: return null
            val expiry = find(0x5F24)?.let { nibbles(it).take(4) }?.takeIf(::plausibleYymm)
                ?: track2?.second?.takeIf(::plausibleYymm)
            return EmvCard(pan, expiry?.let { "${it.substring(2, 4)}/${it.substring(0, 2)}" })
        }

        /** (PAN, YYMM) from Track 2 Equivalent Data. */
        internal fun parseTrack2(raw: ByteArray): Pair<String, String?>? {
            val s = nibbles(raw)
            val sep = s.indexOf('D')
            if (sep <= 0) return null
            val pan = s.substring(0, sep)
            if (!pan.all(Char::isDigit)) return null
            val yymm = s.substring(sep + 1).take(4).takeIf { it.length == 4 && it.all(Char::isDigit) }
            return pan to yymm
        }

        private fun nibbles(raw: ByteArray): String = raw.toHex()

        private fun plausiblePan(pan: String) = pan.length in 12..19 && pan.all(Char::isDigit)

        private fun plausibleYymm(yymm: String) =
            yymm.length == 4 && yymm.all(Char::isDigit) && yymm.substring(2, 4).toInt() in 1..12

        private fun bcd(digits: String): ByteArray = hex(digits)
    }
}
