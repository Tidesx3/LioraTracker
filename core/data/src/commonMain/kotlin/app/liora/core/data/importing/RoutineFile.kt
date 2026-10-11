package app.liora.core.data.importing

import app.liora.core.domain.importing.SetFields
import app.liora.core.model.Equipment
import app.liora.core.model.Mass
import app.liora.core.model.Muscle
import app.liora.core.model.ProgressionRule
import app.liora.core.model.RepRange
import app.liora.core.model.SetType
import app.liora.core.model.TrackingType
import kotlin.time.Duration

/*
 * Routine files: routines written for Liora to import, mostly by AIs, in the public format described in
 * docs/routine-format.md (format "liora-routine", version 1). A file holds one or more routines, the folder
 * they go in, and the custom exercises they need. Exercises are named the way people and AIs name them, and
 * matched to the ones here when the file is previewed (see RoutineImportRepository), like an import from Hevy.
 *
 * The format is meant to be written by hand and by AIs, so values come in the forms people write: weights
 * with or without their unit ("80 kg"), rep ranges ("8-12"), times ("1:30", "90s") and distances ("5 km").
 * Everything is checked before anything is saved, and every problem is reported with where it is, so whoever
 * wrote the file can fix it.
 */

/** What a routine file asks for, read and checked, before its exercises are matched to the ones here. */
data class RoutineFile(
    val folder: FolderPlan?,
    val customExercises: List<CustomExercisePlan>,
    val routines: List<RoutinePlan>,
    /** What the file says that Liora leaves out or changes, such as a field it doesn't know. */
    val warnings: List<RoutineFileProblem>,
) {
    /** The custom exercise a routine names, by its name in any case. */
    fun customExercise(name: String): CustomExercisePlan? =
        customExercises.firstOrNull { it.name.equals(name.trim(), ignoreCase = true) }
}

/** A new folder for every routine in the file. */
data class FolderPlan(
    val name: String,
    /** Its routines take turns in their order. */
    val rotation: Boolean = false,
)

/** An exercise the file defines, for routines that need one Liora doesn't have. */
data class CustomExercisePlan(
    val name: String,
    /** The exercise it's a variation of, as the file names it: a name or an id. */
    val variationOf: String? = null,
    /** Null takes the base exercise's. */
    val trackingType: TrackingType? = null,
    val equipment: Equipment? = null,
    val primaryMuscles: Set<Muscle>? = null,
    val secondaryMuscles: Set<Muscle>? = null,
    /** The exercise's own note. */
    val note: String? = null,
    /** A machine or cable's own load step. */
    val loadStep: Mass? = null,
)

data class RoutinePlan(
    val name: String,
    val notes: String? = null,
    val exercises: List<ExercisePlan>,
)

/** An exercise in a routine, as the file plans it. */
data class ExercisePlan(
    /** The exercise as the file names it: a custom exercise's name, an id, or a name to match. */
    val exercise: String,
    /** Exercises next to each other with the same label are a superset. */
    val superset: String? = null,
    val restSeconds: Int? = null,
    val progression: ProgressionRule? = null,
    /** The exercise's own note, for an exercise that has none yet. */
    val note: String? = null,
    val sets: List<SetPlan>,
)

/** A planned set, in stored units; targets the file doesn't give are null. */
data class SetPlan(
    val type: SetType = SetType.Normal,
    val weight: Mass? = null,
    val reps: RepRange? = null,
    val duration: Duration? = null,
    val distanceMeters: Double? = null,
    val rpe: Double? = null,
) {
    /** Which targets it has, which decides the columns an exercise needs for it. */
    val fields: SetFields
        get() = SetFields(weight != null, reps != null, duration != null, distanceMeters != null)
}

/** Something wrong with a routine file, or worth knowing about it, and where in the file it is. */
data class RoutineFileProblem(
    val kind: Kind,
    val place: ProblemPlace = ProblemPlace(),
    /** What the file has there, or the name the problem is about. */
    val value: String? = null,
) {
    val isError: Boolean get() = kind.isError

    enum class Kind(
        /** Errors keep the file from being imported; the others are only worth knowing. */
        val isError: Boolean,
    ) {
        /** There's no JSON object in the text, or it's broken. */
        NotJson(isError = true),

        /** JSON, but not a routine file. */
        NotARoutineFile(isError = true),

        /** A file for a newer Liora; [value] is its version. */
        NewerVersion(isError = true),

        /** [ProblemPlace.field] is needed but missing. */
        Missing(isError = true),

        /** [ProblemPlace.field] holds [value], which isn't something Liora can read there. */
        Invalid(isError = true),

        /** A weight is a bare number, and the file doesn't say its unit with `weightUnit`. */
        NoWeightUnit(isError = true),

        /** Two custom exercises share the name [value]. */
        DuplicateExercise(isError = true),

        /** The file has a field Liora doesn't know, [value]; it's left out. */
        UnknownField(isError = false),

        /** Exercises with superset label [value] aren't next to each other; each run is a superset of its own. */
        SupersetApart(isError = false),

        /** Progression is on, but a working set has no rep range to climb through. */
        ProgressionNeedsRanges(isError = false),

        /** A custom exercise is a variation of [value], which isn't an exercise here; it's made on its own. */
        BaseNotFound(isError = false),
    }
}

/**
 * Where a problem is: the routine, the exercise and the set, by name or by number ("#2") where the file
 * doesn't name it, and the field as the file spells it. All null is the file itself.
 */
data class ProblemPlace(
    val routine: String? = null,
    /** An exercise in a routine as the file names it, or a custom exercise. */
    val exercise: String? = null,
    /** The set's place in the exercise's list, from 1. */
    val set: Int? = null,
    val field: String? = null,
)
