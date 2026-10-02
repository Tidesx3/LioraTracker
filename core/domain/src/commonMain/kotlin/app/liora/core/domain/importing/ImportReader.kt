package app.liora.core.domain.importing

import app.liora.core.model.Units
import kotlinx.datetime.TimeZone

/** Recognizes an export from another app by its columns and reads it. */
object ImportReader {
    /**
     * What [text] holds: Hevy's workouts or measurements, or Strong's workouts. Local times are read in
     * [zone], and weights and distances that don't say their unit in [units]. Null when it's none of these.
     */
    fun read(
        text: String,
        units: Units,
        zone: TimeZone,
    ): ImportFile? {
        val table = CsvTable.of(text)
        return when {
            HevyCsv.isWorkouts(table) -> HevyCsv.workouts(table, zone)
            HevyCsv.isMeasurements(table) -> HevyCsv.measurements(table)
            StrongCsv.isWorkouts(table) -> StrongCsv.workouts(table, units, zone)
            else -> null
        }
    }
}
