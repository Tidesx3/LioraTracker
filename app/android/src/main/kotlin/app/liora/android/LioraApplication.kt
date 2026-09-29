package app.liora.android

import android.app.Application
import app.liora.android.di.appModule
import app.liora.android.workout.RestAlarmScheduler
import app.liora.android.workout.WorkoutNotifications
import app.liora.android.workout.WorkoutNotifier
import app.liora.core.data.AppStartup
import app.liora.core.data.dataModule
import app.liora.core.database.androidDatabaseModule
import app.liora.feature.exercises.exercisesModule
import app.liora.feature.logger.loggerModule
import app.liora.feature.train.trainModule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.android.ext.android.get
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.core.module.Module

open class LioraApplication : Application() {
    /** Lives as long as the process; for work that must not be tied to a screen. */
    private val processScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@LioraApplication)
            modules(databaseModules() + listOf(appModule, dataModule, trainModule, loggerModule, exercisesModule))
        }
        processScope.launch { get<AppStartup>().run() }
        // The workout notification and the end-of-rest alarm follow the database for as long as the
        // process lives, whichever screen (if any) is showing.
        WorkoutNotifications.createChannels(this)
        get<WorkoutNotifier>().start(processScope)
        get<RestAlarmScheduler>().start(processScope)
    }

    /** Where the database lives. Tests swap in an in-memory database. */
    protected open fun databaseModules(): List<Module> = listOf(androidDatabaseModule)
}
