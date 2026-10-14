package app.liora.android

import app.liora.core.data.settings.SettingsRepository
import app.liora.core.data.workout.WeekProgressRepository
import app.liora.core.data.workout.WorkoutHistoryRepository
import app.liora.core.domain.TrainingCalendar
import app.liora.core.domain.WeekProgress
import app.liora.core.domain.WeekStreaks
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * The week as of a fixed [today], for tests whose screenshots show the week's card but can't fix the whole
 * clock (the workout bar's time runs on the real one): the card stays the same from day to day. Workouts after
 * [today] don't count yet.
 */
internal class WeekAsOf(
    private val history: WorkoutHistoryRepository,
    private val settings: SettingsRepository,
    private val today: LocalDate,
) : WeekProgressRepository {
    override fun progress(firstDayOfWeek: DayOfWeek): Flow<WeekProgress> =
        combine(history.trainingStarts, settings.settings) { starts, chosen ->
            val zone = TimeZone.currentSystemDefault()
            val days = starts.map { TrainingCalendar.dayOf(it, zone) }.toSet()
            WeekStreaks.of(days, chosen.weeklyGoals, today, firstDayOfWeek)
        }

    override suspend fun setGoal(
        days: Int,
        firstDayOfWeek: DayOfWeek,
    ) {
        val thisWeek = TrainingCalendar.weekStart(today, firstDayOfWeek)
        settings.update { it.copy(weeklyGoals = it.weeklyGoals.withGoal(days, thisWeek)) }
    }

    companion object {
        /** Koin's week as of [today]; load it before a screen first asks for the week. */
        fun module(today: LocalDate): Module =
            module { factory<WeekProgressRepository> { WeekAsOf(get(), get(), today) } }
    }
}
