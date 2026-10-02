package app.liora.core.data

import app.liora.core.common.HybridLogicalClock
import app.liora.core.common.IdGenerator
import app.liora.core.data.backup.BackupRepository
import app.liora.core.data.backup.OfflineBackupRepository
import app.liora.core.data.backup.OfflineWorkoutCsvExport
import app.liora.core.data.backup.WorkoutCsvExport
import app.liora.core.data.body.BodyRepository
import app.liora.core.data.body.OfflineBodyRepository
import app.liora.core.data.body.OfflineProgressPhotoRepository
import app.liora.core.data.body.ProgressPhotoRepository
import app.liora.core.data.exercise.ExerciseRepository
import app.liora.core.data.exercise.OfflineExerciseRepository
import app.liora.core.data.gym.GymProfileRepository
import app.liora.core.data.gym.OfflineGymProfileRepository
import app.liora.core.data.routine.OfflineRoutineRepository
import app.liora.core.data.routine.RoutineRepository
import app.liora.core.data.seed.ExerciseCatalogSeeder
import app.liora.core.data.settings.OfflineSettingsRepository
import app.liora.core.data.settings.SettingsRepository
import app.liora.core.data.sync.DeviceIdentity
import app.liora.core.data.sync.SyncStamper
import app.liora.core.data.workout.ActiveWorkoutRepository
import app.liora.core.data.workout.LocalRestTimerRepository
import app.liora.core.data.workout.OfflineActiveWorkoutRepository
import app.liora.core.data.workout.OfflineWorkoutHistoryRepository
import app.liora.core.data.workout.RestTimerRepository
import app.liora.core.data.workout.SetLogger
import app.liora.core.data.workout.WorkoutHistoryRepository
import app.liora.core.database.databaseModule
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module
import kotlin.time.Clock

/**
 * Repositories and their plumbing. The app also has to provide the platform database module, an
 * `ExerciseSeedSource`, a `PhotoStorage` and `ExportFiles`.
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
        singleOf(::OfflineSettingsRepository) bind SettingsRepository::class
        singleOf(::OfflineGymProfileRepository) bind GymProfileRepository::class
        single<ActiveWorkoutRepository> {
            val settings = get<SettingsRepository>()
            OfflineActiveWorkoutRepository(get(), get(), get(), get(), get(), get(), get()) {
                settings.current().rest
            }
        }
        singleOf(::OfflineWorkoutHistoryRepository) bind WorkoutHistoryRepository::class
        singleOf(::OfflineBodyRepository) bind BodyRepository::class
        singleOf(::OfflineProgressPhotoRepository) bind ProgressPhotoRepository::class
        singleOf(::OfflineBackupRepository) bind BackupRepository::class
        singleOf(::OfflineWorkoutCsvExport) bind WorkoutCsvExport::class
        // The workout in progress has one set logger, shared by the logger and the notification.
        single<SetLogger> { get<ActiveWorkoutRepository>().sets }
        singleOf(::LocalRestTimerRepository) bind RestTimerRepository::class
    }

/** Work that runs once per process start, off the main thread. */
class AppStartup(
    private val seeder: ExerciseCatalogSeeder,
) {
    suspend fun run() {
        seeder.seedIfNeeded()
    }
}
