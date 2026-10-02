package app.liora.core.domain

import app.liora.core.model.Measurement
import app.liora.core.model.MeasurementType
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

/** Measurements by day, how far they moved, and when a new one counts as taken. */
class BodyMeasurementsTest {
    private val vienna = TimeZone.of("Europe/Vienna")

    @Test
    fun aDayMeasuredTwiceCountsWithItsLastValue() {
        val measurements =
            listOf(
                weight(at(2026, 9, 1, 7, 0), 82.4),
                weight(at(2026, 9, 1, 21, 30), 83.1),
                weight(at(2026, 9, 2, 7, 0), 82.2),
                Measurement("w", MeasurementType.Waist, at(2026, 9, 1, 7, 0), 0.86),
            )
        assertEquals(
            listOf(DayValue(LocalDate(2026, 9, 1), 83.1), DayValue(LocalDate(2026, 9, 2), 82.2)),
            BodyMeasurements.daily(measurements.shuffled(), MeasurementType.Bodyweight, vienna),
        )
    }

    @Test
    fun daysFollowTheUsersCalendar() {
        // 23:30 in Vienna is already the next day in UTC.
        val late = listOf(weight(at(2026, 9, 1, 23, 30), 82.0))
        assertEquals(LocalDate(2026, 9, 1), BodyMeasurements.daily(late, MeasurementType.Bodyweight, vienna)[0].day)
    }

    @Test
    fun changeLooksBackAMonth() {
        val daily =
            listOf(
                day(2026, 7, 20, 85.0),
                day(2026, 8, 1, 84.0),
                day(2026, 8, 20, 83.0),
                day(2026, 9, 1, 82.5),
            )
        // 1 September looks back to 2 August or earlier: 1 August.
        assertEquals(MeasurementChange(LocalDate(2026, 8, 1), -1.5), BodyMeasurements.change(daily))
    }

    @Test
    fun aShortHistoryComparesWithItsFirstDay() {
        val daily = listOf(day(2026, 8, 20, 83.0), day(2026, 8, 27, 83.0), day(2026, 9, 1, 83.6))
        val change = BodyMeasurements.change(daily)!!
        assertEquals(LocalDate(2026, 8, 20), change.since)
        assertEquals(0.6, change.amount, 1e-9)
    }

    @Test
    fun oneDayHasNoChange() {
        assertNull(BodyMeasurements.change(listOf(day(2026, 9, 1, 82.0))))
        assertNull(BodyMeasurements.change(emptyList()))
    }

    @Test
    fun todayIsNowAndOtherDaysAreMidday() {
        val now = at(2026, 9, 2, 7, 15)
        assertEquals(now, BodyMeasurements.takenAt(LocalDate(2026, 9, 2), now, vienna))
        assertEquals(at(2026, 8, 30, 12, 0), BodyMeasurements.takenAt(LocalDate(2026, 8, 30), now, vienna))
    }

    @Test
    fun implausibleValuesAreCaught() {
        assertTrue(BodyMeasurements.isPlausible(MeasurementType.Bodyweight, 82.5))
        assertFalse(BodyMeasurements.isPlausible(MeasurementType.Bodyweight, 825.0))
        assertFalse(BodyMeasurements.isPlausible(MeasurementType.Bodyweight, 0.0))
        assertTrue(BodyMeasurements.isPlausible(MeasurementType.BodyFat, 18.5))
        assertFalse(BodyMeasurements.isPlausible(MeasurementType.BodyFat, 100.0))
        // Circumferences are stored in metres: 85 cm, not 85 m.
        assertTrue(BodyMeasurements.isPlausible(MeasurementType.Waist, 0.85))
        assertFalse(BodyMeasurements.isPlausible(MeasurementType.Waist, 85.0))
    }

    private fun weight(
        at: Instant,
        kg: Double,
    ) = Measurement(id = at.toString(), type = MeasurementType.Bodyweight, takenAt = at, value = kg)

    private fun day(
        year: Int,
        month: Int,
        day: Int,
        value: Double,
    ) = DayValue(LocalDate(year, month, day), value)

    private fun at(
        year: Int,
        month: Int,
        day: Int,
        hour: Int,
        minute: Int,
    ) = LocalDateTime(year, month, day, hour, minute).toInstant(vienna)
}
