package app.liora.android

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import app.liora.core.data.AppStartup
import app.liora.core.data.workout.ActiveWorkoutRepository
import app.liora.core.database.dao.WorkoutDao
import app.liora.core.model.Mass
import app.liora.feature.progress.ProgressTags
import app.liora.feature.progress.report.ReportTags
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
import org.koin.core.context.loadKoinModules
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * The Progress tab end to end: the streak and this week, the consistency grid, sets per muscle week by
 * week, and the records board with a stalled lift. "Now" is fixed and six weeks of Push and Pull are
 * seeded before it, so screenshots stay the same from day to day.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], application = TestLioraApplication::class)
class ProgressFlowTest {
    @get:Rule
    val composeRule = createEmptyComposeRule()

    private val koin get() = GlobalContext.get()
    private val originalZone = java.util.TimeZone.getDefault()

    @Before
    fun seed() {
        java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone(ZONE))
        // Thursday, 13 August 2026: the app's "now" for this test.
        val now = at(2026, 8, 13, 12, 0)
        loadKoinModules(module { single<Clock> { FixedClock(now) } })
        runBlocking { koin.get<AppStartup>().run() }
        // Six weeks: Push on Mondays, Pull on Wednesdays. Bench tops out on 20 July and stalls; flyes
        // and pull-ups keep climbing.
        val bench = listOf(80.0, 82.5, 85.0, 82.5, 85.0, 85.0)
        val flyes = listOf(20.0, 20.0, 22.5, 22.5, 24.0, 24.0)
        val pullups = listOf(8, 8, 9, 9, 10, 10)
        repeat(WEEKS) { week ->
            val monday = at(2026, 7, 6, 18, 0) + (week * DAYS_PER_WEEK).days
            finishedWorkout(monday, BENCH_ID to Logged(bench[week], 10), FLYES_ID to Logged(flyes[week], 12))
            finishedWorkout(monday + 2.days, PULLUPS_ID to Logged(null, pullups[week]))
        }
    }

    @After
    fun tearDown() {
        stopKoin()
        java.util.TimeZone.setDefault(originalZone)
    }

    @Test
    @Config(qualifiers = COVER)
    fun streakMusclesAndBests() {
        openProgress()
        waitForText("6 weeks")
        composeRule.onNodeWithText("2 workouts").assertIsDisplayed()
        composeRule.onNode(hasTestTag(ProgressTags.CONSISTENCY)).assertIsDisplayed()
        composeRule.onRoot().captureRoboImage("src/test/screenshots/progress_overview_dark.png")

        // This week: bench and flyes each give the chest three sets.
        scrollTo("Chest")
        composeRule.onNodeWithText("6 sets").assertIsDisplayed()
        composeRule.onRoot().captureRoboImage("src/test/screenshots/progress_muscles_dark.png")
        // Last week, and back.
        composeRule.onNodeWithContentDescription("Previous week").performClick()
        waitForText("Last week")
        composeRule.onNodeWithContentDescription("Next week").performClick()
        waitForText("This week")
        // Back on this week there is no next one.
        composeRule.waitUntil(TIMEOUT_MS) {
            composeRule.onAllNodesWithContentDescription("Next week").fetchSemanticsNodes().isEmpty()
        }

        // The bests: bench hasn't beaten 20 July since.
        scrollTo("Stalled for 3 weeks")
        composeRule.onRoot().captureRoboImage("src/test/screenshots/progress_bests_dark.png")
        composeRule.onNodeWithText(BENCH).performClick()
        waitForText("No new best since")
    }

    @Test
    @Config(qualifiers = INNER)
    fun innerScreen_anExerciseOpensBesideTheOverview() {
        openProgress()
        waitForText("Pick an exercise")
        scrollTo(BENCH)
        composeRule.onNodeWithText(BENCH).performClick()
        waitForText("No new best since")
        // The overview stays, with the open exercise highlighted.
        composeRule.onNode(isSelected() and hasText(BENCH)).assertIsDisplayed()
        composeRule.onRoot().captureRoboImage("src/test/screenshots/fold_inner_progress_dark.png")
    }

    @Test
    @Config(qualifiers = COVER)
    fun monthlyReport() {
        openProgress()
        waitForText("Monthly report")
        composeRule.onNodeWithText("Monthly report").performClick()
        waitForText("August 2026")
        // Four workouts so far in August, eight in July.
        waitForText("Last month: 8")
        composeRule.onNode(hasTestTag(ReportTags.CALENDAR)).assertIsDisplayed()
        composeRule.onRoot().captureRoboImage("src/test/screenshots/monthly_report_dark.png")
        // Flyes and pull-ups set records; the stalled bench didn't.
        scrollTo("Dumbbell Flyes: ", substring = true)
        composeRule.onAllNodesWithText("Barbell Bench Press: ", substring = true).assertCountEquals(0)

        // July is the first month with training: nothing before it.
        composeRule.onNodeWithContentDescription("Previous month").performClick()
        waitForText("July 2026")
        composeRule.onAllNodesWithContentDescription("Previous month").assertCountEquals(0)
    }

    @Test
    @Config(qualifiers = INNER)
    fun innerScreen_theReportOpensBesideTheOverview() {
        openProgress()
        waitForText("Monthly report")
        composeRule.onNodeWithText("Monthly report").performClick()
        waitForText("August 2026")
        waitForText("Last month: 8")
        composeRule.onRoot().captureRoboImage("src/test/screenshots/fold_inner_monthly_report_dark.png")
    }

    @Test
    @Config(qualifiers = "de-$COVER")
    fun germanMonthlyReport() {
        openProgress("Fortschritt")
        waitForText("Monatsbericht")
        composeRule.onNodeWithText("Monatsbericht").performClick()
        waitForText("August 2026")
        waitForText("Vormonat: 8")
        composeRule.onRoot().captureRoboImage("src/test/screenshots/de_monthly_report_dark.png")
    }

    @Test
    @Config(qualifiers = "de-$COVER")
    fun germanWeeksStartOnMonday() {
        openProgress("Fortschritt")
        waitForText("6 Wochen")
        composeRule.onNodeWithText("2 Trainings").assertIsDisplayed()
        scrollTo("Brust")
        composeRule.onNodeWithText("6 Sätze").assertIsDisplayed()
        composeRule.onRoot().captureRoboImage("src/test/screenshots/de_progress_muscles_dark.png")
    }

    private fun openProgress(tab: String = "Progress") {
        Robolectric.buildActivity(MainActivity::class.java).setup()
        composeRule.waitForIdle()
        composeRule.onNodeWithText(tab).performClick()
    }

    /** Scrolls the overview until [text] shows; it may not be composed before. */
    private fun scrollTo(
        text: String,
        substring: Boolean = false,
    ) {
        composeRule.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(text, substring = substring))
    }

    private class Logged(
        val kg: Double?,
        val reps: Int,
    )

    /** A finished workout of three sets per exercise, moved to [start]: three minutes a set. */
    private fun finishedWorkout(
        start: Instant,
        vararg exercises: Pair<String, Logged>,
    ) = runBlocking {
        val workouts = koin.get<ActiveWorkoutRepository>()
        val workout = workouts.startEmptyWorkout()
        workouts.editor.addExercises(exercises.map { it.first })
        val active = workouts.activeWorkout.first()!!
        active.exercises.zip(exercises) { exercise, (_, logged) ->
            exercise.sets.forEach { set ->
                workouts.sets.updateSet(set.id) { copy(weight = logged.kg?.let(::Mass), reps = logged.reps) }
                workouts.sets.completeSet(set.id)
            }
        }
        workouts.finish()
        val dao = koin.get<WorkoutDao>()
        val stored = dao.setsOf(workout.id)
        dao.upsertSets(
            stored.mapIndexed { index, set ->
                set.copy(completedAt = (start + SET_MINUTES * (index + 1)).toEpochMilliseconds())
            },
        )
        val row = checkNotNull(dao.get(workout.id))
        dao.upsert(
            row.copy(
                startedAt = start.toEpochMilliseconds(),
                endedAt = (start + SET_MINUTES * (stored.size + 1)).toEpochMilliseconds(),
            ),
        )
    }

    private fun at(
        year: Int,
        month: Int,
        day: Int,
        hour: Int,
        minute: Int,
    ) = LocalDateTime(year, month, day, hour, minute).toInstant(TimeZone.of(ZONE))

    // Room emits on a background dispatcher that Compose's idling doesn't track, so wait explicitly.
    private fun waitForText(
        text: String,
        substring: Boolean = true,
    ) = composeRule.waitUntil(TIMEOUT_MS) {
        composeRule.onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().isNotEmpty()
    }

    private class FixedClock(
        private val now: Instant,
    ) : Clock {
        override fun now() = now
    }

    private companion object {
        const val COVER = "w411dp-h960dp-night-xxhdpi"
        const val INNER = "w984dp-h1092dp-night-xhdpi"
        const val ZONE = "Europe/Vienna"

        // The first test in a JVM pays for class loading and the six weeks of seeding.
        const val TIMEOUT_MS = 20_000L
        const val WEEKS = 6
        const val DAYS_PER_WEEK = 7
        val SET_MINUTES = 3.minutes

        const val BENCH_ID = "fedb.Barbell_Bench_Press_-_Medium_Grip"
        const val BENCH = "Barbell Bench Press"
        const val FLYES_ID = "fedb.Dumbbell_Flyes"
        const val PULLUPS_ID = "fedb.Pullups"
    }
}
