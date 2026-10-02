package app.liora.core.domain

import kotlin.time.Duration
import kotlin.time.Duration.Companion.days

/**
 * What the user chose in Settings, as the app's rules use it. They follow the user from device to
 * device; a new install starts with these defaults.
 */
data class Settings(
    /** Rest when neither the workout nor the exercise sets its own. */
    val rest: RestDefaults = RestDefaults(),
    val oneRepMaxFormula: OneRepMaxFormula = OneRepMaxFormula.Epley,
    /** How long a lift can go without a new best before it counts as stalled. */
    val stallWindow: Duration = Stalls.DEFAULT_WINDOW,
    val theme: ThemeMode = ThemeMode.System,
    /** Colors from the wallpaper, where the phone offers them (Android 12 and up). */
    val dynamicColor: Boolean = false,
) {
    companion object {
        /** The stall windows to choose from: two to eight weeks. */
        val STALL_WINDOWS: List<Duration> = listOf(2, 3, 4, 6, 8).map { (it * DAYS_PER_WEEK).days }

        private const val DAYS_PER_WEEK = 7
    }
}

/** Light or dark, or as the phone is set. */
enum class ThemeMode { System, Light, Dark }
