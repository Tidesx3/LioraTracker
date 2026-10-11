package app.liora.core.data.importing

import app.liora.core.common.IdGenerator
import app.liora.core.data.backup.ExportFileException
import app.liora.core.data.backup.ExportFiles
import app.liora.core.data.exercise.ExerciseRepository
import app.liora.core.data.exercise.customName
import app.liora.core.data.exercise.editSettings
import app.liora.core.data.exercise.toEntity
import app.liora.core.data.importing.RoutineFileProblem.Kind
import app.liora.core.data.routine.writeNewFolder
import app.liora.core.data.routine.writeRoutine
import app.liora.core.data.sync.SyncStamper
import app.liora.core.database.TransactionRunner
import app.liora.core.database.dao.ExerciseDao
import app.liora.core.database.dao.RoutineDao
import app.liora.core.domain.Supersets
import app.liora.core.domain.importing.ExerciseChoice
import app.liora.core.domain.importing.ExerciseMatcher
import app.liora.core.domain.importing.ExerciseSuggestion
import app.liora.core.domain.importing.SetFields
import app.liora.core.model.Equipment
import app.liora.core.model.Exercise
import app.liora.core.model.ExerciseDraft
import app.liora.core.model.Routine
import app.liora.core.model.RoutineExercise
import app.liora.core.model.RoutineSet
import app.liora.core.model.TrackingType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * Routines from routine files (see [RoutineFile]), which AIs write for people. A file is read first, so
 * the user can see what it holds and which exercise each name becomes, and saved afterwards: its folder,
 * its routines, the custom exercises they need, and their notes.
 */
interface RoutineImportRepository {
    /** Reads the routine file at [source] (see [ExportFiles]). Throws [ExportFileException] when it can't be read. */
    suspend fun readFile(source: String): RoutineImportReading

    /** Reads the routine file in [text], which may be wrapped in other text such as an AI's reply. */
    suspend fun read(text: String): RoutineImportReading

    /**
     * Saves what [preview] holds. Each exercise goes where [choices] say, by its name in the file, or where
     * the preview suggested. It finishes even when the screen that asked for it closes.
     */
    suspend fun import(
        preview: RoutineImportPreview,
        choices: Map<String, ExerciseChoice>,
    ): RoutineImportResult
}

/** A routine file read and matched: ready to import, or why it can't be. */
sealed interface RoutineImportReading {
    data class Ready(
        val preview: RoutineImportPreview,
    ) : RoutineImportReading

    /** Errors first, then whatever else is worth knowing. */
    data class Invalid(
        val problems: List<RoutineFileProblem>,
    ) : RoutineImportReading
}

/** What a routine file holds, and what each exercise it names would become. */
class RoutineImportPreview internal constructor(
    val folder: FolderPlan?,
    val routines: List<RoutinePlan>,
    /** Every exercise the file names, in the order it first does. */
    val exercises: List<RoutineImportExercise>,
    /** What Liora leaves out or changes, such as a field it doesn't know. */
    val warnings: List<RoutineFileProblem>,
)

/** An exercise as the file names it, and what it becomes unless the user says otherwise. */
data class RoutineImportExercise(
    /** A custom exercise's name, an exercise's id, or a name to match, as the file has it. */
    val name: String,
    /** Its sets in the file's routines; none for a custom exercise no routine uses. */
    val sets: Int,
    /** What those sets hold, which decides how a new exercise for them is tracked. */
    val fields: SetFields,
    val suggestion: ExerciseSuggestion,
    /** How the file defines it, for one it brings along. */
    val custom: CustomExercisePlan?,
    /** Named by its id: it's that exercise, with nothing to choose. */
    val fixed: Boolean,
)

data class RoutineImportResult(
    /** The routines made, in the file's order. */
    val routineIds: List<String>,
    val newExercises: Int,
)

