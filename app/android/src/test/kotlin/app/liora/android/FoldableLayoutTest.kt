package app.liora.android

import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import app.liora.android.ui.ShellTags
import app.liora.core.data.AppStartup
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.koin.core.context.stopKoin
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The Galaxy Z Fold 7's two screens. The cover screen (~411 dp, compact) stacks one screen at a time
 * under a bottom bar; the inner screen (~984 dp, expanded) gets a navigation rail and puts the exercise
 * library beside the open exercise. Folding and unfolding mid-browse keeps the search and the exercise.
 *
 * Runs the real MainActivity, so resizing goes through the manifest's configChanges like on a device.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], application = TestLioraApplication::class)
class FoldableLayoutTest {
    @get:Rule
    val composeRule = createEmptyComposeRule()

    @Before
    fun seedCatalog() = runBlocking { GlobalContext.get().get<AppStartup>().run() }

    @After
    fun tearDown() = stopKoin()

    @Test
    @Config(qualifiers = COVER)
    fun coverScreen_bottomBarAndOneScreenAtATime() {
        launch()
        composeRule.onNodeWithTag(ShellTags.NAVIGATION_BAR).assertIsDisplayed()
        composeRule.onAllNodesWithTag(ShellTags.NAVIGATION_RAIL).assertCountEquals(0)

        composeRule.onNodeWithText("Exercises").performClick()
        waitForText(BENCH)
        composeRule.onRoot().captureRoboImage("src/test/screenshots/fold_cover_exercises_dark.png")

        composeRule.onAllNodesWithText(BENCH).onFirst().performClick()
        waitForText("How to")
        // The exercise replaces the library, with a way back to it.
        composeRule.onAllNodes(hasSetTextAction()).assertCountEquals(0)
        composeRule.onNodeWithContentDescription("Back").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = INNER)
    fun innerScreen_railAndLibraryBesideTheExercise() {
        val activity = launch().get()
        composeRule.onNodeWithTag(ShellTags.NAVIGATION_RAIL).assertIsDisplayed()
        composeRule.onAllNodesWithTag(ShellTags.NAVIGATION_BAR).assertCountEquals(0)

        composeRule.onNodeWithText("Exercises").performClick()
        waitForText("Pick an exercise")
        waitForText(BENCH)
        composeRule.onRoot().captureRoboImage("src/test/screenshots/fold_inner_exercises_empty_dark.png")

        composeRule.onAllNodesWithText(BENCH).onFirst().performClick()
        waitForText("How to")
        // Both panes: the search stays, the open exercise is highlighted, and nothing needs a back arrow.
        composeRule.onNode(hasSetTextAction()).assertIsDisplayed()
        composeRule.onNode(isSelected() and hasText(BENCH)).assertIsDisplayed()
        composeRule.onAllNodesWithContentDescription("Back").assertCountEquals(0)
        composeRule.onRoot().captureRoboImage("src/test/screenshots/fold_inner_exercises_dark.png")

        // Picking another exercise replaces the open one, so back returns straight to the empty pane.
        composeRule.onNode(hasSetTextAction()).performTextInput("deadlift")
        waitForText(DEADLIFT)
        composeRule.onAllNodesWithText(DEADLIFT).onFirst().performClick()
        composeRule.waitUntil(TIMEOUT_MS) { nodeExists(isSelected() and hasText(DEADLIFT)) }
        activity.onBackPressedDispatcher.onBackPressed()
        waitForText("Pick an exercise")
    }

    @Test
    @Config(qualifiers = INNER)
    fun innerScreen_theRunningWorkoutHeadsTheRail() {
        launch()
        composeRule.onNodeWithText("Start empty workout").performClick()
        waitForText("Finish")
        // The logger takes the whole screen.
        composeRule.onAllNodesWithTag(ShellTags.NAVIGATION_RAIL).assertCountEquals(0)

        composeRule.onNodeWithContentDescription("Minimize workout").performClick()
        composeRule.waitUntil(TIMEOUT_MS) { nodeExists(openWorkout) }
        composeRule.onNodeWithTag(ShellTags.NAVIGATION_RAIL).assertIsDisplayed()
        composeRule.onRoot().captureRoboImage("src/test/screenshots/fold_inner_train_active_workout_dark.png")

        composeRule.onNode(openWorkout).performClick()
        waitForText("Finish")
    }

    @Test
    @Config(qualifiers = COVER)
    fun unfoldingAndFoldingKeepTheSearchAndTheOpenExercise() {
        val controller = launch()
        composeRule.onNodeWithText("Exercises").performClick()
        waitForText(BENCH)
        composeRule.onNode(hasSetTextAction()).performTextInput("deadlift")
        waitForText(DEADLIFT)
        composeRule.onAllNodesWithText(DEADLIFT).onFirst().performClick()
        waitForText("How to")

        // Unfold: the library comes back beside the exercise, search as typed.
        controller.resize(INNER_SIZE)
        composeRule.waitUntil(TIMEOUT_MS) { nodeExists(hasSetTextAction() and hasText("deadlift")) }
        composeRule.onNodeWithTag(ShellTags.NAVIGATION_RAIL).assertIsDisplayed()
        composeRule.onNode(isSelected() and hasText(DEADLIFT)).assertIsDisplayed()

        // Fold: one screen again, still the deadlift, and back returns to the same search.
        controller.resize(COVER_SIZE)
        composeRule.waitUntil(TIMEOUT_MS) { !nodeExists(hasSetTextAction()) }
        composeRule.onNodeWithContentDescription("Back").performClick()
        composeRule.waitUntil(TIMEOUT_MS) { nodeExists(hasSetTextAction() and hasText("deadlift")) }
    }

    private fun launch(): ActivityController<MainActivity> =
        Robolectric.buildActivity(MainActivity::class.java).setup().also { composeRule.waitForIdle() }

    /** What folding or unfolding does to the window: a size change the activity handles in place. */
    private fun ActivityController<MainActivity>.resize(size: String) {
        RuntimeEnvironment.setQualifiers("+$size")
        configurationChange()
        composeRule.waitForIdle()
    }

    private fun nodeExists(matcher: SemanticsMatcher) =
        composeRule.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty()

    // Room emits on a background dispatcher that Compose's idling doesn't track, so wait explicitly.
    private fun waitForText(text: String) =
        composeRule.waitUntil(TIMEOUT_MS) { composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }

    private val openWorkout = hasContentDescription("Open workout")

    private companion object {
        const val COVER_SIZE = "w411dp-h960dp"
        const val INNER_SIZE = "w984dp-h1092dp"
        const val COVER = "$COVER_SIZE-night-xxhdpi"
        const val INNER = "$INNER_SIZE-night-xhdpi"
        const val BENCH = "Barbell Bench Press"
        const val DEADLIFT = "Barbell Deadlift"
        const val TIMEOUT_MS = 10_000L
    }
}
