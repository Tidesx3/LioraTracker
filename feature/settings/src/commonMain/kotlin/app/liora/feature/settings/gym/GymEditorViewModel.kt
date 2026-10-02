package app.liora.feature.settings.gym

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.liora.core.data.gym.GymProfileRepository
import app.liora.core.data.settings.SettingsRepository
import app.liora.core.domain.GymProfiles
import app.liora.core.model.WeightUnit
import app.liora.core.navigation.GymEditorRoute
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class GymEditorUiState(
    val loading: Boolean = true,
    val isNew: Boolean = true,
    val draft: GymDraft? = null,
    val hasChanges: Boolean = false,
    val saving: Boolean = false,
    /** Set once a save or delete has landed; the screen closes in reaction to it. */
    val done: Boolean = false,
) {
    val canSave: Boolean get() = !saving && draft?.toProfile(id = "") != null
}

/**
 * Edits a copy of a gym, or a new one that starts with standard equipment in the chosen weight unit,
 * and writes it in one go on save.
 */
class GymEditorViewModel(
    private val route: GymEditorRoute,
    private val gyms: GymProfileRepository,
    settings: SettingsRepository,
) : ViewModel() {
    private var nextKey = 0
    private val original = MutableStateFlow<GymDraft?>(null)
    private val draft = MutableStateFlow<GymDraft?>(null)
    private val saving = MutableStateFlow(false)
    private val done = MutableStateFlow(false)

    val uiState: StateFlow<GymEditorUiState> =
        combine(draft, original, saving, done) { current, stored, isSaving, isDone ->
            GymEditorUiState(
                loading = current == null,
                isNew = route.gymId == null,
                draft = current,
                hasChanges = current != stored,
                saving = isSaving,
                done = isDone,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GymEditorUiState())

    init {
        viewModelScope.launch {
            val gym =
                route.gymId?.let { gyms.get(it) }
                    ?: GymProfiles.standard(settings.current().units.weight)
            val loaded = gym.toDraft(::newKey).let { if (route.gymId == null) it.copy(name = "") else it }
            original.value = loaded
            draft.value = loaded
        }
    }

    fun edit(change: GymDraft.() -> GymDraft) = draft.update { it?.change() }

    /**
     * Pounds or kilograms. A gym still on the standard equipment swaps to the other unit's standard set;
     * one that's been changed keeps its numbers, read in the new unit.
     */
    fun setUnit(unit: WeightUnit) =
        edit {
            val standard = GymProfiles.standard(this.unit).toDraft { 0 }
            if (sameEquipment(standard)) {
                GymProfiles.standard(unit).toDraft(::newKey).copy(name = name)
            } else {
                copy(unit = unit)
            }
        }

    fun addPlate() = edit { copy(plates = plates + PlateRow(newKey())) }

    fun addDumbbells() = edit { copy(dumbbells = dumbbells + DumbbellRow(newKey())) }

    fun save() {
        val profile = draft.value?.toProfile(route.gymId.orEmpty()) ?: return
        saving.value = true
        viewModelScope.launch {
            gyms.save(profile)
            done.value = true
        }
    }

    fun delete() {
        val id = route.gymId ?: return
        saving.value = true
        viewModelScope.launch {
            gyms.delete(id)
            done.value = true
        }
    }

    private fun newKey(): Int = nextKey++
}
