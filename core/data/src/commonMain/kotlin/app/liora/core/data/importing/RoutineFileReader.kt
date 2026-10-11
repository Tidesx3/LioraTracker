package app.liora.core.data.importing

import app.liora.core.data.importing.RoutineFileProblem.Kind
import app.liora.core.model.Equipment
import app.liora.core.model.Mass
import app.liora.core.model.Muscle
import app.liora.core.model.ProgressionRule
import app.liora.core.model.SetType
import app.liora.core.model.TrackingType
import app.liora.core.model.WeightUnit
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlin.time.Duration.Companion.seconds

/** A routine file read from text: what it holds, or why it can't be imported. */
sealed interface RoutineFileResult {
    data class Read(
        val file: RoutineFile,
    ) : RoutineFileResult

    /** The file can't be imported. [problems] says why, with whatever else is worth knowing. */
    data class Invalid(
        val problems: List<RoutineFileProblem>,
    ) : RoutineFileResult
}

/** Reads routine files (see [RoutineFile]). */
object RoutineFileReader {
    const val FORMAT = "liora-routine"
    const val VERSION = 1

    @OptIn(ExperimentalSerializationApi::class)
    private val json =
        Json {
            isLenient = true
            allowTrailingComma = true
            allowComments = true
        }

    /**
     * The routine file in [text]. The text may hold more than the file, such as an AI's reply around it or
     * the fences of a code block: the JSON object in it is what's read.
     */
    fun read(text: String): RoutineFileResult {
        val document = jsonObjectIn(text) ?: return invalid(RoutineFileProblem(Kind.NotJson))
        val problem = whatItIsnt(document)
        if (problem != null) return invalid(problem)
        // A single routine on its own is read as a file with one routine.
        return FileReader(document, single = document.isSingleRoutine).read()
    }

    /** Why [document] isn't a routine file this Liora reads, or null when it is one. */
    private fun whatItIsnt(document: JsonObject): RoutineFileProblem? {
        val format = (document["format"] as? JsonPrimitive)?.content
        val holdsSomething =
            document["routines"] != null || document.isSingleRoutine || document["customExercises"] != null
        val version = (document["version"] as? JsonPrimitive)?.content
        return when {
            (format != null && format != FORMAT) || !holdsSomething -> {
                RoutineFileProblem(Kind.NotARoutineFile)
            }

            version != null && (version.toIntOrNull() ?: Int.MAX_VALUE) > VERSION -> {
                RoutineFileProblem(Kind.NewerVersion, value = version)
            }

            else -> {
                null
            }
        }
    }

    private val JsonObject.isSingleRoutine: Boolean get() = this["routines"] == null && this["exercises"] is JsonArray

    private fun jsonObjectIn(text: String): JsonObject? {
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        if (start < 0 || end < start) return null
        return try {
            json.parseToJsonElement(text.substring(start, end + 1)) as? JsonObject
        } catch (_: SerializationException) {
            null
        }
    }

    private fun invalid(problem: RoutineFileProblem) = RoutineFileResult.Invalid(listOf(problem))
}

