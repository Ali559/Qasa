package alibarznji.qasa.vault.csv

import alibarznji.qasa.vault.VaultPayload
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** Port of iOS `VaultCSV.swift`: Android export → import, and iOS-written files on Android. */
class VaultCsvTest {

    private val header = VaultCsv.columns.joinToString(",")
    private val collection = "3f2b8c1e-9a4d-4e7b-8c21-5d6f7a8b9c0d"

    /** Every field set to a non-default value so a dropped field would show up. */
    private val all13: List<VaultPayload> = listOf(
        VaultPayload.Login("GitHub", "a@b.c", "ali", "p,\"w\nx", "https://github.com", "n", collection),
        VaultPayload.SecureNote("Diary", "line 1\n\nline \"3\", end", "purple", collection, protectItem = true),
        VaultPayload.Card("Visa", "Ali B", "4111111111111111", "123", "12/29", "note", "gold"),
        VaultPayload.BankAccount("Main", "Ali B", "123", "456", "AE", "Rafidain"),
        VaultPayload.Name("Mrs", "Ali", "Barznji", "M", "ali", "01/02/1990", "Erbil"),
        VaultPayload.PersonalEmail("Work mail", "Work", "w@x.y"),
        VaultPayload.Phone("Office", "Work Cell", "TR", "0770123"),
        VaultPayload.Address(
            "Home", "100m St", "Erbil", "44001", "IQ", "Ali", "B2", "3", "12", "4321",
            "a1b2c3d4-0000-4000-8000-000000000001",
        ),
        VaultPayload.Company("Qasa", "CTO"),
        VaultPayload.Website("Blog", "https://ali.dev/a?b=c"),
        VaultPayload.IdCard("Ali", "IQ", "ID1", "01/01/2020", "01/01/2030", "Erbil"),
        VaultPayload.Passport("Ali", "DE", "P1", "02/02/2021", "02/02/2031", "Berlin"),
        VaultPayload.DriverLicense("Ali", "IQ", "D1", "03/03/2022", "03/03/2032", ""),
    )

    @Test fun roundTripsAll13Types() {
        val csv = VaultCsv.exportCsv(all13)
        assertEquals(all13, VaultCsv.parseImport(csv))
        // iOS: no trailing newline, header first.
        assertTrue(csv.startsWith("$header\n"))
        assertTrue(!csv.endsWith("\n"))
    }

    @Test fun roundTripSurvivesCrlfLineEndings() {
        val csv = VaultCsv.exportCsv(all13).replace("\n", "\r\n")
        // Quoted newlines become CRLF too, so compare only the types and titles.
        val back = VaultCsv.parseImport(csv)
        assertEquals(all13.map(VaultCsv::typeName), back.map(VaultCsv::typeName))
        assertEquals(all13.map(VaultCsv::iosDisplayName), back.map(VaultCsv::iosDisplayName))
    }

    @Test fun fingerprintIsStableAcrossExportImport() {
        val back = VaultCsv.parseImport(VaultCsv.exportCsv(all13))
        assertEquals(all13.map(VaultCsv::fingerprint), back.map(VaultCsv::fingerprint))
        assertNotEquals(VaultCsv.fingerprint(all13[10]), VaultCsv.fingerprint(all13[11]))
    }

    // Hand-written rows in exactly the shape iOS `VaultCSV.row(for:)` emits: iOS property
    // names in the JSON, sorted keys, `/` escaped as `\/`, the picker under "type".

    private val iosLogin = "login,GitHub,ali,\"p,\"\"w\",https://github.com,a@b.c,,,,,,,,,," +
        "\"{\"\"email\"\":\"\"a@b.c\"\",\"\"itemName\"\":\"\"GitHub\"\",\"\"note\"\":\"\"\"\"," +
        "\"\"password\"\":\"\"p,\\\"\"w\"\",\"\"username\"\":\"\"ali\"\",\"\"website\"\":\"\"https:\\/\\/github.com\"\"}\""
    private val iosCard = "card,Visa,,,,,Ali B,4111111111111111,123,12/29,,,,,," +
        "\"{\"\"accountHolder\"\":\"\"Ali B\"\",\"\"color\"\":\"\"blue\"\",\"\"expiryDate\"\":\"\"12\\/29\"\"," +
        "\"\"itemName\"\":\"\"Visa\"\",\"\"note\"\":\"\"\"\",\"\"number\"\":\"\"4111111111111111\"\"," +
        "\"\"securityCode\"\":\"\"123\"\"}\""
    private val iosPhone = "phone,Office,,,,,,0770123,,,,,,,," +
        "\"{\"\"countryCode\"\":\"\"IQ\"\",\"\"itemName\"\":\"\"Office\"\",\"\"number\"\":\"\"0770123\"\"," +
        "\"\"type\"\":\"\"Work Cell\"\"}\""
    private val iosName = "name,Ali Barznji,,,,ali,,,,01/02/1990,,,,Erbil,," +
        "\"{\"\"birthDate\"\":\"\"01\\/02\\/1990\"\",\"\"birthPlace\"\":\"\"Erbil\"\"," +
        "\"\"defaultUsername\"\":\"\"ali\"\",\"\"firstName\"\":\"\"Ali\"\",\"\"lastName\"\":\"\"Barznji\"\"," +
        "\"\"middleName\"\":\"\"\"\",\"\"type\"\":\"\"Mr\"\"}\""

    private val iosItems = listOf(
        VaultPayload.Login(itemName = "GitHub", email = "a@b.c", username = "ali", password = "p,\"w", website = "https://github.com"),
        VaultPayload.Card(itemName = "Visa", accountHolder = "Ali B", number = "4111111111111111", securityCode = "123", expiryDate = "12/29"),
        VaultPayload.Phone(itemName = "Office", type = "Work Cell", countryCode = "IQ", number = "0770123"),
        VaultPayload.Name(type = "Mr", firstName = "Ali", lastName = "Barznji", defaultUsername = "ali", birthDate = "01/02/1990", birthPlace = "Erbil"),
    )

