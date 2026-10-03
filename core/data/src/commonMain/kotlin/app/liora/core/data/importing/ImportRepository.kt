package app.liora.core.data.importing

import app.liora.core.common.IdGenerator
import app.liora.core.data.backup.ExportFileException
import app.liora.core.data.backup.ExportFiles
import app.liora.core.data.exercise.ExerciseRepository
import app.liora.core.data.exercise.customName
import app.liora.core.data.exercise.editSettings
import app.liora.core.data.exercise.toEntity
import app.liora.core.data.settings.SettingsRepository
import app.liora.core.data.sync.SyncStamper
import app.liora.core.database.TransactionRunner
import app.liora.core.database.dao.ExerciseDao
import app.liora.core.database.dao.MeasurementDao
import app.liora.core.database.dao.WorkoutDao
import app.liora.core.database.model.MeasurementEntity
import app.liora.core.database.model.SyncMetadata
import app.liora.core.database.model.WorkoutEntity
import app.liora.core.database.model.WorkoutExerciseEntity
import app.liora.core.database.model.WorkoutSetEntity
import app.liora.core.domain.BodyMeasurements
import app.liora.core.domain.Supersets
import app.liora.core.domain.TrainingCalendar
import app.liora.core.domain.importing.ExerciseChoice
import app.liora.core.domain.importing.ExerciseMatcher
import app.liora.core.domain.importing.ExerciseSuggestion
import app.liora.core.domain.importing.ImportReader
import app.liora.core.domain.importing.ImportSource
import app.liora.core.domain.importing.ImportedMeasurement
import app.liora.core.domain.importing.ImportedSet
import app.liora.core.domain.importing.ImportedWorkout
import app.liora.core.domain.importing.MeasurementFile
import app.liora.core.domain.importing.SetFields
import app.liora.core.domain.importing.WorkoutFile
import app.liora.core.model.DistanceUnit
import app.liora.core.model.Exercise
import app.liora.core.model.ExerciseDraft
import app.liora.core.model.TrackingType
import app.liora.core.model.Units
import app.liora.core.model.WeightUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.datetime.TimeZone
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Workouts and body measurements from other apps' exports (see [ImportReader]). A file is read first,
 * so the user can look at what it holds and which exercise each name becomes, and saved afterwards.
 *
 * Importing a newer export from the same app adds only what's new: a workout that started in the same
 * minute as one here is here already, and so is a measurement of a type on a day that has one.
 */
interface ImportRepository {
    /**
     * Reads the export at [source] (see [ExportFiles]), with its local times in [zone]. Throws
     * [UnknownImportException] when it isn't an export Liora knows, and [ExportFileException] when it
     * can't be read.
     */
    suspend fun read(
        source: String,
        zone: TimeZone,
    ): ImportPreview

    /**
     * [preview] read again with weights in [unit], for a file that doesn't say its units (see
     * [ImportPreview.assumedWeightUnit]). Distances go along: kilometres with kilograms, miles with pounds.
     */
    suspend fun withWeightUnit(
        preview: ImportPreview,
        unit: WeightUnit,
    ): ImportPreview

    /**
     * Saves what [preview] holds that isn't here yet. Each exercise's sets go where [choices] say, by its
     * name in the file, or where the preview suggested. It finishes even when the screen that asked for it
     * closes.
     */
    suspend fun import(
        preview: ImportPreview,
        choices: Map<String, ExerciseChoice>,
    ): ImportResult
}

/** The file isn't an export Liora knows how to read. */
class UnknownImportException : Exception("Not an export from Hevy or Strong")

/** What a file holds that isn't here yet, and what each exercise in it would become. */
@Suppress("LongParameterList") // one per part of the review, and the file to read again
class ImportPreview internal constructor(
    val source: ImportSource,
    /** Workouts that aren't here yet, oldest first. */
    val workouts: List<ImportedWorkout>,
    /** Workouts in the file that are here already, and are skipped. */
    val workoutsHere: Int,
    /** Every exercise the new workouts name, by name. */
    val exercises: List<ImportedExerciseName>,
    /** Measurements that aren't here yet, oldest first. */
    val measurements: List<ImportedMeasurement>,
    /** Measurements in the file for a type and day that has one here already, and are skipped. */
    val measurementsHere: Int,
    /** The unit weights were read in when the file doesn't say; null when it does. */
    val assumedWeightUnit: WeightUnit?,
    internal val text: String,
    internal val zone: TimeZone,
) {
    val sets: Int get() = workouts.sumOf { workout -> workout.exercises.sumOf { it.sets.size } }

    /** Nothing in the file that isn't here already. */
    val isEmpty: Boolean get() = workouts.isEmpty() && measurements.isEmpty()
}