/** Reads one file, collecting every problem rather than stopping at the first. */
@Suppress("TooManyFunctions") // one function per part of the format
private class FileReader(
    private val document: JsonObject,
    /** The document is a routine itself, not a file of routines. */
    private val single: Boolean,
) {
    private val problems = mutableListOf<RoutineFileProblem>()

    /** What bare numbers in weights mean; null when the file doesn't say. */
    private var weightUnit: WeightUnit? = null

    fun read(): RoutineFileResult {
        val here = ProblemPlace()
        checkFields(document, if (single) FILE_FIELDS + ROUTINE_FIELDS else FILE_FIELDS, here)
        weightUnit =
            text(document, "weightUnit", here)?.let { name ->
                weightUnit(name) ?: null.also { invalid(here.copy(field = "weightUnit"), name) }
            }
        val folder = readFolder(document["folder"])
        val customExercises = list(document, "customExercises", here).mapIndexedNotNull(::readCustomExercise)
        customExercises
            .groupBy { it.name.lowercase() }
            .filterValues { it.size > 1 }
            .forEach { (_, same) -> problems += RoutineFileProblem(Kind.DuplicateExercise, value = same.first().name) }
        val routines =
            if (single) {
                listOfNotNull(readRoutine(0, document))
            } else {
                // A file may bring only exercises.
                list(document, "routines", here, required = customExercises.isEmpty()).mapIndexedNotNull(::readRoutine)
            }
        return if (problems.any { it.isError }) {
            RoutineFileResult.Invalid(problems.toList())
        } else {
            RoutineFileResult.Read(RoutineFile(folder, customExercises, routines, problems.toList()))
        }
    }

    private fun readFolder(element: JsonElement?): FolderPlan? {
        val here = ProblemPlace(field = "folder")
        return when (element) {
            null, JsonNull -> {
                null
            }

            is JsonPrimitive -> {
                element.content
                    .trim()
                    .ifEmpty { null }
                    ?.let(::FolderPlan)
            }

            is JsonObject -> {
                checkFields(element, FOLDER_FIELDS, here)
                val name = text(element, "name", here, required = true) ?: return null
                FolderPlan(name, rotation = flag(element, "rotation", here))
            }

            else -> {
                null.also { invalid(here, element.toString()) }
            }
        }
    }

    private fun readCustomExercise(
        index: Int,
        element: JsonElement,
    ): CustomExercisePlan? {
        val unnamed = ProblemPlace(exercise = "#${index + 1}")
        val fields = element as? JsonObject ?: return null.also { invalid(unnamed, element.toString()) }
        val name = text(fields, "name", unnamed, required = true) ?: return null
        val here = ProblemPlace(exercise = name)
        checkFields(fields, CUSTOM_EXERCISE_FIELDS, here)
        val variationOf = text(fields, "variationOf", here)
        val tracking = key(fields, "tracking", here, TrackingType::fromKey)
        if (tracking == null && variationOf == null && "tracking" !in fields) missing(here.copy(field = "tracking"))
        return CustomExercisePlan(
            name = name,
            variationOf = variationOf,
            trackingType = tracking,
            equipment = key(fields, "equipment", here, Equipment::fromKey),
            primaryMuscles = muscles(fields, "primaryMuscles", here),
            secondaryMuscles = muscles(fields, "secondaryMuscles", here),
            note = text(fields, "note", here) ?: text(fields, "notes", here),
            loadStep = weight(fields, "loadStep", here),
        )
    }

    private fun readRoutine(
        index: Int,
        element: JsonElement,
    ): RoutinePlan? {
        val unnamed = ProblemPlace(routine = "#${index + 1}")
        val fields = element as? JsonObject ?: return null.also { invalid(unnamed, element.toString()) }
        val name = text(fields, "name", unnamed, required = true)
        val here = ProblemPlace(routine = name ?: unnamed.routine)
        if (!single) checkFields(fields, ROUTINE_FIELDS, here)
        val exercises =
            list(fields, "exercises", here, required = true).mapIndexedNotNull { position, exercise ->
                readExercise(here, position, exercise)
            }
        checkSupersets(here, exercises)
        return name?.let { RoutinePlan(it, text(fields, "notes", here), exercises) }
    }

    private fun readExercise(
        routine: ProblemPlace,
        index: Int,
        element: JsonElement,
    ): ExercisePlan? {
        val unnamed = routine.copy(exercise = "#${index + 1}")
        val fields = element as? JsonObject ?: return null.also { invalid(unnamed, element.toString()) }
        val name = text(fields, "exercise", unnamed) ?: text(fields, "name", unnamed)
        if (name == null) missing(unnamed.copy(field = "exercise"))
        val here = routine.copy(exercise = name ?: unnamed.exercise)
        val rest = number(fields, "rest", here) { seconds(it)?.takeIf { rest -> rest in REST_SECONDS } }
        val sets = readSets(here, fields)
        val progression = progression(fields, here)
        if (progression != null && sets.any { it.type.progresses && it.reps?.isRange != true }) {
            problems += RoutineFileProblem(Kind.ProgressionNeedsRanges, here)
        }
        return ExercisePlan(
            exercise = name ?: return null,
            superset = (fields["superset"] as? JsonPrimitive)?.takeUnless { it is JsonNull }?.content?.trim(),
            restSeconds = rest,
            progression = progression,
            note = text(fields, "note", here) ?: text(fields, "notes", here),
            sets = sets,
        )
    }

    /**
     * The sets of an exercise: a list, or a number of sets alike. Targets on the exercise itself apply to
     * every set that doesn't give its own.
     */
    private fun readSets(
        here: ProblemPlace,
        fields: JsonObject,
    ): List<SetPlan> {
        checkFields(fields, EXERCISE_FIELDS + TARGET_FIELDS, here)
        val shared = readTargets(here, fields)
        return when (val sets = fields["sets"]) {
            null, JsonNull -> {
                emptyList<SetPlan>().also { missing(here.copy(field = "sets")) }
            }

            is JsonPrimitive -> {
                val count = number(fields, "sets", here, ::count) ?: 0
                List(count) { shared }
            }

            is JsonArray -> {
                if (sets.isEmpty()) missing(here.copy(field = "sets"))
                sets.flatMapIndexed { index, set -> readSetEntry(here.copy(set = index + 1), set, fields, shared) }
            }

            else -> {
                emptyList<SetPlan>().also { invalid(here.copy(field = "sets"), sets.toString()) }
            }
        }
    }

    /** A set in an exercise's list, as often as its `count` says, with the exercise's [shared] targets. */
    private fun readSetEntry(
        here: ProblemPlace,
        element: JsonElement,
        exercise: JsonObject,
        shared: SetPlan,
    ): List<SetPlan> {
        val fields = element as? JsonObject ?: return emptyList<SetPlan>().also { invalid(here, element.toString()) }
        checkFields(fields, TARGET_FIELDS + "count", here)
        val own = readTargets(here, fields)

        fun has(name: String) = name in fields || name !in exercise
        val set =
            SetPlan(
                type = if (has("type")) own.type else shared.type,
                weight = if (has("weight")) own.weight else shared.weight,
                reps = if (has("reps")) own.reps else shared.reps,
                duration = if (has("duration")) own.duration else shared.duration,
                distanceMeters = if (has("distance")) own.distanceMeters else shared.distanceMeters,
                rpe = if (has("rpe")) own.rpe else shared.rpe,
            )
        return List(number(fields, "count", here, ::count) ?: 1) { set }
    }

    /** The targets in [fields]: a set's own, or those an exercise gives all its sets. */
    private fun readTargets(
        here: ProblemPlace,
        fields: JsonObject,
    ) = SetPlan(
        type = key(fields, "type", here, SetType::fromKey) ?: SetType.Normal,
        weight = weight(fields, "weight", here),
        reps = number(fields, "reps", here, ::repRange),
        duration =
            number(fields, "duration", here) {
                seconds(it)?.takeIf { time ->
                    time in DURATION_SECONDS
                }
            }?.seconds,
        distanceMeters =
            number(fields, "distance", here) {
                meters(it)?.takeIf { length ->
                    length in DISTANCE_METERS
                }
            },
        rpe = number(fields, "rpe", here, ::rpe),
    )

    /** The exercise's progression rule; `"none"` or `false` is none. */
    private fun progression(
        fields: JsonObject,
        here: ProblemPlace,
    ): ProgressionRule? {
        val key = text(fields, "progression", here)?.lowercase() ?: return null
        if (key in NO_PROGRESSION) return null
        return ProgressionRule.fromKey(key) ?: null.also { invalid(here.copy(field = "progression"), key) }
    }

    /** Exercises with the same superset label must be next to each other to be one superset. */
    private fun checkSupersets(
        routine: ProblemPlace,
        exercises: List<ExercisePlan>,
    ) {
        exercises
            .mapIndexedNotNull { index, exercise -> exercise.superset?.let { it to index } }
            .groupBy({ it.first }, { it.second })
            .filterValues { indices -> indices.last() - indices.first() + 1 != indices.size }
            .keys
            .forEach { problems += RoutineFileProblem(Kind.SupersetApart, routine, it) }
    }

    /** A weight: a number in the file's `weightUnit`, or with its own unit. */
    private fun weight(
        fields: JsonObject,
        name: String,
        here: ProblemPlace,
    ): Mass? {
        val text = text(fields, name, here) ?: return null
        val place = here.copy(field = name)
        val amount = quantity(text)
        val unit = if (amount == null) null.also { invalid(place, text) } else unitOf(amount.unit, place, text)
        val mass = if (amount != null && unit != null) Mass.of(amount.value, unit) else return null
        if (mass.kilograms > MAX_KILOGRAMS) invalid(place, text)
        // Zero is no target, like an empty field in the editor.
        return mass.takeIf { it.kilograms > 0 && it.kilograms <= MAX_KILOGRAMS }
    }

    /** The unit a weight written as [text] is in: its own, else the file's [weightUnit]. */
    private fun unitOf(
        name: String?,
        place: ProblemPlace,
        text: String,
    ): WeightUnit? =
        if (name == null) {
            weightUnit ?: null.also { problems += RoutineFileProblem(Kind.NoWeightUnit, place, text) }
        } else {
            weightUnit(name) ?: null.also { invalid(place, text) }
        }

    /** A value [read] from the field's text; reported when it can't be read. */
    private fun <T> number(
        fields: JsonObject,
        name: String,
        here: ProblemPlace,
        read: (String) -> T?,
    ): T? {
        val text = text(fields, name, here) ?: return null
        val value = read(text)
        if (value == null) invalid(here.copy(field = name), text)
        return value
    }

    /** One of the format's keys, such as a tracking type, turned into its value by [of]. */
    private fun <T> key(
        fields: JsonObject,
        name: String,
        here: ProblemPlace,
        of: (String) -> T?,
    ): T? {
        val text = text(fields, name, here) ?: return null
        return of(text.trim().lowercase()) ?: null.also { invalid(here.copy(field = name), text) }
    }

    private fun muscles(
        fields: JsonObject,
        name: String,
        here: ProblemPlace,
    ): Set<Muscle>? {
        val element = fields[name]
        if (element == null || element is JsonNull) return null
        val keys = (element as? JsonArray)?.map { (it as? JsonPrimitive)?.content } ?: listOf(null)
        return keys
            .mapNotNull { key ->
                key?.let { Muscle.fromKey(it.trim().lowercase()) }
                    ?: null.also { invalid(here.copy(field = name), key) }
            }.toSet()
    }

    private fun flag(
        fields: JsonObject,
        name: String,
        here: ProblemPlace,
    ): Boolean {
        val element = fields[name] ?: return false
        return (element as? JsonPrimitive)?.booleanOrNull
            ?: false.also { if (element !is JsonNull) invalid(here.copy(field = name), element.toString()) }
    }

    /** The field's text, a number's included; null when it's missing, empty or null. */
    private fun text(
        fields: JsonObject,
        name: String,
        here: ProblemPlace,
        required: Boolean = false,
    ): String? {
        val element = fields[name]
        val text =
            when (element) {
                null, JsonNull -> null
                is JsonPrimitive -> element.content.trim().ifEmpty { null }
                else -> null.also { invalid(here.copy(field = name), element.toString()) }
            }
        // Something other than text there is reported as it is, not as missing.
        val absent = element == null || element is JsonPrimitive
        if (text == null && required && absent) missing(here.copy(field = name))
        return text
    }

    private fun list(
        fields: JsonObject,
        name: String,
        here: ProblemPlace,
        required: Boolean = false,
    ): List<JsonElement> =
        when (val element = fields[name]) {
            is JsonArray -> {
                element.also { if (required && it.isEmpty()) missing(here.copy(field = name)) }
            }

            null, JsonNull -> {
                emptyList<JsonElement>().also { if (required) missing(here.copy(field = name)) }
            }

            else -> {
                emptyList<JsonElement>().also { invalid(here.copy(field = name), element.toString()) }
            }
        }

    private fun checkFields(
        fields: JsonObject,
        known: Set<String>,
        here: ProblemPlace,
    ) {
        fields.keys.filterNot { it in known }.forEach { problems += RoutineFileProblem(Kind.UnknownField, here, it) }
    }

    private fun missing(place: ProblemPlace) {
        problems += RoutineFileProblem(Kind.Missing, place)
    }

    private fun invalid(
        place: ProblemPlace,
        value: String?,
    ) {
        problems += RoutineFileProblem(Kind.Invalid, place, value)
    }
}

