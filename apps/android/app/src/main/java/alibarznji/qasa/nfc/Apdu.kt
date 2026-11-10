package alibarznji.qasa.nfc

/**
 * The one thing the chip readers need from Android: send a command APDU, get the response
 * APDU (data plus the two status bytes). [NfcTagReader] backs it with `IsoDep`; the unit
 * tests back it with canned responses. No android imports here, so the EMV and eMRTD
 * readers run on the JVM.
 */
fun interface CardChannel {
    /** Throws [NfcReadException] ([NfcFailure.Lost]) when the chip leaves the field. */
    fun transceive(command: ByteArray): ByteArray
}

/** Why a read stopped; the sheet maps each to one message. */
enum class NfcFailure {
    /** The chip left the field mid-read. */
    Lost,
    /** Not a payment card / eMRTD we can read (no PPSE, no PAN, PACE-only chip, …). */
    Unsupported,
    /** The eMRTD refused BAC: the document number or dates do not match the chip. */
    Access,
}

class NfcReadException(val failure: NfcFailure, message: String? = null, cause: Throwable? = null) :
    Exception(message ?: failure.name, cause)

/** A response APDU split into its data and status word. */
class ApduResponse(val data: ByteArray, val sw: Int) {
    val ok: Boolean get() = sw == SW_OK

    companion object {
        const val SW_OK = 0x9000

        fun of(raw: ByteArray): ApduResponse {
            if (raw.size < 2) throw NfcReadException(NfcFailure.Unsupported, "short response")
            val sw = ((raw[raw.size - 2].toInt() and 0xFF) shl 8) or (raw[raw.size - 1].toInt() and 0xFF)
            return ApduResponse(raw.copyOfRange(0, raw.size - 2), sw)
        }
    }
}

/**
 * Sends [command] and follows the T=0 style chaining some cards still use over ISO-DEP:
 * `61xx` (more data: GET RESPONSE) and `6Cxx` (wrong Le: resend with xx).
 */
internal fun CardChannel.send(command: ByteArray): ApduResponse {
    var r = ApduResponse.of(transceive(command))
    if (r.sw shr 8 == 0x6C) {
        val again = command.copyOf()
        again[again.size - 1] = (r.sw and 0xFF).toByte()
        r = ApduResponse.of(transceive(again))
    }
    var data = r.data
    var guard = 0
    while (r.sw shr 8 == 0x61 && guard++ < 32) {
        r = ApduResponse.of(transceive(byteArrayOf(0x00, 0xC0.toByte(), 0x00, 0x00, (r.sw and 0xFF).toByte())))
        data += r.data
    }
    return ApduResponse(data, r.sw)
}

internal fun ByteArray.toHex(): String = joinToString("") { "%02X".format(it.toInt() and 0xFF) }

internal fun hex(s: String): ByteArray {
    val clean = s.filterNot { it.isWhitespace() }
    require(clean.length % 2 == 0) { "odd hex length" }
    return ByteArray(clean.length / 2) { clean.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
}

internal fun bytes(vararg values: Int): ByteArray = ByteArray(values.size) { values[it].toByte() }
