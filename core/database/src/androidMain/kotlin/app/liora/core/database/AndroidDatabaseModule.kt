package app.liora.core.database

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.SQLiteDriver
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

private const val DATABASE_NAME = "liora.db"

val androidDatabaseModule =
    module {
        single<RoomDatabase.Builder<LioraDatabase>> { androidDatabaseBuilder(androidContext()) }
        single<SQLiteDriver> { BundledSQLiteDriver() }
    }

fun androidDatabaseBuilder(context: Context): RoomDatabase.Builder<LioraDatabase> {
    val appContext = context.applicationContext
    return Room.databaseBuilder<LioraDatabase>(
        context = appContext,
        name = appContext.getDatabasePath(DATABASE_NAME).absolutePath,
    )
}
