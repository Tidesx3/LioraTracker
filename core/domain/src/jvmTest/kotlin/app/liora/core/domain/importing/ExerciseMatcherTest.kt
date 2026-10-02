package app.liora.core.domain.importing

import app.liora.core.model.Equipment
import app.liora.core.model.Exercise
import app.liora.core.model.ExerciseCategory
import app.liora.core.model.ExerciseSettings
import app.liora.core.model.TrackingType
import kotlin.test.Test
import kotlin.test.assertEquals

/** How names from Hevy and Strong find the exercises here, in English and German. */
class ExerciseMatcherTest {
    private val bench = exercise("bench", "Barbell Bench Press", Equipment.Barbell, rank = 1, "Bankdrücken")
    private val legPress = exercise("leg-press", "Leg Press", Equipment.Machine, rank = 5, "Beinpresse")
    private val lat =
        exercise("lat", "Wide-Grip Lat Pulldown", Equipment.Cable, rank = 7, "Lat Pulldown", "Latzug (Kabel)")
    private val fly = exercise("fly", "Dumbbell Flyes", Equipment.Dumbbell, rank = 20)
    private val crunch =
        exercise("crunch", "Crunch", Equipment.Bodyweight, rank = 30, type = TrackingType.BodyweightReps)
    private val cableCrunch = exercise("cable-crunch", "Cable Crunch", Equipment.Cable, rank = 31)
    private val pullUp =
        exercise("pull-up", "Pull-Up", Equipment.Bodyweight, rank = 6, "Klimmzug", type = TrackingType.BodyweightReps)
    private val weightedPullUp =
        exercise(
            "weighted-pull-up",
            "Weighted Pull-Up",
            Equipment.Other,
            rank = 40,
            "Klimmzug (Gewichtet)",
            type = TrackingType.WeightedBodyweight,
        )
    private val chinUp =
        exercise("chin-up", "Chin-Up", Equipment.Bodyweight, rank = 9, type = TrackingType.BodyweightReps)
    private val legCurl =
        exercise("leg-curl", "Seated Leg Curl", Equipment.Machine, rank = 15, "Beinbeugen sitzend (Maschine)")
    private val mine =
        exercise("mine", "Latzug Studio Nord", Equipment.Cable, rank = null).copy(isCustom = true, variationOf = "lat")

    private val matcher =
        ExerciseMatcher(
            listOf(bench, legPress, lat, fly, crunch, cableCrunch, pullUp, weightedPullUp, chinUp, legCurl, mine),
        )

    private val weightAndReps = SetFields(weight = true, reps = true)
    private val repsOnly = SetFields(reps = true)

    @Test
    fun namesAndAliasesInAnyLanguageAndOrder() {
        assertExisting(bench, "Bench Press (Barbell)")
        assertExisting(bench, "Bankdrücken")
        assertExisting(lat, "Latzug (Kabel)")
        assertExisting(lat, "lat pulldown")
        assertExisting(fly, "Dumbbell Fly")
        assertExisting(mine, "Latzug Studio Nord")
    }

    @Test
    fun equipmentWordsCountOnlyWhenTheyAgree() {
        assertExisting(legPress, "Leg Press (Machine)")
        assertExisting(legPress, "Beinpresse (Maschine)")
        assertExisting(lat, "Lat Pulldown (Cable)")
        assertExisting(legCurl, "Beinbeugen sitzend")
        // A leg press on cables is something else.
        assertNew(legPress, "Leg Press (Cable)")
        assertNew(lat, "Lat Pulldown (Machine)")
    }

    @Test
    fun theSameWordsWinOverAnotherEquipment() {
        assertExisting(crunch, "Crunch", repsOnly)
        assertExisting(cableCrunch, "Crunch (Cable)")
    }

    @Test
    fun gymSpecificMachinesBecomeVariations() {
        assertNew(legPress, "Beinpresse Studio Nord")
        assertNew(lat, "Latzug Studio Nord unten")
        assertNew(bench, "Bench Press Paused")
    }

    @Test
    fun weightOnABodyweightExerciseGoesToItsWeightedVariant() {
        assertExisting(pullUp, "Klimmzug", repsOnly)
        assertExisting(weightedPullUp, "Klimmzug", weightAndReps)
        // No weighted chin-up here: a new one, tracked with weight.
        assertNew(chinUp, "Chin-Up", weightAndReps)
        assertEquals(TrackingType.WeightedBodyweight, weightAndReps.trackingTypeFor(chinUp.trackingType))
    }

    @Test
    fun unrelatedNamesStartOnTheirOwn() {
        assertEquals(ExerciseSuggestion(ExerciseChoice.New(basedOn = null), closest = null), suggest("Sled Push"))
        // Sharing only a word for equipment or posture doesn't make two exercises alike.
        assertEquals(
            ExerciseSuggestion(ExerciseChoice.New(basedOn = null), closest = null),
            suggest("Seated Machine Twist"),
        )
    }

    @Test
    fun trackingTypesThatKeepEverything() {
        assertEquals(TrackingType.WeightReps, weightAndReps.trackingTypeFor(null))
        assertEquals(TrackingType.BodyweightReps, repsOnly.trackingTypeFor(null))
        assertEquals(TrackingType.Duration, SetFields(duration = true).trackingTypeFor(null))
        assertEquals(TrackingType.DurationWeight, SetFields(weight = true, duration = true).trackingTypeFor(null))
        assertEquals(TrackingType.DistanceDuration, SetFields(duration = true, distance = true).trackingTypeFor(null))
        assertEquals(TrackingType.WeightDistance, SetFields(weight = true, distance = true).trackingTypeFor(null))
        // The base's own type when it fits, even if it has more columns.
        assertEquals(
            TrackingType.DistanceDuration,
            SetFields(duration = true).trackingTypeFor(TrackingType.DistanceDuration),
        )
        assertEquals(TrackingType.Duration, SetFields(duration = true).trackingTypeFor(TrackingType.WeightReps))
    }

    private fun suggest(
        name: String,
        fields: SetFields = weightAndReps,
    ) = matcher.suggest(name, fields)

    private fun assertExisting(
        expected: Exercise,
        name: String,
        fields: SetFields = weightAndReps,
    ) = assertEquals(ExerciseSuggestion(ExerciseChoice.Existing(expected.id), expected.id), suggest(name, fields), name)

    private fun assertNew(
        base: Exercise,
        name: String,
        fields: SetFields = weightAndReps,
    ) = assertEquals(ExerciseSuggestion(ExerciseChoice.New(base.id), base.id), suggest(name, fields), name)

    private fun exercise(
        id: String,
        name: String,
        equipment: Equipment,
        rank: Int?,
        vararg terms: String,
        type: TrackingType = TrackingType.WeightReps,
    ) = Exercise(
        id = id,
        name = name,
        searchTerms = terms.toList(),
        trackingType = type,
        equipment = equipment,
        category = ExerciseCategory.Strength,
        primaryMuscles = emptySet(),
        secondaryMuscles = emptySet(),
        instructions = emptyList(),
        imageUrls = emptyList(),
        isCustom = false,
        variationOf = null,
        notes = null,
        rank = rank,
        settings = ExerciseSettings(),
    )
}
