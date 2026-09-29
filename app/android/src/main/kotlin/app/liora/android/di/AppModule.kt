package app.liora.android.di

import android.content.Context
import app.liora.android.ui.LioraAppViewModel
import app.liora.android.workout.RestAlarmScheduler
import app.liora.android.workout.WorkoutNotifier
import app.liora.core.data.seed.ExerciseSeedSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val appModule =
    module {
        viewModelOf(::LioraAppViewModel)
        single<ExerciseSeedSource> { assetSeedSource(androidContext()) }
        single { WorkoutNotifier(androidContext(), get(), get(), get(), get()) }
        single { RestAlarmScheduler(androidContext(), get(), get()) }
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
