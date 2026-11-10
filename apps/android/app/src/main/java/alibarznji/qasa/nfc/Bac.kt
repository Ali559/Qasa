package alibarznji.qasa.nfc

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Basic Access Control and its Secure Messaging, ICAO 9303 Part 11 §4.3 and §9.8, with
 * two-key 3DES and the ISO/IEC 9797-1 MAC algorithm 3 ("retail MAC", padding method 2).
 * Only `DESede` and `SHA-1` from the platform JCE; single DES is run as DESede with
 * K‖K‖K so no provider needs a plain `DES` cipher.
 */
object BacCrypto {
    /** Kseed = first 16 bytes of SHA-1(MRZ information). */
    fun keySeed(mrzInfo: String): ByteArray =
        sha1(mrzInfo.toByteArray(Charsets.US_ASCII)).copyOf(16)

    /** 9303 Part 11 §9.7.1: SHA-1(Kseed ‖ counter) truncated to 16 bytes, DES parity set. */
    fun deriveKey(seed: ByteArray, counter: Int): ByteArray {
        val d = seed + bytes(counter ushr 24, counter ushr 16, counter ushr 8, counter)
        return adjustParity(sha1(d).copyOf(16))
    }

    const val ENC = 1
    const val MAC = 2

    /** Each byte gets odd parity in its low bit (the DES key-parity convention). */
    fun adjustParity(key: ByteArray): ByteArray = ByteArray(key.size) { i ->
        val b = key[i].toInt() and 0xFE
        val ones = Integer.bitCount(b)
        (if (ones % 2 == 0) b or 1 else b).toByte()
    }

    fun encrypt(key16: ByteArray, data: ByteArray): ByteArray = tdes(Cipher.ENCRYPT_MODE, key16, data)

    fun decrypt(key16: ByteArray, data: ByteArray): ByteArray = tdes(Cipher.DECRYPT_MODE, key16, data)

    private fun tdes(mode: Int, key16: ByteArray, data: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("DESede/CBC/NoPadding")
        cipher.init(mode, SecretKeySpec(key16 + key16.copyOf(8), "DESede"), IvParameterSpec(ByteArray(8)))
        return cipher.doFinal(data)
    }

    private fun singleDes(mode: Int, key8: ByteArray, block: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("DESede/ECB/NoPadding")
        cipher.init(mode, SecretKeySpec(key8 + key8 + key8, "DESede"))
        return cipher.doFinal(block)
    }

    /** ISO/IEC 9797-1 padding method 2: 0x80, then zeros to a multiple of 8. */
    fun pad(data: ByteArray): ByteArray {
        val out = ByteArray((data.size / 8 + 1) * 8)
        data.copyInto(out)
        out[data.size] = 0x80.toByte()
        return out
    }

    fun unpad(data: ByteArray): ByteArray {
        var i = data.size - 1
        while (i >= 0 && data[i] == 0.toByte()) i--
        if (i < 0 || data[i] != 0x80.toByte()) throw NfcReadException(NfcFailure.Unsupported, "bad padding")
        return data.copyOf(i)
    }

    /**
     * ISO/IEC 9797-1 MAC algorithm 3 over [data] padded with method 2: DES-CBC with Ka over
     * every block, then decrypt with Kb and encrypt with Ka.
     */
    fun mac(key16: ByteArray, data: ByteArray): ByteArray {
        val ka = key16.copyOfRange(0, 8)
        val kb = key16.copyOfRange(8, 16)
        val padded = pad(data)
        var h = ByteArray(8)
        for (off in padded.indices step 8) {
            val block = ByteArray(8) { (padded[off + it].toInt() xor h[it].toInt()).toByte() }
            h = singleDes(Cipher.ENCRYPT_MODE, ka, block)
        }
        h = singleDes(Cipher.DECRYPT_MODE, kb, h)
        return singleDes(Cipher.ENCRYPT_MODE, ka, h)
    }

