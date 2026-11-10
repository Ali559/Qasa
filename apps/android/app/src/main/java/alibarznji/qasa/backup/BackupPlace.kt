package alibarznji.qasa.backup

/**
 * Where the backup file actually lives, from the document provider that holds it. Qasa
 * never uploads backups itself, so this is what tells the user whether the file is off
 * the phone (a cloud provider) or still on it.
 */
sealed interface BackupPlace {
    data class Cloud(val name: String) : BackupPlace
    data object ThisPhone : BackupPlace
    /** A provider we don't know: the file name is all we show. */
    data object Unknown : BackupPlace

    companion object {
        private val cloud = mapOf(
            "com.google.android.apps.docs.storage" to "Google Drive",
            "com.google.android.apps.docs.storage.legacy" to "Google Drive",
            "com.microsoft.skydrive.content.StorageAccessProvider" to "OneDrive",
            "com.microsoft.skydrive.content.external" to "OneDrive",
            "com.dropbox.product.android.dbapp.document_provider.documents" to "Dropbox",
            "com.box.android.documents" to "Box",
            "org.nextcloud.documents" to "Nextcloud",
        )
        private val onDevice = setOf(
            "com.android.externalstorage.documents",
            "com.android.providers.downloads.documents",
            "com.android.providers.media.documents",
        )

        /** From the content URI's authority (`content://<authority>/…`). */
        fun of(authority: String?): BackupPlace = when {
            authority == null -> Unknown
            authority in onDevice -> ThisPhone
            else -> cloud[authority]?.let(::Cloud) ?: Unknown
        }
    }
}
