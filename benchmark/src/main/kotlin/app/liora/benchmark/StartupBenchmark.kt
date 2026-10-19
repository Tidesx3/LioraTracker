package app.liora.benchmark

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import app.liora.benchmark.Liora.startOnTrain
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * A cold start, from a tap on the icon until the Train tab shows the week and the routines (the app reports
 * itself fully drawn then): without any code compiled ahead, as right after an install, and with the baseline
 * profile, as the release build runs once Android has compiled it. The difference is what the profile buys.
 */
@RunWith(Parameterized::class)
class StartupBenchmark(
    private val compilation: CompilationMode,
) {
    @get:Rule
    val benchmark = MacrobenchmarkRule()

    @Before
    fun prepare() = Liora.prepare()

    @Test
    fun coldStart() =
        benchmark.measureRepeated(
            packageName = Liora.PACKAGE,
            metrics = listOf(StartupTimingMetric()),
            compilationMode = compilation,
            startupMode = StartupMode.COLD,
            iterations = 10,
        ) { startOnTrain() }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun compilations() = listOf(CompilationMode.None(), CompilationMode.Partial(BaselineProfileMode.Require))
    }
}