    fun sha1(data: ByteArray): ByteArray = MessageDigest.getInstance("SHA-1").digest(data)
}

/** The keys BAC agrees, plus the send sequence counter both sides start from. */
class SessionKeys(val ksEnc: ByteArray, val ksMac: ByteArray, val ssc: ByteArray)

/**
 * The BAC handshake (9303 Part 11 §4.3.4): the reader proves it knows the MRZ key by
 * encrypting RND.IFD ‖ RND.ICC ‖ K.IFD, the chip answers with RND.ICC ‖ RND.IFD ‖ K.ICC,
 * and K.IFD ⊕ K.ICC seeds the session keys.
 */
class Bac(private val mrzInfo: String, private val random: (Int) -> ByteArray = ::secureRandomBytes) {
    private val seed = BacCrypto.keySeed(mrzInfo)
    val kEnc: ByteArray = BacCrypto.deriveKey(seed, BacCrypto.ENC)
    val kMac: ByteArray = BacCrypto.deriveKey(seed, BacCrypto.MAC)

    /** Runs GET CHALLENGE and EXTERNAL AUTHENTICATE on [channel]. */
    fun authenticate(channel: CardChannel): SessionKeys {
        val challenge = ApduResponse.of(channel.transceive(bytes(0x00, 0x84, 0x00, 0x00, 0x08)))
        if (!challenge.ok || challenge.data.size != 8) {
            // A PACE-only chip (some ID cards issued since 2018) does not offer BAC at all.
            throw NfcReadException(NfcFailure.Unsupported, "GET CHALLENGE ${"%04X".format(challenge.sw)}")
        }
        val rndIcc = challenge.data
        val rndIfd = random(8)
        val kIfd = random(16)
        val cmdData = mutualAuthData(rndIfd, rndIcc, kIfd)
        val r = ApduResponse.of(channel.transceive(bytes(0x00, 0x82, 0x00, 0x00, 0x28) + cmdData + bytes(0x28)))
        if (!r.ok || r.data.size != 40) {
            // 6300 / 6982: the chip did not accept our MRZ key (wrong number or dates).
            throw NfcReadException(NfcFailure.Access, "EXTERNAL AUTHENTICATE ${"%04X".format(r.sw)}")
        }
        return sessionKeys(rndIfd, rndIcc, kIfd, r.data)
    }

    /** E.IFD ‖ M.IFD for EXTERNAL AUTHENTICATE. */
    fun mutualAuthData(rndIfd: ByteArray, rndIcc: ByteArray, kIfd: ByteArray): ByteArray {
        val eIfd = BacCrypto.encrypt(kEnc, rndIfd + rndIcc + kIfd)
        return eIfd + BacCrypto.mac(kMac, eIfd)
    }

    /** Checks the chip's answer and derives KSenc, KSmac and SSC. */
    fun sessionKeys(rndIfd: ByteArray, rndIcc: ByteArray, kIfd: ByteArray, response: ByteArray): SessionKeys {
        val eIcc = response.copyOfRange(0, 32)
        val mIcc = response.copyOfRange(32, 40)
        if (!BacCrypto.mac(kMac, eIcc).contentEquals(mIcc)) throw NfcReadException(NfcFailure.Access, "bad M.ICC")
        val r = BacCrypto.decrypt(kEnc, eIcc)
        if (!r.copyOfRange(0, 8).contentEquals(rndIcc) || !r.copyOfRange(8, 16).contentEquals(rndIfd)) {
            throw NfcReadException(NfcFailure.Access, "nonce mismatch")
        }
        val kIcc = r.copyOfRange(16, 32)
        val seed = ByteArray(16) { (kIfd[it].toInt() xor kIcc[it].toInt()).toByte() }
        return SessionKeys(
            ksEnc = BacCrypto.deriveKey(seed, BacCrypto.ENC),
            ksMac = BacCrypto.deriveKey(seed, BacCrypto.MAC),
            ssc = rndIcc.copyOfRange(4, 8) + rndIfd.copyOfRange(4, 8),
        )
    }
}

