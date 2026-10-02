package app.liora.core.data.settings

import app.liora.core.data.sync.SyncStamper
import app.liora.core.database.TransactionRunner
import app.liora.core.database.dao.PreferenceDao
import app.liora.core.database.model.PreferenceEntity
import app.liora.core.domain.OneRepMaxFormula
import app.liora.core.domain.RestDefaults
import app.liora.core.domain.Settings
import app.liora.core.domain.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.seconds

/** The user's settings, kept in the synced `preference` table. */
interface SettingsRepository {
    val settings: Flow<Settings>

    /** Applies [change]; only the choices it actually changes are written, so they alone sync. */
    suspend fun update(change: (Settings) -> Settings)
}

internal class OfflineSettingsRepository(
    private val dao: PreferenceDao,
    private val transactions: TransactionRunner,
    private val stamper: SyncStamper,
) : SettingsRepository {
    override val settings: Flow<Settings> =
        dao
            .observeAll()
            .map { rows -> SettingsCodec.decode(rows.associate { it.key to it.value }) }
            .distinctUntilChanged()

    override suspend fun update(change: (Settings) -> Settings) =
        transactions.inTransaction {
            val rows = dao.all().associateBy { it.key }
            val live = rows.values.filter { it.sync.deletedAt == null }.associate { it.key to it.value }
            val current = SettingsCodec.decode(live)
            val before = SettingsCodec.encode(current)
            // Choices left alone aren't written: a new device would otherwise overwrite, with its
            // defaults, what was chosen on another one.
            val changed = SettingsCodec.encode(change(current)).filter { (key, value) -> before[key] != value }
            val written =
                changed.map { (key, value) ->
                    rows[key]?.let { row ->
                        row.copy(value = value, sync = stamper.touch(row.sync).copy(deletedAt = null))
                    }
                        ?: PreferenceEntity(key, value, stamper.newRow())
                }
            if (written.isNotEmpty()) dao.upsertAll(written)
        }
}

/**
 * Settings as preference rows: stable keys and plain values, so rows synced from a newer app version
 * that this one doesn't understand fall back to the default instead of breaking.
 */
internal object SettingsCodec {
    private const val REST_WORKING = "rest.working_sec"
    private const val REST_WARMUP = "rest.warmup_sec"
    private const val FORMULA = "progress.e1rm_formula"
    private const val STALL_WINDOW = "progress.stall_window_days"
    private const val THEME = "appearance.theme"
    private const val DYNAMIC_COLOR = "appearance.dynamic_color"

    private val formulas = mapOf(OneRepMaxFormula.Epley to "epley", OneRepMaxFormula.Brzycki to "brzycki")
    private val themes = mapOf(ThemeMode.System to "system", ThemeMode.Light to "light", ThemeMode.Dark to "dark")

    fun encode(settings: Settings): Map<String, String> =
        mapOf(
            REST_WORKING to
                settings.rest.working.inWholeSeconds
                    .toString(),
            REST_WARMUP to
                settings.rest.warmup.inWholeSeconds
                    .toString(),
            FORMULA to formulas.getValue(settings.oneRepMaxFormula),
            STALL_WINDOW to settings.stallWindow.inWholeDays.toString(),
            THEME to themes.getValue(settings.theme),
            DYNAMIC_COLOR to settings.dynamicColor.toString(),
        )

    fun decode(values: Map<String, String>): Settings {
        val defaults = Settings()
        val seconds = { key: String -> values[key]?.toIntOrNull()?.takeIf { it >= 0 }?.seconds }
        return Settings(
            rest =
                RestDefaults(
                    working = seconds(REST_WORKING) ?: defaults.rest.working,
                    warmup = seconds(REST_WARMUP) ?: defaults.rest.warmup,
                ),
            oneRepMaxFormula = values[FORMULA]?.let { formulas.keyFor(it) } ?: defaults.oneRepMaxFormula,
            stallWindow = values[STALL_WINDOW]?.toIntOrNull()?.takeIf { it > 0 }?.days ?: defaults.stallWindow,
            theme = values[THEME]?.let { themes.keyFor(it) } ?: defaults.theme,
            dynamicColor = values[DYNAMIC_COLOR]?.toBooleanStrictOrNull() ?: defaults.dynamicColor,
        )
    }

    private fun <K> Map<K, String>.keyFor(value: String): K? = entries.firstOrNull { it.value == value }?.key
}
