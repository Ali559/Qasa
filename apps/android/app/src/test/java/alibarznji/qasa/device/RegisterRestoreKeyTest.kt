package alibarznji.qasa.device

import alibarznji.qasa.network.DeviceLimitErrorBody
import alibarznji.qasa.network.RegisterDeviceRequest
import alibarznji.qasa.network.RegisterDeviceResponse
import alibarznji.qasa.network.RestoreKeyStatus
import java.util.Base64
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class RegisterRestoreKeyTest {
    /** The same settings as `QasaApiClient.json`. */
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; explicitNulls = false }

    private fun body(req: RegisterDeviceRequest): JsonObject =
        json.parseToJsonElement(json.encodeToString(RegisterDeviceRequest.serializer(), req)).jsonObject

    @Test fun withoutKeyTheBodyIsUnchanged() {
        val b = body(buildRegisterRequest("acc", "fp", "Pixel", null))
        assertFalse("restore_key" in b)
        assertFalse("replace_device" in b)
        assertEquals(JsonPrimitive("android"), b["platform"])
        // Asking to replace without a key sends nothing extra either.
        assertFalse("replace_device" in body(buildRegisterRequest("acc", "fp", "Pixel", null, replaceDevice = true)))
    }

    @Test fun withKeySendsBase64() {
        val key = ByteArray(32) { it.toByte() }
        val b = body(buildRegisterRequest("acc", "fp", "Pixel", key))
        assertEquals(JsonPrimitive(Base64.getEncoder().encodeToString(key)), b["restore_key"])
        assertFalse("replace_device" in b)
    }

    @Test fun replaceSendsTheFlag() {
        val b = body(buildRegisterRequest("acc", "fp", "Pixel", ByteArray(32), replaceDevice = true))
        assertEquals(JsonPrimitive(true), b["replace_device"])
    }

    private fun limit(extra: String) = json.decodeFromString(
        DeviceLimitErrorBody.serializer(),
        """{"error":"device_limit_exceeded","message":"Limit","devices_used":1,"device_limit":1$extra}""",
    )

    @Test fun parsesThe403RestoreKeyFields() {
        assertNull(limit("").restoreKeyStatus)
        assertNull(limit(""","restore_key":null""").restoreKeyStatus)
        assertEquals(RestoreKeyStatus.Unknown, limit(""","restore_key":"unknown"""").restoreKeyStatus)
        assertEquals(RestoreKeyStatus.NotEnough, limit(""","restore_key":"not_enough"""").restoreKeyStatus)
        // A status this build doesn't know reads as unknown.
        assertEquals(RestoreKeyStatus.Unknown, limit(""","restore_key":"later"""").restoreKeyStatus)
        val r = limit(
            ""","restore_key":"replaceable","replaceable_device":""" +
                """{"id":"d1","device_name":"Old Pixel","platform":"android","created_at":"2026-01-01T00:00:00Z"}""",
        )
        assertEquals(RestoreKeyStatus.Replaceable, r.restoreKeyStatus)
        assertEquals("Old Pixel", r.replaceable_device?.device_name)
        assertEquals("d1", r.replaceable_device?.id)
        // Replaceable without the device can't be offered.
        assertEquals(RestoreKeyStatus.Unknown, limit(""","restore_key":"replaceable"""").restoreKeyStatus)
    }

    @Test fun parsesReplacedDevice() {
        val base = """{"device_id":"d2","license_token":"t","devices_used":1,"device_limit":1,"tier":"free""""
        assertNull(json.decodeFromString(RegisterDeviceResponse.serializer(), "$base}").replaced_device)
        val r = json.decodeFromString(
            RegisterDeviceResponse.serializer(),
            """$base,"replaced_device":{"id":"d1","device_name":"Old Pixel"}}""",
        )
        assertEquals("Old Pixel", r.replaced_device?.device_name)
    }
}
