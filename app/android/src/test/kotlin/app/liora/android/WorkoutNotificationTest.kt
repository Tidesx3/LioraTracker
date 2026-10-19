package app.liora.android

import android.Manifest
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationManager
import android.content.Intent
import android.os.Looper
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.VibratorManager
import app.liora.android.workout.RestAlarmReceiver
import app.liora.android.workout.WorkoutNotificationText
import app.liora.android.workout.WorkoutNotifications
import app.liora.android.workout.WorkoutNotifier
import app.liora.core.data.AppStartup
import app.liora.core.data.routine.RoutineRepository
import app.liora.core.data.workout.ActiveWorkoutRepository
import app.liora.core.data.workout.RestTimerRepository
import app.liora.core.data.workout.SetLogger
import app.liora.core.domain.RestSound
import app.liora.core.model.ActiveWorkout
import app.liora.core.model.Mass
import app.liora.core.model.RepRange
import app.liora.core.model.Routine
import app.liora.core.model.RoutineExercise
import app.liora.core.model.RoutineSet
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.koin.core.context.stopKoin
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowVibrator
import kotlin.time.Duration.Companion.seconds

/**
 * The live workout notification: the next set with last session's values, logging it from the
 * notification, the rest countdown with −15 s, +15 s (each with a click) and skip, the end-of-rest alarm
 * and its alert.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = TestLioraApplication::class)
class WorkoutNotificationTest {
    private val app = RuntimeEnvironment.getApplication()
    private val koin get() = GlobalContext.get()
    private val notifications get() = app.getSystemService(NotificationManager::class.java)

    @Before
    fun seed() =
        runBlocking {
            shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
            koin.get<AppStartup>().run()
            koin.get<RoutineRepository>().save(PUSH)
        }

    @After
    fun tearDown() = stopKoin()

    @Test
    fun theNextSetCanBeLoggedFromTheNotification() {
        runBlocking {
            logLastSession()
            koin.get<ActiveWorkoutRepository>().startFromRoutine(PUSH.id)
            koin.get<WorkoutNotifier>().refresh()
        }
        val set = awaitNotification { it.title == "Push" }
        assertEquals("Barbell Bench Press · Set 1 of 3 · 80 kg × 10", set.text)
        // Android 16 shows it as a Live Update, with a chip in the status bar saying where you are.
        assertLiveUpdate(set)
        assertEquals("1/3", set.shortCriticalText)
        assertEquals(listOf("Log set"), set.actions.map { it.title.toString() })

        set.actions.single().tap()
        awaitWorkout {
            it.exercises
                .single()
                .sets
                .first()
                .isCompleted
        }

        // Resting now: the countdown, what's next, and an alarm for when the rest is up.
        // The rest timer and the workout reach the notifier as separate updates; wait for both.
        val resting =
            awaitNotification {
                it.isResting && it.text == "Next: Barbell Bench Press · Set 2 of 3 · 80 kg × 10"
            }
        assertEquals(listOf("−15 s", "+15 s", "Skip rest"), resting.actions.map { it.title.toString() })
        // Still a Live Update while resting: the chip counts the rest down.
        assertLiveUpdate(resting)
        assertTrue(resting.extras.getBoolean(Notification.EXTRA_CHRONOMETER_COUNT_DOWN))
        // The bar counts milliseconds, so it moves on every update instead of once a second.
        assertEquals(90_000, resting.extras.getInt(Notification.EXTRA_PROGRESS_MAX))
        val timer = runBlocking { koin.get<RestTimerRepository>().current() }!!
        assertEquals(90.seconds, timer.total)
        awaitAlarmAt(timer.endsAt.toEpochMilliseconds())
        // The header's countdown rounds down, the title up: its end is set just under a second later so
        // both read the same.
        assertEquals(timer.endsAt.toEpochMilliseconds() + 999, resting.`when`)

        // +15 s and −15 s move the end of the rest, and the alarm with it, each with a click to feel.
        assertNoClick()
        resting.actions[1].tap()
        assertClicked()
        awaitAlarmAt(awaitTimer { it?.total == 105.seconds }!!.endsAt.toEpochMilliseconds())
        ShadowVibrator.reset()
        resting.actions[0].tap()
        assertClicked()
        val extended = awaitTimer { it?.total == 90.seconds }!!
        awaitAlarmAt(extended.endsAt.toEpochMilliseconds())

        // The alarm goes off: the rest ends with a heads-up, and the notification is back to the next set.
        app.sendBroadcast(
            Intent(app, RestAlarmReceiver::class.java)
                .setAction(WorkoutNotifications.ACTION_REST_OVER)
                .putExtra(WorkoutNotifications.EXTRA_ENDS_AT, extended.endsAt.toEpochMilliseconds()),
        )
        shadowOf(Looper.getMainLooper()).idle()
        awaitTimer { it == null }
        val alert = awaitNotification(WorkoutNotifications.ID_REST_OVER) { it.title == "Break’s over" }
        assertEquals(WorkoutNotifications.CHANNEL_REST_OVER, alert.channelId)
        assertEquals(Notification.VISIBILITY_PUBLIC, alert.visibility)
        // Liora plays the sound itself, so the channel only vibrates and vibrate mode doesn't mute it.
        assertEquals(listOf(RestSound.Chime), koin.get<RecordingRestSoundPlayer>().played)
        val channel = notifications.getNotificationChannel(alert.channelId)
        assertNull(channel.sound)
        awaitNotification { it.title == "Push" && it.text.startsWith("Barbell Bench Press · Set 2 of 3") }
    }

    @Test
    fun skippingTheRestAndEndingTheWorkout() {
        runBlocking {
            val workout = koin.get<ActiveWorkoutRepository>().startFromRoutine(PUSH.id)
            val sets = koin.get<SetLogger>()
            sets.updateSet(
                workout.exercises
                    .single()
                    .sets
                    .first()
                    .id,
            ) { copy(reps = 8) }
            sets.completeCurrentSet()
            koin.get<WorkoutNotifier>().refresh()
        }
        val resting = awaitNotification { it.isResting }
        resting.actions
            .last()
            .actionIntent
            .send()
        shadowOf(Looper.getMainLooper()).idle()
        awaitTimer { it == null }
        awaitNotification { it.title == "Push" }

        runBlocking {
            koin.get<ActiveWorkoutRepository>().discard()
            koin.get<WorkoutNotifier>().refresh()
        }
        waitFor { notifications.activeNotifications.none { it.id == WorkoutNotifications.ID_WORKOUT } }
        assertNull(shadowOf(app.getSystemService(AlarmManager::class.java)).nextScheduledAlarm)
    }

    /** Android 17 shows its countdown right after the title, so there the title leaves the time out. */
    @Test
    fun theRestsTitleHasTheTimeLeftOnlyWhereTheCountdownSitsApart() {
        assertEquals("Rest 1:30", WorkoutNotificationText(app, timeBesideTitle = false).restTitle("1:30"))
        assertEquals("Rest", WorkoutNotificationText(app, timeBesideTitle = true).restTitle("1:30"))
    }

    /** A finished Push session: every set 80 kg × 10. */
    private suspend fun logLastSession() {
        val workouts = koin.get<ActiveWorkoutRepository>()
        val sets = koin.get<SetLogger>()
        workouts.startFromRoutine(PUSH.id).exercises.single().sets.forEach { set ->
            sets.updateSet(set.id) { copy(reps = 10) }
            sets.completeSet(set.id)
        }
        workouts.finish()
    }

    /**
     * What Android 16 asks of a Live Update since it shipped them (QPR1, `Notification.hasPromotableCharacteristics`
     * with `ui_rich_ongoing` on): it asks for it, it's ongoing with a title, has a style the system can draw
     * (none, big text, call or progress), isn't a group summary, draws no views of its own, and isn't
     * colorized. Robolectric's Android 16 is the first release, which still had other rules, so they're
     * checked here instead of calling the method. And it's readable on the lock screen without unlocking,
     * even when the phone hides what notifications say there.
     */
    private fun assertLiveUpdate(notification: Notification) {
        assertEquals(Notification.VISIBILITY_PUBLIC, notification.visibility)
        assertTrue(notification.extras.getBoolean(Notification.EXTRA_REQUEST_PROMOTED_ONGOING))
        assertTrue(notification.flags and Notification.FLAG_ONGOING_EVENT != 0)
        assertTrue(notification.title.orEmpty().isNotBlank())
        val style = notification.extras.getString(Notification.EXTRA_TEMPLATE)
        assertTrue(
            "Style $style",
            style == null || style.substringAfter('$') in setOf("BigTextStyle", "CallStyle", "ProgressStyle"),
        )
        assertTrue(notification.flags and Notification.FLAG_GROUP_SUMMARY == 0)
        @Suppress("DEPRECATION")
        assertTrue(
            listOf(notification.contentView, notification.bigContentView, notification.headsUpContentView).all {
                it ==
                    null
            },
        )
        assertFalse(notification.extras.getBoolean(Notification.EXTRA_COLORIZED))
    }

    private val Notification.title get() = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()

    private val Notification.text get() = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()

    /** The rest countdown, titled with the time left ("Rest 1:30"). */
    private val Notification.isResting get() = title?.matches(REST_TITLE) == true

    private fun awaitNotification(
        id: Int = WorkoutNotifications.ID_WORKOUT,
        matches: (Notification) -> Boolean,
    ): Notification {
        var found: Notification? = null
        waitFor {
            found =
                notifications.activeNotifications
                    .firstOrNull { it.id == id }
                    ?.notification
                    ?.takeIf(matches)
            found != null
        }
        return found!!
    }

    private fun awaitWorkout(matches: (ActiveWorkout) -> Boolean) =
        waitFor { runBlocking { koin.get<ActiveWorkoutRepository>().activeWorkout.first() }?.let(matches) == true }

    private fun awaitTimer(matches: (app.liora.core.model.RestTimer?) -> Boolean): app.liora.core.model.RestTimer? {
        var timer: app.liora.core.model.RestTimer? = null
        waitFor {
            timer = runBlocking { koin.get<RestTimerRepository>().current() }
            matches(timer)
        }
        return timer
    }

    /** Taps a button on the notification; its receiver runs on the main thread. */
    private fun Notification.Action.tap() {
        actionIntent.send()
        shadowOf(Looper.getMainLooper()).idle()
    }

    private val vibrator get() = shadowOf(app.getSystemService(VibratorManager::class.java).defaultVibrator)

    private fun assertNoClick() = assertNull(vibrator.vibrationAttributesFromLastVibration)

    /** One click, as a notification vibration: Android drops touch ones from an app in the background. */
    private fun assertClicked() {
        val attributes = vibrator.vibrationAttributesFromLastVibration as VibrationAttributes?
        assertEquals(VibrationAttributes.USAGE_NOTIFICATION, attributes?.usage)
        assertEquals(VibrationEffect.EFFECT_CLICK, vibrator.effectId)
    }

    private fun awaitAlarmAt(millis: Long) =
        waitFor { shadowOf(app.getSystemService(AlarmManager::class.java)).nextScheduledAlarm?.triggerAtTime == millis }

    /** Receivers and the notifier work on background threads; poll until they're done. */
    private fun waitFor(condition: () -> Boolean) =
        runBlocking {
            withTimeout(TIMEOUT) {
                while (!condition()) {
                    shadowOf(Looper.getMainLooper()).idle()
                    delay(POLL)
                }
            }
        }

    private companion object {
        val TIMEOUT = 10.seconds
        val POLL = 20.seconds / 1000
        val REST_TITLE = Regex("""Rest \d+:\d{2}""")

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
