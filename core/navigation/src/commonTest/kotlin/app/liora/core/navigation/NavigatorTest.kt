package app.liora.core.navigation

import kotlin.test.Test
import kotlin.test.assertEquals

class NavigatorTest {
    private val navigator = Navigator(startRoute = TrainRoute, topLevelRoutes = setOf(TrainRoute, ExercisesRoute))

    @Test
    fun openingFromAListReplacesWhatItOpenedBefore() {
        navigator.selectTopLevel(ExercisesRoute)
        navigator.openFromList(ExercisesRoute, ExerciseDetailRoute("bench"))
        navigator.navigate(ExerciseEditorRoute(exerciseId = "bench"))

        navigator.openFromList(ExercisesRoute, ExerciseDetailRoute("squat"))

        assertEquals(listOf(TrainRoute, ExercisesRoute, ExerciseDetailRoute("squat")), navigator.backStack)
        navigator.goBack()
        assertEquals(ExercisesRoute, navigator.currentRoute)
    }

    @Test
    fun closingAListClosesWhatItOpened() {
        navigator.navigate(BodyRoute)
        navigator.openFromList(BodyRoute, MeasurementDetailRoute("waist"))

        navigator.close(BodyRoute)

        assertEquals(listOf(TrainRoute), navigator.backStack)
    }

    @Test
    fun eachChangeSaysWhichWayItWent() {
        navigator.navigate(BodyRoute)
        assertEquals(NavigationMove.Forward, navigator.lastMove)
        navigator.goBack()
        assertEquals(NavigationMove.Back, navigator.lastMove)

        navigator.selectTopLevel(ExercisesRoute)
        assertEquals(NavigationMove.Tab, navigator.lastMove)
        navigator.openFromList(ExercisesRoute, ExerciseDetailRoute("bench"))
        assertEquals(NavigationMove.Forward, navigator.lastMove)
        // Re-selecting the tab returns to its root.
        navigator.selectTopLevel(ExercisesRoute)
        assertEquals(NavigationMove.Back, navigator.lastMove)
        // Back from another tab's root crosses to the start tab.
        navigator.goBack()
        assertEquals(NavigationMove.Tab, navigator.lastMove)
        assertEquals(TrainRoute, navigator.currentRoute)
    }

    @Test
    fun openingFromAListNotOnTheStackJustNavigates() {
        navigator.navigate(LoggerRoute)
        navigator.openFromList(ExercisesRoute, ExerciseDetailRoute("bench"))

        assertEquals(listOf(TrainRoute, LoggerRoute, ExerciseDetailRoute("bench")), navigator.backStack)
    }
}
