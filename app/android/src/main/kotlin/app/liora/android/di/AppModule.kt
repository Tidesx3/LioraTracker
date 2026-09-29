package app.liora.android.di

import android.content.Context
import app.liora.android.ui.LioraAppViewModel
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
