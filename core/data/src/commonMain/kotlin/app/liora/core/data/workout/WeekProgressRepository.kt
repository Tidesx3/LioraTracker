package app.liora.core.data.workout

import app.liora.core.data.settings.SettingsRepository
import app.liora.core.domain.TrainingCalendar
import app.liora.core.domain.WeekProgress
import app.liora.core.domain.WeekStreaks
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.TimeZone
import kotlin.time.Clock

/**
 * This week against the weekly goal, and the streak, for the Train tab, the summary, the widget and Settings.
 * Weeks start on [DayOfWeek]s the locale gives, so callers pass it.
 */
interface WeekProgressRepository {
    /** This week so far and the streak, as of now; again as workouts are trained or the goal changes. */
    fun progress(firstDayOfWeek: DayOfWeek): Flow<WeekProgress>

    /** Aims for [days] training days a week from this week on; the weeks before keep the goal they had. */
    suspend fun setGoal(
        days: Int,
        firstDayOfWeek: DayOfWeek,
    )
}

internal class OfflineWeekProgressRepository(
    private val history: WorkoutHistoryRepository,
    private val settings: SettingsRepository,
    private val clock: Clock,
) : WeekProgressRepository {
    override fun progress(firstDayOfWeek: DayOfWeek): Flow<WeekProgress> =
        combine(
            history.trainingStarts,
            settings.settings.map { it.weeklyGoals }.distinctUntilChanged(),
        ) { starts, goals ->
            val zone = TimeZone.currentSystemDefault()
            val days = starts.map { TrainingCalendar.dayOf(it, zone) }.toSet()
            WeekStreaks.of(days, goals, TrainingCalendar.dayOf(clock.now(), zone), firstDayOfWeek)
        }.distinctUntilChanged()

    override suspend fun setGoal(
        days: Int,
        firstDayOfWeek: DayOfWeek,
    ) {
        val today = TrainingCalendar.dayOf(clock.now(), TimeZone.currentSystemDefault())
        val thisWeek = TrainingCalendar.weekStart(today, firstDayOfWeek)
        settings.update { it.copy(weeklyGoals = it.weeklyGoals.withGoal(days, thisWeek)) }
    }
}