@Suppress("LongParameterList") // reads a file and the exercises, and writes exercises, folders and routines
internal class OfflineRoutineImportRepository(
    private val files: ExportFiles,
    private val exercises: ExerciseRepository,
    private val exerciseDao: ExerciseDao,
    private val routineDao: RoutineDao,
    private val transactions: TransactionRunner,
    private val ids: IdGenerator,
    private val stamper: SyncStamper,
) : RoutineImportRepository {
    override suspend fun readFile(source: String): RoutineImportReading = read(files.readText(source))

    override suspend fun read(text: String): RoutineImportReading =
        withContext(Dispatchers.Default) {
            when (val result = RoutineFileReader.read(text)) {
                is RoutineFileResult.Invalid -> RoutineImportReading.Invalid(result.problems.sortedBy { !it.isError })
                is RoutineFileResult.Read -> preview(result.file)
            }
        }

    private suspend fun preview(file: RoutineFile): RoutineImportReading {
        val all = exercises.observeExercises(MATCHING_LANGUAGE).first()
        val matching = Matching(file, all)
        val named =
            file.routines
                .flatMap { it.exercises }
                .groupBy { file.customExercise(it.exercise)?.name ?: it.exercise }
        // Custom exercises no routine uses are made too: a file may bring only exercises.
        val unused =
            file.customExercises
                .filter {
                    it.name !in named
                }.associate { it.name to emptyList<ExercisePlan>() }
        val rows = (named + unused).map { (name, planned) -> matching.row(name, planned) }
        if (matching.problems.any { it.isError }) {
            return RoutineImportReading.Invalid((matching.problems + file.warnings).sortedBy { !it.isError })
        }
        return RoutineImportReading.Ready(
            RoutineImportPreview(
                folder = file.folder,
                routines = file.routines,
                exercises = rows,
                warnings = file.warnings + matching.problems,
            ),
        )
    }

    override suspend fun import(
        preview: RoutineImportPreview,
        choices: Map<String, ExerciseChoice>,
    ): RoutineImportResult =
        withContext(NonCancellable) {
            val known = exercises.observeExercises(MATCHING_LANGUAGE).first().associateBy { it.id }
            transactions.inTransaction {
                val targets =
                    preview.exercises.associate { row ->
                        row.name to target(row, choices[row.name] ?: row.suggestion.choice, known)
                    }
                adoptNotes(preview, targets)
                val folderId =
                    preview.folder?.let { folder ->
                        ids.newId().also { writeNewFolder(routineDao, stamper, it, folder.name, folder.rotation) }
                    }
                val routineIds =
                    preview.routines.map { plan ->
                        val routine = routineOf(plan, folderId) { name -> targets.getValue(nameIn(preview, name)) }
                        writeRoutine(routineDao, stamper, routine)
                        routine.id
                    }
                RoutineImportResult(routineIds, newExercises = targets.values.count { it.isNew })
            }
        }

    /**
     * The routine [plan] plans, in [folderId], with each exercise where [targetOf] says. Targets an exercise
     * has no column for are left out, and so is progression where it doesn't fit.
     */
    private fun routineOf(
        plan: RoutinePlan,
        folderId: String?,
        targetOf: (name: String) -> Target,
    ): Routine {
        val groups = supersetGroups(plan.exercises.map { it.superset })
        return Routine(
            id = ids.newId(),
            name = plan.name,
            folderId = folderId,
            notes = plan.notes,
            exercises =
                plan.exercises.mapIndexed { index, planned ->
                    val target = targetOf(planned.exercise)
                    RoutineExercise(
                        id = ids.newId(),
                        exerciseId = target.exerciseId,
                        supersetGroup = groups[index],
                        restSeconds = planned.restSeconds,
                        sets = planned.sets.map { it.toRoutineSet(ids.newId(), target.trackingType) },
                        progression = planned.progression?.takeIf { it.fits(target.trackingType) },
                    )
                },
        )
    }

    /** The exercise [row] goes to: one here, or a new one made for it. */
    private suspend fun target(
        row: RoutineImportExercise,
        choice: ExerciseChoice,
        known: Map<String, Exercise>,
    ): Target {
        val existing = (choice as? ExerciseChoice.Existing)?.let { known[it.exerciseId] }
        if (existing != null) return Target(existing.id, existing.trackingType, isNew = false)
        val base = (choice as? ExerciseChoice.New)?.basedOn?.let(known::get)
        val draft = row.custom?.let { draftOf(it, base, row.fields) } ?: draftOf(row.name, base, row.fields)
        val id = ids.newId()
        exerciseDao.upsertExercises(listOf(draft.toEntity(id, stamper.newRow())))
        exerciseDao.upsertNames(listOf(customName(id, draft.name)))
        val note = draft.notes.trim().ifEmpty { null }
        val step = row.custom?.loadStep?.kilograms
        if (note != null || step != null) {
            editSettings(exerciseDao, stamper, id) { copy(stickyNote = note, loadStepKg = step) }
        }
        return Target(id, draft.trackingType, isNew = true)
    }

    /**
     * Each note the file gives an exercise becomes its own note, where it has none yet: it shows wherever the
     * exercise does, so one the user wrote is never replaced. New exercises got theirs as they were made.
     */
    private suspend fun adoptNotes(
        preview: RoutineImportPreview,
        targets: Map<String, Target>,
    ) {
        val fromCustom = preview.exercises.mapNotNull { row -> row.custom?.note?.let { row.name to it } }
        val fromRoutines =
            preview.routines.flatMap { it.exercises }.mapNotNull { planned ->
                planned.note?.let { nameIn(preview, planned.exercise) to it }
            }
        (fromCustom + fromRoutines)
            .mapNotNull { (name, note) -> targets[name]?.let { it.exerciseId to note } }
            .distinctBy { it.first }
            .forEach { (exerciseId, note) ->
                val current = exerciseDao.getSettings(exerciseId)?.takeIf { it.sync.deletedAt == null }?.stickyNote
                if (current == null) editSettings(exerciseDao, stamper, exerciseId) { copy(stickyNote = note.trim()) }
            }
    }

    /** Matches the names in a file to the exercises here, noting what doesn't add up. */
    private class Matching(
        private val file: RoutineFile,
        exercises: List<Exercise>,
    ) {
        private val byId = exercises.associateBy { it.id }
        private val matcher = ExerciseMatcher(exercises)
        val problems = mutableListOf<RoutineFileProblem>()

        fun row(
            name: String,
            planned: List<ExercisePlan>,
        ): RoutineImportExercise {
            val sets = planned.flatMap { it.sets }
            val fields = sets.fold(SetFields.None) { all, set -> all + set.fields }
            val custom = file.customExercise(name)
            val exercise = byId[name]
            val suggestion =
                when {
                    custom != null -> customSuggestion(custom, fields)
                    exercise != null -> ExerciseSuggestion(ExerciseChoice.Existing(exercise.id), exercise.id)
                    else -> nameSuggestion(name, fields)
                }
            return RoutineImportExercise(name, sets.size, fields, suggestion, custom, fixed = exercise != null)
        }

        /**
         * One the file brings along: the user's own of that name if there is one, so importing twice doesn't
         * make two, else a new one, based on what it's a variation of.
         */
        private fun customSuggestion(
            custom: CustomExercisePlan,
            fields: SetFields,
        ): ExerciseSuggestion {
            val base = custom.variationOf?.let(::resolve)
            if (custom.variationOf != null && base == null) {
                problems +=
                    RoutineFileProblem(Kind.BaseNotFound, ProblemPlace(exercise = custom.name), custom.variationOf)
            }
            val same = matcher.suggest(custom.name, custom.trackingType?.let(::fieldsOf) ?: fields)
            return if (same.choice is ExerciseChoice.Existing) {
                same
            } else {
                ExerciseSuggestion(ExerciseChoice.New(basedOn = base), base ?: same.closest)
            }
        }

        private fun nameSuggestion(
            name: String,
            fields: SetFields,
        ): ExerciseSuggestion {
            // An id that names no exercise here is a mistake, not a name for a new exercise.
            if (ID_PREFIXES.any { name.startsWith(it) }) {
                problems += RoutineFileProblem(Kind.Invalid, ProblemPlace(exercise = name, field = "exercise"), name)
            }
            val suggestion = matcher.suggest(name, fields)
            if (suggestion.choice is ExerciseChoice.Existing) return suggestion
            // Unlike a logged set, a planned target is only a plan: an exercise of the same name that has no
            // column for one (weight on a plank) is still the one meant, and the target is left out.
            val same = matcher.suggest(name, SetFields.None)
            return if (same.choice is ExerciseChoice.Existing) same else suggestion
        }

        /** The exercise here that a name or id means, or null. */
        private fun resolve(reference: String): String? =
            byId[reference]?.id
                ?: (matcher.suggest(reference, SetFields.None).choice as? ExerciseChoice.Existing)?.exerciseId
    }

    private class Target(
        val exerciseId: String,
        val trackingType: TrackingType,
        val isNew: Boolean,
    )

    private companion object {
        /** Any language would do: an exercise's search terms hold its names in all of them. */
        const val MATCHING_LANGUAGE = "en"

        /** How built-in exercises' ids start. */
        val ID_PREFIXES = listOf("fedb.", "liora.")
    }
}

