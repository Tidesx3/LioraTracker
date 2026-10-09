package app.liora.android

import android.app.Application
import app.liora.android.di.ProcessScope
import app.liora.android.di.appModule
import app.liora.android.health.HealthConnectActivityScreens
import app.liora.android.health.HealthConnectScreensFactory
import app.liora.android.health.HealthTriggers
import app.liora.android.sync.CloudConsentFactory
import app.liora.android.sync.DriveConsent
import app.liora.android.sync.SyncTriggers
import app.liora.android.sync.WorkSyncScheduler
import app.liora.android.workout.RestAlarmScheduler
import app.liora.android.workout.WorkoutNotifications
import app.liora.android.workout.WorkoutNotifier
import app.liora.core.data.AppStartup
import app.liora.core.data.dataModule
import app.liora.core.data.health.HealthStore
import app.liora.core.data.sync.SyncScheduler
import app.liora.core.data.sync.SyncStores
import app.liora.core.database.androidDatabaseModule
import app.liora.feature.body.bodyModule
import app.liora.feature.exercises.exercisesModule
import app.liora.feature.history.historyModule
import app.liora.feature.logger.loggerModule
import app.liora.feature.progress.progressModule
import app.liora.feature.settings.settingsModule
import app.liora.feature.train.trainModule
import app.liora.feature.walkthrough.walkthroughModule
import app.liora.integration.healthconnect.HealthConnectStore
import app.liora.sync.gdrive.GoogleDriveAccess
import kotlinx.coroutines.launch
import org.koin.android.ext.android.get
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.core.module.Module
import org.koin.dsl.bind
import org.koin.dsl.module

open class LioraApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@LioraApplication)
            modules(
                databaseModules() + syncModules() + healthModules() +
                    listOf(
                        appModule,
                        dataModule,
                        trainModule,
                        historyModule,
                        loggerModule,
                        exercisesModule,
                        progressModule,
                        bodyModule,
                        settingsModule,
                        walkthroughModule,
                    ),
            )
        }
        val processScope = get<ProcessScope>()
        processScope.launch { get<AppStartup>().run() }
        // The workout notification and the end-of-rest alarm follow the database for as long as the
        // process lives, whichever screen (if any) is showing.
        WorkoutNotifications.createChannels(this)
        get<WorkoutNotifier>().start(processScope)
        get<RestAlarmScheduler>().start(processScope)
        get<SyncTriggers>().start(processScope)
        get<HealthTriggers>().start(processScope)
    }

    /** Where the database lives. Tests swap in an in-memory database. */
    protected open fun databaseModules(): List<Module> = listOf(androidDatabaseModule)

    /** Syncing to Google Drive in the background. Tests swap in a store in memory. */
    protected open fun syncModules(): List<Module> =
        listOf(
            module {
                single<SyncScheduler> { WorkSyncScheduler(androidContext()) }
                single { GoogleDriveAccess(androidContext()) } bind SyncStores::class
                single { CloudConsentFactory { activity -> DriveConsent(activity, get()) } }
            },
        )

    /** Health Connect on this phone. Tests swap in one in memory. */
    protected open fun healthModules(): List<Module> =
        listOf(
            module {
                single<HealthStore> { HealthConnectStore(androidContext()) }
                single { HealthConnectScreensFactory { activity -> HealthConnectActivityScreens(activity) } }
            },
        )
}
