package alibarznji.qasa.vpn

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.wireguard.crypto.Key
import com.wireguard.crypto.KeyPair
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * This device's WireGuard keypair. The private key is made here, never leaves the device and
 * is kept in Keystore-backed encrypted preferences; the server only ever sees the public key.
 * One key per install: the server reuses the session for the same device, key and node.
 */
@Singleton
class WireGuardKeyStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val prefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            FILE,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    @Synchronized
    fun keyPair(): KeyPair {
        prefs.getString(KEY_PRIVATE, null)
            ?.let { runCatching { KeyPair(Key.fromBase64(it)) }.getOrNull() }
            ?.let { return it }
        val fresh = KeyPair()
        prefs.edit().putString(KEY_PRIVATE, fresh.privateKey.toBase64()).commit()
        return fresh
    }

    /** Sign-out and "clear device": the next connect registers a new key. */
    @Synchronized
    fun clear() {
        prefs.edit().remove(KEY_PRIVATE).commit()
    }

    private companion object {
        const val FILE = "qasa.vpn.keys"
        const val KEY_PRIVATE = "wg.privateKey"
    }
}
