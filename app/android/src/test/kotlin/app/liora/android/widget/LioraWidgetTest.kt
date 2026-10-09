package app.liora.android.widget

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.widget.FrameLayout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.compose
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.testing.unit.hasStartActivityClickAction
import androidx.glance.appwidget.testing.unit.runGlanceAppWidgetUnitTest
import androidx.glance.testing.unit.hasContentDescription
import androidx.glance.testing.unit.hasText
import app.liora.android.TestLioraApplication
import app.liora.core.data.AppStartup
import app.liora.core.data.routine.RoutineRepository
import app.liora.core.data.workout.ActiveWorkoutRepository
import app.liora.core.data.workout.RestTimerRepository
import app.liora.core.data.workout.SetLogger
import app.liora.core.model.Mass
import app.liora.core.model.RepRange
import app.liora.core.model.Routine
import app.liora.core.model.RoutineExercise
import app.liora.core.model.RoutineSet
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.koin.core.context.stopKoin
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/** The home-screen widget: what it shows from the app's data, and its faces. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], application = TestLioraApplication::class)
class LioraWidgetTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()

    @After
    fun tearDown() = stopKoin()

    @Test
    fun itFollowsTheRotationAndTheWorkout() =
        runBlocking {
            val koin = GlobalContext.get()
            val states = koin.get<WidgetStates>()
            val routines = koin.get<RoutineRepository>()
            val workouts = koin.get<ActiveWorkoutRepository>()
            koin.get<AppStartup>().run()
            val folder = routines.createFolder(PPL)
            routines.save(routine("push", "Push Day", folder))
            routines.save(routine("pull", "Pull Day", folder))

            // A plain folder: nothing's next, and no streak yet.
            assertEquals(WidgetState.QuickStart(streakWeeks = 0), states.current())

            routines.setRotation(folder, rotation = true)
            assertEquals(WidgetState.NextUp(PPL, "push", "Push Day", streakWeeks = 0), states.current())

            // Training: the name, a set done, and the rest it started.
            workouts.startFromRoutine("push")
            koin.get<SetLogger>().completeCurrentSet()
            val training = states.current() as WidgetState.Training
            assertEquals("Push Day", training.name)
            assertEquals(1, training.setsDone)
            assertNotNull(training.restEndsAt)

            // A rest running out changes nothing stored, and still changes what the widget shows.
            koin.get<RestTimerRepository>().start(1.seconds)
            withTimeout(5.seconds) {
                states.changes.first { it is WidgetState.Training && it.restEndsAt == null }
            }

            // Done: Pull is next, and this week counts.
            workouts.finish()
            assertEquals(WidgetState.NextUp(PPL, "pull", "Pull Day", streakWeeks = 1), states.current())
        }

    @Test
    fun nextUpStartsTheRoutine() =
        runGlanceAppWidgetUnitTest {
            setContext(context)
            setAppWidgetSize(WIDE)
            provideComposable { LioraWidgetContent(NEXT_UP, NOW, wallpaperColors = false) }
            onNode(hasText("Next up in $PPL")).assertExists()
            onNode(hasText("Pull Day")).assertExists()
            onNode(hasText("3 weeks in a row")).assertExists()
            onNode(hasStartActivityClickAction(WidgetIntents.startRoutine(context, "pull"))).assertExists()
        }

    @Test
    fun withoutARotationItStartsAnEmptyWorkout() =
        runGlanceAppWidgetUnitTest {
            setContext(context)
            setAppWidgetSize(SMALL)
            provideComposable {
                LioraWidgetContent(
                    WidgetState.QuickStart(streakWeeks = 0),
                    NOW,
                    wallpaperColors = false,
                )
            }
            onNode(hasText("Quick start")).assertExists()
            onNode(hasContentDescription("Start empty workout")).assertExists()
            onNode(hasStartActivityClickAction(WidgetIntents.startEmpty(context))).assertExists()
        }

    @Test
    fun duringAWorkoutItOpensTheLogger() =
        runGlanceAppWidgetUnitTest {
            setContext(context)
            setAppWidgetSize(WIDE)
            provideComposable { LioraWidgetContent(RESTING, NOW, wallpaperColors = false) }
            onNode(hasText("Push Day")).assertExists()
            onNode(hasText("Rest")).assertExists()
            onNode(hasText("7 sets done")).assertExists()
            onNode(hasStartActivityClickAction(WidgetIntents.openLogger(context))).assertExists()
        }

    @Test
    fun faces() {
        // The widget's real RemoteViews, drawn on a wallpaper grey: wide, then small, light, then dark.
        val faces =
            listOf(
                NEXT_UP to WIDE,
                WidgetState.QuickStart(streakWeeks = 0) to WIDE,
                TRAINING to WIDE,
                RESTING to WIDE,
                NEXT_UP to SMALL,
                RESTING to SMALL,
            )
        val light = faces.map { (state, size) -> render(state, size, night = false) }
        val dark = faces.map { (state, size) -> render(state, size, night = true) }
        sheet(light, dark).captureRoboImage("src/test/screenshots/widget_faces.png")
        assertTrue(light.all { it.width > 0 })
    }

    /** The widget's RemoteViews for [state] at [size], applied and drawn. */
    private fun render(
        state: WidgetState,
        size: DpSize,
        night: Boolean,
    ): Bitmap {
        val config =
            Configuration(context.resources.configuration).apply {
                val mode = if (night) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
                uiMode = mode or (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv())
            }
        val themed = context.createConfigurationContext(config)
        val views = runBlocking { FixedWidget(state).compose(themed, size = size) }
        val view = views.apply(themed, FrameLayout(themed))
        val width = px(size.width)
        val height = px(size.height)
        view.measure(exactly(width), exactly(height))
        view.layout(0, 0, width, height)
        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { view.draw(Canvas(it)) }
    }

    /** Light faces on the left, dark on the right, one under the other. */
    private fun sheet(
        light: List<Bitmap>,
        dark: List<Bitmap>,
    ): Bitmap {
        val gap = px(16.dp)
        val column = light.maxOf { it.width }
        val sheet =
            Bitmap.createBitmap(
                gap * 3 + column * 2,
                gap + light.sumOf { it.height + gap },
                Bitmap.Config.ARGB_8888,
            )
        val canvas = Canvas(sheet)
        canvas.drawColor(WALLPAPER)
        var top = gap.toFloat()
        light.zip(dark).forEach { (day, night) ->
            canvas.drawBitmap(day, gap.toFloat(), top, null)
            canvas.drawBitmap(night, (gap * 2 + column).toFloat(), top, null)
            top += day.height + gap
        }
        return sheet
    }

    private fun px(dp: Dp) = (dp.value * context.resources.displayMetrics.density).roundToInt()

    private fun exactly(px: Int) = View.MeasureSpec.makeMeasureSpec(px, View.MeasureSpec.EXACTLY)

    /** The widget showing one state, whatever the app's data. */
    private class FixedWidget(
        private val state: WidgetState,
    ) : GlanceAppWidget() {
        override val sizeMode: SizeMode = SizeMode.Exact

        override suspend fun provideGlance(
            context: Context,
            id: GlanceId,
        ) = provideContent { LioraWidgetContent(state, NOW, wallpaperColors = false) }
    }

    private fun routine(
        id: String,
        name: String,
        folder: String,
    ) = Routine(
        id = id,
        name = name,
        folderId = folder,
        exercises =
            listOf(
                RoutineExercise(
                    id = "$id-e",
                    exerciseId = "fedb.Barbell_Bench_Press_-_Medium_Grip",
                    sets = List(3) { RoutineSet("$id-$it", weight = Mass(60.0), reps = RepRange(8, 12)) },
                ),
            ),
    )

    private companion object {
        const val PPL = "Push Pull Legs"
        val NOW = Instant.parse("2026-10-09T18:00:00Z")
        val WIDE = DpSize(320.dp, 140.dp)
        val SMALL = DpSize(160.dp, 72.dp)
        const val WALLPAPER = 0xFF8A8F98.toInt()
        val NEXT_UP = WidgetState.NextUp(PPL, "pull", "Pull Day", streakWeeks = 3)
        val TRAINING = WidgetState.Training("Push Day", NOW - 42.minutes - 10.seconds, setsDone = 7, restEndsAt = null)
        val RESTING = TRAINING.copy(restEndsAt = NOW + 95.seconds)
    }
}
