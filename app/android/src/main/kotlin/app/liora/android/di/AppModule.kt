package app.liora.android.di

import android.content.Context
import app.liora.android.backup.AndroidExportFiles
import app.liora.android.health.HealthTriggers
import app.liora.android.importing.SharedRoutines
import app.liora.android.photos.AndroidPhotoStorage
import app.liora.android.sync.SyncTriggers
import app.liora.android.ui.LioraAppViewModel
import app.liora.android.update.UpdateTriggers
import app.liora.android.widget.WidgetStates
import app.liora.android.widget.WidgetUpdater
import app.liora.android.workout.AndroidRestSoundPlayer
import app.liora.android.workout.RestAlarmScheduler
import app.liora.android.workout.WorkoutForeground
import app.liora.android.workout.WorkoutNotifier
import app.liora.core.data.backup.ExportFiles
import app.liora.core.data.body.PhotoStorage
import app.liora.core.data.seed.ExerciseSeedSource
import app.liora.core.data.workout.RestSoundPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.withContext
import org.koin.android.ext.koin.androidApplication
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module
import org.koin.dsl.onClose

val appModule =
    module {
        // Stopping Koin (as every UI test does) cancels it, so no work outlives the test that started it.
        single { ProcessScope() } onClose { it?.cancel() }
        viewModelOf(::LioraAppViewModel)
        single<ExerciseSeedSource> { assetSeedSource(androidContext()) }
        single<PhotoStorage> { AndroidPhotoStorage(androidContext()) }
        single<ExportFiles> { AndroidExportFiles(androidContext()) }
        single { WorkoutNotifier(androidContext(), get(), get(), get(), get(), get()) }
        single { RestAlarmScheduler(androidContext(), get(), get()) }
        single { WorkoutForeground(androidApplication(), get()) }
        single<RestSoundPlayer> { AndroidRestSoundPlayer(androidContext()) }
        single { SyncTriggers(get(), get()) }
        single { HealthTriggers(get(), get()) }
        single { WidgetStates(get(), get(), get(), get(), get(), get()) }
        single { WidgetUpdater(androidContext(), get()) }
        single { UpdateTriggers(get()) }
        single { SharedRoutines(androidContext()) }
    }

/** The catalog built by `tools/seed/build-seed.mjs`, shipped in `assets/seed/`. */
private fun assetSeedSource(context: Context) =
    ExerciseSeedSource {
        withContext(Dispatchers.IO) {
            context.assets
                .open("seed/exercises.json")
                .bufferedReader()
                .use { it.readText() }
        }
    }

/** Work that lives as long as the process rather than a screen: the workout notification, the rest alarm. */
class ProcessScope : CoroutineScope by CoroutineScope(SupervisorJob() + Dispatchers.Default)
