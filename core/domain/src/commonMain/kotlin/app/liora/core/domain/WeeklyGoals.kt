package app.liora.core.domain

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/** A weekly goal of [days] training days, from the week that starts on [from] on. */
data class GoalChange(
    val from: LocalDate,
    val days: Int,
)

/**
 * The weekly goals chosen over time, oldest first. Each holds from its week until the next one, so changing the
 * goal never rewrites the weeks before. Before the first, any week with training counts, as the streak always
 * had it.
 */
data class WeeklyGoals(
    val changes: List<GoalChange> = emptyList(),
) {
    /** Whether a goal was ever chosen. */
    val isSet: Boolean get() = changes.isNotEmpty()

    /** The goal now, in training days a week. */
    val current: Int get() = changes.lastOrNull()?.days ?: ANY

    /** The goal for the week that starts on [weekStart]. */
    fun forWeek(weekStart: LocalDate): Int = changes.lastOrNull { it.from <= weekStart }?.days ?: ANY

    /** A goal of [days] from the week starting [from] on; one chosen earlier the same week is replaced. */
    fun withGoal(
        days: Int,
        from: LocalDate,
    ): WeeklyGoals = WeeklyGoals(changes.filter { it.from < from } + GoalChange(from, days.coerceIn(CHOICES)))

    companion object {
        /** Before any goal: a week with any training. */
        const val ANY = 1

        /** The goals to choose from, in training days a week. */
        val CHOICES: IntRange = 1..7

        /** What the first-launch walkthrough suggests. */
        const val SUGGESTED = 3
    }
}

/** How a week that's over went against its goal. */
enum class WeekResult {
    /** The goal was reached. */
    Met,

    /** Short of the goal, and a rest week kept the streak going. */
    Rested,

    /** Short of the goal, with no rest week in hand: the streak ended. */
    Missed,
}

/**
 * This week against its goal, and the streak: weeks in a row that reached their goal, kept going through a week
 * short of it by a rest week in hand. Worked out from the days trained, never stored, so every device agrees.
 */
data class WeekProgress(
    /** The day it's worked out on. */
    val today: LocalDate,
    /** This week's first day. */
    val weekStart: LocalDate,
    /** The days trained this week so far. */
    val days: Set<LocalDate>,
    /** This week's goal, in training days. */
    val goal: Int,
    /** Whether a goal was ever chosen; before one is, any week with training counts. */
    val goalSet: Boolean,
    /** Weeks in a row that reached their goal, this one once it has. Rested weeks keep it, but don't add to it. */
    val streak: Int,
    /** Rest weeks in hand. */
    val restWeeks: Int,
    /** How each week before this one went, by its first day, from the first week trained. */
    val weeks: Map<LocalDate, WeekResult>,
    /** This week reached the goal and so earned a rest week. */
    val earnedRestWeek: Boolean,
) {
    val done: Int get() = days.size

    val met: Boolean get() = done >= goal

    /** Nothing to show yet: no goal chosen, and no training. */
    val isBlank: Boolean get() = !goalSet && done == 0 && weeks.isEmpty()

    /** How last week went, or null before any training. */
    val lastWeek: WeekResult? get() = weeks[weekStart.minus(DAYS_PER_WEEK, DateTimeUnit.DAY)]

    private companion object {
        const val DAYS_PER_WEEK = 7
    }
}

/**
 * Weekly goals and their streak. A week counts when it has as many training days as its goal (two workouts on a
 * day are one day, so splitting off cardio doesn't count twice). This week adds to the streak once reached and
 * doesn't end it before it's over. Every [EARN_EVERY] weeks reached earn a rest week, at most [MAX_REST_WEEKS] in
 * hand; a week that falls short uses one, and the streak goes on without counting it.
 */
object WeekStreaks {
    const val EARN_EVERY = 6
    const val MAX_REST_WEEKS = 2

    fun of(
        trainingDays: Set<LocalDate>,
        goals: WeeklyGoals,
        today: LocalDate,
        firstDayOfWeek: DayOfWeek,
    ): WeekProgress {
        val thisWeek = TrainingCalendar.weekStart(today, firstDayOfWeek)
        val byWeek = trainingDays.filter { it <= today }.groupBy { TrainingCalendar.weekStart(it, firstDayOfWeek) }
        val run = Run()
        val results = linkedMapOf<LocalDate, WeekResult>()
        var week = byWeek.keys.minOrNull()
        while (week != null && week < thisWeek) {
            results[week] = run.week(met = (byWeek[week]?.size ?: 0) >= goals.forWeek(week))
            week = week.plus(DAYS_PER_WEEK, DateTimeUnit.DAY)
        }
        val days = byWeek[thisWeek].orEmpty().toSet()
        val goal = goals.forWeek(thisWeek)
        val earned = days.size >= goal && run.reached()
        return WeekProgress(
            today = today,
            weekStart = thisWeek,
            days = days,
            goal = goal,
            goalSet = goals.isSet,
            streak = run.streak,
            restWeeks = run.restWeeks,
            weeks = results,
            earnedRestWeek = earned,
        )
    }

    /** The streak as it runs, week by week. */
    private class Run {
        var streak = 0
            private set
        var restWeeks = 0
            private set
        private var sinceRestWeek = 0

        fun week(met: Boolean): WeekResult =
            when {
                met -> {
                    reached()
                    WeekResult.Met
                }

                restWeeks > 0 -> {
                    restWeeks--
                    WeekResult.Rested
                }

                else -> {
                    streak = 0
                    sinceRestWeek = 0
                    WeekResult.Missed
                }
            }

        /** A week reached: one more in the streak; returns whether it earned a rest week. */
        fun reached(): Boolean {
            streak++
            sinceRestWeek++
            if (sinceRestWeek < EARN_EVERY) return false
            sinceRestWeek = 0
            if (restWeeks >= MAX_REST_WEEKS) return false
            restWeeks++
            return true
        }
    }

    private const val DAYS_PER_WEEK = 7
}
