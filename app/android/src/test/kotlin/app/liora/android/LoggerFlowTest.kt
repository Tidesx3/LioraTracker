package app.liora.android

import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import app.liora.core.data.AppStartup
import app.liora.core.data.routine.RoutineRepository
import app.liora.core.data.workout.ActiveWorkoutRepository
import app.liora.core.data.workout.SetLogger
import app.liora.core.model.Mass
import app.liora.core.model.RepRange
import app.liora.core.model.Routine
import app.liora.core.model.RoutineExercise
import app.liora.core.model.RoutineSet
import app.liora.feature.logger.LoggerTags
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
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The logger end to end: a routine's sets logged with one tap each from last session's values, the
 * rest timer that follows, typing a set on the number pad, and the two panes on the Fold's inner screen.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], application = TestLioraApplication::class)
class LoggerFlowTest {
    @get:Rule
    val composeRule = createEmptyComposeRule()

    @Before
    fun seed() =
        runBlocking {
            GlobalContext.get().get<AppStartup>().run()
            GlobalContext.get().get<RoutineRepository>().save(PUSH)
        }

    @After
    fun tearDown() = stopKoin()

    @Test
    @Config(qualifiers = COVER)
    fun lastSessionsSetsTakeOneTapEach() {
        logLastSession()
        launch()
        waitForText("Start")
        composeRule.onNodeWithText("Start").performClick()
        waitForText("Finish")
        // Last time's sets show in the previous column and as placeholders.
        composeRule.onAllNodesWithText("80 kg × 10").assertCountEquals(3)

        composeRule.onAllNodesWithContentDescription("Log set").onFirst().performClick()
        waitForText("Skip")
        composeRule.onNodeWithText("Rest").assertIsDisplayed()
        composeRule.waitUntil(TIMEOUT_MS) { loggedSets() == 1 }
        composeRule.onRoot().captureRoboImage("src/test/screenshots/logger_resting_dark.png")

        composeRule.onNodeWithText("Skip").performClick()
        composeRule.waitUntil(TIMEOUT_MS) { !nodeExists(hasText("Skip")) }
    }

    @Test
    @Config(qualifiers = COVER)
    fun typeASetOnTheNumberPadAndMoveOn() {
        launch()
        composeRule.onNodeWithText("Start empty workout").performClick()
        waitForText("Add exercises")
        composeRule.onNodeWithText("Add exercises").performClick()
        composeRule.waitUntil(TIMEOUT_MS) { nodeExists(hasSetTextAction()) }
        composeRule.onNode(hasSetTextAction()).performTextInput("bench press")
        waitForText(BENCH)
        composeRule.onAllNodesWithText(BENCH).onFirst().performClick()
        waitForText("Add 1 exercise")
        composeRule.onNodeWithText("Add 1 exercise").performClick()
        waitForText("Add set")

        // Weight, Next, reps, then the tick on the pad logs the set and jumps to the next one.
        composeRule.onAllNodesWithTag(LoggerTags.CELL)[0].performClick()
        composeRule.waitUntil(TIMEOUT_MS) { nodeExists(hasTestTag(LoggerTags.PAD)) }
        listOf("8", "2", ".", "5").forEach { padKey(it).performClick() }
        waitForText("82.5")
        padButton("Next field").performClick()
        listOf("1", "0").forEach { padKey(it).performClick() }
        padButton("Log set").performClick()

        composeRule.waitUntil(TIMEOUT_MS) { loggedSets() == 1 }
        waitForText("Skip")
        // The second set's weight is up next, already selected.
        composeRule.waitUntil(TIMEOUT_MS) { nodeExists(hasTestTag(LoggerTags.CELL) and isSelected()) }
        composeRule.onAllNodesWithTag(LoggerTags.CELL)[2].assert(isSelected())
        composeRule.onRoot().captureRoboImage("src/test/screenshots/logger_pad_dark.png")
    }

    @Test
    @Config(qualifiers = INNER)
    fun innerScreen_theSetUpNextBesideTheWorkout() {
        logLastSession()
        launch()
        waitForText("Start")
        composeRule.onNodeWithText("Start").performClick()
        waitForText("Set 1 of 3")
        // The pad is always there beside the list, and logs the set that's up next.
        composeRule.onNode(hasTestTag(LoggerTags.PAD)).assertIsDisplayed()
        composeRule.onNodeWithText("Last time: 80 kg × 10").assertIsDisplayed()
        composeRule.onNode(hasText("Log set") and !hasAnyAncestor(hasTestTag(LoggerTags.PAD))).performClick()
        waitForText("Set 2 of 3")
        waitForText("Skip")
        composeRule.onRoot().captureRoboImage("src/test/screenshots/fold_inner_logger_dark.png")
    }

    @Test
    @Config(qualifiers = "de-$COVER")
    fun germanDecimalCommaOnThePad() {
        launch()
        waitForText("Starten")
        composeRule.onNodeWithText("Starten").performClick()
        waitForText("Beenden")
        composeRule.onAllNodesWithTag(LoggerTags.CELL)[0].performClick()
        composeRule.waitUntil(TIMEOUT_MS) { nodeExists(hasTestTag(LoggerTags.PAD)) }
        listOf("8", "2", ",", "5").forEach { padKey(it).performClick() }
        waitForText("82,5")
        composeRule.onRoot().captureRoboImage("src/test/screenshots/de_logger_pad_dark.png")
    }

    /** A finished Push session: every set 80 kg × 10. */
    private fun logLastSession() =
        runBlocking {
            val koin = GlobalContext.get()
            val workouts = koin.get<ActiveWorkoutRepository>()
            val sets = koin.get<SetLogger>()
            val workout = workouts.startFromRoutine(PUSH.id)
            workout.exercises.single().sets.forEach { set ->
                sets.updateSet(set.id) { copy(reps = 10) }
                sets.completeSet(set.id)
            }
            workouts.finish()
        }

    private fun launch() {
        Robolectric.buildActivity(MainActivity::class.java).setup()
        composeRule.waitForIdle()
    }

    private fun padKey(label: String): SemanticsNodeInteraction =
        composeRule.onNode(hasText(label) and hasAnyAncestor(hasTestTag(LoggerTags.PAD)))

    private fun padButton(description: String): SemanticsNodeInteraction =
        composeRule.onNode(hasContentDescription(description) and hasAnyAncestor(hasTestTag(LoggerTags.PAD)))

    /** How many sets are ticked off: their tick now offers to undo. */
    private fun loggedSets() =
        composeRule.onAllNodesWithContentDescription("Undo logged set").fetchSemanticsNodes().size

    private fun nodeExists(matcher: SemanticsMatcher) =
        composeRule.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty()

    // Room emits on a background dispatcher that Compose's idling doesn't track, so wait explicitly.
    private fun waitForText(text: String) =
        composeRule.waitUntil(TIMEOUT_MS) { composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }

    private companion object {
        const val COVER = "w411dp-h960dp-night-xxhdpi"
        const val INNER = "w984dp-h1092dp-night-xhdpi"
        const val BENCH = "Barbell Bench Press"
        const val TIMEOUT_MS = 10_000L

        val PUSH =
            Routine(
                id = "push",
                name = "Push",
                exercises =
                    listOf(
                        RoutineExercise(
                            id = "push-bench",
                            exerciseId = "fedb.Barbell_Bench_Press_-_Medium_Grip",
                            restSeconds = 90,
                            sets =
                                List(
                                    3,
                                ) { RoutineSet("push-bench-$it", weight = Mass(80.0), reps = RepRange(8, 12)) },
                        ),
                    ),
            )
    }
}
