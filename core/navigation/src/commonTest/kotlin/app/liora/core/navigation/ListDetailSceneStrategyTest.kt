package app.liora.core.navigation

import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.scene.Scene
import androidx.navigation3.scene.SceneStrategyScope
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ListDetailSceneStrategyTest {
    private val home = entry("home")
    private val library = entry("library", ListDetail.listPane {})
    private val bench = entry("bench", ListDetail.detailPane())
    private val benchEditor = entry("bench-editor", ListDetail.detailPane())
    private val logger = entry("logger")

    @Test
    fun narrowWindowsAlwaysGetOnePane() {
        assertNull(scene(twoPane = false, home, library, bench))
    }

    @Test
    fun aListOnTopGetsThePlaceholderBesideIt() {
        val scene = scene(twoPane = true, home, library)!!
        assertEquals(listOf("library"), scene.shown())
        assertEquals(listOf("home"), scene.previousEntries.map { it.contentKey })
    }

    @Test
    fun aDetailShowsBesideItsList() {
        val scene = scene(twoPane = true, home, library, bench)!!
        assertEquals(listOf("library", "bench"), scene.shown())
        // Back from the pair returns to the list with its placeholder.
        assertEquals(listOf("home", "library"), scene.previousEntries.map { it.contentKey })
    }

    @Test
    fun stackedDetailsShowTheTopOneBesideTheList() {
        assertEquals(listOf("library", "bench-editor"), scene(twoPane = true, library, bench, benchEditor)!!.shown())
    }

    @Test
    fun anOpenedDetailKeepsTheSceneSoTheListStaysPut() {
        assertEquals(scene(twoPane = true, library)!!.key, scene(twoPane = true, library, bench)!!.key)
    }

    @Test
    fun aDetailWithoutAListBelowItIsASinglePane() {
        assertNull(scene(twoPane = true, home, bench))
        // Something full-screen in between (the logger) breaks the pair.
        assertNull(scene(twoPane = true, library, logger, bench))
    }

    @Test
    fun untaggedEntriesAreLeftToTheDefault() {
        assertNull(scene(twoPane = true, library, bench, logger))
    }

    private fun scene(
        twoPane: Boolean,
        vararg entries: NavEntry<String>,
    ): Scene<String>? =
        with(ListDetailSceneStrategy<String>(twoPane)) {
            SceneStrategyScope<String>().calculateScene(entries.toList())
        }

    private fun Scene<String>.shown() = entries.map { it.contentKey }

    private fun entry(
        key: String,
        metadata: Map<String, Any> = emptyMap(),
    ) = NavEntry(key, contentKey = key, metadata = metadata) {}
}
