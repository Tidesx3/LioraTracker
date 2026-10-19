package app.liora.benchmark

import android.content.Intent
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until

/**
 * The app being measured, and how the benchmarks get around it: the benchmark build of Liora, which installs as
 * its own app beside the real one with a year of made-up training. Screens are found by their test tags, which
 * the app shows UI Automator as resource ids, so the benchmarks work in any language and on either screen of
 * the Fold.
 */
internal object Liora {
    const val PACKAGE = "app.liora.benchmark"

    /** The made-up training's push day (`BenchmarkTraining.PUSH_ROUTINE_ID` in the app is the same). */
    const val PUSH_ROUTINE_ID = "0192f000-0000-7000-8000-00000000b001"

    /** The app's tabs, in their order on the bar and the rail (`TopLevelDestination`). */
    enum class Tab { Train, History, Progress, Exercises }

    /**
     * Gets the app ready to be measured and leaves it stopped: the made-up training restored (only the first
     * time, which takes a while), the walkthrough behind, no workout running, Liquid Glass on or off, and
     * notifications allowed, so Android's prompt never covers a workout (`BenchmarkDataActivity`).
     */
    fun prepare(
        device: UiDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()),
        glass: Boolean = true,
    ) {
        device.executeShellCommand("pm grant $PACKAGE android.permission.POST_NOTIFICATIONS")
        device.executeShellCommand("am start -W -n $PACKAGE/$DATA_ACTIVITY --ez glass $glass")
        check(device.wait(Until.hasObject(By.pkg(PACKAGE).text(READY)), PREPARE_TIMEOUT_MS)) {
            "The benchmark build didn't get ready; is it installed (benchmarkRelease)?"
        }
        device.executeShellCommand("am force-stop $PACKAGE")
    }

    /** Opens the app from the launcher and waits for the Train tab's content, where startup ends. */
    fun MacrobenchmarkScope.startOnTrain() {
        startActivityAndWait()
        device.await(By.res(TRAIN_WEEK))
    }

    /** Starts the push day from the widget's Start, which opens the logger on it. */
    fun MacrobenchmarkScope.startPushDay() {
        startActivityAndWait(
            Intent(START_ROUTINE)
                .setClassName(PACKAGE, MAIN_ACTIVITY)
                .putExtra(EXTRA_ROUTINE_ID, PUSH_ROUTINE_ID),
        )
        device.await(By.res(LOGGER_CHECK))
    }

    fun UiDevice.openTab(tab: Tab) {
        val chrome = findObject(By.res(NAVIGATION_BAR)) ?: await(By.res(NAVIGATION_RAIL))
        chrome.findObjects(By.clickable(true))[tab.ordinal].click()
        // The tab's list, once its content has come in.
        await(By.scrollable(true).hasDescendant(By.clickable(true)))
    }

    /** Flings the screen's list [times] towards [direction], waiting for it to settle each time. */
    fun UiDevice.fling(
        direction: Direction,
        times: Int,
    ) = repeat(times) {
        mainList().fling(direction)
        waitForIdle()
    }

    /** Ticks the next open set off, as the thumb does in the gym. */
    fun UiDevice.tickNextSet() {
        await(By.res(LOGGER_CHECK)).click()
        waitForIdle()
    }

    /** Opens the first item tagged [tag] (a workout, a best), and waits for [shown] on what opens. */
    fun UiDevice.openFirst(
        tag: String,
        shown: BySelector? = null,
    ) {
        await(By.res(tag)).click()
        shown?.let { await(it) }
        waitForIdle()
    }

    /** Scrolls the screen's list down until something tagged [tag] shows. */
    fun UiDevice.scrollTo(tag: String) {
        if (!hasObject(By.res(tag))) mainList().scrollUntil(Direction.DOWN, Until.hasObject(By.res(tag)))
        await(By.res(tag))
    }

    /**
     * The tallest list on screen, its gestures kept clear of the edges: the side edges are Android's back
     * gesture, and the floating tabs at the bottom take drags of their own.
     */
    private fun UiDevice.mainList(): UiObject2 {
        await(By.scrollable(true))
        val list = findObjects(By.scrollable(true)).maxBy { it.visibleBounds.height() }
        val bounds = list.visibleBounds
        list.setGestureMargins(
            displayWidth / SIDE_MARGIN_PARTS,
            bounds.height() / TOP_MARGIN_PARTS,
            displayWidth / SIDE_MARGIN_PARTS,
            bounds.height() / BOTTOM_MARGIN_PARTS,
        )
        return list
    }

    fun UiDevice.await(selector: BySelector): UiObject2 =
        checkNotNull(wait(Until.findObject(selector), WAIT_TIMEOUT_MS)) { "Nothing on screen matches $selector" }

    // The app's test tags (ShellTags, TrainTags, HistoryTags, ProgressTags, ExerciseDetailTags, LoggerTags).
    const val TRAIN_WEEK = "train.week"
    const val HISTORY_WORKOUT = "history.workout"
    const val PROGRESS_BEST = "progress.best"
    const val EXERCISE_CHART = "exercise.chart"
    const val LOGGER_CHECK = "logger.check"
    private const val NAVIGATION_BAR = "navigation_bar"
    private const val NAVIGATION_RAIL = "navigation_rail"

    private const val MAIN_ACTIVITY = "app.liora.android.MainActivity"
    private const val DATA_ACTIVITY = "app.liora.android.benchmark.BenchmarkDataActivity"
    private const val READY = "Ready to be measured"

    // The widget's Start (WidgetIntents).
    private const val START_ROUTINE = "app.liora.action.START_ROUTINE"
    private const val EXTRA_ROUTINE_ID = "routine_id"

    /** The first time, the catalog is seeded and a year of training restored. */
    private const val PREPARE_TIMEOUT_MS = 120_000L
    private const val WAIT_TIMEOUT_MS = 10_000L
    private const val SIDE_MARGIN_PARTS = 5
    private const val TOP_MARGIN_PARTS = 6
    private const val BOTTOM_MARGIN_PARTS = 3
}
