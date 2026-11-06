package alibarznji.qasa.vault

import org.junit.Assert.assertEquals
import org.junit.Test

/** Every payload type must survive encode → decode; a failure here means the item can't be saved. */
class VaultPayloadRoundTripTest {
    private fun roundTrip(p: VaultPayload) =
        assertEquals(p, VaultJson.decodePayload(VaultJson.encodePayload(p)))

    @Test fun name() = roundTrip(VaultPayload.Name(type = "mr", firstName = "Ali"))
    @Test fun personalEmail() = roundTrip(VaultPayload.PersonalEmail(itemName = "x", type = "work", email = "a@b.c"))
    @Test fun phone() = roundTrip(VaultPayload.Phone(itemName = "x", type = "workCell", number = "123"))
    @Test fun login() = roundTrip(VaultPayload.Login(itemName = "x", password = "p"))
    @Test fun loginWithTotp() = roundTrip(VaultPayload.Login(itemName = "x", totp = "otpauth://totp/Qasa?secret=ABC"))

    @Test fun loginSavedBefore2faStillDecodes() {
        val legacy = """{"type":"login","itemName":"x","password":"p","collectionId":null}"""
        assertEquals(VaultPayload.Login(itemName = "x", password = "p"), VaultJson.decodePayload(legacy.toByteArray()))
    }

    @Test fun defaultsUseIosRawValues() {
        val json = String(VaultJson.encodePayload(VaultPayload.Phone(number = "1")))
        // iOS `PhoneKind` is a Codable enum whose raw value is "Cell"; "cell" would not decode there.
        assert(json.contains("\"phoneType\":\"Cell\"")) { json }
        assert(json.contains("\"type\":\"phone\"")) { json }
    }
}
