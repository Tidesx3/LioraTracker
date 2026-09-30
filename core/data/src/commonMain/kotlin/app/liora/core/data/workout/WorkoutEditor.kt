package app.liora.core.data.workout

import app.liora.core.common.IdGenerator
import app.liora.core.data.sync.SyncStamper
import app.liora.core.database.TransactionRunner
import app.liora.core.database.dao.ExerciseDao
import app.liora.core.database.dao.WorkoutDao
import app.liora.core.database.model.WorkoutExerciseEntity
import app.liora.core.database.model.WorkoutSetEntity
import app.liora.core.domain.Supersets
import app.liora.core.model.SetType

/** Changes to the exercises of the workout in progress. Every change leaves supersets consistent. */
interface WorkoutEditor {
    /** Adds exercises at the end, each with as many sets as last time, or a sensible start. */
    suspend fun addExercises(exerciseIds: List<String>)

    /** Swaps in another exercise, keeping the sets; values the new one has no column for are dropped. */
    suspend fun replaceExercise(
        workoutExerciseId: String,
        exerciseId: String,
    )

    suspend fun removeExercise(workoutExerciseId: String)

    /** Puts the exercises in this order; ids not listed keep their place at the end. */
    suspend fun reorder(workoutExerciseIds: List<String>)

    suspend fun linkWithNext(workoutExerciseId: String)

    suspend fun unlink(workoutExerciseId: String)

    /** Rest after this exercise's working sets, in this workout: null uses the exercise's own, 0 is off. */
    suspend fun setRest(
        workoutExerciseId: String,
        seconds: Int?,
    )

    suspend fun setNotes(
        workoutExerciseId: String,
        notes: String?,
    )
}

internal class OfflineWorkoutEditor(
    private val workoutDao: WorkoutDao,
    private val exerciseDao: ExerciseDao,
    private val transactions: TransactionRunner,
    private val ids: IdGenerator,
    private val stamper: SyncStamper,
) : WorkoutEditor {
    override suspend fun addExercises(exerciseIds: List<String>) {
        if (exerciseIds.isEmpty()) return
        transactions.inTransaction {
            val rows = workoutDao.activeRows() ?: return@inTransaction
            val lastTypes =
                workoutDao
                    .lastSessionSets(exerciseIds.distinct())
                    .groupBy({ it.exerciseId }, { it.set.setType })
            val added =
                exerciseIds.map { exerciseId ->
                    WorkoutExerciseEntity(
                        id = ids.newId(),
                        workoutId = rows.workout.id,
                        exerciseId = exerciseId,
                        position = 0,
                        supersetGroup = null,
                        restSeconds = null,
                        notes = null,
                        sync = stamper.newRow(),
                    )
                }
            val sets =
                added.flatMap { exercise ->
                    // As many sets as last time; never done: three for a lift, one for a hold or a run.
                    val types =
                        lastTypes[exercise.exerciseId]
                            ?: List(
                                if (exerciseDao.trackingTypeOf(exercise.exerciseId).usesReps) DEFAULT_SETS else 1,
                            ) { SetType.Normal }
                    types.mapIndexed {
                        index,
                        type,
                        ->
                        emptySetRow(ids.newId(), exercise.id, index, type, stamper.newRow())
                    }
                }
            save(rows, rows.exercises + added)
            workoutDao.upsertSets(sets)
        }
    }

    override suspend fun replaceExercise(
        workoutExerciseId: String,
        exerciseId: String,
    ) {
        transactions.inTransaction {
            val rows = workoutDao.activeRows() ?: return@inTransaction
            val trackingType = exerciseDao.trackingTypeOf(exerciseId)
            save(rows, rows.exercises.map { if (it.id == workoutExerciseId) it.copy(exerciseId = exerciseId) else it })
            val sets =
                rows.setsOf(workoutExerciseId).mapNotNull { row ->
                    val kept = row.withValues(row.toLoggedSet().keepOnly(trackingType))
                    if (kept == row) null else kept.copy(sync = stamper.touch(row.sync))
                }
            if (sets.isNotEmpty()) workoutDao.upsertSets(sets)
        }
    }

    override suspend fun removeExercise(workoutExerciseId: String) {
        transactions.inTransaction {
            val rows = workoutDao.activeRows() ?: return@inTransaction
            val sets = rows.setsOf(workoutExerciseId).map { it.copy(sync = stamper.tombstone(it.sync)) }
            if (sets.isNotEmpty()) workoutDao.upsertSets(sets)
            save(rows, rows.exercises.filterNot { it.id == workoutExerciseId })
        }
    }

    override suspend fun reorder(workoutExerciseIds: List<String>) =
        edit { exercises ->
            val rank = workoutExerciseIds.withIndex().associate { (index, id) -> id to index }
            exercises.sortedBy { rank[it.id] ?: Int.MAX_VALUE }
        }

    override suspend fun linkWithNext(workoutExerciseId: String) =
        edit { exercises ->
            val index = exercises.indexOfFirst { it.id == workoutExerciseId }
            if (index !in 0 until exercises.lastIndex) {
                exercises
            } else {
                exercises.withGroups(Supersets.linkWithNext(exercises.map { it.supersetGroup }, index))
            }
        }

    override suspend fun unlink(workoutExerciseId: String) =
        edit { exercises ->
            val index = exercises.indexOfFirst { it.id == workoutExerciseId }
            if (index <
                0
            ) {
                exercises
            } else {
                exercises.withGroups(Supersets.unlink(exercises.map { it.supersetGroup }, index))
            }
        }

    override suspend fun setRest(
        workoutExerciseId: String,
        seconds: Int?,
    ) = edit { exercises -> exercises.map { if (it.id == workoutExerciseId) it.copy(restSeconds = seconds) else it } }

    override suspend fun setNotes(
        workoutExerciseId: String,
        notes: String?,
    ) = edit { exercises ->
        exercises.map { if (it.id == workoutExerciseId) it.copy(notes = notes?.trim()?.ifEmpty { null }) else it }
    }

    private suspend fun edit(change: (List<WorkoutExerciseEntity>) -> List<WorkoutExerciseEntity>) {
        transactions.inTransaction {
            val rows = workoutDao.activeRows() ?: return@inTransaction
            save(rows, change(rows.exercises))
        }
    }

    /** Writes [exercises] in this order, renumbered, with supersets repaired after whatever changed. */
    private suspend fun save(
        rows: WorkoutRows,
        exercises: List<WorkoutExerciseEntity>,
    ) {
        val groups = Supersets.normalize(exercises.map { it.supersetGroup })
        val updated =
            exercises.mapIndexed {
                index,
                exercise,
                ->
                exercise.copy(position = index, supersetGroup = groups[index])
            }
        workoutDao.saveExercises(rows.exercises, updated, stamper)
    }

    private companion object {
        const val DEFAULT_SETS = 3
    }
}

private fun List<WorkoutExerciseEntity>.withGroups(groups: List<Int?>) =
    zip(groups) { exercise, group -> exercise.copy(supersetGroup = group) }
