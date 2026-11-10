package alibarznji.qasa.nfc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TlvTest {
    @Test fun nestedTemplateWithMultiByteTags() {
        // 6F { 84 (2 bytes), A5 { BF0C { 61 { 4F (7 bytes) } } } }
        val fci = hex("6F15" + "84020102" + "A50F" + "BF0C0C" + "610A" + "4F07A0000000031010" + "00")
        // The trailing 00 inside 61 is padding the parser skips.
        val top = Tlv.parse(fci).single()
        assertEquals(0x6F, top.tag)
        assertTrue(top.constructed)
        assertEquals("A0000000031010", top.find(0x4F)!!.value.toHex())
        assertEquals(0xBF0C, top.find(0xBF0C)!!.tag)
        assertNull(top.find(0x5A))
    }

    @Test fun longFormLengths() {
        val value = ByteArray(200) { it.toByte() }
        val encoded = Tlv.encode(0x5F1F, value)
        assertEquals("5F1F81C8", encoded.copyOfRange(0, 4).toHex())
        val parsed = Tlv.parse(encoded).single()
        assertEquals(0x5F1F, parsed.tag)
        assertEquals(200, parsed.value.size)

        val big = Tlv.encode(0x53, ByteArray(300))
        assertEquals("5382012C", big.copyOfRange(0, 4).toHex())
        assertEquals(300, Tlv.parse(big).single().value.size)
    }

    @Test fun findAllInDocumentOrder() {
        val data = hex("70" + "0C" + "6104" + "4F020001" + "6104" + "4F020002")
        assertEquals(listOf("0001", "0002"), Tlv.findAll(data, 0x4F).map { it.value.toHex() })
    }

    @Test fun overlongValueIsCut() {
        val parsed = Tlv.parse(hex("5A08411111")).single()
        assertEquals("411111", parsed.value.toHex())
    }
}
