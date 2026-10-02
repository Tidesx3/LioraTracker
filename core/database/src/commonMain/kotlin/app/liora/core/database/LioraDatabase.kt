package app.liora.core.database

import androidx.room.AutoMigration
import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import androidx.room.TypeConverters
import androidx.room.immediateTransaction
import androidx.room.useWriterConnection
import androidx.sqlite.SQLiteDriver
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import app.liora.core.database.dao.ExerciseDao
import app.liora.core.database.dao.GymProfileDao
import app.liora.core.database.dao.LocalMetaDao
import app.liora.core.database.dao.MeasurementDao
import app.liora.core.database.dao.PreferenceDao
import app.liora.core.database.dao.ProgressPhotoDao
import app.liora.core.database.dao.RoutineDao
import app.liora.core.database.dao.WorkoutDao
import app.liora.core.database.model.ExerciseEntity
import app.liora.core.database.model.ExerciseNameEntity
import app.liora.core.database.model.ExerciseSettingsEntity
import app.liora.core.database.model.GymProfileEntity
import app.liora.core.database.model.LocalMetaEntity
import app.liora.core.database.model.MeasurementEntity
import app.liora.core.database.model.PreferenceEntity
import app.liora.core.database.model.ProgressPhotoEntity
import app.liora.core.database.model.RoutineEntity
import app.liora.core.database.model.RoutineExerciseEntity
import app.liora.core.database.model.RoutineFolderEntity
import app.liora.core.database.model.RoutineSetEntity
import app.liora.core.database.model.WorkoutEntity
import app.liora.core.database.model.WorkoutExerciseEntity
import app.liora.core.database.model.WorkoutSetEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO

@Database(
    entities = [
        ExerciseEntity::class,
        ExerciseNameEntity::class,
        ExerciseSettingsEntity::class,
        RoutineFolderEntity::class,
        RoutineEntity::class,
        RoutineExerciseEntity::class,
        RoutineSetEntity::class,
        WorkoutEntity::class,
        WorkoutExerciseEntity::class,
        WorkoutSetEntity::class,
        MeasurementEntity::class,
        ProgressPhotoEntity::class,
        PreferenceEntity::class,
        LocalMetaEntity::class,
        GymProfileEntity::class,
    ],
    version = 3,
    autoMigrations = [
        // v2: workout_set.target_reps_min/max, so a workout started from a routine keeps its rep targets.
        AutoMigration(from = 1, to = 2),
        // v3: gym_profile, the gyms whose plates, dumbbells and stacks weights round to.
        AutoMigration(from = 2, to = 3),
    ],
)
@ConstructedBy(LioraDatabaseConstructor::class)
@TypeConverters(Converters::class)
abstract class LioraDatabase : RoomDatabase() {
    abstract fun exerciseDao(): ExerciseDao

    abstract fun workoutDao(): WorkoutDao

    abstract fun routineDao(): RoutineDao

    abstract fun localMetaDao(): LocalMetaDao

    abstract fun measurementDao(): MeasurementDao

    abstract fun progressPhotoDao(): ProgressPhotoDao

    abstract fun preferenceDao(): PreferenceDao

    abstract fun gymProfileDao(): GymProfileDao
}

// Room generates the actual implementations for each platform.
@Suppress("KotlinNoActualForExpect", "NO_ACTUAL_FOR_EXPECT")
expect object LioraDatabaseConstructor : RoomDatabaseConstructor<LioraDatabase> {
    override fun initialize(): LioraDatabase
}

/**
 * Shared configuration for every platform's builder. The bundled driver ships the same modern SQLite
 * on every device, instead of whatever version the OS happens to have.
 */
fun RoomDatabase.Builder<LioraDatabase>.buildDatabase(driver: SQLiteDriver = BundledSQLiteDriver()): LioraDatabase =
    setDriver(driver)
        .setQueryCoroutineContext(Dispatchers.IO)
        .build()

/** Runs [block] in one write transaction; DAO calls inside it share the transaction. */
class TransactionRunner(
    private val database: LioraDatabase,
) {
    suspend fun <R> inTransaction(block: suspend () -> R): R =
        database.useWriterConnection { transactor ->
            transactor.immediateTransaction { block() }
        }
}
