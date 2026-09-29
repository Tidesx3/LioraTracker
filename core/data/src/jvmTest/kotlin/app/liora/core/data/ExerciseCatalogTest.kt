package app.liora.core.data

import app.liora.core.data.exercise.OfflineExerciseRepository
import app.liora.core.data.seed.ExerciseCatalogSeeder
import app.liora.core.data.seed.ExerciseSeedSource
import app.liora.core.database.TransactionRunner
import app.liora.core.database.inMemoryLioraDatabase
import app.liora.core.model.Muscle
import app.liora.core.model.TrackingType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Clock

class ExerciseCatalogTest {
    private val database = inMemoryLioraDatabase()
    private var seedJson = seed(version = 1, benchName = "Barbell Bench Press")
    private val seeder =
        ExerciseCatalogSeeder(
            source = ExerciseSeedSource { seedJson },
            exerciseDao = database.exerciseDao(),
            meta = database.localMetaDao(),
            transactions = TransactionRunner(database),
            clock = Clock.System,
        )
    private val repository = OfflineExerciseRepository(database.exerciseDao())

    @AfterTest
    fun tearDown() = database.close()

    @Test
    fun namesResolveInTheRequestedLanguageWithEnglishFallback() =
        runTest {
            assertTrue(seeder.seedIfNeeded())

            val german = repository.observeExercises("de").first().associateBy { it.id }
            assertEquals("Bankdrücken (Langhantel)", german.getValue("fedb.bench").name)
            assertEquals(TrackingType.WeightReps, german.getValue("fedb.bench").trackingType)
            assertEquals(setOf(Muscle.Chest), german.getValue("fedb.bench").primaryMuscles)
            assertEquals(1, german.getValue("fedb.bench").rank)
            assertTrue("Bench Press" in german.getValue("fedb.bench").searchTerms)
            assertTrue("Barbell Bench Press" in german.getValue("fedb.bench").searchTerms)

            val french = repository.observeExercises("fr").first().associateBy { it.id }
            assertEquals("Barbell Bench Press", french.getValue("fedb.bench").name)
        }

    @Test
    fun seedsOnlyWhenTheBundledVersionIsNewer() =
        runTest {
            assertTrue(seeder.seedIfNeeded())
            assertFalse(seeder.seedIfNeeded())

            seedJson = seed(version = 2, benchName = "Bench Press (Barbell)")
            assertTrue(seeder.seedIfNeeded())
            val english = repository.observeExercises("en").first().associateBy { it.id }
            assertEquals("Bench Press (Barbell)", english.getValue("fedb.bench").name)
            // Renamed built-ins drop their old names from search instead of piling up aliases.
            assertFalse("Barbell Bench Press" in english.getValue("fedb.bench").searchTerms)
        }

    private fun seed(
        version: Int,
        benchName: String,
    ) = """
        {
          "version": $version,
          "source": "test",
          "exercises": [
            {"id":"fedb.bench","names":{"en":"$benchName","de":"Bankdrücken (Langhantel)"},
             "aliases":{"en":["Bench Press"],"de":["Bankdrücken"]},"tracking":"weight_reps","equipment":"barbell",
             "category":"strength","primary":["chest"],"secondary":["shoulders","triceps"],
             "instructions":["Press."],"images":[],"rank":1},
            {"id":"liora.dead_hang","names":{"en":"Dead Hang","de":"Dead Hang (Hängen)"},
             "tracking":"duration","equipment":"bodyweight","category":"strength","primary":["forearms"]}
          ]
        }
        """.trimIndent()
}
