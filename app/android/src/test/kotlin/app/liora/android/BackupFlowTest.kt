package app.liora.android

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollToKeyAction
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToKey
import app.liora.core.data.AppStartup
import app.liora.core.data.body.ProgressPhotoRepository
import app.liora.core.data.exercise.ExerciseRepository
import app.liora.core.data.workout.ActiveWorkoutRepository
import app.liora.core.data.workout.WorkoutHistoryRepository
import app.liora.core.model.Equipment
import app.liora.core.model.ExerciseDraft
import app.liora.core.model.Mass
import app.liora.core.model.PhotoPose
import app.liora.core.model.TrackingType
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
import java.util.zip.ZipFile
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * Settings' data section end to end, through the system file picker: a backup with its photo, the
 * look at it before restoring, and the CSV export.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], application = TestLioraApplication::class)
class BackupFlowTest {
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
        // Friday, 2 October 2026, so the file names and the backup's date stay put.
        val now = LocalDateTime(2026, 10, 2, 18, 30).toInstant(zone)
        loadKoinModules(module { single<Clock> { FixedClock(now) } })
        runBlocking {
            koin.get<AppStartup>().run()
            seed()
        }
    }

    @After
    fun tearDown() {
        stopKoin()
        java.util.TimeZone.setDefault(originalZone)
    }

    @Test
    @Config(qualifiers = COVER)
    fun backingUpLookingAtItAndRestoring() {
        openData()
        composeRule.onRoot().captureRoboImage("src/test/screenshots/settings_data_dark.png")

        val backup = file("backup.zip")
        composeRule.onNodeWithText("Back up everything").performClick()
        val create = pick(backup)
        assertEquals(Intent.ACTION_CREATE_DOCUMENT, create.action)
        assertEquals("liora-backup-2026-10-02.zip", create.getStringExtra(Intent.EXTRA_TITLE))
        waitForText("Backup saved")
        val photo =
            runBlocking {
                koin
                    .get<ProgressPhotoRepository>()
                    .photos
                    .first()
                    .single()
            }
        ZipFile(backup).use { zip ->
            assertTrue(zip.getEntry("liora.json") != null)
            assertTrue(zip.getEntry("photos/${photo.id}.jpg") != null)
        }

        composeRule.onNodeWithText("Restore a backup").performClick()
        assertEquals(Intent.ACTION_OPEN_DOCUMENT, pick(backup).action)
        waitForText("Restore this backup?")
        composeRule.onNodeWithText("1 workout").assertIsDisplayed()
        composeRule.onNodeWithText("1 custom exercise").assertIsDisplayed()
        composeRule.onNodeWithText("1 progress photo").assertIsDisplayed()
        composeRule.onRoot().captureRoboImage("src/test/screenshots/settings_restore_dark.png")

        // Everything in it is here already, as it was.
        composeRule.onNodeWithText("Restore").performClick()
        waitForText("This phone already has everything in that backup")
    }

    @Test
    @Config(qualifiers = COVER)
    fun exportingWorkoutsAsCsv() {
        openData()
        val csv = file("workouts.csv")
        composeRule.onNodeWithText("Export workouts as CSV").performClick()
        assertEquals("liora-workouts-2026-10-02.csv", pick(csv).getStringExtra(Intent.EXTRA_TITLE))
        waitForText("Workouts exported")

        val rows = csv.readText().removePrefix(Char(0xFEFF).toString()).split("\r\n")
        assertEquals(
            "2026-10-02 17:30,2026-10-02 18:25,Push,Bench Press Studio,,1,normal,80,8,,,,,",
            rows[1],
        )
    }

    @Test
    @Config(qualifiers = "de-$COVER")
    fun german() {
        openData("Einstellungen", "Deine Daten")
        composeRule.onNodeWithText("Alles sichern").performClick()
        pick(file("backup.zip"))
        waitForText("Sicherung gespeichert")
        composeRule.onNodeWithText("Sicherung wiederherstellen").performClick()
        pick(file("backup.zip"))
        waitForText("Diese Sicherung wiederherstellen?")
        composeRule.onNodeWithText("1 Training").assertIsDisplayed()
        composeRule.onRoot().captureRoboImage("src/test/screenshots/de_settings_restore_dark.png")
    }

    private fun openData(
        settings: String = "Settings",
        section: String = "Your data",
    ) {
        activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription(settings).performClick()
        composeRule.waitUntil(TIMEOUT_MS) {
            composeRule.onAllNodes(hasScrollToKeyAction()).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNode(hasScrollToKeyAction()).performScrollToKey("data")
        waitForText(section)
    }

    /** Answers the file picker the app just opened with [file]; returns what the app asked it for. */
    private fun pick(file: File): Intent {
        val shadow = shadowOf(activity)
        val started = checkNotNull(shadow.nextStartedActivityForResult) { "No file picker opened" }
        shadow.receiveResult(started.intent, Activity.RESULT_OK, Intent().setData(Uri.fromFile(file)))
        return started.intent
    }

    private fun file(name: String) = File(context.cacheDir, name)

    /** A finished workout with a custom exercise, and a progress photo. */
    private suspend fun seed() {
        val bench =
            koin.get<ExerciseRepository>().createCustom(
                ExerciseDraft("Bench Press Studio", TrackingType.WeightReps, Equipment.Barbell),
            )
        val workouts = koin.get<ActiveWorkoutRepository>()
        val workout = workouts.startEmptyWorkout()
        workouts.rename("Push")
        workouts.editor.addExercises(listOf(bench))
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
        // Writes are stamped by the clock the app started with, so the times are set here.
        val start = LocalDateTime(2026, 10, 2, 17, 30).toInstant(zone)
        koin.get<WorkoutHistoryRepository>().edit(workout.id).setTimes(start, start + 55.minutes)
        val photo = syntheticPhoto(context, PhotoPose.Front, waist = 220f, takenAt = "2026:10:01 08:00:00")
        koin.get<ProgressPhotoRepository>().add(photo, PhotoPose.Front)
    }

    // Room emits on a background dispatcher that Compose's idling doesn't track, so wait explicitly.
    private fun waitForText(text: String) =
        composeRule.waitUntil(TIMEOUT_MS) {
            composeRule.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()
        }

    private class FixedClock(
        private val now: Instant,
    ) : Clock {
        override fun now() = now
    }

    private companion object {
        const val COVER = "w411dp-h960dp-night-xxhdpi"
        const val ZONE = "Europe/Berlin"
        const val TIMEOUT_MS = 20_000L
    }
}