/** An exercise as the file names it, and what it becomes unless the user says otherwise. */
data class ImportedExerciseName(
    val name: String,
    /** Its sets in the new workouts. */
    val sets: Int,
    /** What those sets hold, which decides how a new exercise for them is tracked. */
    val fields: SetFields,
    val suggestion: ExerciseSuggestion,
)

data class ImportResult(
    val workouts: Int,
    val sets: Int,
    val newExercises: Int,
    val measurements: Int,
)

@Suppress("LongParameterList") // reads a file and the settings, and writes exercises, workouts and measurements
internal class OfflineImportRepository(
    private val files: ExportFiles,
    private val exercises: ExerciseRepository,
    private val settings: SettingsRepository,
    private val workoutDao: WorkoutDao,
    private val exerciseDao: ExerciseDao,
    private val measurementDao: MeasurementDao,
    private val transactions: TransactionRunner,
    private val ids: IdGenerator,
    private val stamper: SyncStamper,
    private val clock: Clock,
) : ImportRepository {
    override suspend fun read(
        source: String,
        zone: TimeZone,
    ): ImportPreview = preview(files.readText(source), settings.current().units, zone)

    override suspend fun withWeightUnit(
        preview: ImportPreview,
        unit: WeightUnit,
    ): ImportPreview {
        val distance = if (unit == WeightUnit.Pound) DistanceUnit.Mile else DistanceUnit.Kilometer
        val units = settings.current().units.copy(weight = unit, distance = distance)
        return preview(preview.text, units, preview.zone)
    }

    private suspend fun preview(
        text: String,
        units: Units,
        zone: TimeZone,
    ): ImportPreview =
        withContext(Dispatchers.Default) {
            when (val file = ImportReader.read(text, units, zone) ?: throw UnknownImportException()) {
                is WorkoutFile -> workoutPreview(file, units, text, zone)
                is MeasurementFile -> measurementPreview(file, text, zone)
            }
        }

    private suspend fun workoutPreview(
        file: WorkoutFile,
        units: Units,
        text: String,
        zone: TimeZone,
    ): ImportPreview {
        val here = workoutDao.finishedStarts().map(::minuteOf).toSet()
        val (known, new) = file.workouts.partition { minuteOf(it.startedAt) in here }
        val workouts = new.sortedBy { it.startedAt }
        val matcher = ExerciseMatcher(exercises.observeExercises(MATCHING_LANGUAGE).first())
        val names =
            workouts
                .flatMap { it.exercises }
                .groupBy { it.name }
                .map { (name, appearances) ->
                    val sets = appearances.flatMap { it.sets }
                    val fields = sets.fold(SetFields.None) { all, set -> all + set.fields }
                    ImportedExerciseName(name, sets.size, fields, matcher.suggest(name, fields))
                }.sortedBy { it.name.lowercase() }
        return ImportPreview(
            source = file.source,
            workouts = workouts,
            workoutsHere = known.size,
            exercises = names,
            measurements = emptyList(),
            measurementsHere = 0,
            assumedWeightUnit = units.weight.takeUnless { file.unitsStated },
            text = text,
            zone = zone,
        )
    }

    private suspend fun measurementPreview(
        file: MeasurementFile,
        text: String,
        zone: TimeZone,
    ): ImportPreview {
        val here = measuredDays(zone)
        val (known, new) = file.measurements.partition { (it.type.key to it.day) in here }
        return ImportPreview(
            source = file.source,
            workouts = emptyList(),
            workoutsHere = 0,
            exercises = emptyList(),
            measurements = new,
            measurementsHere = known.size,
            assumedWeightUnit = null,
            text = text,
            zone = zone,
        )
    }

    override suspend fun import(
        preview: ImportPreview,
        choices: Map<String, ExerciseChoice>,
    ): ImportResult =
        withContext(NonCancellable) {
            val known = exercises.observeExercises(MATCHING_LANGUAGE).first().associateBy { it.id }
            transactions.inTransaction {
                // Checked again: the same file may have been imported since it was read.
                val here = workoutDao.finishedStarts().map(::minuteOf).toSet()
                val workouts = preview.workouts.filter { minuteOf(it.startedAt) !in here }
                val used = workouts.flatMap { workout -> workout.exercises.map { it.name } }.toSet()
                val targets =
                    preview.exercises
                        .filter { it.name in used }
                        .associate { it.name to target(it, choices[it.name] ?: it.suggestion.choice, known) }
                var sets = 0
                var saved = 0
                for (workout in workouts) {
                    val written = save(workout, targets)
                    if (written > 0) saved++
                    sets += written
                }
                adoptNotes(exerciseDao, stamper, workouts) { name -> targets[name]?.exerciseId }
                ImportResult(
                    workouts = saved,
                    sets = sets,
                    newExercises = targets.values.count { it.isNew },
                    measurements = saveMeasurements(preview.measurements, preview.zone),
                )
            }
        }

    /** The exercise [name]'s sets go to: one here, or a new one made for them. */
    private suspend fun target(
        name: ImportedExerciseName,
        choice: ExerciseChoice,
        known: Map<String, Exercise>,
    ): Target {
        val existing = (choice as? ExerciseChoice.Existing)?.let { known[it.exerciseId] }
        if (existing != null) return Target(existing.id, existing.trackingType, isNew = false)
        val base = (choice as? ExerciseChoice.New)?.basedOn?.let(known::get)
        val trackingType = name.fields.trackingTypeFor(base?.trackingType)
        val draft =
            (base?.let { ExerciseDraft.variationOf(it, name.name) } ?: ExerciseDraft(name = name.name))
                .copy(trackingType = trackingType)
        val id = ids.newId()
        exerciseDao.upsertExercises(listOf(draft.toEntity(id, stamper.newRow())))
        exerciseDao.upsertNames(listOf(customName(id, name.name)))
        return Target(id, trackingType, isNew = true)
    }

    /**
     * Writes [workout] with the values each exercise has a column for; returns how many sets that was.
     * The sets are ticked off one after another over the workout's time, as if logged live.
     */
    private suspend fun save(
        workout: ImportedWorkout,
        targets: Map<String, Target>,
    ): Int {
        val exercises =
            workout.exercises.mapNotNull { exercise ->
                val target = targets[exercise.name] ?: return@mapNotNull null
                val sets = exercise.sets.filter { it.hasValuesFor(target.trackingType) }
                if (sets.isEmpty()) null else Triple(exercise, target, sets)
            }
        val total = exercises.sumOf { it.third.size }
        if (total == 0) return 0
        val workoutId = ids.newId()
        val start = workout.startedAt.toEpochMilliseconds()
        val end = workout.endedAt.toEpochMilliseconds()
        workoutDao.upsert(
            WorkoutEntity(
                id = workoutId,
                name = workout.name,
                startedAt = start,
                endedAt = end,
                routineId = null,
                notes = workout.notes,
                bodyweightKg = null,
                sync = stamper.newRow(),
            ),
        )
        val groups = Supersets.normalize(exercises.map { it.first.supersetGroup })
        var done = 0
        val exerciseRows = mutableListOf<WorkoutExerciseEntity>()
        val setRows = mutableListOf<WorkoutSetEntity>()
        exercises.forEachIndexed { position, (exercise, target, sets) ->
            val workoutExerciseId = ids.newId()
            exerciseRows +=
                WorkoutExerciseEntity(
                    id = workoutExerciseId,
                    workoutId = workoutId,
                    exerciseId = target.exerciseId,
                    position = position,
                    supersetGroup = groups[position],
                    restSeconds = null,
                    notes = exercise.notes,
                    sync = stamper.newRow(),
                )
            sets.forEachIndexed { index, set ->
                done++
                setRows +=
                    set.toRow(
                        id = ids.newId(),
                        workoutExerciseId = workoutExerciseId,
                        position = index,
                        type = target.trackingType,
                        completedAt = start + (end - start) * done / total,
                        sync = stamper.newRow(),
                    )
            }
        }
        workoutDao.upsertExercises(exerciseRows)
        workoutDao.upsertSets(setRows)
        return total
    }

    /** Writes the measurements for a type and day that doesn't have one yet; returns how many. */
    private suspend fun saveMeasurements(
        measurements: List<ImportedMeasurement>,
        zone: TimeZone,
    ): Int {
        if (measurements.isEmpty()) return 0
        val here = measuredDays(zone)
        val now = clock.now()
        val rows =
            measurements
                .filter { (it.type.key to it.day) !in here }
                .map {
                    MeasurementEntity(
                        id = ids.newId(),
                        takenAt = BodyMeasurements.takenAt(it.day, now, zone).toEpochMilliseconds(),
                        type = it.type.key,
                        value = it.value,
                        sync = stamper.newRow(),
                    )
                }
        measurementDao.upsertAll(rows)
        return rows.size
    }

    /** Each measured type with each day it has a value on. */
    private suspend fun measuredDays(zone: TimeZone) =
        measurementDao
            .all()
            .map { it.type to TrainingCalendar.dayOf(Instant.fromEpochMilliseconds(it.takenAt), zone) }
            .toSet()

    private class Target(
        val exerciseId: String,
        val trackingType: TrackingType,
        val isNew: Boolean,
    )

    private companion object {
        /** Any language would do: an exercise's search terms hold its names in all of them. */
        const val MATCHING_LANGUAGE = "en"
        const val MILLIS_PER_MINUTE = 60_000L

        fun minuteOf(instant: Instant): Long = minuteOf(instant.toEpochMilliseconds())

        fun minuteOf(millis: Long): Long = millis.floorDiv(MILLIS_PER_MINUTE)
    }
}

