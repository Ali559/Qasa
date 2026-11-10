package alibarznji.qasa.nfc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

/** ICAO 9303 Part 3 check digits and the Part 4 (TD3) / Part 5 (TD1) specimen MRZs. */
class MrzTest {
    private val td3l1 = "P<UTOERIKSSON<<ANNA<MARIA<<<<<<<<<<<<<<<<<<<"
    private val td3l2 = "L898902C36UTO7408122F1204159ZE184226B<<<<<10"
    private val td1l1 = "I<UTOD231458907<<<<<<<<<<<<<<<"
    private val td1l2 = "7408122F1204159UTO<<<<<<<<<<<6"
    private val td1l3 = "ERIKSSON<<ANNA<MARIA<<<<<<<<<<"

    @Test fun checkDigits() {
        assertEquals('6', Mrz.checkDigit("L898902C3"))
        assertEquals('2', Mrz.checkDigit("740812"))
        assertEquals('9', Mrz.checkDigit("120415"))
        assertEquals('1', Mrz.checkDigit("ZE184226B<<<<<"))
        assertEquals('7', Mrz.checkDigit("D23145890"))
        // Part 11 Appendix D.
        assertEquals('3', Mrz.checkDigit("L898902C<"))
        assertEquals('1', Mrz.checkDigit("690806"))
        assertEquals('6', Mrz.checkDigit("940623"))
    }

    @Test fun compositeCheckDigits() {
        // TD3: line 2 positions 1–10, 14–20, 22–43.
        assertEquals(td3l2[43], Mrz.checkDigit(td3l2.substring(0, 10) + td3l2.substring(13, 20) + td3l2.substring(21, 43)))
        // TD1: line 1 positions 6–30, line 2 positions 1–7, 9–15, 19–29.
        assertEquals(
            td1l2[29],
            Mrz.checkDigit(td1l1.substring(5) + td1l2.substring(0, 7) + td1l2.substring(8, 15) + td1l2.substring(18, 29)),
        )
    }

    @Test fun td3Passport() {
        val m = Mrz.parse(td3l1 + "\n" + td3l2)!!
        assertEquals("P", m.documentCode)
        assertEquals("UTO", m.issuingState)
        assertEquals("ERIKSSON", m.surname)
        assertEquals("ANNA MARIA", m.givenNames)
        assertEquals("Anna Maria Eriksson", m.displayName)
        assertEquals("L898902C3", m.documentNumber)
        assertEquals("UTO", m.nationality)
        assertEquals("740812", m.birthDate)
        assertEquals("120415", m.expiryDate)
        assertEquals("2012-04-15", m.expiryIso)
        assertNull(m.countryCodeIso2) // Utopia is not a vault country
    }

    @Test fun td1IdCard() {
        val m = Mrz.parse(td1l1 + td1l2 + td1l3)!!
        assertEquals("I", m.documentCode)
        assertEquals("UTO", m.issuingState)
        assertEquals("D23145890", m.documentNumber)
        assertEquals("Anna Maria Eriksson", m.displayName)
        assertEquals("740812", m.birthDate)
        assertEquals("2012-04-15", m.expiryIso)
    }

    @Test fun td1LongDocumentNumber() {
        // Part 5 note j: 12-character number, `<` in the check digit slot, rest + check
        // digit in the optional data.
        val l1 = "I<UTOD23145890<AB112234566<<<<"
        val m = Mrz.parse(l1 + td1l2 + td1l3)!!
        assertEquals("D23145890AB11223456", m.documentNumber)
    }

    @Test fun dg1Template() {
        val dg1 = Tlv.encode(0x61, Tlv.encode(0x5F1F, (td3l1 + td3l2).toByteArray(Charsets.US_ASCII)))
        assertEquals("L898902C3", Mrz.parseDg1(dg1)!!.documentNumber)
        assertNull(Mrz.parse("TOO SHORT"))
    }

    @Test fun countryCodes() {
        assertEquals("IQ", Mrz.iso2("IRQ"))
        assertEquals("DE", Mrz.iso2("D<<"))
        assertEquals("GB", Mrz.iso2("GBR"))
        assertNull(Mrz.iso2("UTO"))
        val m = Mrz.parse("P<D<<MUSTERMANN<<ERIKA".padEnd(44, '<') + td3l2)!!
        assertEquals("DE", m.countryCodeIso2)
        assertEquals("Erika Mustermann", m.displayName)
    }

    @Test fun bacKeyInfoPadsAndNormalizes() {
        assertEquals(
            "L898902C<369080619406236",
            Mrz.bacKeyInfo("l898902c", LocalDate.of(1969, 8, 6), LocalDate.of(1994, 6, 23)),
        )
    }

    @Test fun datePivots() {
        val today = LocalDate.of(2026, 11, 10)
        assertEquals(LocalDate.of(1974, 8, 12), Mrz.yymmddToDate("740812", pivotFuture = false, today = today))
        assertEquals(LocalDate.of(2012, 4, 15), Mrz.yymmddToDate("120415", pivotFuture = true, today = today))
        assertEquals(LocalDate.of(2034, 1, 31), Mrz.yymmddToDate("340131", pivotFuture = true, today = today))
        assertNull(Mrz.yymmddToDate("341301", pivotFuture = true, today = today))
    }
}
