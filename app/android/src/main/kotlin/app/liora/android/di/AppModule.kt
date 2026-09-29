package app.liora.android.di

import app.liora.android.ui.LioraAppViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val appModule =
    module {
        viewModelOf(::LioraAppViewModel)
    }
