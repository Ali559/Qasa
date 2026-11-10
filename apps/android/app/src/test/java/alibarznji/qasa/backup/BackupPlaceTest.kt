package alibarznji.qasa.backup

import org.junit.Assert.assertEquals
import org.junit.Test

class BackupPlaceTest {
    @Test
    fun knowsCloudAndPhoneProviders() {
        assertEquals(BackupPlace.Cloud("Google Drive"), BackupPlace.of("com.google.android.apps.docs.storage"))
        assertEquals(BackupPlace.ThisPhone, BackupPlace.of("com.android.externalstorage.documents"))
        assertEquals(BackupPlace.ThisPhone, BackupPlace.of("com.android.providers.downloads.documents"))
        assertEquals(BackupPlace.Unknown, BackupPlace.of("com.example.files"))
        assertEquals(BackupPlace.Unknown, BackupPlace.of(null))
    }
}
