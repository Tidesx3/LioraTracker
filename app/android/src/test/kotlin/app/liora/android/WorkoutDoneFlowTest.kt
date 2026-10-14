package app.liora.android

import android.content.Intent
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import app.liora.android.workout.WorkoutNotifications
import app.liora.core.data.AppStartup
import app.liora.core.data.routine.RoutineRepository
import app.liora.core.data.workout.ActiveWorkoutRepository
import app.liora.core.data.workout.SetLogger
import app.liora.core.data.workout.WorkoutHistoryRepository
import app.liora.core.model.LoggedSet
import app.liora.core.model.Mass
import app.liora.core.model.RepRange
import app.liora.core.model.Routine
import app.liora.core.model.RoutineExercise
import app.liora.core.model.RoutineSet
import app.liora.feature.logger.LoggerTags
import app.liora.feature.logger.done.WorkoutDoneTags
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
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * The summary a finished workout opens: the medal and how many workouts are in the books, what this one
 * came to, its records, and each exercise's best set; Done goes back. At the cover and inner screen sizes,
 * and in German. The clock stands still on Friday, 2 October 2026, so the date it shows stays put.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], application = TestLioraApplication::class)
class WorkoutDoneFlowTest {
    @get:Rule
    val composeRule = createEmptyComposeRule()

    private val koin get() = GlobalContext.get()
    private val originalZone = java.util.TimeZone.getDefault()
    private val zone = TimeZone.of(ZONE)
    private val now = LocalDateTime(2026, 10, 2, 18, 30).toInstant(zone)

