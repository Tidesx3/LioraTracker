package app.liora.core.domain

import app.liora.core.model.Measurement
import app.liora.core.model.MeasurementKind
import app.liora.core.model.MeasurementType
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toInstant
import kotlin.time.Instant

/** A measurement's value on one day. */
data class DayValue(
    val day: LocalDate,
    val value: Double,
)

/** How far a measurement moved: from the value on [since] to the latest one. */
data class MeasurementChange(
    val since: LocalDate,
    val amount: Double,
)

/**
 * Body measurements by day, which is how Body charts, lists and edits them. A day measured more than
 * once (morning and evening, or readings from another app) counts with its last value.
 */
object BodyMeasurements {
    /** How far back a change looks: a month sees a trend through the day-to-day noise of a scale. */
    const val CHANGE_WINDOW_DAYS = 30

    /** How far back [around] looks for a value: a week of not stepping on the scale. */
    const val DAYS_AROUND = 7

    /** [type]'s values by day where the user is, oldest first. */
    fun daily(
        measurements: List<Measurement>,
        type: MeasurementType,
        zone: TimeZone,
    ): List<DayValue> =
        measurements
            .filter { it.type == type }
            .sortedBy { it.takenAt }
            .groupBy { TrainingCalendar.dayOf(it.takenAt, zone) }
            .map { (day, onDay) -> DayValue(day, onDay.last().value) }

    /**
     * The change to the latest value from about a month before it: from the last day at least
     * [CHANGE_WINDOW_DAYS] earlier, or from the first day when the history is shorter. Null until there
     * are two days to compare.
     */
    fun change(daily: List<DayValue>): MeasurementChange? {
        val latest = daily.lastOrNull() ?: return null
        val cutoff = latest.day.minus(CHANGE_WINDOW_DAYS, DateTimeUnit.DAY)
        val baseline = daily.lastOrNull { it.day <= cutoff } ?: daily.first()
        return if (baseline.day == latest.day) null else MeasurementChange(baseline.day, latest.value - baseline.value)
    }

    /**
     * The value on [day], or else the latest in the [DAYS_AROUND] days before it: what someone weighed
     * when a photo was taken, say, without weighing in every day.
     */
    fun around(
        daily: List<DayValue>,
        day: LocalDate,
    ): DayValue? {
        val earliest = day.minus(DAYS_AROUND, DateTimeUnit.DAY)
        return daily.lastOrNull { it.day in earliest..day }
    }

    /**
     * When a new measurement on [day] counts as taken: now when that's today, otherwise midday, well
     * clear of the day's edges should the time zone change later.
     */
    fun takenAt(
        day: LocalDate,
        now: Instant,
        zone: TimeZone,
    ): Instant = if (TrainingCalendar.dayOf(now, zone) == day) now else LocalDateTime(day, MIDDAY).toInstant(zone)

    /** Whether [value] (in the stored unit) is one a person could measure, which catches a slipped digit. */
    fun isPlausible(
        type: MeasurementType,
        value: Double,
    ): Boolean =
        value > 0.0 &&
            when (type.kind) {
                MeasurementKind.Mass -> value <= MAX_KILOGRAMS
                MeasurementKind.Percent -> value < MAX_PERCENT
                MeasurementKind.Length -> value <= MAX_METRES
            }

    private val MIDDAY = LocalTime(12, 0)
    private const val MAX_KILOGRAMS = 650.0
    private const val MAX_PERCENT = 100.0
    private const val MAX_METRES = 3.0
}
