package alibarznji.qasa.account

import alibarznji.qasa.network.QasaApiException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountDeletionTest {

    private val accountId = "3f1c2b9e-8a7d-4c6b-9e5f-1a2b3c4d5e6f"

    /** Local identity and vault as the fake sees them; erased together like `LocalDataEraser`. */
    private class FakePorts(
        var storedAccountId: String?,
        private val failWith: Exception? = null,
    ) : AccountDeletionPorts {
        var licenseToken: String? = "token"
        var deviceFingerprint: String? = "fingerprint"
        var vaultItems = mutableListOf("login-1", "card-1")
        val sent = mutableListOf<AccountDeletionCall>()
        var eraseCount = 0

        override fun accountId(): String? = storedAccountId

        override suspend fun send(call: AccountDeletionCall) {
            sent += call
            failWith?.let { throw it }
        }

        override suspend fun eraseLocal() {
            eraseCount++
            storedAccountId = null
            licenseToken = null
            deviceFingerprint = null
            vaultItems.clear()
        }
    }

    @Test
    fun requestIsDeleteOnAccountPathWithConfirmBody() {
        val call = AccountDeletionRequest.build("https://api.example.test/", accountId)
        assertEquals("DELETE", call.method)
        assertEquals("https://api.example.test/v1/account", call.url)
        assertEquals(
            JsonObject(mapOf("confirm_account_id" to JsonPrimitive(accountId))),
            Json.parseToJsonElement(call.bodyJson),
        )
    }

    @Test
    fun successSendsOnceThenErasesIdentityAndVault() = runTest {
        val ports = FakePorts(accountId)
        val result = deleteAccount(ports, "https://api.example.test")

        assertEquals(AccountDeletionResult.Deleted, result)
        assertEquals(1, ports.sent.size)
        assertEquals("https://api.example.test/v1/account", ports.sent.single().url)
        assertEquals(1, ports.eraseCount)
        assertNull(ports.storedAccountId)
        assertNull(ports.licenseToken)
        assertNull(ports.deviceFingerprint)
        assertTrue(ports.vaultItems.isEmpty())
    }

    @Test
    fun serverErrorKeepsEverythingLocal() = runTest {
        val ports = FakePorts(accountId, failWith = QasaApiException.HttpStatus(500))
        val result = deleteAccount(ports, "https://api.example.test")

        assertTrue(result is AccountDeletionResult.Failed)
        assertEquals(0, ports.eraseCount)
        assertEquals(accountId, ports.storedAccountId)
        assertNotNull(ports.licenseToken)
        assertNotNull(ports.deviceFingerprint)
        assertEquals(listOf("login-1", "card-1"), ports.vaultItems)
    }

    @Test
    fun offlineKeepsEverythingLocal() = runTest {
        val ports = FakePorts(accountId, failWith = QasaApiException.Network(java.io.IOException("offline")))
        assertTrue(deleteAccount(ports, "https://api.example.test") is AccountDeletionResult.Failed)
        assertEquals(0, ports.eraseCount)
        assertEquals(accountId, ports.storedAccountId)
    }

    @Test
    fun noAccountSendsNothingAndErasesNothing() = runTest {
        val ports = FakePorts(storedAccountId = null)
        assertEquals(AccountDeletionResult.NoAccount, deleteAccount(ports, "https://api.example.test"))
        assertTrue(ports.sent.isEmpty())
        assertFalse(ports.eraseCount > 0)
    }
}