    @Test fun parsesIosWrittenRows() {
        val csv = listOf(header, iosLogin, iosCard, iosPhone, iosName).joinToString("\n")
        assertEquals(iosItems, VaultCsv.parseImport(csv))
    }

    @Test fun exportIsByteIdenticalToIos() {
        val expected = listOf(header, iosLogin, iosCard, iosPhone, iosName).joinToString("\n")
        assertEquals(expected, VaultCsv.exportCsv(iosItems))
    }

    @Test fun exportNormalisesLegacyAndroidPickerRaws() {
        // Android briefly stored lower-camel raws; iOS's Codable enums would reject them.
        val legacy = VaultPayload.Phone(itemName = "Office", type = "workCell", countryCode = "IQ", number = "0770123")
        assertEquals(VaultCsv.fieldsJson(iosItems[2]), VaultCsv.fieldsJson(legacy))
        assertEquals(VaultCsv.fieldsJson(iosItems[3]), VaultCsv.fieldsJson((iosItems[3] as VaultPayload.Name).copy(type = "mr")))
    }

    @Test fun collectionIdIsUppercaseInCsvAndLowercaseOnAndroid() {
        val json = VaultCsv.fieldsJson(all13[0])
        assertTrue(json, json.contains("\"collectionId\":\"${collection.uppercase()}\""))
        val ios = "$header\nlogin,X,,,,,,,,,,,,,,\"{\"\"collectionId\"\":\"\"${collection.uppercase()}\"\",\"\"itemName\"\":\"\"X\"\"}\""
        assertEquals(collection, (VaultCsv.parseImport(ios).single() as VaultPayload.Login).collectionId)
        // nil optionals are omitted, as Swift's encodeIfPresent does.
        assertTrue(!VaultCsv.fieldsJson(VaultPayload.Login(itemName = "x")).contains("collectionId"))
    }

    // Wide-column fallback (iOS: only login/secureNote/card/bankAccount are rebuilt).

    @Test fun wideColumnFallbackWithoutFieldsColumn() {
        val csv = """
            Type, Title ,username,password,url,notes,content,number
            login,Mail,ali,pw,https://m.com,hi,,
            secureNote,Memo,,,,,body,
            card,Visa,,,,,,4111
            phone,Office,,,,,,0770
        """.trimIndent()
        assertEquals(
            listOf(
                VaultPayload.Login(itemName = "Mail", username = "ali", password = "pw", website = "https://m.com", note = "hi"),
                VaultPayload.SecureNote(title = "Memo", content = "body"),
                VaultPayload.Card(itemName = "Visa", number = "4111"),
            ),
            VaultCsv.parseImport(csv),
        )
    }

    @Test fun malformedFieldsJsonFallsBackToWideColumns() {
        val csv = "$header\nlogin,Mail,ali,pw,,,,,,,,,,,,{not json"
        assertEquals(listOf(VaultPayload.Login(itemName = "Mail", username = "ali", password = "pw")), VaultCsv.parseImport(csv))
    }

    @Test fun rowsWithoutTitleAreSkipped() {
        assertEquals(emptyList<VaultPayload>(), VaultCsv.parseImport("$header\nlogin,,ali,pw\n\n   \n"))
    }

    @Test fun emptyFileAndBadHeader() {
        try {
            VaultCsv.parseImport(" \n\r\n")
            fail()
        } catch (_: VaultCsvException.Empty) {
        }
        try {
            VaultCsv.parseImport("name,username\nx,y")
            fail()
        } catch (e: VaultCsvException.InvalidHeader) {
            assertEquals("'type', 'title'", e.missing)
        }
    }

    // RFC 4180 primitives.

    @Test fun escapeQuotesOnlyWhenNeeded() {
        assertEquals("plain", CsvPrimitives.escape("plain"))
        assertEquals("\"a,b\"", CsvPrimitives.escape("a,b"))
        assertEquals("\"say \"\"hi\"\"\"", CsvPrimitives.escape("say \"hi\""))
        assertEquals("\"l1\nl2\"", CsvPrimitives.escape("l1\nl2"))
        assertEquals("\"cr\r\"", CsvPrimitives.escape("cr\r"))
        assertEquals("", CsvPrimitives.escape(""))
    }

    @Test fun parseLineHandlesQuotedCommasQuotesAndNewlines() {
        assertEquals(listOf("a", "b,c", "d\"e", "", "f\ng"), CsvPrimitives.parseLine("a,\"b,c\",\"d\"\"e\",,\"f\ng\""))
        assertEquals(listOf("", ""), CsvPrimitives.parseLine(","))
        assertEquals(listOf("\""), CsvPrimitives.parseLine("\"\"\"\""))
    }

    @Test fun parseRecordsKeepsQuotedNewlinesTogether() {
        val records = CsvPrimitives.parseRecords("h1,h2\n\"a\n\nb\",\"x\"\"\ny\"\nlast")
        assertEquals(listOf("h1,h2", "\"a\n\nb\",\"x\"\"\ny\"", "last"), records)
        assertEquals(listOf("a\n\nb", "x\"\ny"), CsvPrimitives.parseLine(records[1]))
    }

    @Test fun jsonWriterMatchesAppleEscaping() {
        assertEquals(
            "{\"a\":\"x\\/y\\\"z\\\\\\n\\t\\u0001é\",\"b\":true}",
            IosJsonWriter.write(mapOf("b" to true, "a" to "x/y\"z\\\n\t\u0001é", "c" to null)),
        )
    }
}