/** The name the preview lists the exercise a routine names under: a custom exercise's own, else as written. */
private fun nameIn(
    preview: RoutineImportPreview,
    name: String,
): String =
    preview.exercises.firstOrNull { it.custom != null && it.name.equals(name.trim(), ignoreCase = true) }?.name ?: name

/** A new exercise the file defines: what it says, and the rest from what it's a variation of. */
private fun draftOf(
    custom: CustomExercisePlan,
    base: Exercise?,
    fields: SetFields,
) = ExerciseDraft(
    name = custom.name,
    trackingType = custom.trackingType ?: fields.trackingTypeFor(base?.trackingType),
    equipment = custom.equipment ?: base?.equipment ?: Equipment.Other,
    primaryMuscles = custom.primaryMuscles ?: base?.primaryMuscles.orEmpty(),
    secondaryMuscles = custom.secondaryMuscles ?: base?.secondaryMuscles.orEmpty(),
    notes = custom.note.orEmpty(),
    variationOf = base?.let { it.variationOf ?: it.id.takeUnless { _ -> it.isCustom } },
)

/** A new exercise for a name nothing here has, like one from another app's export. */
private fun draftOf(
    name: String,
    base: Exercise?,
    fields: SetFields,
): ExerciseDraft =
    (base?.let { ExerciseDraft.variationOf(it, name) } ?: ExerciseDraft(name = name))
        .copy(trackingType = fields.trackingTypeFor(base?.trackingType))

/** The values a set of an exercise tracked as [type] has. */
private fun fieldsOf(type: TrackingType) =
    SetFields(type.usesWeight, type.usesReps, type.usesDuration, type.usesDistance)

/** The plan's targets that an exercise tracked as [type] has columns for; RPE goes with reps. */
private fun SetPlan.toRoutineSet(
    id: String,
    type: TrackingType,
) = RoutineSet(
    id = id,
    type = this.type,
    weight = weight.takeIf { type.usesWeight },
    reps = reps.takeIf { type.usesReps },
    duration = duration.takeIf { type.usesDuration },
    distanceMeters = distanceMeters.takeIf { type.usesDistance },
    rpe = rpe.takeIf { type.usesReps },
)

/** Superset labels as the groups [Supersets] numbers: the same label, the same group, runs apart split. */
private fun supersetGroups(labels: List<String?>): List<Int?> {
    val numbers =
        labels
            .filterNotNull()
            .distinct()
            .withIndex()
            .associate { (index, label) -> label to index + 1 }
    return Supersets.normalize(labels.map { it?.let(numbers::getValue) })
}
