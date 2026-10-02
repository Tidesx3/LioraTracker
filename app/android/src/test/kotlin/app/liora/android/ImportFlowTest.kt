package app.liora.android

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollToKeyAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
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
import app.liora.core.data.workout.WorkoutHistoryRepository
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
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
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Importing a Hevy export from Settings: the file picker, the review of where each exercise goes,
 * changing that, and the workouts landing in history.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], application = TestLioraApplication::class)
class ImportFlowTest {
    @get:Rule
    val composeRule = createEmptyComposeRule()

    private val koin get() = GlobalContext.get()
    private val context get() = koin.get<Context>()
    private val originalZone = java.util.TimeZone.getDefault()
    private val zone = TimeZone.of(ZONE)
    private lateinit var activity: Activity

    @Before
    fun setUp() {
        java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone(ZONE))
        val now = LocalDateTime(2026, 10, 2, 18, 30).toInstant(zone)
        loadKoinModules(module { single<Clock> { FixedClock(now) } })
        runBlocking { koin.get<AppStartup>().run() }
    }

    @After
    fun tearDown() {
        stopKoin()
        java.util.TimeZone.setDefault(originalZone)
    }

    @Test
    @Config(qualifiers = COVER)
    fun reviewingAndImportingAHevyExport() {
        openImport()
        waitForText("Import from Hevy")
        composeRule.onNodeWithText("2 workouts").assertIsDisplayed()
        composeRule.onNodeWithText("New, a variation of Leg Press").assertIsDisplayed()
        composeRule.onNodeWithText("Goes to Barbell Bench Press").assertIsDisplayed()
        composeRule.onRoot().captureRoboImage("src/test/screenshots/import_review_dark.png")

        // Merge the gym's lat pulldown into the built-in one.
        composeRule.onNodeWithText("Latzug Studio Nord").performClick()
        waitForText("Add as a new exercise")
        composeRule.onRoot().captureRoboImage("src/test/screenshots/import_choice_dark.png")
        composeRule.onNodeWithText("Use Wide-Grip Lat Pulldown").performClick()
        waitForText("Goes to Wide-Grip Lat Pulldown")

        // And pick the leg press from the library.
        composeRule.onNodeWithText("Beinpresse Studio Nord").performClick()
        waitForText("Choose another exercise…")
        composeRule.onNodeWithText("Choose another exercise…").performClick()
        composeRule.waitUntil(TIMEOUT_MS) { nodeExists(hasSetTextAction()) }
        composeRule.onNode(hasSetTextAction()).performTextInput("leg press")
        composeRule.waitUntil(TIMEOUT_MS) { nodeExists(hasText("Leg Press")) }
        composeRule.onAllNodesWithText("Leg Press").onFirst().performClick()
        waitForText("Goes to Leg Press")

        composeRule.onNodeWithText("Import").performClick()
        waitForText("Imported")
        composeRule.onNodeWithText("2 workouts\n7 sets").assertIsDisplayed()

        val workouts = runBlocking { koin.get<WorkoutHistoryRepository>().workouts.first() }
        assertEquals(listOf("Pull", "Push"), workouts.map { it.name })
        assertEquals(LocalDateTime(2026, 9, 30, 17, 39).toInstant(zone), workouts.first().startedAt)
        val names =
            runBlocking { koin.get<ExerciseRepository>().observeExercises("en").first() }.associate {
                it.id to
                    it.name
            }
        assertEquals(
            listOf("Wide-Grip Lat Pulldown", "Weighted Pull Ups"),
            workouts.first().exercises.map { names[it.exerciseId] },
        )
        assertEquals(
            "Leg Press",
            names[
                workouts
                    .last()
                    .exercises
                    .last()
                    .exerciseId,
            ],
        )

        composeRule.onNodeWithText("Done").performClick()
        waitForText("Your data")
    }

    @Test
    @Config(qualifiers = INNER)
    fun theReviewOnTheInnerScreen() {
        openImport()
        waitForText("New, a variation of Leg Press")
        composeRule.onRoot().captureRoboImage("src/test/screenshots/fold_inner_import_review_dark.png")
    }

    @Test
    @Config(qualifiers = "de-$COVER")
    fun german() {
        openImport("Einstellungen", "Deine Daten", "Aus Hevy oder Strong importieren")
        waitForText("Import aus Hevy")
        composeRule.onNodeWithText("2 Trainings").assertIsDisplayed()
        composeRule.onNodeWithText("Neu, Variante von Beinpresse").assertIsDisplayed()
        composeRule.onRoot().captureRoboImage("src/test/screenshots/de_import_review_dark.png")
    }

    @Test
    @Config(qualifiers = COVER)
    fun aFileThatIsntAnExport() {
        openImport(content = "name,value\nsomething,1\n")
        waitForText("That file isn’t from Hevy or Strong")
        composeRule.onNodeWithText("Close").performClick()
        waitForText("Your data")
    }

    /** Opens Settings' data section and imports a file holding [content]. */
    private fun openImport(
        settings: String = "Settings",
        section: String = "Your data",
        row: String = "Import from Hevy or Strong",
        content: String = HEVY,
    ) {
        activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription(settings).performClick()
        composeRule.waitUntil(TIMEOUT_MS) { nodeExists(hasScrollToKeyAction()) }
        composeRule.onNode(hasScrollToKeyAction()).performScrollToKey("data")
        waitForText(section)
        composeRule.onNodeWithText(row).performClick()
        val file = File(context.cacheDir, "workout_data.csv").apply { writeText(content) }
        assertEquals(Intent.ACTION_OPEN_DOCUMENT, pick(file).action)
    }

    /** Answers the file picker the app just opened with [file]; returns what the app asked it for. */
    private fun pick(file: File): Intent {
        val shadow = shadowOf(activity)
        val started = checkNotNull(shadow.nextStartedActivityForResult) { "No file picker opened" }
        shadow.receiveResult(started.intent, Activity.RESULT_OK, Intent().setData(Uri.fromFile(file)))
        return started.intent
    }

    private fun nodeExists(matcher: SemanticsMatcher) =
        composeRule.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty()

    // Room emits on a background dispatcher that Compose's idling doesn't track, so wait explicitly.
    private fun waitForText(text: String) =
        composeRule.waitUntil(TIMEOUT_MS) { nodeExists(hasText(text, substring = true)) }

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

        /** A synthetic export laid out like Hevy's from a German phone. */
        val HEVY =
            """
            "title","start_time","end_time","description","exercise_title","superset_id","exercise_notes","set_index","set_type","weight_kg","reps","distance_km","duration_seconds","rpe"
            "Pull","30 Sept. 2026, 17:39","30 Sept. 2026, 19:17","","Latzug Studio Nord",,"",0,"normal",75,12,,,
            "Pull","30 Sept. 2026, 17:39","30 Sept. 2026, 19:17","","Latzug Studio Nord",,"",1,"normal",75,10,,,
            "Pull","30 Sept. 2026, 17:39","30 Sept. 2026, 19:17","","Klimmzug (Gewichtet)",,"",0,"normal",10,6,,,
            "Push","28 Sept. 2026, 18:05","28 Sept. 2026, 19:10","","Bankdrücken (Langhantel)",,"",0,"warmup",40,10,,,
            "Push","28 Sept. 2026, 18:05","28 Sept. 2026, 19:10","","Bankdrücken (Langhantel)",,"",1,"normal",80,8,,,
            "Push","28 Sept. 2026, 18:05","28 Sept. 2026, 19:10","","Seitheben (Kurzhantel)",,"",0,"normal",10,15,,,
            "Push","28 Sept. 2026, 18:05","28 Sept. 2026, 19:10","","Beinpresse Studio Nord",,"",0,"normal",150,12,,,
            """.trimIndent()
    }
}
