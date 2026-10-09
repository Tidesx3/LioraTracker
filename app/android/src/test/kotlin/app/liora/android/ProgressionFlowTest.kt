package app.liora.android

import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasScrollToKeyAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToKey
import androidx.compose.ui.test.performTextInput
import app.liora.core.data.AppStartup
import app.liora.core.data.exercise.ExerciseRepository
import app.liora.core.data.routine.RoutineRepository
import app.liora.core.data.settings.SettingsRepository
import app.liora.core.data.workout.ActiveWorkoutRepository
import app.liora.core.domain.RepAims
import app.liora.core.model.Mass
import app.liora.core.model.ProgressionRule
import app.liora.core.model.RepRange
import app.liora.core.model.Routine
import app.liora.core.model.RoutineExercise
import app.liora.core.model.RoutineSet
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.koin.core.context.stopKoin
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Double progression in the app: the editor, the next targets on routine detail and in the logger, a cable's step. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], application = TestLioraApplication::class)
class ProgressionFlowTest {
    @get:Rule
    val composeRule = createEmptyComposeRule()

    private val koin get() = GlobalContext.get()

    @Before
    fun seed() =
        runBlocking {
            koin.get<AppStartup>().run()
            val routines = koin.get<RoutineRepository>()
            routines.save(
                Routine(
                    id = "arms",
                    name = "Arms",
                    exercises =
                        listOf(
                            RoutineExercise(
                                id = "arms-pushdown",
                                exerciseId = PUSHDOWN,
                                sets = List(3) { RoutineSet("arms-$it", weight = Mass(25.0), reps = RepRange(8, 12)) },
                                progression = ProgressionRule.DoubleProgression,
                            ),
                        ),
                ),
            )
            // Last time, every set reached the top of its range.
            val workouts = koin.get<ActiveWorkoutRepository>()
            workouts.startFromRoutine("arms").exercises.single().sets.forEach { set ->
                workouts.sets.updateSet(set.id) { copy(weight = Mass(25.0), reps = 12) }
                workouts.sets.completeSet(set.id)
            }
            workouts.finish()
        }

    @After
    fun tearDown() = stopKoin()

    @Test
    @Config(qualifiers = COVER)
    fun routineDetailAndTheLoggerShowTheNextTargets() {
        launch()
        openRoutine("Arms")
        waitForText(TOP_REACHED)
        // A step up on the cable's stack, with the range still the goal.
        composeRule.onAllNodesWithText("30 kg × 8–12").assertCountEquals(3)
        composeRule.onRoot().captureRoboImage("src/test/screenshots/progression_routine_detail_dark.png")

        composeRule.onNodeWithText("Start workout").performClick()
        waitForText("Finish")
        waitForText(TOP_REACHED)
        // The weight is filled in; the reps to aim for are the placeholder, the bottom of the range.
        val sets =
            runBlocking {
                koin
                    .get<ActiveWorkoutRepository>()
                    .activeWorkout
                    .first()!!
                    .exercises
                    .single()
                    .sets
            }
        assertEquals(List(3) { Mass(30.0) to 8 }, sets.map { it.weight to it.aimReps })
        composeRule.onRoot().captureRoboImage("src/test/screenshots/progression_logger_dark.png")
    }

    @Test
    @Config(qualifiers = COVER)
    fun theEditorTurnsItOnAndAsksForRepRanges() {
        runBlocking {
            koin.get<RoutineRepository>().save(
                Routine(
                    id = "back",
                    name = "Back",
                    exercises =
                        listOf(
                            RoutineExercise(
                                id = "back-row",
                                exerciseId = ROW,
                                sets = List(3) { RoutineSet("back-$it", weight = Mass(60.0), reps = RepRange(10)) },
                            ),
                        ),
                ),
            )
        }
        launch()
        openRoutine("Back")
        waitForText("Start workout")
        composeRule.onNodeWithContentDescription("Edit").performClick()
        waitForText("Seated Cable Row")
        composeRule.onAllNodesWithContentDescription("More options").onFirst().performClick()
        composeRule.onNodeWithText("Double progression").performClick()
        waitForText(EDITOR_LABEL)
        // Exactly 10 reps leaves nothing to climb through.
        composeRule.onNodeWithText("Give each working set a rep range, like 8–12").assertExists()
        composeRule.onRoot().captureRoboImage("src/test/screenshots/progression_editor_dark.png")

        composeRule.onAllNodesWithContentDescription("More options").onFirst().performClick()
        composeRule.onNodeWithText("Stop progression").performClick()
        composeRule.waitUntil(TIMEOUT_MS) { !nodeExists(hasText(EDITOR_LABEL)) }
    }

