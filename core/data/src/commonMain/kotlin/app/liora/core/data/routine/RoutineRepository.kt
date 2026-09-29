package app.liora.core.data.routine

import app.liora.core.common.IdGenerator
import app.liora.core.data.sync.SyncStamper
import app.liora.core.database.TransactionRunner
import app.liora.core.database.dao.RoutineDao
import app.liora.core.database.model.RoutineEntity
import app.liora.core.database.model.RoutineExerciseEntity
import app.liora.core.database.model.RoutineFolderEntity
import app.liora.core.database.model.RoutineSetEntity
import app.liora.core.database.model.SyncMetadata
import app.liora.core.database.model.SyncedRow
import app.liora.core.model.Mass
import app.liora.core.model.RepRange
import app.liora.core.model.Routine
import app.liora.core.model.RoutineExercise
import app.liora.core.model.RoutineFolder
import app.liora.core.model.RoutineSet
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlin.time.Duration.Companion.seconds

/** Routines and their folders. Unlimited: no caps on routines, folders, exercises or sets. */
interface RoutineRepository {
    val folders: Flow<List<RoutineFolder>>

    /** Every routine with its exercises and sets, in display order. */
    val routines: Flow<List<Routine>>

    fun observeRoutine(id: String): Flow<Routine?>

    suspend fun get(id: String): Routine?

    /**
     * Creates or updates [routine] with its exercises and sets. Rows that didn't change keep their
     * sync stamp, so an edit only syncs what was edited; removed exercises and sets become tombstones.
     */
    suspend fun save(routine: Routine)

    /** Copies a routine under [name], placed last, and returns the copy's id. */
    suspend fun duplicate(
        id: String,
        name: String,
    ): String

    suspend fun delete(id: String)

    /** Moves a routine into [folderId] (null: out of any folder), at the end. */
    suspend fun moveToFolder(
        routineId: String,
        folderId: String?,
    )

    suspend fun createFolder(name: String): String

    suspend fun renameFolder(
        id: String,
        name: String,
    )

    /** Deletes a folder. Its routines move out of it rather than disappearing with it. */
    suspend fun deleteFolder(id: String)
}

internal class OfflineRoutineRepository(
    private val routineDao: RoutineDao,
    private val transactions: TransactionRunner,
    private val ids: IdGenerator,
    private val stamper: SyncStamper,
) : RoutineRepository {
    override val folders: Flow<List<RoutineFolder>> =
        routineDao.observeFolders().map { rows -> rows.map { RoutineFolder(id = it.id, name = it.name) } }

    override val routines: Flow<List<Routine>> =
        combine(
            routineDao.observeRoutines(),
            routineDao.observeExercises(),
            routineDao.observeSets(),
            ::assemble,
        )

    override fun observeRoutine(id: String): Flow<Routine?> =
        routines.map { all -> all.firstOrNull { it.id == id } }.distinctUntilChanged()

    override suspend fun get(id: String): Routine? = observeRoutine(id).first()

    override suspend fun save(routine: Routine) {
        require(routine.isValid) { "A routine needs a name" }
        transactions.inTransaction {
            val existing = routineDao.getRoutine(routine.id)?.takeIf { it.sync.deletedAt == null }
            val row =
                RoutineEntity(
                    id = routine.id,
                    folderId = routine.folderId,
                    name = routine.name.trim(),
                    notes = routine.notes?.trim()?.ifEmpty { null },
                    position = existing?.position ?: (routineDao.maxRoutinePosition() + 1),
                    sync = existing?.sync ?: NO_SYNC,
                )
            stamped(listOf(row), listOfNotNull(existing)) { entity, sync -> entity.copy(sync = sync) }
                .forEach { routineDao.upsertRoutine(it) }

            // Read both before writing: once an exercise is tombstoned, its sets no longer show up.
            val storedExercises = routineDao.exercisesOf(routine.id)
            val storedSets = routineDao.setsOf(routine.id)
            val exerciseRows = routine.exercises.mapIndexed { index, exercise -> exercise.toEntity(routine.id, index) }
            val setRows =
                routine.exercises.flatMap { exercise ->
                    exercise.sets.mapIndexed { index, set -> set.toEntity(exercise.id, index) }
                }
            routineDao.upsertExercises(
                stamped(exerciseRows, storedExercises) { entity, sync -> entity.copy(sync = sync) },
            )
            routineDao.upsertSets(stamped(setRows, storedSets) { entity, sync -> entity.copy(sync = sync) })
        }
    }

    override suspend fun duplicate(
        id: String,
        name: String,
    ): String {
        val original = requireNotNull(get(id)) { "No routine $id" }
        val copy =
            original.copy(
                id = ids.newId(),
                name = name,
                exercises =
                    original.exercises.map { exercise ->
                        exercise.copy(
                            id = ids.newId(),
                            sets = exercise.sets.map { it.copy(id = ids.newId()) },
                        )
                    },
            )
        save(copy)
        return copy.id
    }

    override suspend fun delete(id: String) {
        transactions.inTransaction {
            val routine = routineDao.getRoutine(id)?.takeIf { it.sync.deletedAt == null } ?: return@inTransaction
            routineDao.upsertSets(routineDao.setsOf(id).map { it.copy(sync = stamper.tombstone(it.sync)) })
            routineDao.upsertExercises(routineDao.exercisesOf(id).map { it.copy(sync = stamper.tombstone(it.sync)) })
            routineDao.upsertRoutine(routine.copy(sync = stamper.tombstone(routine.sync)))
        }
    }

    override suspend fun moveToFolder(
        routineId: String,
        folderId: String?,
    ) {
        transactions.inTransaction {
            val routine = routineDao.getRoutine(routineId) ?: return@inTransaction
            if (routine.folderId == folderId) return@inTransaction
            routineDao.upsertRoutine(
                routine.copy(
                    folderId = folderId,
                    position = routineDao.maxRoutinePosition() + 1,
                    sync = stamper.touch(routine.sync),
                ),
            )
        }
    }

    override suspend fun createFolder(name: String): String {
        require(name.isNotBlank()) { "A folder needs a name" }
        val id = ids.newId()
        transactions.inTransaction {
            routineDao.upsertFolder(
                RoutineFolderEntity(
                    id = id,
                    name = name.trim(),
                    position = routineDao.maxFolderPosition() + 1,
                    sync = stamper.newRow(),
                ),
            )
        }
        return id
    }

    override suspend fun renameFolder(
        id: String,
        name: String,
    ) {
        require(name.isNotBlank()) { "A folder needs a name" }
        transactions.inTransaction {
            val folder = routineDao.getFolder(id) ?: return@inTransaction
            routineDao.upsertFolder(folder.copy(name = name.trim(), sync = stamper.touch(folder.sync)))
        }
    }

    override suspend fun deleteFolder(id: String) {
        transactions.inTransaction {
            val folder = routineDao.getFolder(id)?.takeIf { it.sync.deletedAt == null } ?: return@inTransaction
            var position = routineDao.maxRoutinePosition()
            routineDao.routinesInFolder(id).sortedBy { it.position }.forEach { routine ->
                position++
                routineDao.upsertRoutine(
                    routine.copy(folderId = null, position = position, sync = stamper.touch(routine.sync)),
                )
            }
            routineDao.upsertFolder(folder.copy(sync = stamper.tombstone(folder.sync)))
        }
    }

    /**
     * The writes that turn the [stored] rows into [rows]: new rows get a fresh sync stamp, changed ones
     * a touched stamp, and stored rows missing from [rows] become tombstones. Unchanged rows are left
     * out, so they are neither rewritten nor synced again.
     */
    private suspend inline fun <E : SyncedRow> stamped(
        rows: List<E>,
        stored: List<E>,
        withSync: (E, SyncMetadata) -> E,
    ): List<E> {
        val storedById = stored.associateBy { it.id }
        val keptIds = rows.map { it.id }.toSet()
        val upserts =
            rows.mapNotNull { row ->
                val previous = storedById[row.id]
                when {
                    previous == null -> withSync(row, stamper.newRow())
                    withSync(row, previous.sync) == previous -> null
                    else -> withSync(row, stamper.touch(previous.sync))
                }
            }
        val tombstones = stored.filter { it.id !in keptIds }.map { withSync(it, stamper.tombstone(it.sync)) }
        return upserts + tombstones
    }
}

