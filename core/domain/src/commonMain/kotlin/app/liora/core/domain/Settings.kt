package app.liora.core.domain

import app.liora.core.model.Units
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days

/**
 * What the user chose in Settings, as the app's rules use it. They follow the user from device to
 * device; a new install starts with these defaults.
 */
data class Settings(
    /** Rest when neither the workout nor the exercise sets its own. */
    val rest: RestDefaults = RestDefaults(),
    /** What plays when a rest runs out. */
    val restSound: RestSound = RestSound.Chime,
    /** An RPE column in the logger, to rate how hard each set was. Off for those who don't rate their sets. */
    val rpe: Boolean = false,
    val units: Units = Units(),
    val oneRepMaxFormula: OneRepMaxFormula = OneRepMaxFormula.Epley,
    /** How long a lift can go without a new best before it counts as stalled. */
    val stallWindow: Duration = Stalls.DEFAULT_WINDOW,
    /** How far above the heaviest lately a progression target may go before it is flagged, in percent. */
    val jumpLimitPercent: Int = DEFAULT_JUMP_LIMIT,
    /** How double progression behaves. */
    val progression: ProgressionSettings = ProgressionSettings(),
    val theme: ThemeMode = ThemeMode.System,
    /** Colors from the wallpaper, where the phone offers them (Android 12 and up). */
    val dynamicColor: Boolean = false,
    /** Liquid Glass: bars that blur and bend what's beneath them, over soft glows. Off is the solid look. */
    val glass: Boolean = true,
) {
    companion object {
        /** The stall windows to choose from: two to eight weeks. */
        val STALL_WINDOWS: List<Duration> = listOf(2, 3, 4, 6, 8).map { (it * DAYS_PER_WEEK).days }

        /** The jump limits to choose from, in percent. */
        val JUMP_LIMITS: List<Int> = listOf(5, 10, 15, 20, 25)

        const val DEFAULT_JUMP_LIMIT = 10

        private const val DAYS_PER_WEEK = 7
    }
}

/** The sound that ends a rest, or none. The vibration comes either way, with the notification. */
enum class RestSound { Chime, Bell, Marimba, Off }

/** Light or dark, or as the phone is set. */
enum class ThemeMode { System, Light, Dark }
