package app.liora.core.database

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import org.junit.Rule
import java.nio.file.Files
import kotlin.io.path.Path
import kotlin.io.path.deleteIfExists
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Every schema version that shipped must migrate to the current one without losing data. Schemas come
 * from `schemas/`, which Room exports on every build and which is committed.
 */
class MigrationTest {
    private val databaseFile = Files.createTempFile("liora-migration", ".db").also { it.deleteIfExists() }

    @get:Rule
    val helper =
        MigrationTestHelper(
            schemaDirectoryPath = Path("schemas"),
            databasePath = databaseFile,
            driver = BundledSQLiteDriver(),
            databaseClass = LioraDatabase::class,
            databaseFactory = { LioraDatabaseConstructor.initialize() },
        )

    @AfterTest
    fun cleanUp() {
        databaseFile.deleteIfExists()
    }

    @Test
    fun v1To2KeepsLoggedSetsAndAddsEmptyRepTargets() {
        helper.createDatabase(1).apply {
            execSQL(
                """
                INSERT INTO workout_set (id, workout_exercise_id, position, set_type, weight_kg, reps,
                    duration_sec, distance_m, rpe, completed_at, created_at, hlc, deleted_at, dirty)
                VALUES ('s1', 'we1', 0, 'normal', 82.5, 8, NULL, NULL, NULL, 1000, 1000, 'hlc', NULL, 1)
                """.trimIndent(),
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(2)
        migrated.prepare("SELECT weight_kg, reps, target_reps_min, target_reps_max FROM workout_set").use { row ->
            assertTrue(row.step())
            assertEquals(82.5, row.getDouble(0))
            assertEquals(8, row.getLong(1))
            assertTrue(row.isNull(2) && row.isNull(3))
        }
        migrated.close()
    }

    @Test
    fun v2To3KeepsPreferencesAndAddsGymProfiles() {
        helper.createDatabase(2).apply {
            execSQL(
                """
                INSERT INTO preference (`key`, value, created_at, hlc, deleted_at, dirty)
                VALUES ('units.weight', 'lb', 1000, 'hlc', NULL, 1)
                """.trimIndent(),
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(3)
        migrated.prepare("SELECT value FROM preference WHERE `key` = 'units.weight'").use { row ->
            assertTrue(row.step())
            assertEquals("lb", row.getText(0))
        }
        migrated.execSQL(
            """
            INSERT INTO gym_profile (id, name, unit, barbell_kg, ez_bar_kg, plates, dumbbells, stack_step_kg,
                created_at, hlc, deleted_at, dirty)
            VALUES ('g1', 'Studio Nord', 'kg', 20.0, 10.0, '[]', '[]', 5.0, 2000, 'hlc', NULL, 1)
            """.trimIndent(),
        )
        migrated.prepare("SELECT name, stack_step_kg FROM gym_profile").use { row ->
            assertTrue(row.step())
            assertEquals("Studio Nord", row.getText(0))
            assertEquals(5.0, row.getDouble(1))
        }
        migrated.close()
    }

    @Test
    fun v3To4KeepsRoutinesAndSetsAndAddsProgressionAndRotations() {
        helper.createDatabase(3).apply {
            execSQL(
                """
                INSERT INTO routine_folder (id, name, position, created_at, hlc, deleted_at, dirty)
                VALUES ('f1', 'Push Pull Legs', 0, 1000, 'hlc', NULL, 1)
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO routine_exercise (id, routine_id, exercise_id, position, superset_group, rest_sec, notes,
                    created_at, hlc, deleted_at, dirty)
                VALUES ('re1', 'r1', 'fedb.bench', 0, NULL, 120, NULL, 1000, 'hlc', NULL, 1)
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO exercise_settings (exercise_id, sticky_note, rest_working_sec, rest_warmup_sec, archived,
                    created_at, hlc, deleted_at, dirty)
                VALUES ('fedb.bench', 'Seat 4', NULL, NULL, 0, 1000, 'hlc', NULL, 1)
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO workout_set (id, workout_exercise_id, position, set_type, weight_kg, reps, duration_sec,
                    distance_m, rpe, completed_at, target_reps_min, target_reps_max, created_at, hlc, deleted_at, dirty)
                VALUES ('s1', 'we1', 0, 'normal', 80.0, 12, NULL, NULL, NULL, 1000, 8, 12, 1000, 'hlc', NULL, 1)
                """.trimIndent(),
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(4)
        // Folders aren't rotations until made one; exercises have no rule, no own step, sets no aim.
        migrated.prepare("SELECT name, rotation FROM routine_folder").use { row ->
            assertTrue(row.step())
            assertEquals("Push Pull Legs", row.getText(0))
            assertEquals(0, row.getLong(1))
        }
        migrated.prepare("SELECT rest_sec, progression FROM routine_exercise").use { row ->
            assertTrue(row.step())
            assertEquals(120, row.getLong(0))
            assertTrue(row.isNull(1))
        }
        migrated.prepare("SELECT sticky_note, load_step_kg FROM exercise_settings").use { row ->
            assertTrue(row.step())
            assertEquals("Seat 4", row.getText(0))
            assertTrue(row.isNull(1))
        }
        migrated.prepare("SELECT weight_kg, reps, target_reps_max, aim_reps FROM workout_set").use { row ->
            assertTrue(row.step())
            assertEquals(80.0, row.getDouble(0))
            assertEquals(12, row.getLong(1))
            assertEquals(12, row.getLong(2))
            assertTrue(row.isNull(3))
        }
        migrated.close()
    }
}
