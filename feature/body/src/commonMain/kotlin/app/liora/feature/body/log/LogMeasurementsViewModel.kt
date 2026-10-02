package app.liora.feature.body.log

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.liora.core.data.body.BodyRepository
import app.liora.core.data.settings.SettingsRepository
import app.liora.core.domain.BodyMeasurements
import app.liora.core.domain.TrainingCalendar
import app.liora.core.domain.parseDecimalInput
import app.liora.core.model.MeasurementType
import app.liora.core.model.Units
import app.liora.core.navigation.LogMeasurementsRoute
import app.liora.core.ui.fromShown
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.time.Clock

/**
 * What's being typed: the day, and the fields changed so far as typed. Fields not changed show what
 * the day already has, and saving leaves them alone. Kept apart from the stored values so typing never
 * waits on the database.
 */
data class LogForm(
    val today: LocalDate,
    val day: LocalDate,
    val edits: Map<MeasurementType, String> = emptyMap(),
    /** The units values are typed in. */
    val units: Units = Units(),
    val saving: Boolean = false,
    /** Set once the save has landed; the screen closes in response. */
    val done: Boolean = false,
) {
    /** A value that isn't a number, or not one a person could measure. An empty field is fine: it removes the entry. */
    fun isInvalid(type: MeasurementType): Boolean {
        val text = edits[type] ?: return false
        return text.isNotBlank() && valueOf(type, text) == null
    }

    val canSave: Boolean get() = edits.isNotEmpty() && edits.keys.none(::isInvalid) && !saving

    /** What saving writes: each changed type's value, or null to remove the day's entry. */
    fun changes(): Map<MeasurementType, Double?> = edits.mapValues { (type, text) -> valueOf(type, text) }

    private fun valueOf(
        type: MeasurementType,
        text: String,
    ): Double? =
        parseDecimalInput(text)
            ?.let { type.fromShown(it, units) }
            ?.takeIf { BodyMeasurements.isPlausible(type, it) }
}

/** What the form's day already has, and the latest value before it as a hint. */
data class DayValues(
    val onDay: Map<MeasurementType, Double> = emptyMap(),
    val before: Map<MeasurementType, Double> = emptyMap(),
)

/** Logs or corrects one day's measurements, today's by default. */
class LogMeasurementsViewModel(
    route: LogMeasurementsRoute,
    private val body: BodyRepository,
    settings: SettingsRepository,
    clock: Clock,
) : ViewModel() {
    private val zone = TimeZone.currentSystemDefault()
    private val today = TrainingCalendar.dayOf(clock.now(), zone)
    private val state = MutableStateFlow(LogForm(today = today, day = route.day?.let(LocalDate::parse) ?: today))
    val form: StateFlow<LogForm> = state.asStateFlow()

    init {
        viewModelScope.launch {
            settings.settings.collect { chosen -> state.update { it.copy(units = chosen.units) } }
        }
    }

    val dayValues: StateFlow<DayValues> =
        combine(body.measurements, state.map { it.day }.distinctUntilChanged()) { all, day ->
            val byType =
                MeasurementType.entries.associateWith { type ->
                    BodyMeasurements.daily(all, type, zone)
                }
            DayValues(
                onDay = byType.mapNotNullValues { days -> days.firstOrNull { it.day == day }?.value },
                before = byType.mapNotNullValues { days -> days.lastOrNull { it.day < day }?.value },
            )
        }.flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DayValues())

    fun edit(
        type: MeasurementType,
        text: String,
    ) {
        state.update { it.copy(edits = it.edits + (type to text)) }
    }

    /** Moves the form to [day]; what was typed stays, the other fields show that day's values. */
    fun selectDay(day: LocalDate) {
        state.update { it.copy(day = day.coerceAtMost(today)) }
    }

    fun save() {
        val current = state.value
        if (!current.canSave) return
        state.update { it.copy(saving = true) }
        viewModelScope.launch {
            body.saveDay(current.day, current.changes(), zone)
            state.update { it.copy(saving = false, done = true) }
        }
    }

    private fun <K, V, R : Any> Map<K, V>.mapNotNullValues(transform: (V) -> R?): Map<K, R> =
        mapNotNull { (key, value) -> transform(value)?.let { key to it } }.toMap()
}
