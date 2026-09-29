package app.liora.core.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavMetadataKey
import androidx.navigation3.runtime.contains
import androidx.navigation3.runtime.get
import androidx.navigation3.runtime.metadata
import androidx.navigation3.scene.Scene
import androidx.navigation3.scene.SceneStrategy
import androidx.navigation3.scene.SceneStrategyScope

/**
 * List-detail layouts: features tag entries with [listPane] or [detailPane] metadata, and on wide
 * windows [ListDetailSceneStrategy] shows a list next to what it opened. Everywhere else the entries
 * stack as single screens, so the same navigation code serves the Fold's cover and inner screens.
 */
object ListDetail {
    /** A list destination. [placeholder] fills the detail pane until the list opens something. */
    fun listPane(placeholder: @Composable () -> Unit): Map<String, Any> =
        metadata { put(ListPaneKey, ListPane(placeholder)) }

    /** Shown beside the nearest list below it on the back stack, e.g. an exercise or its editor. */
    fun detailPane(): Map<String, Any> = metadata { put(DetailPaneKey, true) }
}

/** Where an entry is being shown, so screens can adapt their chrome to sitting beside another pane. */
enum class PaneRole { Single, List, Detail }

val LocalPaneRole = staticCompositionLocalOf { PaneRole.Single }

/**
 * Shows a list and its detail side by side when [twoPane] is true: the top entry is a list (the detail
 * pane shows its placeholder), or the top entries are details resting on a list. Otherwise it returns
 * null and NavDisplay falls back to a single pane.
 */
class ListDetailSceneStrategy<T : Any>(
    private val twoPane: Boolean,
) : SceneStrategy<T> {
    override fun SceneStrategyScope<T>.calculateScene(entries: List<NavEntry<T>>): Scene<T>? {
        if (!twoPane) return null
        // Below the details on top (if any) must be a list; anything else is a single pane.
        val listIndex = entries.indexOfLast { DetailPaneKey !in it.metadata }
        val list = entries.getOrNull(listIndex)?.let { it.metadata[ListPaneKey] } ?: return null
        return ListDetailScene(
            list = entries[listIndex],
            detail = entries.last().takeIf { listIndex < entries.lastIndex },
            placeholder = list.placeholder,
            previousEntries = entries.dropLast(1),
        )
    }
}

/**
 * Keyed by the list, so opening another detail swaps the right pane in place while the list, its
 * scroll position and search query stay put.
 */
private class ListDetailScene<T : Any>(
    private val list: NavEntry<T>,
    private val detail: NavEntry<T>?,
    private val placeholder: @Composable () -> Unit,
    override val previousEntries: List<NavEntry<T>>,
) : Scene<T> {
    override val key: Any = ListDetailScene::class to list.contentKey

    override val entries: List<NavEntry<T>> = listOfNotNull(list, detail)

    override val content: @Composable () -> Unit = {
        Row(Modifier.fillMaxSize()) {
            Box(Modifier.width(ListPaneWidth).fillMaxHeight()) {
                CompositionLocalProvider(LocalPaneRole provides PaneRole.List) { list.Content() }
            }
            VerticalDivider()
            Box(Modifier.weight(1f).fillMaxHeight()) {
                CompositionLocalProvider(LocalPaneRole provides PaneRole.Detail) {
                    if (detail == null) placeholder() else key(detail.contentKey) { detail.Content() }
                }
            }
        }
    }

    override fun equals(other: Any?): Boolean =
        other is ListDetailScene<*> &&
            list == other.list &&
            detail == other.detail &&
            previousEntries == other.previousEntries

    override fun hashCode(): Int = (list.hashCode() * 31 + detail.hashCode()) * 31 + previousEntries.hashCode()
}

private class ListPane(
    val placeholder: @Composable () -> Unit,
)

// Metadata is stored under the key's toString(), so these are explicit and namespaced.
private object ListPaneKey : NavMetadataKey<ListPane> {
    override fun toString() = "liora.listPane"
}

private object DetailPaneKey : NavMetadataKey<Boolean> {
    override fun toString() = "liora.detailPane"
}

/** Material's standard list pane width; the detail pane takes the rest. */
private val ListPaneWidth = 360.dp
