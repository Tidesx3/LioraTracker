package app.liora.android

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import app.liora.core.data.AppStartup
import app.liora.core.data.workout.ActiveWorkoutRepository
import app.liora.core.database.dao.WorkoutDao
import app.liora.core.model.Mass
import app.liora.feature.exercises.detail.ExerciseDetailTags
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
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
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * An exercise's progress on its page: the chart per metric, its records and past sessions, and the
 * stall badge. Charted sessions are seeded at fixed dates in a fixed zone, so screenshots stay stable.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], application = TestLioraApplication::class)
class ExerciseProgressFlowTest {
    @get:Rule
    val composeRule = createEmptyComposeRule()

    private val koin get() = GlobalContext.get()
    private val originalZone = java.util.TimeZone.getDefault()

    @Before
    fun seedCatalog() {
        java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone(ZONE))
        runBlocking { koin.get<AppStartup>().run() }
    }

    @After
    fun tearDown() {
        stopKoin()
        java.util.TimeZone.setDefault(originalZone)
    }

    @Test
    @Config(qualifiers = COVER)
    fun chartRecordsAndSessions() {
        seedFiveWeeksOfBench()
        openBenchPress()
        waitForChart()
        // 87.5 kg × 5 on 4 August: Epley's estimate is 102.1 kg.
        waitForText("102.1 kg")
        composeRule.onRoot().captureRoboImage("src/test/screenshots/exercise_progress_dark.png")

        // The chip, above the record of the same name.
        composeRule.onAllNodesWithText("Heaviest weight").onFirst().performClick()
        waitForText("87.5 kg")

        // The newest session opens its workout.
        val session = hasText("Tuesday, August 4, 2026")
        composeRule.onNode(hasScrollToIndexAction()).performScrollToNode(session)
        composeRule.onNode(session).performClick()
        waitForText("Repeat workout")
    }

    @Test
    @Config(qualifiers = COVER)
    fun noNewBestForWeeksIsAStall() {
        val now = Clock.System.now()
        finishedBench(now - 40.days, 100.0, 5)
        // The best still standing.
        finishedBench(now - 30.days, 102.5, 5)
        finishedBench(now - 14.days, 100.0, 5)
        finishedBench(now - 3.days, 102.5, 5)
        openBenchPress()
        waitForText("Stalled for 4 weeks")
        composeRule.onNode(hasTestTag(ExerciseDetailTags.STALLED)).assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = INNER)
    fun innerScreen_progressBesideTheLibrary() {
        seedFiveWeeksOfBench()
        openBenchPress()
        waitForChart()
        composeRule.onRoot().captureRoboImage("src/test/screenshots/fold_inner_exercise_progress_dark.png")
    }

    @Test
    @Config(qualifiers = "de-$COVER")
    fun germanProgress() {
        seedFiveWeeksOfBench()
        openBenchPress(tab = "Übungen", name = "Bankdrücken (Langhantel)")
        waitForChart()
        waitForText("102,1 kg")
        composeRule.onNodeWithText("Geschätztes 1RM").assertIsDisplayed()
        composeRule.onRoot().captureRoboImage("src/test/screenshots/de_exercise_progress_dark.png")
    }

    /** Five weekly bench sessions in July and August, each a little heavier. */
    private fun seedFiveWeeksOfBench() {
        listOf(80.0 to 5, 82.5 to 5, 85.0 to 5, 85.0 to 6, 87.5 to 5).forEachIndexed { week, (kg, reps) ->
            finishedBench(at(2026, 7, 7, 18, 0) + (week * DAYS_PER_WEEK).days, kg, reps)
        }
    }

    /** A finished workout of three bench sets of [kg] × [reps], moved to [start]. */
    private fun finishedBench(
        start: Instant,
        kg: Double,
        reps: Int,
    ) = runBlocking {
        val workouts = koin.get<ActiveWorkoutRepository>()
        val workout = workouts.startEmptyWorkout()
        workouts.editor.addExercises(listOf(BENCH_ID))
        val sets =
            workouts.activeWorkout
                .first()!!
                .exercises
                .single()
                .sets
        sets.forEach { set ->
            workouts.sets.updateSet(set.id) { copy(weight = Mass(kg), reps = reps) }
            workouts.sets.completeSet(set.id)
        }
        workouts.finish()
        val dao = koin.get<WorkoutDao>()
        dao.upsertSets(
            dao.setsOf(workout.id).mapIndexed { index, set ->
                set.copy(completedAt = (start + SET_MINUTES * (index + 1)).toEpochMilliseconds())
            },
        )
        val row = checkNotNull(dao.get(workout.id))
        dao.upsert(
            row.copy(
                startedAt = start.toEpochMilliseconds(),
                endedAt = (start + SET_MINUTES * (sets.size + 1)).toEpochMilliseconds(),
            ),
        )
    }

    private fun openBenchPress(
        tab: String = "Exercises",
        name: String = BENCH,
    ) {
        Robolectric.buildActivity(MainActivity::class.java).setup()
        composeRule.waitForIdle()
        composeRule.onNodeWithText(tab).performClick()
        waitForText(name)
        composeRule.onAllNodesWithText(name).onFirst().performClick()
    }

    private fun at(
        year: Int,
        month: Int,
        day: Int,
        hour: Int,
        minute: Int,
    ) = LocalDateTime(year, month, day, hour, minute).toInstant(TimeZone.of(ZONE))

    private fun waitForChart() {
        composeRule.waitUntil(TIMEOUT_MS) {
            composeRule.onAllNodes(hasTestTag(ExerciseDetailTags.CHART)).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNode(hasTestTag(ExerciseDetailTags.CHART)).assertIsDisplayed()
    }

    // Room emits on a background dispatcher that Compose's idling doesn't track, so wait explicitly.
    private fun waitForText(text: String) =
        composeRule.waitUntil(TIMEOUT_MS) {
            composeRule.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()
        }

    private companion object {
        const val COVER = "w411dp-h960dp-night-xxhdpi"
        const val INNER = "w984dp-h1092dp-night-xhdpi"
        const val ZONE = "Europe/Vienna"
        const val TIMEOUT_MS = 10_000L
        const val DAYS_PER_WEEK = 7
        val SET_MINUTES = 3.minutes

        const val BENCH_ID = "fedb.Barbell_Bench_Press_-_Medium_Grip"
        const val BENCH = "Barbell Bench Press"
    }
}
