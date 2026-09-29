package app.liora.core.data

import app.liora.core.common.HybridLogicalClock
import app.liora.core.common.IdGenerator
import app.liora.core.data.exercise.ExerciseRepository
import app.liora.core.data.exercise.OfflineExerciseRepository
import app.liora.core.data.routine.OfflineRoutineRepository
import app.liora.core.data.routine.RoutineRepository
import app.liora.core.data.seed.ExerciseCatalogSeeder
import app.liora.core.data.sync.DeviceIdentity
import app.liora.core.data.sync.SyncStamper
import app.liora.core.data.workout.ActiveWorkoutRepository
import app.liora.core.data.workout.LocalRestTimerRepository
import app.liora.core.data.workout.OfflineActiveWorkoutRepository
import app.liora.core.data.workout.OfflineSetLogger
import app.liora.core.data.workout.OfflineWorkoutEditor
import app.liora.core.data.workout.RestTimerRepository
import app.liora.core.data.workout.SetLogger
import app.liora.core.data.workout.WorkoutEditor
import app.liora.core.database.databaseModule
import app.liora.core.domain.RestDefaults
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module
import kotlin.time.Clock

/**
 * Repositories and their plumbing. The app also has to provide the platform database module and an
 * `ExerciseSeedSource`.
 */
val dataModule =
    module {
        includes(databaseModule)
        single<Clock> { Clock.System }
        single { IdGenerator(clock = get()) }
        singleOf(::DeviceIdentity)
        single { HybridLogicalClock(clock = get()) { get<DeviceIdentity>().deviceId() } }
        singleOf(::SyncStamper)
        singleOf(::ExerciseCatalogSeeder)
        singleOf(::AppStartup)
        singleOf(::OfflineExerciseRepository) bind ExerciseRepository::class
        singleOf(::OfflineRoutineRepository) bind RoutineRepository::class
        singleOf(::OfflineActiveWorkoutRepository) bind ActiveWorkoutRepository::class
        singleOf(::OfflineWorkoutEditor) bind WorkoutEditor::class
        singleOf(::OfflineSetLogger) bind SetLogger::class
        singleOf(::LocalRestTimerRepository) bind RestTimerRepository::class
        // Until Settings make them adjustable.
        single { RestDefaults() }
    }

/** Work that runs once per process start, off the main thread. */
class AppStartup(
    private val seeder: ExerciseCatalogSeeder,
) {
    suspend fun run() {
        seeder.seedIfNeeded()
    }
}
