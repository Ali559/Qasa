package alibarznji.qasa.nfc

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.time.LocalDate

/**
 * ICAO Doc 9303 Part 11, Appendix D ("Worked example: Basic Access Control"): key
 * derivation (D.2), mutual authentication and session keys (D.3), and secure messaging of
 * SELECT EF.COM and two READ BINARYs (D.4).
 */
class BacTest {
    private val mrzInfo = "L898902C<369080619406236"
    private val rndIcc = hex("4608F91988702212")
    private val rndIfd = hex("781723860C06C226")
    private val kIfd = hex("0B795240CB7049B01C19B33E32804F0B")

    private fun bac() = Bac(mrzInfo)

    @Test fun mrzInformation() {
        assertEquals(mrzInfo, Mrz.bacKeyInfo("L898902C", LocalDate.of(1969, 8, 6), LocalDate.of(1994, 6, 23)))
    }

    @Test fun keyDerivation() {
        assertEquals("239AB9CB282DAF66231DC5A4DF6BFBAE", BacCrypto.keySeed(mrzInfo).toHex())
        val bac = bac()
        assertEquals("AB94FDECF2674FDFB9B391F85D7F76F2", bac.kEnc.toHex())
        assertEquals("7962D9ECE03D1ACD4C76089DCE131543", bac.kMac.toHex())
    }

    @Test fun parityAdjustment() {
        // D.2: Ka before / after parity for Kenc.
        assertEquals("AB94FDECF2674FDF", BacCrypto.adjustParity(hex("AB94FCEDF2664EDF")).toHex())
    }

    @Test fun mutualAuthenticateCommand() {
        val data = bac().mutualAuthData(rndIfd, rndIcc, kIfd)
        assertEquals(
            "72C29C2371CC9BDB65B779B8E8D37B29ECC154AA56A8799FAE2F498F76ED92F2" + "5F1448EEA8AD90A7",
            data.toHex(),
        )
    }

    @Test fun sessionKeysFromChipAnswer() {
        val answer = hex("46B9342A41396CD7386BF5803104D7CEDC122B9132139BAF2EEDC94EE178534F2F2D235D074D7449")
        val keys = bac().sessionKeys(rndIfd, rndIcc, kIfd, answer)
        assertEquals("979EC13B1CBFE9DCD01AB0FED307EAE5", keys.ksEnc.toHex())
        assertEquals("F1CB1F1FB5ADF208806B89DC579DC1F8", keys.ksMac.toHex())
        assertEquals("887022120C06C226", keys.ssc.toHex())
    }

    @Test fun tamperedChipAnswerIsRefused() {
        val answer = hex("46B9342A41396CD7386BF5803104D7CEDC122B9132139BAF2EEDC94EE178534F2F2D235D074D7448")
        try {
            bac().sessionKeys(rndIfd, rndIcc, kIfd, answer)
            fail("expected Access")
        } catch (e: NfcReadException) {
            assertEquals(NfcFailure.Access, e.failure)
        }
    }

    private fun sm() = SecureMessaging(
        SessionKeys(
            ksEnc = hex("979EC13B1CBFE9DCD01AB0FED307EAE5"),
            ksMac = hex("F1CB1F1FB5ADF208806B89DC579DC1F8"),
            ssc = hex("887022120C06C226"),
        ),
    )

    @Test fun secureMessagingSelectAndReadBinary() {
        val sm = sm()

        // SELECT EF.COM.
        val select = sm.wrap(0x00, 0xA4, 0x02, 0x0C, hex("011E"), le = null)
        assertEquals("0CA4020C158709016375432908C044F68E08BF8B92D635FF24F800", select.toHex())
        assertEquals("887022120C06C227", sm.ssc().toHex())
        val selected = sm.unwrap(hex("990290008E08FA855A5D4C50A8ED9000"))
        assertTrue(selected.ok)
        assertEquals("887022120C06C228", sm.ssc().toHex())

        // READ BINARY, first 4 bytes.
        val read4 = sm.wrap(0x00, 0xB0, 0x00, 0x00, null, le = 4)
        assertEquals("0CB000000D9701048E08ED6705417E96BA5500", read4.toHex())
        val head = sm.unwrap(hex("8709019FF0EC34F9922651990290008E08AD55CC17140B2DED9000"))
        assertEquals("60145F01", head.data.toHex())
        assertEquals(0x9000, head.sw)
        assertEquals(0x16, MrtdReader.tlvTotalLength(head.data))

        // READ BINARY, the remaining 18 bytes from offset 4.
        val read18 = sm.wrap(0x00, 0xB0, 0x00, 0x04, null, le = 0x12)
        assertEquals("0CB000040D9701128E082EA28A70F3C7B53500", read18.toHex())
        val rest = sm.unwrap(
            hex("871901FB9235F4E4037F2327DCC8964F1F9B8C30F42C8E2FFF224A990290008E08C8B2787EAEA07D749000"),
        )
        assertEquals("04303130365F36063034303030305C026175", rest.data.toHex())
        assertEquals("887022120C06C22C", sm.ssc().toHex())
    }

    @Test fun answerWithBadMacIsRefused() {
        val sm = sm()
        sm.wrap(0x00, 0xA4, 0x02, 0x0C, hex("011E"), le = null)
        try {
            sm.unwrap(hex("990290008E08FA855A5D4C50A8EE9000"))
            fail("expected a MAC failure")
        } catch (e: NfcReadException) {
            assertEquals(NfcFailure.Unsupported, e.failure)
        }
    }

