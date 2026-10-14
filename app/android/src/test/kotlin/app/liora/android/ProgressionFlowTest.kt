package app.liora.android

import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasScrollToKeyAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
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
import app.liora.core.data.workout.WorkoutHistoryRepository
import app.liora.core.domain.RepAims
import app.liora.core.model.ActiveWorkout
import app.liora.core.model.Mass
import app.liora.core.model.ProgressionRule
import app.liora.core.model.RepRange
import app.liora.core.model.Routine
import app.liora.core.model.RoutineExercise
import app.liora.core.model.RoutineSet
import app.liora.feature.logger.LoggerTags
import app.liora.feature.logger.done.WorkoutDoneTags
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
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/** Double progression in the app: the editor, the next targets on routine detail and in the logger, a cable's step. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], application = TestLioraApplication::class)
class ProgressionFlowTest {
    @get:Rule
    val composeRule = createEmptyComposeRule()

    private val koin get() = GlobalContext.get()
    private val originalZone = java.util.TimeZone.getDefault()

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
    fun tearDown() {
        stopKoin()
        java.util.TimeZone.setDefault(originalZone)
    }

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
        // The exercise page shows when it was trained: a fixed day, so the screenshot stays the same.
        runBlocking {
            val history = koin.get<WorkoutHistoryRepository>()
            val last = history.workouts.first().single()
            history.edit(last.id).setTimes(SHOWN_DAY, SHOWN_DAY + 1.hours)
        }
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
    @Config(qualifiers = COVER)
    fun aStepEarnedIsCelebratedThenSummedUp() {
        earnAStep()
        composeRule.onNodeWithText("35 kg next time").assertExists()
        // Every set at the top of its range has its chevron; the count on the way there has made way for the step.
        composeRule.onAllNodesWithContentDescription("Top of the range").assertCountEquals(3)
        composeRule.onNodeWithText("3 of 3 sets at the top").assertDoesNotExist()
        composeRule.onRoot().captureRoboImage("src/test/screenshots/progression_step_earned_dark.png")

        finish()
        waitForText("1 step up earned!")
        composeRule.onNode(hasText("Triceps Pushdown") and hasText("30 kg") and hasText("35 kg")).assertExists()
        composeRule.onRoot().captureRoboImage("src/test/screenshots/progression_done_step_dark.png")
        // The next start makes the step the summary named.
        assertEquals(
            Mass(35.0),
            runBlocking { koin.get<ActiveWorkoutRepository>().startFromRoutine("arms") }.heaviest(),
        )
    }

    @Test
    @Config(qualifiers = COVER)
    fun withoutProgressionTheSummarySaysWhatsReadyForMore() {
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
                                sets = List(3) { RoutineSet("back-$it", weight = Mass(60.0), reps = RepRange(10, 15)) },
                            ),
                        ),
                ),
            )
        }
        launch()
        openRoutine("Back")
        waitForText("Start workout")
        composeRule.onNodeWithText("Start workout").performClick()
        waitForText("Finish")
        logToday(reps = 15, complete = 3)
        // The top of the range shows on each set, but no step: the row has no progression.
        composeRule.waitUntil(TIMEOUT_MS) {
            composeRule.onAllNodesWithContentDescription("Top of the range").fetchSemanticsNodes().size == 3
        }
        composeRule.onNodeWithText("Step earned!").assertDoesNotExist()

        finish()
        waitForText("Ready to go heavier?")
        composeRule.onNodeWithText("Every set at the top of its range: Seated Cable Row.").assertExists()
    }

    @Test
    @Config(qualifiers = LIGHT_COVER)
    fun aStepEarnedInLightGlass() {
        earnAStep()
        composeRule.onRoot().captureRoboImage("src/test/screenshots/progression_step_earned_light.png")

        finish()
        waitForText("1 step up earned!")
        composeRule.onRoot().captureRoboImage("src/test/screenshots/progression_done_step_light.png")
    }

    @Test
    @Config(qualifiers = COVER)
    fun aStepEarnedWithoutGlass() {
        runBlocking { koin.get<SettingsRepository>().update { it.copy(glass = false) } }
        earnAStep()
        composeRule.onRoot().captureRoboImage("src/test/screenshots/progression_step_earned_solid_dark.png")

        finish()
        waitForText("1 step up earned!")
        composeRule.onRoot().captureRoboImage("src/test/screenshots/progression_done_step_solid_dark.png")
    }

    @Test
    @Config(qualifiers = INNER)
    fun aStepEarnedOnTheInnerScreen() {
        earnAStep()
        composeRule.onRoot().captureRoboImage("src/test/screenshots/fold_inner_progression_step_earned_dark.png")
    }

    @Test
    @Config(qualifiers = "de-$COVER")
    fun aStepEarnedInGerman() {
        earnAStep(GERMAN)
        composeRule.onNodeWithText("Nächstes Mal 35 kg").assertExists()
        composeRule.onRoot().captureRoboImage("src/test/screenshots/de_progression_step_earned_dark.png")

        finish(duration = "52 Min.")
        waitForText("1 Steigerung verdient!")
        composeRule.onRoot().captureRoboImage("src/test/screenshots/de_progression_done_step_dark.png")
    }

    /**
     * Starts Arms, whose pushdown reached the top last time and so starts a step heavier, at 30 kg. Two sets
     * are done at the top, the third ticked off here: the step to 35 kg is earned.
     */
    private fun earnAStep(labels: Labels = ENGLISH) {
        // The summary shows when it was trained: last time a week before the fixed day today's is moved to.
        java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("Europe/Berlin"))
        runBlocking {
            val history = koin.get<WorkoutHistoryRepository>()
            val last = history.workouts.first().single()
            history.edit(last.id).setTimes(SHOWN_DAY - 7.days, SHOWN_DAY - 7.days + 1.hours)
        }
        launch()
        openRoutine("Arms")
        waitForText(labels.start)
        composeRule.onNodeWithText(labels.start).performClick()
        composeRule.waitUntil(TIMEOUT_MS) { nodeExists(hasTestTag(LoggerTags.FINISH)) }
        logToday(reps = 12, complete = 2)
        waitForText(labels.onTheWay)
        composeRule.onAllNodesWithContentDescription(labels.logSet).onFirst().performClick()
        waitForText(labels.earned)
    }

    /** What [earnAStep] reads and taps, in the language the test runs in. */
    private class Labels(
        val start: String,
        val logSet: String,
        val onTheWay: String,
        val earned: String,
    )

    /** Today's sets of the workout's one exercise at [reps], as typed in, and the first [complete] ticked off. */
    private fun logToday(
        reps: Int,
        complete: Int,
    ) = runBlocking {
        val workouts = koin.get<ActiveWorkoutRepository>()
        val sets =
            workouts.activeWorkout
                .first()!!
                .exercises
                .single()
                .sets
        sets.forEach { set -> workouts.sets.updateSet(set.id) { copy(reps = reps) } }
        sets.take(complete).forEach { workouts.sets.completeSet(it.id) }
    }

    /**
     * Finishes the workout from the logger and waits for its summary; then moves the workout to the fixed day,
     * 52 minutes long, so the summary shows the same date and time in every run.
     */
    private fun finish(duration: String = "52 min") {
        composeRule.onNode(hasTestTag(LoggerTags.FINISH)).performClick()
        composeRule.waitUntil(TIMEOUT_MS) { nodeExists(hasTestTag(LoggerTags.FINISH_CONFIRM)) }
        composeRule.onNode(hasTestTag(LoggerTags.FINISH_CONFIRM)).performClick()
        composeRule.waitUntil(TIMEOUT_MS) { nodeExists(hasTestTag(WorkoutDoneTags.CLOSE)) }
        runBlocking {
            val history = koin.get<WorkoutHistoryRepository>()
            val today = history.workouts.first().maxBy { it.startedAt }
            history.edit(today.id).setTimes(SHOWN_DAY, SHOWN_DAY + 52.minutes)
        }
        waitForText(duration)
    }

    private fun ActiveWorkout.heaviest(): Mass? =
        exercises
            .single()
            .sets
            .mapNotNull { it.weight }
            .maxOrNull()

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
        const val LIGHT_COVER = "w411dp-h960dp-notnight-xxhdpi"
        const val TIMEOUT_MS = 10_000L
        const val PUSHDOWN = "fedb.Triceps_Pushdown"
        const val ROW = "fedb.Seated_Cable_Rows"
        const val TOP_REACHED = "12 reps on all 3 sets at 25 kg last time, so 30 kg"
        const val EDITOR_LABEL = "Double progression: a step heavier at the top of the range, as Settings has it"

        val ENGLISH = Labels("Start workout", "Log set", "2 of 3 sets at the top", "Step earned!")
        val GERMAN = Labels("Training starten", "Satz abhaken", "2 von 3 Sätzen am oberen Ende", "Eine Stufe rauf!")

        /** Midday UTC, so the 1st of October from Hawaii to New Zealand. */
        val SHOWN_DAY = Instant.parse("2026-10-01T12:00:00Z")
    }
}