private fun secureRandomBytes(n: Int): ByteArray = ByteArray(n).also { SecureRandom().nextBytes(it) }

/**
 * BAC secure messaging (9303 Part 11 §9.8): every command after the handshake is sent as
 * `0C ins p1 p2 Lc [87 01 enc(data)] [97 Le] [8E 08 mac] 00`, and every answer must carry
 * `99 02 sw` and a valid `8E` over SSC ‖ DOs. The SSC goes up by one per command and per
 * answer.
 */
class SecureMessaging(private val keys: SessionKeys) {
    private val ssc = keys.ssc.copyOf()

    private fun incrementSsc() {
        for (i in ssc.indices.reversed()) {
            ssc[i] = (ssc[i] + 1).toByte()
            if (ssc[i] != 0.toByte()) break
        }
    }

    /** The current SSC, for tests. */
    fun ssc(): ByteArray = ssc.copyOf()

    /**
     * Wraps a plain case 1–4 APDU. [le] is the expected length (null for none; 0 means
     * "up to 256").
     */
    fun wrap(cla: Int, ins: Int, p1: Int, p2: Int, data: ByteArray?, le: Int?): ByteArray {
        val header = bytes(cla or 0x0C, ins, p1, p2)
        var dos = ByteArray(0)
        if (data != null && data.isNotEmpty()) {
            val enc = BacCrypto.encrypt(keys.ksEnc, BacCrypto.pad(data))
            dos += Tlv.encode(0x87, bytes(0x01) + enc)
        }
        if (le != null) dos += Tlv.encode(0x97, bytes(le))
        incrementSsc()
        val mac = BacCrypto.mac(keys.ksMac, ssc + BacCrypto.pad(header) + dos)
        dos += Tlv.encode(0x8E, mac)
        return header + bytes(dos.size) + dos + bytes(0x00)
    }

    /** Verifies and opens an answer; returns (plain data, status word from DO99). */
    fun unwrap(raw: ByteArray): ApduResponse {
        val outer = ApduResponse.of(raw)
        if (outer.sw == 0x6987 || outer.sw == 0x6988) {
            throw NfcReadException(NfcFailure.Access, "SM objects rejected ${"%04X".format(outer.sw)}")
        }
        incrementSsc()
        if (outer.data.isEmpty()) return ApduResponse(ByteArray(0), outer.sw)
        val elements = Tlv.parse(outer.data)
        val do87 = elements.firstOrNull { it.tag == 0x87 }
        val do99 = elements.firstOrNull { it.tag == 0x99 }
        val do8e = elements.firstOrNull { it.tag == 0x8E }
            ?: throw NfcReadException(NfcFailure.Unsupported, "answer has no MAC")
        var covered = ByteArray(0)
        do87?.let { covered += Tlv.encode(0x87, it.value) }
        do99?.let { covered += Tlv.encode(0x99, it.value) }
        val mac = BacCrypto.mac(keys.ksMac, ssc + covered)
        if (!mac.contentEquals(do8e.value)) throw NfcReadException(NfcFailure.Unsupported, "answer MAC mismatch")
        val sw = do99?.value?.takeIf { it.size == 2 }
            ?.let { ((it[0].toInt() and 0xFF) shl 8) or (it[1].toInt() and 0xFF) }
            ?: outer.sw
        val plain = do87?.let {
            if (it.value.firstOrNull() != 0x01.toByte()) throw NfcReadException(NfcFailure.Unsupported, "DO87 padding indicator")
            BacCrypto.unpad(BacCrypto.decrypt(keys.ksEnc, it.value.copyOfRange(1, it.value.size)))
        } ?: ByteArray(0)
        return ApduResponse(plain, sw)
    }
}