    @Test fun paddingRoundTrip() {
        for (n in 0..17) {
            val data = ByteArray(n) { (it + 1).toByte() }
            val padded = BacCrypto.pad(data)
            assertEquals(0, padded.size % 8)
            assertArrayEquals(data, BacCrypto.unpad(padded))
        }
    }

    /**
     * The whole reader against a simulated chip that runs the chip side of the worked
     * example, then serves a TD3 DG1 under the same session keys.
     */
    @Test fun readerAgainstSimulatedChip() {
        val td3 = "P<UTOERIKSSON<<ANNA<MARIA<<<<<<<<<<<<<<<<<<<" + "L898902C36UTO7408122F1204159ZE184226B<<<<<10"
        val chip = SimulatedChip(dg1 = Tlv.encode(0x61, Tlv.encode(0x5F1F, td3.toByteArray(Charsets.US_ASCII))))
        val reader = MrtdReader(chip, Bac(mrzInfo) { n -> if (n == 8) rndIfd.copyOf() else kIfd.copyOf() })
        val mrz = reader.readDg1()
        assertEquals("Anna Maria Eriksson", mrz.displayName)
        assertEquals("L898902C3", mrz.documentNumber)
        assertEquals("2012-04-15", mrz.expiryIso)
    }

    @Test fun wrongMrzKeyIsAccessFailure() {
        val chip = SimulatedChip(dg1 = ByteArray(0))
        val other = Mrz.bacKeyInfo("X1234567", LocalDate.of(1990, 1, 1), LocalDate.of(2030, 1, 1))
        try {
            MrtdReader(chip, Bac(other)).readDg1()
            fail("expected Access")
        } catch (e: NfcReadException) {
            assertEquals(NfcFailure.Access, e.failure)
        }
    }

    /** The chip side of BAC with the appendix's keys and nonces, plus SM file reads. */
    private inner class SimulatedChip(private val dg1: ByteArray) : CardChannel {
        private val kEnc = hex("AB94FDECF2674FDFB9B391F85D7F76F2")
        private val kMac = hex("7962D9ECE03D1ACD4C76089DCE131543")
        private val kIcc = hex("0B4F80323EB3191CB04970CB4052790B")
        private var keys: SessionKeys? = null
        private var ssc = ByteArray(8)
        private var selected: ByteArray? = null

        override fun transceive(command: ByteArray): ByteArray {
            val ins = command[1].toInt() and 0xFF
            val sm = command[0].toInt() and 0x0C == 0x0C
            return when {
                ins == 0xA4 && !sm -> hex("9000")
                ins == 0x84 -> rndIcc + hex("9000")
                ins == 0x82 -> externalAuthenticate(command.copyOfRange(5, 45))
                sm -> secure(command)
                else -> hex("6D00")
            }
        }

        private fun externalAuthenticate(data: ByteArray): ByteArray {
            val e = data.copyOfRange(0, 32)
            if (!BacCrypto.mac(kMac, e).contentEquals(data.copyOfRange(32, 40))) return hex("6300")
            val s = BacCrypto.decrypt(kEnc, e)
            val ifd = s.copyOfRange(0, 8)
            val kifd = s.copyOfRange(16, 32)
            val eIcc = BacCrypto.encrypt(kEnc, rndIcc + ifd + kIcc)
            val seed = ByteArray(16) { (kifd[it].toInt() xor kIcc[it].toInt()).toByte() }
            keys = SessionKeys(
                BacCrypto.deriveKey(seed, BacCrypto.ENC),
                BacCrypto.deriveKey(seed, BacCrypto.MAC),
                rndIcc.copyOfRange(4, 8) + ifd.copyOfRange(4, 8),
            )
            ssc = keys!!.ssc.copyOf()
            return eIcc + BacCrypto.mac(kMac, eIcc) + hex("9000")
        }

        private fun inc() {
            for (i in ssc.indices.reversed()) {
                ssc[i] = (ssc[i] + 1).toByte()
                if (ssc[i] != 0.toByte()) break
            }
        }

        private fun secure(command: ByteArray): ByteArray {
            val k = keys ?: return hex("6982")
            inc()
            val dos = Tlv.parse(command.copyOfRange(5, 5 + (command[4].toInt() and 0xFF)))
            val ins = command[1].toInt() and 0xFF
            val payload = when (ins) {
                0xA4 -> {
                    val enc = dos.first { it.tag == 0x87 }.value
                    selected = BacCrypto.unpad(BacCrypto.decrypt(k.ksEnc, enc.copyOfRange(1, enc.size)))
                    null
                }
                0xB0 -> {
                    val off = ((command[2].toInt() and 0xFF) shl 8) or (command[3].toInt() and 0xFF)
                    val le = dos.first { it.tag == 0x97 }.value[0].toInt() and 0xFF
                    dg1.copyOfRange(off, minOf(dg1.size, off + le))
                }
                else -> null
            }
            inc()
            var body = ByteArray(0)
            if (payload != null) body += Tlv.encode(0x87, bytes(0x01) + BacCrypto.encrypt(k.ksEnc, BacCrypto.pad(payload)))
            body += Tlv.encode(0x99, hex("9000"))
            body += Tlv.encode(0x8E, BacCrypto.mac(k.ksMac, ssc + body))
            return body + hex("9000")
        }
    }
}
