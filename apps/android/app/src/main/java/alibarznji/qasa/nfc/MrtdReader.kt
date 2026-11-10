package alibarznji.qasa.nfc

/**
 * Reads DG1 (the MRZ) of an ICAO 9303 eMRTD — passport or ID card — over BAC:
 *
 * 1. SELECT the LDS1 application `A0000002471001`.
 * 2. BAC with the MRZ key ([Bac]): GET CHALLENGE, EXTERNAL AUTHENTICATE, session keys.
 * 3. Under secure messaging: SELECT EF.DG1 (`0101`), READ BINARY its first bytes to learn
 *    the length, then the rest.
 *
 * Not supported: PACE-only chips (newer EU ID cards, some passports that dropped BAC).
 * They fail GET CHALLENGE or EXTERNAL AUTHENTICATE and come out as
 * [NfcFailure.Unsupported] / [NfcFailure.Access]. PACE needs ECDH over brainpool curves
 * and a generic mapping that the plain JCE cannot do without an extra library. Passive
 * authentication (checking EF.SOD's signature) is not done either: the values only fill an
 * editor the user reviews before saving.
 */
class MrtdReader(
    private val channel: CardChannel,
    private val bac: Bac,
) {
    fun readDg1(): MrzData {
        val select = ApduResponse.of(
            channel.transceive(bytes(0x00, 0xA4, 0x04, 0x0C, AID.size) + AID),
        )
        if (!select.ok) throw NfcReadException(NfcFailure.Unsupported, "no eMRTD application")
        val sm = SecureMessaging(bac.authenticate(channel))
        val dg1 = readFile(sm, DG1)
        return Mrz.parseDg1(dg1) ?: throw NfcReadException(NfcFailure.Unsupported, "DG1 has no MRZ")
    }

    private fun readFile(sm: SecureMessaging, fid: ByteArray): ByteArray {
        val sel = sm.unwrap(channel.transceive(sm.wrap(0x00, 0xA4, 0x02, 0x0C, fid, le = null)))
        if (!sel.ok) throw NfcReadException(NfcFailure.Unsupported, "SELECT file ${"%04X".format(sel.sw)}")
        val head = readBinary(sm, 0, 4)
        val total = tlvTotalLength(head)
        var out = head
        while (out.size < total) {
            val chunk = readBinary(sm, out.size, minOf(CHUNK, total - out.size))
            if (chunk.isEmpty()) break
            out += chunk
        }
        return out
    }

    private fun readBinary(sm: SecureMessaging, offset: Int, length: Int): ByteArray {
        val r = sm.unwrap(channel.transceive(sm.wrap(0x00, 0xB0, offset ushr 8, offset and 0xFF, null, le = length)))
        if (!r.ok) throw NfcReadException(NfcFailure.Unsupported, "READ BINARY ${"%04X".format(r.sw)}")
        return r.data
    }

    companion object {
        val AID: ByteArray = hex("A0000002471001")
        val DG1: ByteArray = bytes(0x01, 0x01)

        /** Small enough that the SM-wrapped answer stays under 256 bytes on every chip. */
        private const val CHUNK = 0xA0

        /** Tag + length bytes + value length of the TLV whose first bytes are [head]. */
        internal fun tlvTotalLength(head: ByteArray): Int {
            if (head.size < 2) throw NfcReadException(NfcFailure.Unsupported, "short file")
            var i = 1
            if (head[0].toInt() and 0x1F == 0x1F) i++
            val first = head[i].toInt() and 0xFF
            return when {
                first < 0x80 -> i + 1 + first
                first == 0x81 -> i + 2 + (head[i + 1].toInt() and 0xFF)
                first == 0x82 -> i + 3 + (((head[i + 1].toInt() and 0xFF) shl 8) or (head[i + 2].toInt() and 0xFF))
                else -> throw NfcReadException(NfcFailure.Unsupported, "bad file length")
            }
        }
    }
}
