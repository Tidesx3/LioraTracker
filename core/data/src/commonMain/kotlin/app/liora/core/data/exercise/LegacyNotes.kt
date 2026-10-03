package app.liora.core.data.exercise

import app.liora.core.data.sync.SyncStamper
import app.liora.core.database.TransactionRunner
import app.liora.core.database.dao.ExerciseDao
import app.liora.core.database.dao.RoutineDao

/**
 * Moves notes from where they used to live onto the exercise's own note (`exercise_settings.sticky_note`),
 * the one the logger, routines and the exercise page show: a custom exercise's notes, and the notes a
 * routine kept on each of its exercises. A note the exercise has already stays first, and different ones
 * follow it on lines of their own, so nothing is lost.
 *
 * The old places are cleared as their notes move, so a second run finds nothing. It runs at every
 * start, which also picks up notes from an older backup restored since.
 */
class LegacyNotes(
    private val exerciseDao: ExerciseDao,
    private val routineDao: RoutineDao,
    private val transactions: TransactionRunner,
    private val stamper: SyncStamper,
) {
    suspend fun move() =
        transactions.inTransaction {
            val custom = exerciseDao.customWithNotes()
            val routineExercises = routineDao.exercisesWithNotes()
            if (custom.isEmpty() && routineExercises.isEmpty()) return@inTransaction
            val moving =
                (custom.map { it.id to it.notes } + routineExercises.map { it.exerciseId to it.notes })
                    .groupBy({ it.first }, { it.second?.trim().orEmpty() })
            moving.forEach { (exerciseId, notes) ->
                val current = exerciseDao.getSettings(exerciseId)?.takeIf { it.sync.deletedAt == null }?.stickyNote
                val merged = (listOfNotNull(current) + notes).filter { it.isNotBlank() }.distinct().joinToString("\n")
                if (merged.isNotEmpty() && merged != current) {
                    editSettings(exerciseDao, stamper, exerciseId) { copy(stickyNote = merged) }
                }
            }
            if (custom.isNotEmpty()) {
                exerciseDao.upsertExercises(custom.map { it.copy(notes = null, sync = stamper.touch(it.sync)) })
            }
            if (routineExercises.isNotEmpty()) {
                routineDao.upsertExercises(
                    routineExercises.map { it.copy(notes = null, sync = stamper.touch(it.sync)) },
                )
            }
        }
}
