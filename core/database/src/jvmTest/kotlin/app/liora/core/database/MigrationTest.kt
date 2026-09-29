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
}
