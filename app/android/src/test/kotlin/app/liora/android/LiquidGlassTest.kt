package app.liora.android

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import app.liora.android.ui.ShellTags
import app.liora.core.data.AppStartup
import app.liora.core.data.routine.RoutineRepository
import app.liora.core.data.settings.SettingsRepository
import app.liora.core.data.workout.ActiveWorkoutRepository
import app.liora.core.data.workout.SetLogger
import app.liora.core.database.dao.WorkoutDao
import app.liora.core.model.Mass
import app.liora.core.model.RepRange
import app.liora.core.model.Routine
import app.liora.core.model.RoutineExercise
import app.liora.core.model.RoutineSet
import app.liora.feature.logger.LoggerTags
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.captureScreenRoboImage
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.koin.core.context.loadKoinModules
import org.koin.core.context.stopKoin
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * Liquid Glass, the look the app starts with: the tabs and the workout bar float as glass over the
 * screens, which reach beneath them on the aurora, and the top bars frost what scrolls under them.
 * Light and dark, on the cover and inner screens, and the solid look it can be switched back to.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], application = TestLioraApplication::class)
class LiquidGlassTest {
    @get:Rule
    val composeRule = createEmptyComposeRule()

    private val koin get() = GlobalContext.get()
    private val originalZone = java.util.TimeZone.getDefault()

