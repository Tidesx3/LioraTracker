package app.liora.core.domain

import app.liora.core.model.Mass
import app.liora.core.model.Measurement
import app.liora.core.model.MeasurementKind
import app.liora.core.model.MeasurementType
import app.liora.core.model.Units
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone

/**
 * Body measurements as CSV for spreadsheets (see [CsvWriter]): one row per day, oldest first, and a
 * column for each measurement taken at least once, named with its unit (`bodyweight_kg`,
 * `body_fat_percent`, `waist_cm` or `waist_in`). A day measured twice shows its last value, as on Body.
 */
object BodyCsv {
    fun format(
        measurements: List<Measurement>,
        units: Units,
        zone: TimeZone,
    ): String {
        val types = measuredTypes(measurements)
        val csv = CsvWriter()
        csv.row(listOf(DATE) + valueHeader(types, units))
        days(measurements, types, units, zone).forEach { day -> csv.row(listOf(CsvWriter.date(day.date)) + day.values) }
        return csv.toString()
    }

    /** The measurements taken at least once, in the order Body lists them. */
    internal fun measuredTypes(measurements: List<Measurement>): List<MeasurementType> {
        val measured = measurements.mapTo(mutableSetOf()) { it.type }
        return MeasurementType.entries.filter { it in measured }
    }

    internal fun valueHeader(
        types: List<MeasurementType>,
        units: Units,
    ): List<String> =
        types.map { type ->
            val unit =
                when (type.kind) {
                    MeasurementKind.Mass -> units.weight.symbol
                    MeasurementKind.Percent -> "percent"
                    MeasurementKind.Length -> units.bodyLength.symbol
                }
            "${type.key}_$unit"
        }

    /** Each day with a measurement, oldest first, with its values in the columns of [types]. */
    internal fun days(
        measurements: List<Measurement>,
        types: List<MeasurementType>,
        units: Units,
        zone: TimeZone,
    ): List<Day> {
        val byType =
            types.associateWith { type ->
                BodyMeasurements.daily(measurements, type, zone).associate {
                    it.day to
                        it.value
                }
            }
        return byType.values
            .flatMap { it.keys }
            .distinct()
            .sorted()
            .map { date -> Day(date, types.map { type -> byType.getValue(type)[date]?.let { text(it, type, units) } }) }
    }

    private fun text(
        value: Double,
        type: MeasurementType,
        units: Units,
    ): String =
        CsvWriter.decimal(
            when (type.kind) {
                MeasurementKind.Mass -> Mass(value).inUnit(units.weight)
                MeasurementKind.Percent -> value
                MeasurementKind.Length -> units.bodyLength.fromMeters(value)
            },
            DECIMALS,
        )

    internal data class Day(
        val date: LocalDate,
        val values: List<String?>,
    )

    private const val DATE = "date"

    /** Enough for a weight typed in pounds, or a circumference in tenths of an inch, to come back as typed. */
    private const val DECIMALS = 2
}
