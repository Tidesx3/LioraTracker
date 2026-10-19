package app.liora.android.benchmark

import app.liora.android.TestLioraApplication
import app.liora.core.data.backup.BackupRepository
import app.liora.core.data.routine.RotationRepository
import app.liora.core.data.routine.RoutineRepository
import app.liora.core.data.settings.SettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.koin.core.context.stopKoin
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * The benchmark builds' made-up training (src/benchmark), which the benchmarks need on the phone: it has to go in
 * through the real restore, as `BenchmarkDataActivity` puts it in, or every benchmark fails there.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = TestLioraApplication::class)
class BenchmarkTrainingTest {
    private val koin get() = GlobalContext.get()

    private val cache get() = RuntimeEnvironment.getApplication().cacheDir

    @After
    fun tearDown() = stopKoin()

    @Test
    fun aYearOfTrainingRestoresTheSameEveryTime() =
        runBlocking {
            // A year back from a Monday: four days a week, five weeks with two, and core and a run once a month.
            assertEquals(211, training().workouts.size)
            val backups = koin.get<BackupRepository>()
            assertTrue(backups.restoreFile(training(), cache) > 0)
            // One seed, so the same rows: restoring it again changes nothing.
            assertEquals(0, backups.restoreFile(training(), cache))

            val routines = koin.get<RoutineRepository>().routines.first()
            assertEquals(
                setOf("Push", "Pull", "Legs", "Upper", "Lower", "Core and a run"),
                routines.map { it.name }.toSet(),
            )
            // The logger benchmark starts the push day by its id.
            assertEquals("Push", routines.single { it.id == BenchmarkTraining.PUSH_ROUTINE_ID }.name)
            // Push was trained last, on Saturday, so the Train tab heads with Pull.
            val rotations = koin.get<RotationRepository>()
            val turn = rotations.turns.first().single()
            assertEquals("Pull", routines.single { it.id == turn.nextRoutineId }.name)
            val settings = koin.get<SettingsRepository>().current()
            assertEquals(4, settings.weeklyGoals.current)
        }

    private fun training() = BenchmarkTraining(LocalDate.of(2026, 10, 19), ZoneOffset.UTC).backup()
}
