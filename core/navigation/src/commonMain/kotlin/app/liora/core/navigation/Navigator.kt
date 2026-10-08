package app.liora.core.navigation

import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.navigation3.runtime.NavKey

/**
 * Tab-aware back stack for Navigation 3. Each top-level tab keeps its own history; switching tabs
 * preserves it. The back stack handed to NavDisplay is the start tab's stack, followed by the current
 * tab's stack, so "back" from another tab's root lands on the start tab.
 */
@Stable
class Navigator(
    private val startRoute: NavKey,
    topLevelRoutes: Set<NavKey>,
) {
    init {
        require(startRoute in topLevelRoutes) { "startRoute must be a top-level route" }
    }

    private val tabs = topLevelRoutes.toList()

    private val stacks: Map<NavKey, SnapshotStateList<NavKey>> =
        tabs.associateWith { mutableStateListOf(it) }

    var topLevelRoute: NavKey by mutableStateOf(startRoute)
        private set

    /**
     * How the back stack last changed, so the screens can move to match: deeper, back, or across
     * tabs. Read when a transition starts; it doesn't need to be observed.
     */
    var lastMove: NavigationMove = NavigationMove.Forward
        private set

    /** Values screens hand back to the screen that opened them. */
    val results = NavigationResults()

    val backStack: List<NavKey> by derivedStateOf {
        val start = stacks.getValue(startRoute)
        if (topLevelRoute == startRoute) start.toList() else start + stacks.getValue(topLevelRoute)
    }

    val currentRoute: NavKey
        get() = backStack.last()

    /** Switches tabs. Re-selecting the current tab pops it back to its root. */
    fun selectTopLevel(route: NavKey) {
        val stack = stacks[route] ?: error("$route is not a top-level route")
        lastMove = if (route == topLevelRoute) NavigationMove.Back else NavigationMove.Tab
        if (route == topLevelRoute && stack.size > 1) {
            stack.removeRange(1, stack.size)
        }
        topLevelRoute = route
    }

    /** Pushes [route] onto the current tab. A route already open elsewhere moves here instead of duplicating. */
    fun navigate(route: NavKey) {
        if (route in stacks) {
            selectTopLevel(route)
            return
        }
        lastMove = NavigationMove.Forward
        stacks.values.forEach { it.remove(route) }
        stacks.getValue(topLevelRoute).add(route)
    }

    /**
     * Opens [route] from the list at [list], replacing whatever the list had opened before. In a
     * list-detail layout the detail pane shows one thing at a time, and back returns to the list rather
     * than through every item looked at.
     */
    fun openFromList(
        list: NavKey,
        route: NavKey,
    ) {
        val stack = stacks.getValue(topLevelRoute)
        val listIndex = stack.lastIndexOf(list)
        if (listIndex >= 0) stack.removeRange(listIndex + 1, stack.size)
        navigate(route)
    }

    /** Hands [value] back to whoever asked under [key], then closes the current screen. */
    fun goBackWithResult(
        key: String,
        value: Any,
    ) {
        results.set(key, value)
        goBack()
    }

    /**
     * Closes [route] together with everything opened on top of it, e.g. a list and the detail beside
     * it. A route that isn't open closes the current screen instead.
     */
    fun close(route: NavKey) {
        val stack = stacks.getValue(topLevelRoute)
        val index = stack.lastIndexOf(route)
        lastMove = NavigationMove.Back
        if (index > 0) stack.removeRange(index, stack.size) else goBack()
    }

    /** The current tab, by its place among the tabs, and every tab's stack: what `save()` writes down. */
    internal fun snapshot(): Pair<Int, List<List<NavKey>>> =
        tabs.indexOf(topLevelRoute) to tabs.map { stacks.getValue(it).toList() }

    /** Puts back a [snapshot]; one for other tabs than these is left alone. */
    internal fun restore(
        topLevel: Int,
        saved: List<List<NavKey>>,
    ) {
        if (saved.size != tabs.size || topLevel !in tabs.indices) return
        if (saved.withIndex().any { (index, stack) -> stack.firstOrNull() != tabs[index] }) return
        tabs.forEachIndexed { index, tab ->
            val stack = stacks.getValue(tab)
            stack.clear()
            stack.addAll(saved[index])
        }
        topLevelRoute = tabs[topLevel]
    }

    fun goBack() {
        val stack = stacks.getValue(topLevelRoute)
        lastMove = NavigationMove.Back
        when {
            stack.size > 1 -> {
                stack.removeAt(stack.lastIndex)
            }

            topLevelRoute != startRoute -> {
                // Back from another tab's root lands on the start tab: across, not back down.
                lastMove = NavigationMove.Tab
                topLevelRoute = startRoute
            }
        }
    }
}

/** Which way the app last moved. */
enum class NavigationMove {
    /** Deeper: a screen opened on top. */
    Forward,

    /** Back to where the user came from. */
    Back,

    /** Across to another tab. */
    Tab,
}
