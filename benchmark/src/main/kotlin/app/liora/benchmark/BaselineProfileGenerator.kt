package app.liora.benchmark

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
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

/**
 * Collects the baseline profile the release build ships with: the code these journeys run gets compiled
 * ahead, at install, instead of being interpreted the first times. `./gradlew :app:android:generateBaselineProfile`
 * runs it on the phone and writes the profile to `app/android/src/release/generated/baselineProfiles`.
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule
    val rule = BaselineProfileRule()

    @Before
    fun prepare() = Liora.prepare()

    /** From a tap on the icon to the Train tab; also the startup profile, which puts that code first in the dex. */
    @Test
    fun startup() = rule.collect(Liora.PACKAGE, includeInStartupProfile = true) { startOnTrain() }

    /** Where the time goes: every tab scrolled, a workout and an exercise's progress opened, and sets logged. */
    @Test
    fun journeys() =
        rule.collect(Liora.PACKAGE, filterPredicate = { BENCHMARK_CODE !in it }) {
            // Ends the workout the last pass started.
            Liora.prepare(device)
            startOnTrain()
            device.fling(Direction.DOWN, 1)
            for (tab in listOf(Tab.History, Tab.Progress, Tab.Exercises)) {
                device.openTab(tab)
                device.fling(Direction.DOWN, 2)
                device.fling(Direction.UP, 2)
            }
            device.openTab(Tab.History)
            device.openFirst(HISTORY_WORKOUT)
            device.pressBack()
            device.openTab(Tab.Progress)
            device.scrollTo(PROGRESS_BEST)
            device.openFirst(PROGRESS_BEST, shown = By.res(EXERCISE_CHART))
            device.pressBack()
            startPushDay()
            repeat(SETS) { device.tickNextSet() }
        }

    private companion object {
        /** The benchmark build's own code (made-up training), which the release build doesn't have. */
        const val BENCHMARK_CODE = "app/liora/android/benchmark/"
        const val SETS = 4
    }
}
