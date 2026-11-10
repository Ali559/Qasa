package alibarznji.qasa.nfc

/**
 * One BER-TLV element (ISO/IEC 8825-1 as used by EMV Book 3 Annex B and ICAO 9303).
 * [tag] keeps every tag byte (`0x9F38`, `0x5F1F`, `0xBF0C`). A constructed element (bit 6
 * of the first tag byte) carries its parsed [children]; a primitive one only [value].
 */
class Tlv(val tag: Int, val value: ByteArray, val children: List<Tlv>) {
    val constructed: Boolean get() = children.isNotEmpty() || isConstructedTag(tag)

    /** The first element with [tag] in this element's subtree (itself included). */
    fun find(tag: Int): Tlv? {
        if (this.tag == tag) return this
        for (c in children) c.find(tag)?.let { return it }
        return null
    }

    /** Every element with [tag] in this subtree, in document order. */
    fun findAll(tag: Int): List<Tlv> {
        val out = mutableListOf<Tlv>()
        if (this.tag == tag) out += this
        children.forEach { out += it.findAll(tag) }
        return out
    }

    companion object {
        /**
         * Parses every element in [data]. Lenient where cards are sloppy: `00` / `FF` padding
         * between elements is skipped, and a value running past the end is cut at the end
         * (some cards miscount a trailing template). A constructed element whose value does
         * not parse as TLV keeps its bytes with no children.
         */
        fun parse(data: ByteArray): List<Tlv> = parse(data, 0, data.size, depth = 0)

        fun find(data: ByteArray, tag: Int): Tlv? = parse(data).firstNotNullOfOrNull { it.find(tag) }

        fun findAll(data: ByteArray, tag: Int): List<Tlv> = parse(data).flatMap { it.findAll(tag) }

        private const val MAX_DEPTH = 16

        private fun parse(data: ByteArray, start: Int, end: Int, depth: Int): List<Tlv> {
            val out = mutableListOf<Tlv>()
            var i = start
            while (i < end) {
                val first = data[i].toInt() and 0xFF
                if (first == 0x00 || first == 0xFF) { i++; continue }
                // Tag: low 5 bits all set means more tag bytes follow, each with bit 8 set
                // except the last.
                var tag = first
                i++
                if (first and 0x1F == 0x1F) {
                    do {
                        if (i >= end) throw TlvException("truncated tag")
                        val b = data[i].toInt() and 0xFF
                        tag = (tag shl 8) or b
                        i++
                    } while (b and 0x80 != 0 && tag ushr 24 == 0)
                }
                if (i >= end) throw TlvException("missing length")
                // Length: short form below 0x80, else 0x81..0x84 says how many bytes follow.
                var len = data[i].toInt() and 0xFF
                i++
                if (len and 0x80 != 0) {
                    val n = len and 0x7F
                    if (n == 0 || n > 3 || i + n > end) throw TlvException("bad length")
                    len = 0
                    repeat(n) { len = (len shl 8) or (data[i++].toInt() and 0xFF) }
                }
                val valueEnd = minOf(end, i + len)
                val value = data.copyOfRange(i, valueEnd)
                val children = if (isConstructedTag(tag) && depth < MAX_DEPTH) {
                    runCatching { parse(data, i, valueEnd, depth + 1) }.getOrDefault(emptyList())
                } else {
                    emptyList()
                }
                out += Tlv(tag, value, children)
                i = valueEnd
            }
            return out
        }

        /** Bit 6 of the first tag byte. */
        private fun isConstructedTag(tag: Int): Boolean {
            var first = tag
            while (first > 0xFF) first = first ushr 8
            return first and 0x20 != 0
        }

        /** Encodes one primitive element (lengths up to 0xFFFF). */
        fun encode(tag: Int, value: ByteArray): ByteArray {
            val tagBytes = when {
                tag > 0xFFFF -> bytes(tag shr 16, tag shr 8, tag)
                tag > 0xFF -> bytes(tag shr 8, tag)
                else -> bytes(tag)
            }
            val len = value.size
            val lenBytes = when {
                len < 0x80 -> bytes(len)
                len <= 0xFF -> bytes(0x81, len)
                else -> bytes(0x82, len shr 8, len)
            }
            return tagBytes + lenBytes + value
        }
    }
}

class TlvException(message: String) : Exception(message)
