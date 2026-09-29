package app.liora.core.database

import androidx.room.RoomDatabase
import androidx.sqlite.SQLiteDriver
import org.koin.dsl.module

/**
 * DAOs and transactions. The platform supplies a `RoomDatabase.Builder<LioraDatabase>` and an
 * [SQLiteDriver], e.g. [androidDatabaseModule] on Android.
 */
val databaseModule =
    module {
        single { get<RoomDatabase.Builder<LioraDatabase>>().buildDatabase(get<SQLiteDriver>()) }
        single { TransactionRunner(get()) }
        single { get<LioraDatabase>().exerciseDao() }
        single { get<LioraDatabase>().workoutDao() }
        single { get<LioraDatabase>().localMetaDao() }
    }
