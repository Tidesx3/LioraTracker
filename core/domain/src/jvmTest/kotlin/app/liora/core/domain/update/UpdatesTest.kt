package app.liora.core.domain.update

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Versions as releases and builds name them, and release notes in the app's language. */
class UpdatesTest {
    @Test
    fun versionsReadFromTagsAndBuildNames() {
        assertEquals(AppVersion(0, 4, 0), AppVersion.parse("0.4.0"))
        assertEquals(AppVersion(0, 4, 0), AppVersion.parse("v0.4.0"))
        assertEquals(AppVersion(1, 12, 3), AppVersion.parse("1.12.3-debug"))
        assertNull(AppVersion.parse("0.4"))
        assertNull(AppVersion.parse("nightly"))
        assertNull(AppVersion.parse("v99999999999.0.0"))
    }

    @Test
    fun versionsCompareByNumberNotText() {
        assertTrue(AppVersion.parse("0.10.0")!! > AppVersion.parse("0.9.9")!!)
        assertTrue(AppVersion.parse("1.0.0")!! > AppVersion.parse("0.99.99")!!)
        assertTrue(AppVersion.parse("0.4.1")!! > AppVersion.parse("0.4.0")!!)
        assertEquals("0.4.1", AppVersion(0, 4, 1).toString())
    }

    @Test
    fun notesComeInTheAppsLanguage() {
        val notes =
            """
            ## English

            Something new.

            - Rotations
            * Widget

            ## Deutsch

            Etwas Neues.

            - Rotationen
            """.trimIndent()
        assertEquals("Something new.\n\n• Rotations\n• Widget", ReleaseNotes.forLanguage(notes, "en"))
        assertEquals("Etwas Neues.\n\n• Rotationen", ReleaseNotes.forLanguage(notes, "de"))
        // Any other language reads the English.
        assertEquals("Something new.\n\n• Rotations\n• Widget", ReleaseNotes.forLanguage(notes, "fr"))
    }

    @Test
    fun notesWithoutSectionsShowWhole() {
        assertEquals("• Fixes", ReleaseNotes.forLanguage("- Fixes\n", "de"))
    }
}
