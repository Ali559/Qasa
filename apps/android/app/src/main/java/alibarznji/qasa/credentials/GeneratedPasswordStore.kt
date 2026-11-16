package alibarznji.qasa.credentials

import alibarznji.qasa.vault.VaultPayload
import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** One "Use a strong password" the autofill service offered (R4, docs/autofill-hardening.md). */
@Serializable
data class GeneratedPassword(
    /** The dataset id ([GeneratedPasswordLog.DATASET_PREFIX]…), as fill events report it. */
    val id: String,
    /** The new login's name and website: the site's host, or the app's label and `androidapp://` link. */
    val name: String,
    val website: String,
    val password: String,
    val createdAt: Long,
    /** The user picked it (a fill event or a save request said so). */
    val picked: Boolean = false,
)

/**
 * "Save the password you created for <site>?" on unlock (R4): Save opens the new-login form
 * with [login]; Not now drops it. [site] is null when there is no name to show.
 */
data class GeneratedSaveOffer(val login: VaultPayload.Login, val site: String?)

/**
 * The rules of the "recent generated" list, pure so they are unit tested. Offered values
 * are kept (a few, briefly) because Android reports a pick only later, by dataset id; a
 * picked one is kept until it is saved, offered once on unlock, or 24 hours old.
 */
object GeneratedPasswordLog {
    const val DATASET_PREFIX = "qasa-strong-"
    const val TTL_MILLIS = 24L * 60 * 60 * 1000
    /** Unpicked offers kept: one per fill request, so the last few screens. */
    const val MAX_OFFERED = 8

    fun prune(entries: List<GeneratedPassword>, now: Long): List<GeneratedPassword> {
        val live = entries.filter { now - it.createdAt in 0 until TTL_MILLIS }
        val picked = live.filter { it.picked }.takeLast(MAX_OFFERED)
        val offered = live.filter { !it.picked }.takeLast(MAX_OFFERED)
        return live.filter { it in picked || it in offered }
    }

    fun offered(entries: List<GeneratedPassword>, entry: GeneratedPassword, now: Long): List<GeneratedPassword> =
        prune(entries.filter { it.id != entry.id } + entry, now)

    /** Marks the entries with these dataset ids, or holding [password], as picked. */
    fun picked(entries: List<GeneratedPassword>, ids: Collection<String>, password: String? = null): List<GeneratedPassword> =
        entries.map { if (it.id in ids || (password != null && it.password == password)) it.copy(picked = true) else it }

    /**
     * The user picked the "Use a strong password" row with this dataset id (its
     * authentication, [StrongPasswordPickActivity]): the entry to fill, and the list with it
     * marked picked. Null (list unchanged) when it is unknown or expired.
     */
    fun pick(entries: List<GeneratedPassword>, id: String, now: Long): Pair<GeneratedPassword?, List<GeneratedPassword>> {
        val live = prune(entries, now)
        val entry = live.firstOrNull { it.id == id } ?: return null to entries
        return entry to live.map { if (it.id == id) it.copy(picked = true) else it }
    }

    /** The unlock offer for [entry]: its new login, and the site the title names (none when blank). */
    fun offer(entry: GeneratedPassword): GeneratedSaveOffer = GeneratedSaveOffer(
        login = VaultPayload.Login(itemName = entry.name, password = entry.password, website = entry.website),
        site = entry.name.trim().ifEmpty { null },
    )

    /**
     * On unlock: the picked password to offer for saving (the newest one no saved login
     * holds), and what stays. Saved ones and the offered one leave the list: each is
     * offered once.
     */
    fun dueOnUnlock(entries: List<GeneratedPassword>, savedPasswords: Set<String>, now: Long): Pair<GeneratedPassword?, List<GeneratedPassword>> {
        val live = prune(entries, now).filter { !(it.picked && it.password in savedPasswords) }
        val due = live.lastOrNull { it.picked }
        return due to live.filter { it != due }
    }
}

/**
 * The "recent generated" list on disk: `files/autofill-generated/recent.bin`, AES-GCM with
 * an Android Keystore key that needs no user authentication (the autofill service writes it
 * while the vault is locked). Excluded from backups and device transfer. Single process, so
 * a lock is enough.
 */
@Singleton
class GeneratedPasswordStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val dir: File get() = File(context.filesDir, "autofill-generated")
    private val file: File get() = File(dir, "recent.bin")

    @Synchronized
    fun read(): List<GeneratedPassword> {
        if (!file.exists()) return emptyList()
        return try {
            val blob = file.readBytes()
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, blob.copyOfRange(0, IV_SIZE)))
            json.decodeFromString<List<GeneratedPassword>>(String(cipher.doFinal(blob, IV_SIZE, blob.size - IV_SIZE), Charsets.UTF_8))
        } catch (_: Exception) {
            // Unreadable (key gone after a reset): nothing to recover.
            file.delete()
            emptyList()
        }
    }

    @Synchronized
    fun update(transform: (List<GeneratedPassword>) -> List<GeneratedPassword>) {
        write(transform(read()))
    }

    @Synchronized
    fun clear() {
        file.delete()
    }

    @Synchronized
    private fun write(entries: List<GeneratedPassword>) {
        if (entries.isEmpty()) {
            file.delete()
            return
        }
        try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, key())
            val sealed = cipher.iv + cipher.doFinal(json.encodeToString(entries).toByteArray(Charsets.UTF_8))
            dir.mkdirs()
            val tmp = File(dir, "recent.bin.tmp")
            tmp.writeBytes(sealed)
            if (!tmp.renameTo(file)) {
                file.delete()
                tmp.renameTo(file)
            }
        } catch (_: Exception) {
            // Best effort: losing the record only loses the unlock reminder.
        }
    }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (store.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    private companion object {
        const val KEY_ALIAS = "qasa_autofill_generated_key"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_SIZE = 12
    }
}