    @Test
    @Config(qualifiers = COVER)
    fun aCableGetsItsOwnStep() {
        launch()
        composeRule.onNodeWithText("Exercises").performClick()
        composeRule.waitUntil(TIMEOUT_MS) { nodeExists(hasSetTextAction()) }
        composeRule.onNode(hasSetTextAction()).performTextInput("Pushdown")
        // The result, not the search field.
        val result = hasText("Triceps Pushdown") and !hasSetTextAction()
        composeRule.waitUntil(TIMEOUT_MS) { nodeExists(result) }
        composeRule.onAllNodes(result).onFirst().performClick()
        waitForText("Weight step")
        composeRule.onNodeWithText("As the gym: 5 kg").assertExists()

        composeRule.onNodeWithText("Weight step").performClick()
        waitForText("Use the gym’s step")
        repeat(2) { composeRule.onNodeWithContentDescription("Smaller step").performClick() }
        composeRule.onNodeWithText("4 kg").assertExists()
        composeRule.onRoot().captureRoboImage("src/test/screenshots/progression_load_step_dark.png")
        composeRule.onNodeWithText("Save").performClick()

        waitForText("4 kg on this one")
        val step =
            runBlocking {
                koin
                    .get<ExerciseRepository>()
                    .observeExercise(PUSHDOWN, "en")
                    .first()
                    ?.settings
                    ?.loadStep
            }
        assertEquals(Mass(4.0), step)
    }

    @Test
    @Config(qualifiers = COVER)
    fun settingsTuneHowItProgresses() {
        launch()
        composeRule.onNodeWithContentDescription("Settings").performClick()
        composeRule.waitUntil(TIMEOUT_MS) { nodeExists(hasScrollToKeyAction()) }
        composeRule.onNode(hasScrollToKeyAction()).performScrollToKey("progression")
        waitForText("Reps to aim for")
        composeRule.onNodeWithText("A rep more on the weakest set").assertExists()
        composeRule.onNodeWithText("Every set reaches the top").assertExists()
        composeRule.onNodeWithText("Step down after two sessions in a row").assertExists()
        composeRule.onNodeWithText("The routine’s own last session").assertExists()
        composeRule.onRoot().captureRoboImage("src/test/screenshots/progression_settings_dark.png")

        composeRule.onNodeWithText("Reps to aim for").performClick()
        waitForText("8, 8, 8 becomes 9, 8, 8, then 9, 9, 8")
        composeRule.onRoot().captureRoboImage("src/test/screenshots/progression_settings_rep_aims_dark.png")
        composeRule.onNodeWithText("The same as last time").performClick()
        waitForText("The same as last time")
        assertEquals(
            RepAims.SameAsLastTime,
            runBlocking {
                koin
                    .get<SettingsRepository>()
                    .current()
                    .progression.repAims
            },
        )
    }

    @Test
    @Config(qualifiers = INNER)
    fun onTheInnerScreen() {
        launch()
        openRoutine("Arms")
        waitForText(TOP_REACHED)
        composeRule.onRoot().captureRoboImage("src/test/screenshots/progression_routine_detail_inner_dark.png")
    }

    @Test
    @Config(qualifiers = "de-$COVER")
    fun inGerman() {
        launch()
        openRoutine("Arms")
        waitForText("Letztes Mal 12 Wdh. in allen 3 Sätzen mit 25 kg, jetzt also 30 kg")
        composeRule.onRoot().captureRoboImage("src/test/screenshots/de_progression_routine_detail_dark.png")
    }

    /** Taps the routine's card on the Train tab; its name is merged into the card. */
    private fun openRoutine(name: String) {
        composeRule.waitUntil(TIMEOUT_MS) {
            composeRule.onAllNodesWithText(name, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onAllNodesWithText(name, useUnmergedTree = true).onFirst().performClick()
    }

    private fun launch(): ActivityController<MainActivity> =
        Robolectric.buildActivity(MainActivity::class.java).setup().also { composeRule.waitForIdle() }

    private fun nodeExists(matcher: SemanticsMatcher) =
        composeRule.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty()

    // Room emits on a background dispatcher that Compose's idling doesn't track, so wait explicitly.
    private fun waitForText(text: String) = composeRule.waitUntil(TIMEOUT_MS) { nodeExists(hasText(text)) }

    private companion object {
        const val COVER = "w411dp-h960dp-night-xxhdpi"
        const val INNER = "w984dp-h1092dp-night-xhdpi"
        const val TIMEOUT_MS = 10_000L
        const val PUSHDOWN = "fedb.Triceps_Pushdown"
        const val ROW = "fedb.Seated_Cable_Rows"
        const val TOP_REACHED = "12 reps on all 3 sets at 25 kg last time, so 30 kg"
        const val EDITOR_LABEL = "Double progression: a step heavier at the top of the range, as Settings has it"
    }
}
