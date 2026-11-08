package alibarznji.qasa.vault

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The decode rule for rows synced in from iOS: stored as received (iOS shape inside the
 * ciphertext), converted to Android's shape only when read.
 *
 * The real converter is Rust (`qasa-ffi` `payloadToAndroid`, loaded through JNA), which
 * can't load in a JVM unit test: a test double stands in for it here. The conversion
 * itself is covered by `core/qasa-ffi`'s `payload_conversion` test.
 */
class VaultPayloadShapeTest {
    private val original = PayloadShapes.toAndroid
    private var converterCalls = 0

    private fun fixture(name: String): ByteArray =
        checkNotNull(javaClass.classLoader?.getResourceAsStream("payload/$name")) { name }.readBytes()

    @Before fun stubConverter() {
        val ios = fixture("ios_login.json")
        val android = fixture("ios_login_as_android.json")
        PayloadShapes.toAndroid = { bytes ->
            converterCalls++
            check(bytes.contentEquals(ios)) { "unexpected payload handed to the converter" }
            android
        }
    }

    @After fun restoreConverter() {
        PayloadShapes.toAndroid = original
    }

    @Test fun iosShapeIsConvertedThenDecoded() {
        val payload = VaultJson.decodePayload(fixture("ios_login.json"))
        assertEquals(1, converterCalls)
        val login = payload as VaultPayload.Login
        assertEquals("GitHub", login.itemName)
        assertEquals("hunter2-🔑", login.password)
        assertEquals("تێبینی", login.note)
        assertEquals("c-1", login.collectionId)
        assertEquals("otpauth://totp/GitHub?secret=JBSWY3DPEHPK3PXP", login.totp)
    }

    @Test fun androidShapeNeverReachesTheConverter() {
        val own = VaultJson.encodePayload(VaultPayload.Login(itemName = "x", password = "p"))
        assertEquals(VaultPayload.Login(itemName = "x", password = "p"), VaultJson.decodePayload(own))
        assertEquals(0, converterCalls)
    }

    @Test fun shapeDetection() {
        assertTrue(VaultJson.isAndroidShape("""{"type":"phone","number":"1"}"""))
        // iOS nests a `type` inside `_0` for phones; the top level has none.
        assertFalse(VaultJson.isAndroidShape("""{"phone":{"_0":{"type":"Work Cell","number":"750"}}}"""))
        assertFalse(VaultJson.isAndroidShape(String(fixture("ios_login.json"))))
        assertFalse(VaultJson.isAndroidShape("not json"))
        assertFalse(VaultJson.isAndroidShape("""{"type":1}"""))
    }
}
