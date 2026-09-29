package app.liora.core.data

import app.liora.core.common.IdGenerator
import org.koin.dsl.module
import kotlin.time.Clock

val dataModule =
    module {
        single<Clock> { Clock.System }
        single { IdGenerator(clock = get()) }
        single<ActiveWorkoutRepository> { InMemoryActiveWorkoutRepository(ids = get(), clock = get()) }
    }