/** Whether the set holds anything [type] has a column for. */
private fun ImportedSet.hasValuesFor(type: TrackingType): Boolean =
    (weight != null && type.usesWeight) ||
        (reps != null && type.usesReps) ||
        (duration != null && type.usesDuration) ||
        (distanceMeters != null && type.usesDistance)

/** The set as a done row, with the values [type] has columns for. RPE goes with reps. */
private fun ImportedSet.toRow(
    id: String,
    workoutExerciseId: String,
    position: Int,
    type: TrackingType,
    completedAt: Long,
    sync: SyncMetadata,
) = WorkoutSetEntity(
    id = id,
    workoutExerciseId = workoutExerciseId,
    position = position,
    setType = this.type,
    weightKg = weight?.kilograms?.takeIf { type.usesWeight },
    reps = reps?.takeIf { type.usesReps },
    durationSeconds = duration?.inWholeSeconds?.toInt()?.takeIf { type.usesDuration },
    distanceMeters = distanceMeters?.takeIf { type.usesDistance },
    rpe = rpe?.takeIf { type.usesReps },
    completedAt = completedAt,
    sync = sync,
)

/**
 * Each exercise's note from the last time it was done in [workouts] becomes the exercise's own note,
 * where it has none yet; [exerciseIdOf] says which exercise a name in the file goes to. Hevy copies a
 * routine's notes into every workout, so a note that's still there last time is the plan, while older
 * ones have had their day; they stay with their workouts.
 */
private suspend fun adoptNotes(
    exerciseDao: ExerciseDao,
    stamper: SyncStamper,
    workouts: List<ImportedWorkout>,
    exerciseIdOf: (name: String) -> String?,
) {
    val lastNote = mutableMapOf<String, String?>()
    for (workout in workouts.sortedBy { it.startedAt }) {
        // An exercise done twice in a workout (once in a superset, say) has its note on one of them.
        workout.exercises.groupBy { exerciseIdOf(it.name) }.forEach { (exerciseId, done) ->
            if (exerciseId !=
                null
            ) {
                lastNote[exerciseId] = done.firstNotNullOfOrNull { it.notes?.trim()?.ifEmpty { null } }
            }
        }
    }
    for ((exerciseId, note) in lastNote) {
        if (note == null) continue
        val current = exerciseDao.getSettings(exerciseId)?.takeIf { it.sync.deletedAt == null }?.stickyNote
        if (current == null) editSettings(exerciseDao, stamper, exerciseId) { copy(stickyNote = note) }
    }
}
