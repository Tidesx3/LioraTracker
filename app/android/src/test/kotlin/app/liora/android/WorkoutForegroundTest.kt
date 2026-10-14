package app.liora.android

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.os.Looper
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import app.liora.android.workout.WorkoutNotifications
import app.liora.android.workout.WorkoutService
import app.liora.core.data.AppStartup
import app.liora.core.data.routine.RoutineRepository
import app.liora.core.data.workout.ActiveWorkoutRepository
import app.liora.core.model.Mass
import app.liora.core.model.RepRange
import app.liora.core.model.Routine
import app.liora.core.model.RoutineExercise
import app.liora.core.model.RoutineSet
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.koin.core.context.stopKoin
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ServiceController
import org.robolectric.annotation.Config

/**
 * A workout runs in a foreground service, so the end of a rest may play its sound with the screen off
 * (Android 17): started while Liora is on screen, under the workout's own notification, and over with the
 * workout.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = TestLioraApplication::class)
class WorkoutForegroundTest {
    // Its clock drives Compose's frames: on its own, idling the main looper with Liora on screen never ends.
    @get:Rule
    val composeRule = createEmptyComposeRule()

    private val app = RuntimeEnvironment.getApplication()
    private val koin get() = GlobalContext.get()
    private val workouts get() = koin.get<ActiveWorkoutRepository>()
    private var service: ServiceController<WorkoutService>? = null

    @Before
    fun seed() =
        runBlocking {
            shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
            koin.get<AppStartup>().run()
            koin.get<RoutineRepository>().save(PUSH)
        }

    @After
    fun tearDown() {
        service?.destroy()
        stopKoin()
    }

    @Test
    fun aWorkoutStartedOnScreenRunsInTheForegroundUntilItEnds() {
        Robolectric.buildActivity(MainActivity::class.java).setup()
        runBlocking { workouts.startFromRoutine(PUSH.id) }
        awaitServiceStarted()

        // Android creates it; here the test does.
        val running = Robolectric.buildService(WorkoutService::class.java).create().startCommand(0, 1)
        service = running
        val shadow = shadowOf(running.get())
        assertEquals(WorkoutNotifications.ID_WORKOUT, shadow.lastForegroundNotificationId)
        assertTrue(WorkoutService.running)
        // Its notification is the workout's, as the notifier keeps posting it.
        waitFor { workoutNotification()?.extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString() == "Push" }

        runBlocking { workouts.discard() }
        waitFor { shadow.isStoppedBySelf }
        assertTrue(shadow.isForegroundStopped)
        assertTrue(shadow.notificationShouldRemoved)
        running.destroy()
        service = null
        assertFalse(WorkoutService.running)
    }

    @Test
    fun offScreenItWaitsUntilLioraIsBack() {
        runBlocking { workouts.startFromRoutine(PUSH.id) }
        // The notifier has seen the workout, and so has the foreground's watch, which runs alongside.
        waitFor { workoutNotification() != null }
        shadowOf(Looper.getMainLooper()).idle()
        // Off screen, Android wouldn't let the service play a sound later, so it isn't started.
        assertNull(shadowOf(app).peekNextStartedService())

        Robolectric.buildActivity(MainActivity::class.java).setup()
        awaitServiceStarted()
    }

    private fun awaitServiceStarted() =
        waitFor { shadowOf(app).peekNextStartedService()?.component?.className == WorkoutService::class.java.name }

    private fun workoutNotification(): Notification? =
        app
            .getSystemService(NotificationManager::class.java)
            .activeNotifications
            .firstOrNull { it.id == WorkoutNotifications.ID_WORKOUT }
            ?.notification

    /** Runs the main looper as it waits, which the service and the notifier resume on. */
    private fun waitFor(condition: () -> Boolean) =
        composeRule.waitUntil(TIMEOUT_MS) {
            shadowOf(Looper.getMainLooper()).idle()
            condition()
        }

    private companion object {
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
