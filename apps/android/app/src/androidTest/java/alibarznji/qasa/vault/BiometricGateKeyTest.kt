package alibarznji.qasa.vault

import android.app.KeyguardManager
import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.security.KeyStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The key that seals the master password for fingerprint unlock must survive an app
 * restart. Each `VaultSecureStore` here stands for one process start: the second must use
 * the key the first one made, not replace it (which broke fingerprint unlock after every
 * restart). Needs a screen lock on the device, as the key does.
 */
@RunWith(AndroidJUnit4::class)
class BiometricGateKeyTest {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun creationDate() =
        KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.getCreationDate("qasa_biometric_gate_key")

    @Test
    fun theGateKeySurvivesARestart() {
        val keyguard = context.getSystemService(KeyguardManager::class.java)
        assumeTrue("needs a screen lock", keyguard.isDeviceSecure)

        val firstStart = VaultSecureStore(context)
        assertNotNull(firstStart.biometricEncryptCipher())
        val made = creationDate()
        assertNotNull(made)

        Thread.sleep(1_100) // a regenerated key would get a later creation date
        val secondStart = VaultSecureStore(context)
        assertNotNull(secondStart.biometricEncryptCipher())
        assertEquals(made, creationDate())
    }
}
