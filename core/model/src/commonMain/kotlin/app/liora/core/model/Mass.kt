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

enum class WeightUnit(
    val kilogramsPerUnit: Double,
    val symbol: String,
) {
    Kilogram(kilogramsPerUnit = 1.0, symbol = "kg"),
    Pound(kilogramsPerUnit = 0.45359237, symbol = "lb"),
}
