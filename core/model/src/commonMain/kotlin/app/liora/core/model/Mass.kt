package app.liora.core.model

import kotlin.jvm.JvmInline

/**
 * A weight. Stored canonically in kilograms; the unit the user sees is a display preference, so
 * switching kg <-> lb never rewrites history.
 */
@JvmInline
value class Mass(
    val kilograms: Double,
) : Comparable<Mass> {
    fun inUnit(unit: WeightUnit): Double = kilograms / unit.kilogramsPerUnit

    operator fun plus(other: Mass): Mass = Mass(kilograms + other.kilograms)

    operator fun minus(other: Mass): Mass = Mass(kilograms - other.kilograms)

    operator fun times(factor: Double): Mass = Mass(kilograms * factor)

    override fun compareTo(other: Mass): Int = kilograms.compareTo(other.kilograms)

    companion object {
        val Zero = Mass(0.0)

        fun of(
            value: Double,
            unit: WeightUnit,
        ): Mass = Mass(value * unit.kilogramsPerUnit)
    }
}

/**
 * [fractionDigits] is how finely weights are shown: kilograms to the hundredth (a 1.25 kg plate a side),
 * pounds to the tenth, since loads in pounds come in whole and half pounds and a weight converted from
 * kilograms would otherwise read like "176.37 lb".
 */
enum class WeightUnit(
    val kilogramsPerUnit: Double,
    val symbol: String,
    val fractionDigits: Int,
) {
    Kilogram(kilogramsPerUnit = 1.0, symbol = "kg", fractionDigits = 2),
    Pound(kilogramsPerUnit = 0.45359237, symbol = "lb", fractionDigits = 1),
}
