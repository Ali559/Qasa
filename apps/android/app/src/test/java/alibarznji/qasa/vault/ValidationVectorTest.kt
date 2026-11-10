package alibarznji.qasa.vault

import java.io.File
import java.time.LocalDate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import uniffi.qasa_ffi.FfiField
import uniffi.qasa_ffi.cardBrand
import uniffi.qasa_ffi.formatCardExpiry
import uniffi.qasa_ffi.formatCardNumber
import uniffi.qasa_ffi.payloadToAndroid

/**
 * The shared field rules as Android applies them, against the golden vectors shared with
 * iOS and the desktop (`core/qasa-vault/tests/validation_vectors.json`). Each case's
 * payload (iOS shape) goes through Android's own model and serializer before reaching the
 * core, so a field the model drops or renames fails here. Fix the code, never the vectors.
 *
 * Calls the real Rust core: the host build of libqasa_ffi (`cargo build -p qasa-ffi --lib`,
 * run by the `buildHostFfi` Gradle task) loaded through JNA.
 */
class ValidationVectorTest {
    private val vectors: JsonObject by lazy {
        var dir: File? = File(System.getProperty("user.dir")).absoluteFile
        while (dir != null && !File(dir, VECTORS).isFile) dir = dir.parentFile
        requireNotNull(dir) { "$VECTORS not found above ${System.getProperty("user.dir")}" }
        Json.parseToJsonElement(File(dir, VECTORS).readText()).jsonObject
    }

    private val today: LocalDate by lazy {
        val t = vectors.getValue("today").jsonObject
        LocalDate.of(t.getValue("year").jsonPrimitive.int, t.getValue("month").jsonPrimitive.int, t.getValue("day").jsonPrimitive.int)
    }

    /** iOS-shape JSON → Android's model, the way a synced-in row is read. */
    private fun decodeIos(json: JsonObject): VaultPayload =
        VaultJson.decodePayload(payloadToAndroid(json.toString().toByteArray(Charsets.UTF_8)))

    private fun JsonObject.cases(key: String) = getValue(key).jsonArray.map { it.jsonObject }
    private fun JsonObject.str(key: String) = getValue(key).jsonPrimitive.content

    @Test fun payloadCases() {
        val failures = mutableListOf<String>()
        for (c in vectors.cases("cases")) {
            val name = c.str("name")
            val payload = decodeIos(c.getValue("payload").jsonObject)
            val expectedIssues = c.getValue("issues").jsonArray.map { it.jsonPrimitive.content }
            val issues = VaultValidation.validate(payload, today).map { it.code }
            if (issues != expectedIssues) failures += "$name: issues $issues, expected $expectedIssues"

            val normalized = VaultValidation.normalize(payload)
            val expected = decodeIos(c.getValue("normalized").jsonObject)
            if (normalized != expected) failures += "$name: normalized $normalized, expected $expected"
        }
        if (failures.isNotEmpty()) fail(failures.joinToString("\n"))
    }

    @Test fun cardBrands() {
        for (c in vectors.cases("card_brands")) {
            assertEquals(c.str("input"), c.str("output"), camel(cardBrand(c.str("input")).name))
        }
    }

    @Test fun cardNumberFormatting() {
        for (c in vectors.cases("format_card_number")) {
            assertEquals(c.str("input"), c.str("output"), formatCardNumber(c.str("input")))
        }
    }

    @Test fun cardExpiryFormatting() {
        for (c in vectors.cases("format_card_expiry")) {
            assertEquals(c.str("input"), c.str("output"), formatCardExpiry(c.str("input")))
        }
    }

    @Test fun dateParsing() {
        for (c in vectors.cases("parse_date")) {
            // An empty output means "not a date".
            assertEquals(c.str("input"), c.str("output"), VaultValidation.parseDate(c.str("input"))?.toString().orEmpty())
        }
    }

    // ---- The repository's write policy (VaultRepository.write uses exactly this) ----

    private val badCard = VaultPayload.Card(itemName = "Visa", number = "4111 1111 1111 1112", expiryDate = "08/29")

    @Test fun userSaveWithIssuesIsRefused() {
        try {
            VaultValidation.prepareForWrite(badCard, SaveOrigin.User, today)
            fail("an invalid user save must throw")
        } catch (e: VaultValidationException) {
            assertEquals(listOf("number:cardNumberChecksum"), e.issues.map { it.code })
            assertEquals(FfiField.NUMBER, e.issues.single().field)
        }
    }

    @Test fun userSaveStoresTheNormalizedForm() {
        val typed = VaultPayload.Name(firstName = "Ali", birthDate = "06/11/2000")
        val (stored, issues) = VaultValidation.prepareForWrite(typed, SaveOrigin.User, today)
        assertTrue(issues.isEmpty())
        assertEquals("2000-11-06", (stored as VaultPayload.Name).birthDate)
    }

    @Test fun importKeepsAnInvalidItemAndReportsIt() {
        val (stored, issues) = VaultValidation.prepareForWrite(badCard, SaveOrigin.Import, today)
        assertEquals(badCard.number, (stored as VaultPayload.Card).number)
        assertEquals(1, issues.size)
        val ok = VaultPayload.Login(itemName = "GitHub", password = "pw")
        assertTrue(VaultValidation.prepareForWrite(ok, SaveOrigin.Import, today).second.isEmpty())
    }

    @Test fun internalWritesAreUntouched() {
        val legacy = VaultPayload.Name(firstName = "Ali", birthDate = "Nov 6, 2000")
        assertEquals(legacy to emptyList<Any>(), VaultValidation.prepareForWrite(legacy, SaveOrigin.Internal, today))
    }

    private fun camel(enumName: String): String =
        enumName.lowercase().split('_').mapIndexed { i, w -> if (i == 0) w else w.replaceFirstChar(Char::uppercase) }.joinToString("")

    private companion object {
        const val VECTORS = "core/qasa-vault/tests/validation_vectors.json"
    }
}
