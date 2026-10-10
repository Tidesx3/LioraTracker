package app.liora.core.data

import app.liora.core.common.Hlc
import app.liora.core.common.HybridLogicalClock
import app.liora.core.common.IdGenerator
import app.liora.core.data.backup.BackupRepository
import app.liora.core.data.backup.CsvExport
import app.liora.core.data.backup.OfflineBackupRepository
import app.liora.core.data.backup.OfflineCsvExport
import app.liora.core.data.backup.SyncedTables
import app.liora.core.data.body.BodyRepository
import app.liora.core.data.body.OfflineBodyRepository
import app.liora.core.data.body.OfflineProgressPhotoRepository
import app.liora.core.data.body.ProgressPhotoRepository
import app.liora.core.data.exercise.ExerciseRepository
import app.liora.core.data.exercise.LegacyNotes
import app.liora.core.data.exercise.OfflineExerciseRepository
import app.liora.core.data.gym.GymProfileRepository
import app.liora.core.data.gym.OfflineGymProfileRepository
import app.liora.core.data.health.HealthRepository
import app.liora.core.data.health.OfflineHealthRepository
import app.liora.core.data.importing.ImportRepository
import app.liora.core.data.importing.OfflineImportRepository
import app.liora.core.data.progression.OfflineProgressionRepository
import app.liora.core.data.progression.ProgressionRepository
import app.liora.core.data.routine.OfflineRotationRepository
import app.liora.core.data.routine.OfflineRoutineRepository
import app.liora.core.data.routine.RotationRepository
import app.liora.core.data.routine.RoutineRepository
import app.liora.core.data.seed.ExerciseCatalogSeeder
import app.liora.core.data.settings.OfflineSettingsRepository
import app.liora.core.data.settings.SettingsRepository
import app.liora.core.data.sync.DeviceIdentity
import app.liora.core.data.sync.NEWEST_MERGED_HLC
import app.liora.core.data.sync.OfflineSyncRepository
import app.liora.core.data.sync.SyncEngine
import app.liora.core.data.sync.SyncRepository
import app.liora.core.data.sync.SyncStamper
import app.liora.core.data.update.LocalUpdateRepository
import app.liora.core.data.update.UpdateRepository
import app.liora.core.data.walkthrough.LocalWalkthroughRepository
import app.liora.core.data.walkthrough.WalkthroughRepository
import app.liora.core.data.workout.ActiveWorkoutRepository
import app.liora.core.data.workout.LocalRestTimerRepository
import app.liora.core.data.workout.OfflineActiveWorkoutRepository
import app.liora.core.data.workout.OfflineWorkoutHistoryRepository
import app.liora.core.data.workout.RestTimerRepository
import app.liora.core.data.workout.SetLogger
import app.liora.core.data.workout.WorkoutHistoryRepository
import app.liora.core.database.dao.LocalMetaDao
import app.liora.core.database.databaseModule
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module
import kotlin.time.Clock

/**
 * Repositories and their plumbing. The app also has to provide the platform database module, an
 * `ExerciseSeedSource`, a `PhotoStorage`, `ExportFiles`, for sync a `SyncScheduler` and `SyncStores`, for updates a
 * `ReleaseSource` and an `UpdateInstaller`, and a
 * `HealthStore`.
 */
val dataModule =
    module {
        includes(databaseModule)
        single<Clock> { Clock.System }
        single { IdGenerator(clock = get()) }
        singleOf(::DeviceIdentity)
        single {
            val meta = get<LocalMetaDao>()
            HybridLogicalClock(clock = get(), seen = { meta.get(NEWEST_MERGED_HLC)?.let(Hlc::parse) }) {
                get<DeviceIdentity>().deviceId()
            }
        }
        singleOf(::SyncStamper)
        singleOf(::ExerciseCatalogSeeder)
        singleOf(::LegacyNotes)
        singleOf(::AppStartup)
        singleOf(::OfflineExerciseRepository) bind ExerciseRepository::class
        singleOf(::OfflineRoutineRepository) bind RoutineRepository::class
        singleOf(::OfflineRotationRepository) bind RotationRepository::class
        singleOf(::OfflineSettingsRepository) bind SettingsRepository::class
        singleOf(::OfflineGymProfileRepository) bind GymProfileRepository::class
        single<ActiveWorkoutRepository> {
            val settings = get<SettingsRepository>()
            OfflineActiveWorkoutRepository(get(), get(), get(), get(), get(), get(), get(), get()) {
                settings.current().rest
            }
        }
        singleOf(::OfflineWorkoutHistoryRepository) bind WorkoutHistoryRepository::class
        singleOf(::OfflineBodyRepository) bind BodyRepository::class
        singleOf(::OfflineProgressPhotoRepository) bind ProgressPhotoRepository::class
        singleOf(::SyncedTables)
        singleOf(::OfflineBackupRepository) bind BackupRepository::class
        singleOf(::SyncEngine)
        singleOf(::OfflineSyncRepository) bind SyncRepository::class
        singleOf(::OfflineHealthRepository) bind HealthRepository::class
        singleOf(::OfflineCsvExport) bind CsvExport::class
        singleOf(::OfflineImportRepository) bind ImportRepository::class
        // The workout in progress has one set logger, shared by the logger and the notification.
        single<SetLogger> { get<ActiveWorkoutRepository>().sets }
        singleOf(::LocalRestTimerRepository) bind RestTimerRepository::class
        singleOf(::LocalWalkthroughRepository) bind WalkthroughRepository::class
        singleOf(::OfflineProgressionRepository) bind ProgressionRepository::class
        singleOf(::LocalUpdateRepository) bind UpdateRepository::class
    }

/** Work that runs once per process start, off the main thread. */
class AppStartup(
    private val walkthrough: WalkthroughRepository,
    private val seeder: ExerciseCatalogSeeder,
    private val legacyNotes: LegacyNotes,
) {
    suspend fun run() {
        // Before the first seed, which is how it tells a new install from one that ran an earlier Liora.
        walkthrough.settle()
        seeder.seedIfNeeded()
        legacyNotes.move()
    }
}
