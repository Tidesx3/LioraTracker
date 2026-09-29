package app.liora.core.domain

import app.liora.core.model.Equipment
import app.liora.core.model.Exercise
import app.liora.core.model.ExerciseCategory
import app.liora.core.model.ExerciseSettings
import app.liora.core.model.TrackingType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ExerciseSearchTest {
    private val deadlift =
        exercise("deadlift", "Kreuzheben (Langhantel)", rank = 3, "Barbell Deadlift", "Deadlift", "Kreuzheben")
    private val bandDeadlift = exercise("band-deadlift", "Kreuzheben mit Bändern", rank = null, "Deadlift with Bands")
    private val bench = exercise("bench", "Bankdrücken (Langhantel)", rank = 1, "Barbell Bench Press", "Bench Press")
    private val dbBench = exercise("db-bench", "Bankdrücken (Kurzhantel)", rank = 8, "Dumbbell Bench Press")
    private val pullUp = exercise("pull-up", "Klimmzug", rank = 6, "Pull-Up", "Pullups", "Klimmzüge")
    private val latPulldown =
        exercise(
            "lat",
            "Latziehen weit (Kabelzug)",
            rank = 7,
            "Wide-Grip Lat Pulldown",
            "Lat Pulldown",
            "Latzug (Kabel)",
        )
    private val legCurl =
        exercise("leg-curl", "Beinbeuger sitzend (Maschine)", rank = 15, "Seated Leg Curl", "Beinbeugen sitzend")
    private val archived =
        exercise(
            "old",
            "Bankdrücken Alt",
            rank = null,
        ).copy(settings = ExerciseSettings(archived = true))

    private val search =
        ExerciseSearch(listOf(bandDeadlift, deadlift, bench, dbBench, pullUp, latPulldown, legCurl, archived))

    @Test
    fun germanNamesIgnoringCaseAndUmlauts() {
        assertEquals(deadlift, search.search("Kreuzheben").first())
        assertEquals(deadlift, search.search("kreuzheben").first())
        assertEquals(bench, search.search("Bankdrücken").first())
        assertEquals(bench, search.search("bankdrucken").first())
        assertEquals(bench, search.search("Bankdruecken").first())
    }

    @Test
    fun typosStillFindTheExercise() {
        assertEquals(deadlift, search.search("Kreuzhebn").first())
        assertEquals(bench, search.search("bnech press").first())
        assertEquals(pullUp, search.search("klimzug").first())
    }

    @Test
    fun englishNamesAliasesAndHevyWordingWork() {
        assertEquals(bench, search.search("bench").first())
        assertEquals(latPulldown, search.search("lat pulldown").first())
        assertEquals(latPulldown, search.search("Latzug").first())
        assertEquals(legCurl, search.search("Beinbeugen sitzend").first())
        assertEquals(pullUp, search.search("pullup").first())
        assertEquals(pullUp, search.search("pull up").first())
    }

    @Test
    fun wordOrderDoesNotMatter() {
        assertEquals(dbBench, search.search("bench dumbbell").first())
    }

    @Test
    fun popularExercisesComeFirstOnTies() {
        val results = search.search("deadlift")
        assertEquals(listOf(deadlift, bandDeadlift), results)
    }

    @Test
    fun emptyQueryListsEverythingByPopularityAndHidesArchived() {
        val results = search.search("  ")
        assertEquals(bench, results.first())
        assertTrue(archived !in results)
        assertTrue(archived in search.search("", includeArchived = true))
    }

    @Test
    fun unrelatedQueriesFindNothing() {
        assertTrue(search.search("zzzz").isEmpty())
        assertTrue(search.search("xy").isEmpty())
    }

    private fun exercise(
        id: String,
        name: String,
        rank: Int?,
        vararg terms: String,
    ) = Exercise(
        id = id,
        name = name,
        searchTerms = terms.toList(),
        trackingType = TrackingType.WeightReps,
        equipment = Equipment.Barbell,
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
