package app.liora.core.data.backup

import app.liora.core.common.Hlc
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.time.Instant

/*
 * The backup file format, version 1: everything the user made, as JSON. It is meant to be read by
 * people and scripts too, so times are ISO 8601, kinds are the app's stable keys (`weight_reps`,
 * `warmup`), and units are in the field names (`weightKg`, `distanceM`, `durationSec`). Workouts and
 * routines nest their exercises and sets. Every row keeps its id, creation time and HLC, so restoring
 * merges row by row like sync does: the newer version of a row wins.
 *
 * Only what the user made is in it: custom exercises (built-ins come with the app), and finished
 * workouts (the one in progress isn't history yet). Device-local state (the rest timer, the gym in
 * use) stays on the device. Optional fields are left out when empty.
 */

/** A whole backup. */
@Serializable
class Backup(
    val format: String,
    val version: Int,
    @Serializable(with = IsoMillis::class) val exportedAt: Long,
    val exercises: List<BackupExercise> = emptyList(),
    val exerciseSettings: List<BackupExerciseSettings> = emptyList(),
    val routineFolders: List<BackupRoutineFolder> = emptyList(),
    val routines: List<BackupRoutine> = emptyList(),
    val workouts: List<BackupWorkout> = emptyList(),
    val measurements: List<BackupMeasurement> = emptyList(),
    val photos: List<BackupPhoto> = emptyList(),
    val gyms: List<BackupGym> = emptyList(),
    val preferences: List<BackupPreference> = emptyList(),
)

/** A custom exercise. */
@Serializable
class BackupExercise(
    val id: String,
    val name: String,
    val trackingType: String,
    val equipment: String,
    val category: String,
    val primaryMuscles: List<String> = emptyList(),
    val secondaryMuscles: List<String> = emptyList(),
    val instructions: List<String> = emptyList(),
    val images: List<String> = emptyList(),
    /** The built-in exercise it's a variation of. */
    val variationOf: String? = null,
    val notes: String? = null,
    @Serializable(with = IsoMillis::class) val createdAt: Long,
    val hlc: String,
)

/** The user's own settings for an exercise, built-in or custom. */
@Serializable
class BackupExerciseSettings(
    val exerciseId: String,
    val stickyNote: String? = null,
    val restWorkingSec: Int? = null,
    val restWarmupSec: Int? = null,
    val archived: Boolean = false,
    @Serializable(with = IsoMillis::class) val createdAt: Long,
    val hlc: String,
)

@Serializable
class BackupRoutineFolder(
    val id: String,
    val name: String,
    val position: Int,
    @Serializable(with = IsoMillis::class) val createdAt: Long,
    val hlc: String,
)

@Serializable
class BackupRoutine(
    val id: String,
    val folderId: String? = null,
    val name: String,
    val notes: String? = null,
    val position: Int,
    @Serializable(with = IsoMillis::class) val createdAt: Long,
    val hlc: String,
    val exercises: List<BackupRoutineExercise> = emptyList(),
)

@Serializable
class BackupRoutineExercise(
    val id: String,
    val exerciseId: String,
    val position: Int,
    val supersetGroup: Int? = null,
    val restSec: Int? = null,
    val notes: String? = null,
    @Serializable(with = IsoMillis::class) val createdAt: Long,
    val hlc: String,
    val sets: List<BackupRoutineSet> = emptyList(),
)

@Serializable
class BackupRoutineSet(
    val id: String,
    val position: Int,
    val setType: String,
    val targetWeightKg: Double? = null,
    val targetRepsMin: Int? = null,
    val targetRepsMax: Int? = null,
    val targetDurationSec: Int? = null,
    val targetDistanceM: Double? = null,
    val targetRpe: Double? = null,
    @Serializable(with = IsoMillis::class) val createdAt: Long,
    val hlc: String,
)

/** A finished workout. */
@Serializable
class BackupWorkout(
    val id: String,
    val name: String? = null,
    @Serializable(with = IsoMillis::class) val startedAt: Long,
    @Serializable(with = IsoMillis::class) val endedAt: Long,
    val routineId: String? = null,
    val notes: String? = null,
    val bodyweightKg: Double? = null,
    @Serializable(with = IsoMillis::class) val createdAt: Long,
    val hlc: String,
    val exercises: List<BackupWorkoutExercise> = emptyList(),
)

@Serializable
class BackupWorkoutExercise(
    val id: String,
    val exerciseId: String,
    val position: Int,
    val supersetGroup: Int? = null,
    val restSec: Int? = null,
    val notes: String? = null,
    @Serializable(with = IsoMillis::class) val createdAt: Long,
    val hlc: String,
    val sets: List<BackupWorkoutSet> = emptyList(),
)

@Serializable
class BackupWorkoutSet(
    val id: String,
    val position: Int,
    val setType: String,
    val weightKg: Double? = null,
    val reps: Int? = null,
    val durationSec: Int? = null,
    val distanceM: Double? = null,
    val rpe: Double? = null,
    /** When the set was ticked off. */
    @Serializable(with = IsoMillis::class) val completedAt: Long? = null,
    val targetRepsMin: Int? = null,
    val targetRepsMax: Int? = null,
    @Serializable(with = IsoMillis::class) val createdAt: Long,
    val hlc: String,
)

