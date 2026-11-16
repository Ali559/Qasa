package alibarznji.qasa.siteicons

import alibarznji.qasa.vault.crypto.VaultCrypto
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/** What a host's last check found (docs/site-icons.md "Cache"). */
enum class SiteIconStatus(val raw: Int) {
    /** The site gave a usable icon ([SiteIconEntry.png]). */
    Icon(1),

    /** The site answered but had no usable icon. */
    None(2),

    /** Offline, timeout, TLS error: try again soon. An earlier icon is kept meanwhile. */
    Failure(3);

    companion object {
        fun fromRaw(raw: Int): SiteIconStatus? = entries.firstOrNull { it.raw == raw }
    }
}

/** One host's cache entry: the 64 × 64 PNG (if any), what the last check found, and when. */
class SiteIconEntry(val host: String, val status: SiteIconStatus, val checkedAt: Long, val png: ByteArray?)

/**
 * The per-device icon cache: one file per host in [dir] (app-private, excluded from
 * backups), each sealed with the vault key exactly like an item (AES-256-GCM,
 * [VaultCrypto]). The file name is an HMAC of the host under a key derived from the vault
 * key, so neither the names nor the contents reveal which sites the vault holds, and a
 * different vault (or a changed master password) simply finds nothing.
 */
class SiteIconCache(
    private val dir: File,
    private val now: () -> Long = System::currentTimeMillis,
) {
    /** The entry for [host] under [key], or null (none, unreadable, or another vault's). */
    fun read(host: String, key: ByteArray): SiteIconEntry? {
        val file = fileFor(host, key)
        if (!file.isFile) return null
        return try {
            decode(VaultCrypto.decrypt(file.readBytes(), key))?.takeIf { it.host == host }
        } catch (_: Exception) {
            file.delete()
            null
        }
    }

    /** Stores what a check of [host] found now. A failure keeps an earlier icon, if any. */
    fun record(host: String, status: SiteIconStatus, png: ByteArray?, key: ByteArray): SiteIconEntry {
        val kept = if (status == SiteIconStatus.Failure) png ?: read(host, key)?.png else png
        val entry = SiteIconEntry(host, status, now(), if (status == SiteIconStatus.None) null else kept)
        dir.mkdirs()
        val target = fileFor(host, key)
        val tmp = File(dir, target.name + ".tmp")
        tmp.writeBytes(VaultCrypto.encrypt(encode(entry), key))
        if (!tmp.renameTo(target)) {
            target.delete()
            tmp.renameTo(target)
        }
        return entry
    }

    /** Whether [entry] (null: never checked) should be checked again now. */
    fun isDue(entry: SiteIconEntry?): Boolean = entry == null || now() - entry.checkedAt >= ttl(entry.status) || now() < entry.checkedAt

    /** Deletes every entry: the setting turned off, sign out, reset, a new master password. */
    fun clear() {
        dir.deleteRecursively()
    }

    private fun fileFor(host: String, key: ByteArray): File = File(dir, hex(hmac(hmac(key, NAME_KEY_LABEL), host.toByteArray(Charsets.UTF_8))) + ".bin")

    companion object {
        const val DAY_MS: Long = 24L * 60 * 60 * 1000
        const val ICON_TTL_MS: Long = 30 * DAY_MS
        const val NONE_TTL_MS: Long = 7 * DAY_MS
        const val FAILURE_TTL_MS: Long = DAY_MS

        private const val FORMAT_VERSION = 1
        private val NAME_KEY_LABEL = "qasa.site-icons.names.v1".toByteArray(Charsets.UTF_8)

        fun ttl(status: SiteIconStatus): Long = when (status) {
            SiteIconStatus.Icon -> ICON_TTL_MS
            SiteIconStatus.None -> NONE_TTL_MS
            SiteIconStatus.Failure -> FAILURE_TTL_MS
        }

        private fun hmac(key: ByteArray, data: ByteArray): ByteArray =
            Mac.getInstance("HmacSHA256").run {
                init(SecretKeySpec(key, "HmacSHA256"))
                doFinal(data)
            }

        private fun hex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it) }

        private fun encode(entry: SiteIconEntry): ByteArray {
            val out = ByteArrayOutputStream()
            DataOutputStream(out).use { d ->
                d.writeByte(FORMAT_VERSION)
                d.writeByte(entry.status.raw)
                d.writeLong(entry.checkedAt)
                d.writeUTF(entry.host)
                val png = entry.png
                d.writeInt(png?.size ?: -1)
                if (png != null) d.write(png)
            }
            return out.toByteArray()
        }

        private fun decode(bytes: ByteArray): SiteIconEntry? = DataInputStream(ByteArrayInputStream(bytes)).use { d ->
            if (d.readUnsignedByte() != FORMAT_VERSION) return null
            val status = SiteIconStatus.fromRaw(d.readUnsignedByte()) ?: return null
            val checkedAt = d.readLong()
            val host = d.readUTF()
            val size = d.readInt()
            val png = if (size < 0) null else ByteArray(size).also { d.readFully(it) }
            SiteIconEntry(host, status, checkedAt, png)
        }
    }
}
