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

    private val stacks: Map<NavKey, SnapshotStateList<NavKey>> =
        topLevelRoutes.associateWith { mutableStateListOf(it) }

    var topLevelRoute: NavKey by mutableStateOf(startRoute)
        private set

    val backStack: List<NavKey> by derivedStateOf {
        val start = stacks.getValue(startRoute)
        if (topLevelRoute == startRoute) start.toList() else start + stacks.getValue(topLevelRoute)
    }

    val currentRoute: NavKey
        get() = backStack.last()

    /** Switches tabs. Re-selecting the current tab pops it back to its root. */
    fun selectTopLevel(route: NavKey) {
        val stack = stacks[route] ?: error("$route is not a top-level route")
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

    fun goBack() {
        val stack = stacks.getValue(topLevelRoute)
        when {
            stack.size > 1 -> stack.removeAt(stack.lastIndex)
            topLevelRoute != startRoute -> topLevelRoute = startRoute
        }
    }
}
