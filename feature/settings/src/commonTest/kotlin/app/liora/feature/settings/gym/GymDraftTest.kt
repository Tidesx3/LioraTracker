package app.liora.feature.settings.gym

import app.liora.core.domain.GymProfiles
import app.liora.core.model.Mass
import app.liora.core.model.PlatePairs
import app.liora.core.model.WeightUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GymDraftTest {
    private var key = 0
    private val standard = GymProfiles.standard(WeightUnit.Pound, name = "Garage")
    private val draft = standard.toDraft { key++ }

    @Test
    fun aGymComesBackFromItsDraftUnchanged() {
        assertEquals(standard.copy(id = "g1"), draft.toProfile("g1"))
        assertTrue(draft.sameEquipment(GymProfiles.standard(WeightUnit.Pound).toDraft { 0 }))
    }

    @Test
    fun emptyRowsAreLeftOutAndPlatesGoHeaviestFirst() {
        val edited =
            draft.copy(
                plates =
                    listOf(
                        PlateRow(90, weight = 2.5, pairs = 2),
                        PlateRow(91),
                        PlateRow(92, weight = 45.0, pairs = 4),
                    ),
                dumbbells = draft.dumbbells + DumbbellRow(93),
            )
        val profile = edited.toProfile("g1")!!
        assertEquals(
            listOf(PlatePairs(Mass.of(45.0, WeightUnit.Pound), 4), PlatePairs(Mass.of(2.5, WeightUnit.Pound), 2)),
            profile.plates,
        )
        assertEquals(standard.dumbbells, profile.dumbbells)
    }

    @Test
    fun nothingToSaveWhileSomethingIsMissingOrMakesNoSense() {
        assertNull(draft.copy(name = " ").toProfile("g1"))
        assertNull(draft.copy(barbell = null).toProfile("g1"))
        assertNull(draft.copy(stackStep = 0.0).toProfile("g1"))
        // A row that's begun must be finished.
        assertNull(draft.copy(plates = draft.plates + PlateRow(90, weight = 1.25)).toProfile("g1"))
        assertNull(draft.copy(dumbbells = listOf(DumbbellRow(90, from = 50.0, to = 10.0, step = 5.0))).toProfile("g1"))
    }
}