    @Before
    fun seed() {
        java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone(ZONE))
        // The Train tab's week as of the Thursday after the last workout, whatever day the test runs.
        loadKoinModules(WeekAsOf.module(LocalDate(2026, 8, 13)))
        runBlocking {
            koin.get<AppStartup>().run()
            val routines = koin.get<RoutineRepository>()
            routines.save(PUSH)
            routines.save(PULL)
            routines.save(LEGS)
            routines.save(UPPER)
            routines.save(LOWER)
        }
        finishedWorkout(PUSH.id, at(2026, 8, 4, 17, 30))
        finishedWorkout(PULL.id, at(2026, 8, 6, 18, 15))
        finishedWorkout(LEGS.id, at(2026, 8, 8, 10, 0))
        finishedWorkout(PUSH.id, at(2026, 8, 11, 17, 45))
    }

    @After
    fun tearDown() {
        stopKoin()
        java.util.TimeZone.setDefault(originalZone)
    }

    @Test
    @Config(qualifiers = COVER_DARK)
    fun dark_theTabsFloatOverTheScreens() {
        launch()
        waitForText("Legs")
        composeRule.onRoot().captureRoboImage("src/test/screenshots/glass_train_dark.png")

        composeRule.onNodeWithText("History").performClick()
        waitForText("August 2026")
        composeRule.onNode(hasText("History") and isSelectable()).assertIsSelected()
        composeRule.onRoot().captureRoboImage("src/test/screenshots/glass_history_dark.png")
    }

    @Test
    @Config(qualifiers = COVER_DARK)
    fun dragAlongTheTabsToSwitch() {
        launch()
        waitForText("Legs")
        val tabs = composeRule.onNodeWithTag(ShellTags.NAVIGATION_BAR)
        // From Train, the drop is dragged over History to Progress: it lifts and follows the finger.
        tabs.performTouchInput {
            down(Offset(width * TRAIN, centerY))
            moveTo(Offset(width * HISTORY, centerY))
            moveTo(Offset(width * PROGRESS, centerY))
        }
        composeRule.onRoot().captureRoboImage("src/test/screenshots/glass_tabs_drag_dark.png")
        // Still on Train until the finger lets go.
        composeRule.onNode(hasText("Train") and isSelectable()).assertIsSelected()
        tabs.performTouchInput { up() }
        composeRule.waitUntil(TIMEOUT_MS) {
            composeRule
                .onAllNodes(hasText("Progress") and isSelectable() and isSelected())
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
    }

    @Test
    @Config(qualifiers = COVER_DARK)
    fun aScreensFloatingButtonStaysClearOfTheTabs() {
        launch()
        composeRule.onNodeWithText("Exercises").performClick()
        waitForText("Search exercises")
        val button = composeRule.onNodeWithContentDescription("Custom exercise").getUnclippedBoundsInRoot()
        val tabs = composeRule.onNodeWithTag(ShellTags.NAVIGATION_BAR).getUnclippedBoundsInRoot()
        assertTrue("the button ends at ${button.bottom}, the tabs start at ${tabs.top}", button.bottom <= tabs.top)
    }

    @Test
    @Config(qualifiers = COVER_DARK)
    fun theLastRoutinesStartClearsTheTabs() {
        launch()
        waitForText("Legs")
        assertLastStartClearsTheTabs()
        // A workout running adds its bar above the tabs.
        composeRule.onNode(hasScrollToIndexAction()).performScrollToIndex(0)
        composeRule.onNodeWithText("Start empty workout").performClick()
        waitForText("Finish")
        composeRule.onNodeWithContentDescription("Minimize workout").performClick()
        waitForText("Resume workout")
        assertLastStartClearsTheTabs()
    }

    private fun assertLastStartClearsTheTabs() {
        val list = composeRule.onNode(hasScrollToIndexAction())
        list.performScrollToNode(hasText("Lower"))
        repeat(SWIPES) { list.performTouchInput { swipeUp() } }
        composeRule.waitForIdle()
        val tabs = composeRule.onNodeWithTag(ShellTags.NAVIGATION_BAR).getUnclippedBoundsInRoot()
        val starts = composeRule.onAllNodesWithText("Start", substring = false).fetchSemanticsNodes()
        val last = starts.maxOf { it.boundsInRoot.bottom }
        val tabsTop = with(composeRule.density) { tabs.top.toPx() }
        assertTrue("the last Start ends at $last px, the tabs start at $tabsTop px", last <= tabsTop)
    }

    @Test
    @Config(qualifiers = COVER_DARK)
    fun menusAndDialogsArePanelsOfGlass() {
        launch()
        waitForText("Legs")
        composeRule.onAllNodesWithContentDescription("More options").onFirst().performClick()
        waitForText("Duplicate")
        captureScreenRoboImage("src/test/screenshots/glass_menu_dark.png")
        composeRule.onNodeWithText("Delete").performClick()
        waitForText("Delete routine?")
        captureScreenRoboImage("src/test/screenshots/glass_dialog_dark.png")
        composeRule.onNodeWithText("Cancel").performClick()
    }

    @Test
    @Config(qualifiers = INNER_DARK)
    fun innerScreen_theRestRingIsADiscOfGlass() {
        launch()
        waitForText("Legs")
        composeRule.onAllNodesWithText("Start").onFirst().performClick()
        waitForText("Finish")
        composeRule.onNode(hasText("Log set") and !hasAnyAncestor(hasTestTag(LoggerTags.PAD))).performClick()
        waitForText("Skip")
        composeRule.onRoot().captureRoboImage("src/test/screenshots/glass_inner_logger_resting_dark.png")
    }

    @Test
    @Config(qualifiers = COVER_LIGHT)
    fun light_theTabsFloatOverTheScreens() {
        launch()
        waitForText("Legs")
        composeRule.onRoot().captureRoboImage("src/test/screenshots/glass_train_light.png")

        composeRule.onNodeWithText("History").performClick()
        waitForText("August 2026")
        composeRule.onRoot().captureRoboImage("src/test/screenshots/glass_history_light.png")
    }

    @Test
    @Config(qualifiers = COVER_DARK)
    fun dark_theWorkoutInProgressFloatsAboveTheTabs() {
        launch()
        waitForText("Legs")
        composeRule.onAllNodesWithText("Start").onFirst().performClick()
        waitForText("Finish")
        // Resting, the timer floats over the end of the list as the accent's glass.
        composeRule.onAllNodesWithContentDescription("Log set").onFirst().performClick()
        waitForText("Skip")
        composeRule.onRoot().captureRoboImage("src/test/screenshots/glass_logger_resting_dark.png")
        composeRule.onNodeWithContentDescription("Minimize workout").performClick()
        waitForText("Resume workout")
        composeRule.onRoot().captureRoboImage("src/test/screenshots/glass_workout_bar_dark.png")
    }

    @Test
    @Config(qualifiers = COVER_LIGHT)
    fun light_theWorkoutInProgressFloatsAboveTheTabs() {
        launch()
        waitForText("Legs")
        composeRule.onAllNodesWithText("Start").onFirst().performClick()
        waitForText("Finish")
        composeRule.onNodeWithContentDescription("Minimize workout").performClick()
        waitForText("Resume workout")
        composeRule.onRoot().captureRoboImage("src/test/screenshots/glass_workout_bar_light.png")
    }

    @Test
    @Config(qualifiers = INNER_DARK)
    fun innerScreen_theRailAndPanesShareTheAurora() {
        launch()
        waitForText("Legs")
        composeRule.onNodeWithText("History").performClick()
        waitForText("August 2026")
        composeRule.onAllNodesWithText("Push").onFirst().performClick()
        waitForText("Repeat workout")
        composeRule.onRoot().captureRoboImage("src/test/screenshots/glass_inner_history_dark.png")
    }

    @Test
    @Config(qualifiers = COVER_DARK)
    fun theSolidLookIsASwitchAway() {
        launch()
        waitForText("Legs")
        composeRule.onNodeWithContentDescription("Settings").performClick()
        waitForText("Rest between sets")
        composeRule.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Liquid Glass"))
        composeRule.onRoot().captureRoboImage("src/test/screenshots/glass_settings_dark.png")
        runBlocking { koin.get<SettingsRepository>().update { it.copy(glass = false) } }
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage("src/test/screenshots/glass_off_settings_dark.png")
    }

    private fun launch(): MainActivity {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        composeRule.waitForIdle()
        return activity
    }

    /** A finished workout from [routineId], every set done, moved to [start]: three minutes a set. */
    private fun finishedWorkout(
        routineId: String,
        start: Instant,
    ) = runBlocking {
        val workouts = koin.get<ActiveWorkoutRepository>()
        val sets = koin.get<SetLogger>()
        val workout = workouts.startFromRoutine(routineId)
        workout.exercises.forEach { exercise -> exercise.sets.forEach { sets.completeSet(it.id) } }
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
    private fun waitForText(text: String) =
        composeRule.waitUntil(TIMEOUT_MS) {
            composeRule.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()
        }

    private companion object {
        const val COVER_DARK = "w411dp-h960dp-night-xxhdpi"
        const val COVER_LIGHT = "w411dp-h960dp-notnight-xxhdpi"
        const val INNER_DARK = "w984dp-h1092dp-night-xhdpi"
        const val ZONE = "Europe/Vienna"

        // Where each tab's middle is across the bar, of four.
        const val SWIPES = 4
        const val TRAIN = 0.125f
        const val HISTORY = 0.375f
        const val PROGRESS = 0.625f
        const val TIMEOUT_MS = 10_000L
        val SET_MINUTES = 3.minutes

        private fun routine(
            id: String,
            name: String,
            vararg exercises: Pair<String, Double?>,
        ) = Routine(
            id = id,
            name = name,
            exercises =
                exercises.mapIndexed { index, (exerciseId, kg) ->
                    RoutineExercise(
                        id = "$id-$index",
                        exerciseId = exerciseId,
                        sets = List(3) { RoutineSet("$id-$index-$it", weight = kg?.let(::Mass), reps = RepRange(10)) },
                    )
                },
        )

        val PUSH =
            routine(
                "push",
                "Push",
                "fedb.Barbell_Bench_Press_-_Medium_Grip" to 80.0,
                "fedb.Dumbbell_Flyes" to 22.5,
                "fedb.Standing_Military_Press" to 45.0,
            )
        val PULL = routine("pull", "Pull", "fedb.Pullups" to null, "fedb.Bent_Over_Barbell_Row" to 70.0)
        val LEGS = routine("legs", "Legs", "fedb.Barbell_Squat" to 100.0, "fedb.Leg_Press" to 160.0)
        val UPPER =
            routine("upper", "Upper", "fedb.Barbell_Bench_Press_-_Medium_Grip" to 82.5, "fedb.Pullups" to null)
        val LOWER = routine("lower", "Lower", "fedb.Barbell_Deadlift" to 140.0, "fedb.Leg_Press" to 170.0)
    }
}