    @Before
    fun setUp() {
        java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone(ZONE))
        loadKoinModules(module { single<Clock> { FixedClock(now) } })
        runBlocking {
            koin.get<AppStartup>().run()
            koin.get<RoutineRepository>().save(PUSH)
            logLastWeek()
            startToday()
        }
    }

    @After
    fun tearDown() {
        stopKoin()
        java.util.TimeZone.setDefault(originalZone)
    }

    @Test
    @Config(qualifiers = COVER)
    fun finishingSumsTheWorkoutUp() {
        finish()
        composeRule.onRoot().captureRoboImage("src/test/screenshots/workout_done_dark.png")
        composeRule.onNodeWithText("That’s a wrap").assertExists()
        composeRule.onNodeWithText("2 workouts in the books").assertExists()
        composeRule.onNodeWithText("3 new personal records!").assertExists()
        // Against last week: the first bench set 5 kg heavier, the rest as then, the skipped one gone.
        composeRule.onNodeWithContentDescription("1 of 6 sets ahead of last time, +50 kg").assertExists()
        // Each exercise with its sets and its best one; the bench's beat last week, the skipped set is gone.
        composeRule
            .onNode(
                hasText("Barbell Bench Press") and hasText("2 sets") and hasText("85 kg × 10") and
                    hasContentDescription("Personal record"),
            ).assertExists()
        composeRule.onNode(hasText("Dumbbell Flyes") and hasText("20 kg × 12")).assertExists()
        composeRule.onNode(hasText("Plank") and hasText("1:00")).assertExists()

        composeRule.onNode(hasTestTag(WorkoutDoneTags.CLOSE)).performClick()
        waitFor(hasText("Start empty workout"))
    }

    @Test
    @Config(qualifiers = INNER)
    fun innerScreen_readsDownTheMiddle() {
        finish()
        composeRule.onRoot().captureRoboImage("src/test/screenshots/fold_inner_workout_done_dark.png")
    }

    @Test
    @Config(qualifiers = "de-$COVER")
    fun german() {
        finish(duration = "52 Min.")
        composeRule.onNodeWithText("Geschafft").assertExists()
        composeRule.onNodeWithText("2 Trainings im Kasten").assertExists()
        composeRule.onNodeWithContentDescription("1 von 6 Sätzen besser als letztes Mal, +50 kg").assertExists()
        composeRule.onRoot().captureRoboImage("src/test/screenshots/de_workout_done_dark.png")
    }

    /**
     * Opens the logger as the workout notification does, finishes, and waits for the summary; then gives
     * the workout its real start, 52 minutes before the clock's now.
     */
    private fun finish(duration: String = "52 min") {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val openLogger = Intent(context, MainActivity::class.java).setAction(WorkoutNotifications.ACTION_OPEN_LOGGER)
        Robolectric.buildActivity(MainActivity::class.java, openLogger).setup()
        waitFor(hasTestTag(LoggerTags.FINISH))
        composeRule.onNode(hasTestTag(LoggerTags.FINISH)).performClick()
        waitFor(hasTestTag(LoggerTags.FINISH_CONFIRM))
        composeRule.onNode(hasTestTag(LoggerTags.FINISH_CONFIRM)).performClick()
        waitFor(hasTestTag(WorkoutDoneTags.CLOSE))
        runBlocking {
            val history = koin.get<WorkoutHistoryRepository>()
            val today = history.workouts.first().maxBy { it.startedAt }
            history.edit(today.id).setTimes(now - 52.minutes, now)
        }
        waitFor(hasText(duration))
        composeRule.waitForIdle()
    }

    /** Last Friday's Push: 80 kg × 10 on the bench, 20 kg × 12 flyes, a minute's plank. */
    private suspend fun logLastWeek() {
        val workouts = koin.get<ActiveWorkoutRepository>()
        val sets = koin.get<SetLogger>()
        val workout = workouts.startFromRoutine(PUSH.id)
        workout.exercises.forEachIndexed { exercise, done ->
            done.sets.forEach { set ->
                sets.updateSet(set.id) { asPlanned(exercise) }
                sets.completeSet(set.id)
            }
        }
        workouts.finish()
        val start = now - 7.days - 60.minutes
        koin.get<WorkoutHistoryRepository>().edit(workout.id).setTimes(start, start + 50.minutes)
    }

    /** Today's: 85 kg on the first bench set, the second as planned, the third skipped; the rest as last week. */
    private suspend fun startToday() {
        val sets = koin.get<SetLogger>()
        val workout = koin.get<ActiveWorkoutRepository>().startFromRoutine(PUSH.id)
        workout.exercises.forEachIndexed { exercise, done ->
            done.sets.forEachIndexed { index, set ->
                if (exercise == 0 && index == 2) return@forEachIndexed
                sets.updateSet(set.id) {
                    if (exercise == 0 && index == 0) copy(weight = Mass(85.0), reps = 10) else asPlanned(exercise)
                }
                sets.completeSet(set.id)
            }
        }
    }

    /** A set of [PUSH]'s [exercise] as the routine has it. */
    private fun LoggedSet.asPlanned(exercise: Int): LoggedSet =
        when (exercise) {
            0 -> copy(weight = Mass(80.0), reps = 10)
            1 -> copy(weight = Mass(20.0), reps = 12)
            else -> copy(duration = 60.seconds)
        }

    // Room emits on a background dispatcher that Compose's idling doesn't track, so wait explicitly.
    private fun waitFor(matcher: SemanticsMatcher) =
        composeRule.waitUntil(TIMEOUT_MS) { composeRule.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty() }

    private class FixedClock(
        private val now: Instant,
    ) : Clock {
        override fun now() = now
    }

    private companion object {
        const val COVER = "w411dp-h960dp-night-xxhdpi"
        const val INNER = "w984dp-h1092dp-night-xhdpi"
        const val ZONE = "Europe/Berlin"
        const val TIMEOUT_MS = 20_000L

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
                                ) { RoutineSet("push-bench-$it", weight = Mass(80.0), reps = RepRange(10, 10)) },
                        ),
                        RoutineExercise(
                            id = "push-flyes",
                            exerciseId = "fedb.Dumbbell_Flyes",
                            restSeconds = 60,
                            sets =
                                List(
                                    2,
                                ) { RoutineSet("push-flyes-$it", weight = Mass(20.0), reps = RepRange(12, 12)) },
                        ),
                        RoutineExercise(
                            id = "push-plank",
                            exerciseId = "fedb.Plank",
                            restSeconds = 60,
                            sets = List(2) { RoutineSet("push-plank-$it", duration = 60.seconds) },
                        ),
                    ),
            )
    }
}