/** A body measurement: kg for bodyweight, percent for body fat, metres for circumferences. */
@Serializable
class BackupMeasurement(
    val id: String,
    @Serializable(with = IsoMillis::class) val takenAt: Long,
    val type: String,
    val value: Double,
    @Serializable(with = IsoMillis::class) val createdAt: Long,
    val hlc: String,
)

/** A progress photo; [file] is its image in the backup archive, if it's there. */
@Serializable
class BackupPhoto(
    val id: String,
    @Serializable(with = IsoMillis::class) val takenAt: Long,
    val pose: String? = null,
    val file: String? = null,
    val blobId: String? = null,
    val notes: String? = null,
    @Serializable(with = IsoMillis::class) val createdAt: Long,
    val hlc: String,
)

/** A gym's equipment, in kilograms; [unit] is what its plates are labelled in. */
@Serializable
@Suppress("LongParameterList") // one per field of the file format
class BackupGym(
    val id: String,
    val name: String,
    val unit: String,
    val barbellKg: Double,
    val ezBarKg: Double,
    /** `[{"kg": 20.0, "pairs": 4}, …]` */
    val plates: JsonElement,
    /** `[{"fromKg": 2.0, "toKg": 40.0, "stepKg": 2.0}, …]` */
    val dumbbells: JsonElement,
    val stackStepKg: Double,
    @Serializable(with = IsoMillis::class) val createdAt: Long,
    val hlc: String,
)

/** One setting, as `SettingsRepository` stores it. */
@Serializable
class BackupPreference(
    val key: String,
    val value: String,
    @Serializable(with = IsoMillis::class) val createdAt: Long,
    val hlc: String,
)

/** The file isn't a backup, or it's damaged. */
class InvalidBackupException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

/** The backup was made by a newer version of the app, in a format this one can't read. */
class NewerBackupException(
    val version: Int,
) : Exception("Backup format $version is newer than ${BackupJson.VERSION}")

/** Reads and writes [Backup]s as JSON. */
object BackupJson {
    const val FORMAT = "liora-backup"
    const val VERSION = 1

    private val json =
        Json {
            prettyPrint = true
            // Empty optional fields stay out of the file; fields from a newer minor change are skipped.
            encodeDefaults = false
            explicitNulls = false
            ignoreUnknownKeys = true
        }

    fun encode(backup: Backup): String = json.encodeToString(backup)

    /**
     * The backup in [text]. Throws [InvalidBackupException] when it isn't one or is damaged, and
     * [NewerBackupException] when a newer version of the app wrote it.
     */
    fun decode(text: String): Backup {
        val backup =
            try {
                parse(text)
            } catch (e: SerializationException) {
                throw InvalidBackupException("Not a backup", e)
            } catch (e: IllegalArgumentException) {
                // Also what a JSON array or a plain value where an object belongs throws.
                throw InvalidBackupException("Not a backup", e)
            }
        return backup.also(::checkHlcs)
    }

    private fun parse(text: String): Backup {
        val document = json.parseToJsonElement(text).jsonObject
        // The version first: a newer format may not decode as this one.
        val version = formatVersion(document)
        if (version > VERSION) throw NewerBackupException(version)
        return json.decodeFromJsonElement(Backup.serializer(), document)
    }

    private fun formatVersion(document: JsonObject): Int {
        if (document["format"]?.jsonPrimitive?.contentOrNull != FORMAT) throw InvalidBackupException("Not a backup")
        return document["version"]?.jsonPrimitive?.intOrNull ?: throw InvalidBackupException("No version")
    }

    /** Rows are merged by HLC, so one that doesn't parse would win or lose at random. */
    private fun checkHlcs(backup: Backup) {
        val damaged = backup.hlcs().firstOrNull { runCatching { Hlc.parse(it) }.isFailure }
        if (damaged != null) throw InvalidBackupException("Damaged HLC: $damaged")
    }
}

/** Every row's HLC. */
internal fun Backup.hlcs(): Sequence<String> =
    sequence {
        exercises.forEach { yield(it.hlc) }
        exerciseSettings.forEach { yield(it.hlc) }
        routineFolders.forEach { yield(it.hlc) }
        routines.forEach { routine ->
            yield(routine.hlc)
            routine.exercises.forEach { exercise ->
                yield(exercise.hlc)
                exercise.sets.forEach { yield(it.hlc) }
            }
        }
        workouts.forEach { workout ->
            yield(workout.hlc)
            workout.exercises.forEach { exercise ->
                yield(exercise.hlc)
                exercise.sets.forEach { yield(it.hlc) }
            }
        }
        measurements.forEach { yield(it.hlc) }
        photos.forEach { yield(it.hlc) }
        gyms.forEach { yield(it.hlc) }
        preferences.forEach { yield(it.hlc) }
    }

/** Epoch milliseconds as an ISO 8601 instant, e.g. `2026-10-02T17:05:00.250Z`. */
internal object IsoMillis : KSerializer<Long> {
    override val descriptor = PrimitiveSerialDescriptor("app.liora.IsoMillis", PrimitiveKind.STRING)

    override fun serialize(
        encoder: Encoder,
        value: Long,
    ) = encoder.encodeString(Instant.fromEpochMilliseconds(value).toString())

    override fun deserialize(decoder: Decoder): Long = Instant.parse(decoder.decodeString()).toEpochMilliseconds()
}

/** Where a photo's image goes in the backup archive. */
internal fun photoFile(id: String) = "photos/$id.jpg"

/** The archive entry that holds the JSON. */
internal const val BACKUP_JSON = "liora.json"
