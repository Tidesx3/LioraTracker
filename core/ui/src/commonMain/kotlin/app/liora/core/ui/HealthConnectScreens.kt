package app.liora.core.ui

import androidx.compose.runtime.staticCompositionLocalOf

/** Health Connect's own screens, which Liora's settings open. */
interface HealthConnectScreens {
    /** Health Connect's permission screen for Liora; true when anything is allowed afterwards. */
    suspend fun requestPermissions(): Boolean

    /** Health Connect's settings, where Liora's permissions can be changed. */
    fun openSettings()

    /** The store page that installs or updates Health Connect. */
    fun install()
}

/** The app provides it (`MainActivity`), since the permission screen needs the activity. */
val LocalHealthConnectScreens =
    staticCompositionLocalOf<HealthConnectScreens> {
        object : HealthConnectScreens {
            override suspend fun requestPermissions() = false

            override fun openSettings() = Unit

            override fun install() = Unit
        }
    }
