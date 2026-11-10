package alibarznji.qasa.nfc

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test
import java.time.LocalDate
import kotlin.random.Random

/** EMV contactless reads against hand-built card answers (test PANs, Luhn-valid). */
class EmvReaderTest {

    private fun t(tag: Int, vararg children: ByteArray) = Tlv.encode(tag, children.fold(ByteArray(0)) { a, b -> a + b })
    private fun t(tag: Int, hexValue: String) = Tlv.encode(tag, hex(hexValue))
    private val ok = hex("9000")

    private val visaAid = "A0000000031010"
    private val ppse = t(
        0x6F,
        t(0x84, "325041592E5359532E4444463031"),
        t(0xA5, t(0xBF0C, t(0x61, t(0x4F, visaAid), t(0x87, "01")))),
    )

    /** A card that answers by INS; records keyed by (record, P2). */
    private class FakeCard(
        val ppse: ByteArray?,
        val fci: Map<String, ByteArray>,
        val gpo: ByteArray?,
        val records: Map<Pair<Int, Int>, ByteArray> = emptyMap(),
    ) : CardChannel {
        val sent = mutableListOf<ByteArray>()
        override fun transceive(command: ByteArray): ByteArray {
            sent += command
            val ok = hex("9000")
            return when (command[1].toInt() and 0xFF) {
                0xA4 -> {
                    val name = command.copyOfRange(5, 5 + command[4])
                    if (name.toString(Charsets.ISO_8859_1) == "2PAY.SYS.DDF01") {
                        ppse?.plus(ok) ?: hex("6A82")
                    } else {
                        fci[name.toHex()]?.plus(ok) ?: hex("6A82")
                    }
                }
                0xA8 -> gpo?.plus(ok) ?: hex("6985")
                0xB2 -> records[(command[2].toInt() and 0xFF) to (command[3].toInt() and 0xFF)]?.plus(ok) ?: hex("6A83")
                else -> hex("6D00")
            }
        }
    }

    private fun reader(card: CardChannel) = EmvReader(card, today = { LocalDate.of(2026, 11, 10) }, random = Random(1))

    @Test fun visaTrack2InGpoAnswer() {
        val fci = t(0x6F, t(0x84, visaAid), t(0xA5, t(0x50, "56495341"), t(0x9F38, "9F66049F02069F37045F2A029A03")))
        val gpo = t(0x77, t(0x82, "2000"), t(0x94, "18010100"), t(0x57, "4111111111111111D28122011234567890000F"))
        val card = FakeCard(ppse, mapOf(visaAid to fci), gpo)
        assertEquals(EmvCard("4111111111111111", "12/28"), reader(card).read())

        // GPO carried the PDOL data: TTQ, zero amount, 4 random bytes, currency, date.
        val gpoCmd = card.sent.first { it[1] == 0xA8.toByte() }
        val dol = gpoCmd.copyOfRange(7, 7 + gpoCmd[6])
        assertEquals(4 + 6 + 4 + 2 + 3, dol.size)
        assertArrayEquals(hex("36004000"), dol.copyOfRange(0, 4))
        assertArrayEquals(ByteArray(6), dol.copyOfRange(4, 10))
        assertArrayEquals(hex("0840"), dol.copyOfRange(14, 16))
        assertArrayEquals(hex("261110"), dol.copyOfRange(16, 19))
    }

    @Test fun format1AnswerAndRecords() {
        val fci = t(0x6F, t(0x84, visaAid), t(0xA5, t(0x50, "56495341")))
        // Format 1: AIP 1980, AFL: SFI 1 records 1..2, SFI 2 record 1.
        val gpo = t(0x80, "1980" + "08010200" + "10010100")
        val records = mapOf(
            (1 to 0x0C) to t(0x70, t(0x9F42, "0978")),
            (2 to 0x0C) to t(0x70, t(0x5F20, "202F")),
            (1 to 0x14) to t(0x70, t(0x5A, "5555555555554444"), t(0x5F24, "290831")),
        )
        val card = FakeCard(ppse, mapOf(visaAid to fci), gpo, records)
        assertEquals(EmvCard("5555555555554444", "08/29"), reader(card).read())
        // No PDOL: the GPO template is `83 00`.
        val gpoCmd = card.sent.first { it[1] == 0xA8.toByte() }
        assertArrayEquals(hex("80A8000002830000"), gpoCmd)
    }

    @Test fun noPpseFallsBackToKnownAids() {
        val mc = "A0000000041010"
        val fci = t(0x6F, t(0x84, mc), t(0xA5, t(0x50, "4D415354455243415244")))
        val gpo = t(0x77, t(0x82, "1980"), t(0x94, "08010100"))
        val records = mapOf((1 to 0x0C) to t(0x70, t(0x9F6B, "5105105105105100D30066010000000000000F")))
        val card = FakeCard(ppse = null, fci = mapOf(mc to fci), gpo = gpo, records = records)
        assertEquals(EmvCard("5105105105105100", "06/30"), reader(card).read())
    }

    @Test fun oddPanPaddedWithF() {
        val r = EmvReader.extract(listOf(t(0x70, t(0x5A, "378282246310005F"), t(0x5F24, "270131"))))
        assertEquals(EmvCard("378282246310005", "01/27"), r)
    }

    @Test fun notAPaymentCard() {
        val card = FakeCard(ppse = null, fci = emptyMap(), gpo = null)
        try {
            reader(card).read()
            fail("expected Unsupported")
        } catch (e: NfcReadException) {
            assertEquals(NfcFailure.Unsupported, e.failure)
        }
    }

    @Test fun track2Parsing() {
        assertEquals("4111111111111111" to "2812", EmvReader.parseTrack2(hex("4111111111111111D28122011234567890000F")))
        assertNull(EmvReader.parseTrack2(hex("4111111111111111")))
    }

    @Test fun dolParsing() {
        assertEquals(
            listOf(0x9F66 to 4, 0x9F02 to 6, 0x95 to 5, 0x9F37 to 4),
            EmvReader.parseDol(hex("9F66049F02069505" + "9F3704")),
        )
    }

    @Test fun chainedAnswers() {
        // 6Cxx: resend with the right Le; 61xx: GET RESPONSE.
        val calls = mutableListOf<String>()
        val channel = CardChannel { cmd ->
            calls += cmd.toHex()
            when (cmd.toHex()) {
                "00B2010C00" -> hex("6C05")
                "00B2010C05" -> hex("0102") + hex("6103")
                "00C0000003" -> hex("030405") + hex("9000")
                else -> hex("6D00")
            }
        }
        val r = channel.send(hex("00B2010C00"))
        assertEquals(0x9000, r.sw)
        assertEquals("0102030405", r.data.toHex())
    }
}