/** Placeholder until [OfflineRoutineRepository.stamped] assigns the real stamp. */
private val NO_SYNC = SyncMetadata(createdAt = 0, hlc = "")

private fun RoutineExercise.toEntity(
    routineId: String,
    position: Int,
) = RoutineExerciseEntity(
    id = id,
    routineId = routineId,
    exerciseId = exerciseId,
    position = position,
    supersetGroup = supersetGroup,
    restSeconds = restSeconds,
    notes = notes?.trim()?.ifEmpty { null },
    sync = NO_SYNC,
)

private fun RoutineSet.toEntity(
    routineExerciseId: String,
    position: Int,
) = RoutineSetEntity(
    id = id,
    routineExerciseId = routineExerciseId,
    position = position,
    setType = type,
    targetWeightKg = weight?.kilograms,
    targetRepsMin = reps?.min,
    targetRepsMax = reps?.max,
    targetDurationSeconds = duration?.inWholeSeconds?.toInt(),
    targetDistanceMeters = distanceMeters,
    targetRpe = rpe,
    sync = NO_SYNC,
)

private fun assemble(
    routines: List<RoutineEntity>,
    exercises: List<RoutineExerciseEntity>,
    sets: List<RoutineSetEntity>,
): List<Routine> {
    val setsByExercise = sets.groupBy { it.routineExerciseId }
    val exercisesByRoutine = exercises.groupBy { it.routineId }
    return routines.map { routine ->
        Routine(
            id = routine.id,
            name = routine.name,
            folderId = routine.folderId,
            notes = routine.notes,
            exercises =
                exercisesByRoutine[routine.id].orEmpty().sortedBy { it.position }.map { exercise ->
                    RoutineExercise(
                        id = exercise.id,
                        exerciseId = exercise.exerciseId,
                        supersetGroup = exercise.supersetGroup,
                        restSeconds = exercise.restSeconds,
                        notes = exercise.notes,
                        sets = setsByExercise[exercise.id].orEmpty().sortedBy { it.position }.map { it.toModel() },
                    )
                },
        )
    }
}

private fun RoutineSetEntity.toModel() =
    RoutineSet(
        id = id,
        type = setType,
        weight = targetWeightKg?.let(::Mass),
        reps = RepRange.of(targetRepsMin, targetRepsMax),
        duration = targetDurationSeconds?.seconds,
        distanceMeters = targetDistanceMeters,
        rpe = targetRpe,
    )
