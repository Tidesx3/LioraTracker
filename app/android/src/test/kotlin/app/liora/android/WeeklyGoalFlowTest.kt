package app.liora.android

import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import app.liora.core.data.AppStartup
import app.liora.core.data.settings.SettingsRepository
import app.liora.core.data.workout.ActiveWorkoutRepository
import app.liora.core.data.workout.WorkoutHistoryRepository
import app.liora.core.model.Mass
import app.liora.feature.train.TrainTags
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import org.junit.After
import org.junit.Assert.assertEquals
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
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * The weekly goal on the Train tab: the week's card with the ring, the days trained and the streak, kept
 * through last week by a rest week; a tap picks a new goal. Friday, 16 October 2026, in Berlin: six weeks
 * that reached a goal of three days earned a rest week, last week had one day and used it, and this week has
 * two so far. No training on Sundays, so weeks starting on Sunday (English) or Monday (German) hold the same.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], application = TestLioraApplication::class)
class WeeklyGoalFlowTest {
    @get:Rule
    val composeRule = createEmptyComposeRule()

    private val koin get() = GlobalContext.get()
    private val originalZone = java.util.TimeZone.getDefault()

    @Before
    fun seed() {
        java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone(ZONE))
        loadKoinModules(module { single<Clock> { FixedClock(at(LocalDate(2026, 10, 16))) } })
        runBlocking {
            koin.get<AppStartup>().run()
            koin.get<SettingsRepository>().update {
                it.copy(weeklyGoals = it.weeklyGoals.withGoal(3, LocalDate(2026, 1, 4)))
            }
            // Six weeks of Monday, Wednesday and Friday, from 24 August.
            repeat(6) { week ->
                val monday = LocalDate(2026, 8, 24).plus(week * 7, DateTimeUnit.DAY)
                listOf(0, 2, 4).forEach { train(monday.plus(it, DateTimeUnit.DAY)) }
            }
            // Last week, one day: the rest week keeps the streak.
            train(LocalDate(2026, 10, 6))
            // This week, Monday and Wednesday.
            train(LocalDate(2026, 10, 12))
            train(LocalDate(2026, 10, 14))
        }
    }

    @After
    fun tearDown() {
        stopKoin()
        java.util.TimeZone.setDefault(originalZone)
    }

    @Test
    @Config(qualifiers = COVER)
    fun theWeekShowsTheGoalTheDaysAndTheStreakAndATapChangesTheGoal() {
        launch()
        waitFor(hasText("6 weeks in a row"))
        composeRule.onNodeWithText("Last week was a rest week, so your streak goes on").assertExists()
        waitFor(hasContentDescription("2 of 3 training days this week"))
        composeRule.onRoot().captureRoboImage("src/test/screenshots/train_week_dark.png")

        composeRule.onNode(hasTestTag(TrainTags.WEEK)).performClick()
        waitFor(hasText("Weekly goal"))
        composeRule.onRoot().captureRoboImage("src/test/screenshots/train_week_goal_dark.png")
        composeRule.onNodeWithText("4 days a week").performClick()
        // From this week on; the weeks before keep their three, so the streak stands.
        waitFor(hasContentDescription("2 of 4 training days this week"))
        composeRule.onNodeWithText("6 weeks in a row").assertExists()
        assertEquals(
            4,
            runBlocking {
                koin
                    .get<SettingsRepository>()
                    .current()
                    .weeklyGoals.current
            },
        )
    }

    @Test
    @Config(qualifiers = LIGHT_COVER)
    fun inLightGlass() {
        launch()
        waitFor(hasText("6 weeks in a row"))
        composeRule.onRoot().captureRoboImage("src/test/screenshots/train_week_light.png")
    }

    @Test
    @Config(qualifiers = INNER)
    fun onTheInnerScreen() {
        launch()
        waitFor(hasText("6 weeks in a row"))
        composeRule.onRoot().captureRoboImage("src/test/screenshots/fold_inner_train_week_dark.png")
    }

    @Test
    @Config(qualifiers = "de-$COVER")
    fun inGerman() {
        launch()
        waitFor(hasText("6 Wochen in Folge"))
        composeRule.onNodeWithText("Letzte Woche war eine Ruhewoche, deine Serie geht weiter").assertExists()
        composeRule.onRoot().captureRoboImage("src/test/screenshots/de_train_week_dark.png")
    }

    /** A workout on [day] at six in the evening: one bench set, done. */
    private suspend fun train(day: LocalDate) {
        val workouts = koin.get<ActiveWorkoutRepository>()
        val workout = workouts.startEmptyWorkout()
        workouts.editor.addExercises(listOf(BENCH))
        val set =
            workouts.activeWorkout
                .first()!!
                .exercises
                .single()
                .sets
                .first()
        workouts.sets.updateSet(set.id) { copy(weight = Mass(80.0), reps = 8) }
        workouts.sets.completeSet(set.id)
        workouts.finish()
        val start = at(day)
        koin.get<WorkoutHistoryRepository>().edit(workout.id).setTimes(start, start + 45.minutes)
    }

    private fun launch() {
        Robolectric.buildActivity(MainActivity::class.java).setup()
        composeRule.waitForIdle()
    }

    // Room emits on a background dispatcher that Compose's idling doesn't track, so wait explicitly.
    private fun waitFor(matcher: SemanticsMatcher) =
        composeRule.waitUntil(TIMEOUT_MS) { composeRule.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty() }

    private fun at(day: LocalDate): Instant =
        LocalDateTime(day.year, day.month, day.day, 18, 0).toInstant(TimeZone.of(ZONE))

    private class FixedClock(
        private val now: Instant,
    ) : Clock {
        override fun now() = now
    }

    private companion object {
        const val COVER = "w411dp-h960dp-night-xxhdpi"
        const val LIGHT_COVER = "w411dp-h960dp-notnight-xxhdpi"
        const val INNER = "w984dp-h1092dp-night-xhdpi"
        const val ZONE = "Europe/Berlin"
        const val BENCH = "fedb.Barbell_Bench_Press_-_Medium_Grip"

        // Seeding twenty-one workouts takes a moment before the first frame.
        const val TIMEOUT_MS = 30_000L
    }
}
