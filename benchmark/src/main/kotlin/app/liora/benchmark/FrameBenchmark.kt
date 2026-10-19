package app.liora.benchmark

import android.os.SystemClock
import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import app.liora.benchmark.Liora.EXERCISE_CHART
import app.liora.benchmark.Liora.HISTORY_WORKOUT
import app.liora.benchmark.Liora.PROGRESS_BEST
import app.liora.benchmark.Liora.Tab
import app.liora.benchmark.Liora.fling
import app.liora.benchmark.Liora.openFirst
import app.liora.benchmark.Liora.openTab
import app.liora.benchmark.Liora.scrollTo
import app.liora.benchmark.Liora.startOnTrain
import app.liora.benchmark.Liora.startPushDay
import app.liora.benchmark.Liora.tickNextSet
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * Smoothness where it shows: the frames while scrolling each tab, switching tabs, opening a workout and an
 * exercise's progress (a card growing into its screen), and logging sets (the tick's pop, the row filling,
 * the rest timer rising). Each with Liquid Glass on and off, so what the glass costs shows. Measured as the
 * release build runs, with the baseline profile.
 */
@RunWith(Parameterized::class)
class FrameBenchmark(
    private val glass: Boolean,
) {
    @get:Rule
    val benchmark = MacrobenchmarkRule()

    @Before
    fun prepare() = Liora.prepare(glass = glass)

    @Test
    fun scrollTrain() = scroll(Tab.Train)

    @Test
    fun scrollHistory() = scroll(Tab.History)

    @Test
    fun scrollProgress() = scroll(Tab.Progress)

    @Test
    fun scrollExercises() = scroll(Tab.Exercises)

    @Test
    fun switchTabs() =
        measure(setup = { startOnTrain() }) {
            for (tab in listOf(Tab.History, Tab.Progress, Tab.Exercises, Tab.Train)) device.openTab(tab)
        }

    @Test
    fun openWorkout() =
        measure(setup = {
            startOnTrain()
            device.openTab(Tab.History)
        }) {
            device.openFirst(HISTORY_WORKOUT)
            device.pressBack()
            device.waitForIdle()
        }

    @Test
    fun openExerciseProgress() =
        measure(setup = {
            startOnTrain()
            device.openTab(Tab.Progress)
            device.scrollTo(PROGRESS_BEST)
        }) {
            device.openFirst(PROGRESS_BEST, shown = By.res(EXERCISE_CHART))
            device.pressBack()
            device.waitForIdle()
        }

    @Test
    fun logSets() =
        measure(setup = {
            // Ends the workout the last pass started.
            Liora.prepare(device, glass)
            startPushDay()
        }) {
            repeat(SETS) {
                device.tickNextSet()
                // Long enough for the pop, the row's fill and the rest timer to play out.
                SystemClock.sleep(SETTLE_MS)
            }
        }

    private fun scroll(tab: Tab) =
        measure(setup = {
            startOnTrain()
            device.openTab(tab)
        }) {
            device.fling(Direction.DOWN, FLINGS)
            device.fling(Direction.UP, FLINGS)
        }

    private fun measure(
        setup: MacrobenchmarkScope.() -> Unit,
        journey: MacrobenchmarkScope.() -> Unit,
    ) = benchmark.measureRepeated(
        packageName = Liora.PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = CompilationMode.Partial(BaselineProfileMode.Require),
        iterations = ITERATIONS,
        setupBlock = {
            killProcess()
            setup()
        },
        measureBlock = journey,
    )

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "glass={0}")
        fun glass() = listOf(true, false)

        private const val ITERATIONS = 5
        private const val FLINGS = 3

        /** The bike, the warm-up and three working sets of the chest press. */
        private const val SETS = 5
        private const val SETTLE_MS = 600L
    }
}
