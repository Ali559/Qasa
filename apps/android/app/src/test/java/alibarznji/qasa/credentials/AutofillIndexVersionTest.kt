package alibarznji.qasa.credentials

import alibarznji.qasa.credentials.AutofillIndexStore.Companion.CURRENT_FORMAT_VERSION
import alibarznji.qasa.credentials.AutofillIndexStore.Companion.formatVersionOf
import alibarznji.qasa.credentials.AutofillIndexStore.Companion.needsRebuild
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AutofillIndexVersionTest {

    @Test
    fun currentFormatIsNewerThanTheIndexWrittenBefore2fa() {
        // Installs from before the 2FA update wrote version 1 and must count as old.
        assertTrue(CURRENT_FORMAT_VERSION > 1)
        assertTrue(needsRebuild(1))
    }

    @Test
    fun currentOrNewerIndexIsLeftAlone() {
        assertFalse(needsRebuild(CURRENT_FORMAT_VERSION))
        assertFalse(needsRebuild(CURRENT_FORMAT_VERSION + 1))
    }

    @Test
    fun missingOrUnreadableIndexIsRebuilt() {
        assertTrue(needsRebuild(null))
        assertNull(formatVersionOf("not json"))
        assertNull(formatVersionOf("[1,2]"))
    }

    @Test
    fun readsVersionFromAnOldIndexFile() {
        val old = """{"version":1,"updatedAt":1700000000000,"entries":[]}"""
        assertEquals(1, formatVersionOf(old))
        assertTrue(needsRebuild(formatVersionOf(old)))
        // A file without the field predates versioning: format 1.
        assertEquals(1, formatVersionOf("""{"updatedAt":1,"entries":[]}"""))
    }

    @Test
    fun aFreshlyWrittenIndexIsCurrentSoTheNextUnlockSkipsTheRebuild() {
        val written = Json { encodeDefaults = true }.encodeToString(
            AutofillIndexFile.serializer(),
            AutofillIndexFile(version = CURRENT_FORMAT_VERSION, updatedAt = 1, entries = emptyList()),
        )
        assertEquals(CURRENT_FORMAT_VERSION, formatVersionOf(written))
        assertFalse(needsRebuild(formatVersionOf(written)))
    }
}
