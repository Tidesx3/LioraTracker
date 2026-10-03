package app.liora.android

import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.SQLiteDriver
import androidx.sqlite.driver.AndroidSQLiteDriver
import app.liora.core.data.workout.RestSoundPlayer
import app.liora.core.database.LioraDatabase
import app.liora.core.domain.RestSound
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.imageDecoderEnabled
import coil3.serviceLoaderEnabled
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.loadKoinModules
import org.koin.core.module.Module
import org.koin.dsl.bind
import org.koin.dsl.module
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.coroutines.EmptyCoroutineContext

/**
 * The real app with an in-memory database on the framework SQLite driver: Robolectric provides
 * framework SQLite for the host OS, while the bundled driver only ships Android binaries. Image loading
 * has no network fetcher, so tests never hit the internet, and it decodes on the main thread, which
 * Compose's idling waits for, so local photos are in the screenshots. Rest sounds aren't played, only
 * noted ([RecordingRestSoundPlayer]).
 */
class TestLioraApplication : LioraApplication() {
    override fun onCreate() {
        super.onCreate()
        SingletonImageLoader.setUnsafe(
            ImageLoader
                .Builder(this)
                .serviceLoaderEnabled(false)
                .coroutineContext(EmptyCoroutineContext)
                // Robolectric's native ImageDecoder doesn't run on every host OS (not on Windows); BitmapFactory does.
                .imageDecoderEnabled(false)
                .build(),
        )
        loadKoinModules(module { single { RecordingRestSoundPlayer() } bind RestSoundPlayer::class })
    }

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

/** Notes the rest sounds asked for instead of playing them. */
class RecordingRestSoundPlayer : RestSoundPlayer {
    val played: MutableList<RestSound> = CopyOnWriteArrayList()

    override suspend fun play(sound: RestSound) {
        played += sound
    }
}
