package app.liora.android

import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.SQLiteDriver
import androidx.sqlite.driver.AndroidSQLiteDriver
import app.liora.core.database.LioraDatabase
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * The real app with an in-memory database on the framework SQLite driver: Robolectric provides
 * framework SQLite for the host OS, while the bundled driver only ships Android binaries.
 */
class TestLioraApplication : LioraApplication() {
    override fun databaseModules(): List<Module> =
        listOf(
            module {
                single<RoomDatabase.Builder<LioraDatabase>> {
                    Room.inMemoryDatabaseBuilder<LioraDatabase>(androidContext())
                }
                single<SQLiteDriver> { AndroidSQLiteDriver() }
            },
        )
}