/** Sets progression counts: neither warm-ups nor drop sets. */
internal val SetType.progresses: Boolean get() = this != SetType.Warmup && this != SetType.Drop

/** Progression turned off in so many words. */
private val NO_PROGRESSION = setOf("none", "off", "false")

/** How many sets alike: a whole number from 1 to 20. */
private fun count(text: String): Int? = text.trim().toIntOrNull()?.takeIf { it in COUNT }

private val FILE_FIELDS = setOf("format", "version", "weightUnit", "folder", "customExercises", "routines", "\$schema")
private val FOLDER_FIELDS = setOf("name", "rotation")
private val CUSTOM_EXERCISE_FIELDS =
    setOf(
        "name",
        "variationOf",
        "tracking",
        "equipment",
        "primaryMuscles",
        "secondaryMuscles",
        "note",
        "notes",
        "loadStep",
    )
private val ROUTINE_FIELDS = setOf("name", "notes", "exercises")
private val EXERCISE_FIELDS = setOf("exercise", "name", "sets", "rest", "superset", "progression", "note", "notes")

/** A set's targets, which an exercise can also give all its sets. */
private val TARGET_FIELDS = setOf("type", "weight", "reps", "duration", "distance", "rpe")

private const val MAX_KILOGRAMS = 1_000.0
private val COUNT = 1..20
private val REST_SECONDS = 0..3_600
private val DURATION_SECONDS = 1..86_400
private val DISTANCE_METERS = 0.1..1_000_000.0
