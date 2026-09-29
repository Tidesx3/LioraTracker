package app.liora.android

import android.app.Application
import app.liora.android.di.appModule
import app.liora.core.data.dataModule
import app.liora.feature.logger.loggerModule
import app.liora.feature.train.trainModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class LioraApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@LioraApplication)
            modules(appModule, dataModule, trainModule, loggerModule)
        }
    }
}
