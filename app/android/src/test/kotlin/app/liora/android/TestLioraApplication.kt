package app.liora.android

import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.SQLiteDriver
import androidx.sqlite.driver.AndroidSQLiteDriver
import app.liora.android.health.HealthConnectScreensFactory
import app.liora.android.sync.CloudConsentFactory
import app.liora.core.data.body.BodyReading
import app.liora.core.data.health.HealthAccess
import app.liora.core.data.health.HealthAvailability
import app.liora.core.data.health.HealthReadings
import app.liora.core.data.health.HealthSession
import app.liora.core.data.health.HealthStore
import app.liora.core.data.sync.PulledBatches
import app.liora.core.data.sync.SyncBackend
import app.liora.core.data.sync.SyncScheduler
import app.liora.core.data.sync.SyncStores
import app.liora.core.data.workout.RestSoundPlayer
import app.liora.core.database.LioraDatabase
import app.liora.core.domain.RestSound
import app.liora.core.ui.CloudConnection
import app.liora.core.ui.CloudConsent
import app.liora.core.ui.HealthConnectScreens
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.imageDecoderEnabled
import coil3.serviceLoaderEnabled
import kotlinx.coroutines.flow.MutableStateFlow
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.loadKoinModules
import org.koin.core.module.Module
import org.koin.dsl.bind
import org.koin.dsl.module
import java.util.concurrent.ConcurrentHashMap
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

    override fun syncModules(): List<Module> =
        listOf(
            module {
                single { TestSyncScheduler() } bind SyncScheduler::class
                single { TestSyncStores() } bind SyncStores::class
                // Google's consent screen can't run here: access is granted at once.
                single { CloudConsentFactory { CloudConsent { CloudConnection.Connected(TEST_ACCOUNT) } } }
            },
        )

    override fun healthModules(): List<Module> =
        listOf(
            module {
                single { TestHealthStore() } bind HealthStore::class
                // Health Connect's permission screen can't run here: everything is allowed at once.
                single {
                    HealthConnectScreensFactory {
                        val store = get<TestHealthStore>()
                        object : HealthConnectScreens {
                            override suspend fun requestPermissions(): Boolean {
                                store.access = TestHealthStore.ALLOWED
                                return true
                            }

                            override fun openSettings() = Unit

                            override fun install() = Unit
                        }
                    }
                }
            },
        )

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

/** Syncs asked for are counted instead of handed to WorkManager. */
class TestSyncScheduler : SyncScheduler {
    override val running = MutableStateFlow(false)
    var started = false
    var syncsAsked = 0

    override fun start() {
        started = true
    }

    override fun syncNow() {
        syncsAsked++
    }

    override fun stop() {
        started = false
    }
}

/** The Google account tests connect sync to. */
const val TEST_ACCOUNT = "lifter@example.com"

/** A store in memory in place of Google Drive; [failure] is thrown by the next syncs while it's set. */
class TestSyncStores : SyncStores {
    val batches: MutableList<ByteArray> = CopyOnWriteArrayList()
    val forgotten: MutableList<String> = CopyOnWriteArrayList()

    @Volatile
    var failure: Exception? = null

    override fun open(account: String): SyncBackend =
        object : SyncBackend {
            override suspend fun push(
                deviceId: String,
                batch: ByteArray,
            ) {
                batches += batch
            }

            override suspend fun pull(
                deviceId: String,
                cursor: String?,
            ): PulledBatches {
                failure?.let { throw it }
                return PulledBatches(emptyList(), cursor)
            }

            override suspend fun putBlob(
                name: String,
                bytes: ByteArray,
            ) = Unit

            override suspend fun getBlob(name: String): ByteArray? = null
        }

    override suspend fun forget(account: String) {
        forgotten += account
    }
}

/** Health Connect in memory: there but not allowed until its permission screen is "shown". */
class TestHealthStore : HealthStore {
    @Volatile
    var access = HealthAccess(HealthAvailability.Available)
    val sessions: MutableMap<String, HealthSession> = ConcurrentHashMap()

    @Volatile
    var readings: List<BodyReading> = emptyList()

    override suspend fun access() = access

    override suspend fun writeSessions(sessions: List<HealthSession>) {
        sessions.forEach { this.sessions[it.id] = it }
    }

    override suspend fun deleteSessions(ids: List<String>) {
        ids.forEach { sessions.remove(it) }
    }

    override suspend fun readings(cursor: String?): HealthReadings =
        HealthReadings(readings.also { readings = emptyList() }, "read")

    override suspend fun revoke() {
        access = HealthAccess(HealthAvailability.Available)
    }

    companion object {
        val ALLOWED = HealthAccess(HealthAvailability.Available, writeWorkouts = true, readBody = true)
    }
}
