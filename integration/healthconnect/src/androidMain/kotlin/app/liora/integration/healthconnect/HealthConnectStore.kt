package app.liora.integration.healthconnect

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.RemoteException
import androidx.activity.result.contract.ActivityResultContract
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.changes.UpsertionChange
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.BodyFatRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.records.metadata.Device
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.request.ChangesTokenRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import app.liora.core.data.body.BodyReading
import app.liora.core.data.health.HealthAccess
import app.liora.core.data.health.HealthAvailability
import app.liora.core.data.health.HealthReadings
import app.liora.core.data.health.HealthSession
import app.liora.core.data.health.HealthStore
import app.liora.core.data.health.HealthUnavailableException
import app.liora.core.model.MeasurementType
import java.io.IOException
import java.time.Instant
import java.time.ZoneId
import kotlin.reflect.KClass

/** Health Connect through its Jetpack client. */
class HealthConnectStore(
    context: Context,
) : HealthStore {
    private val context = context.applicationContext
    private val client by lazy { HealthConnectClient.getOrCreate(this.context) }

    override suspend fun access(): HealthAccess {
        val availability =
            when (HealthConnectClient.getSdkStatus(context)) {
                HealthConnectClient.SDK_AVAILABLE -> HealthAvailability.Available
                HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> HealthAvailability.UpdateNeeded
                else -> HealthAvailability.NotInstalled
            }
        if (availability != HealthAvailability.Available) return HealthAccess(availability)
        val granted = guarded { client.permissionController.getGrantedPermissions() }
        return HealthAccess(
            availability,
            writeWorkouts = WRITE_SESSIONS in granted,
            readBody = READ_WEIGHT in granted || READ_BODY_FAT in granted,
        )
    }

    override suspend fun writeSessions(sessions: List<HealthSession>) =
        guarded {
            sessions.map { it.toRecord() }.chunked(CHUNK).forEach { client.insertRecords(it) }
        }

    override suspend fun deleteSessions(ids: List<String>) =
        guarded {
            ids.chunked(CHUNK).forEach {
                client.deleteRecords(
                    ExerciseSessionRecord::class,
                    recordIdsList = emptyList(),
                    clientRecordIdsList = it,
                )
            }
        }

    override suspend fun readings(cursor: String?): HealthReadings =
        guarded {
            val granted = client.permissionController.getGrantedPermissions()
            val types =
                buildList {
                    if (READ_WEIGHT in granted) add(WeightRecord::class)
                    if (READ_BODY_FAT in granted) add(BodyFatRecord::class)
                }
            // A cursor names the types it covers: one allowed since then is read from the start.
            val covers = types.joinToString(",") { it.simpleName.orEmpty() }
            val token = cursor?.split(CURSOR_SEPARATOR, limit = 2)?.takeIf { it[0] == covers }?.getOrNull(1)
            when {
                types.isEmpty() -> HealthReadings(emptyList(), cursor)
                token == null -> everything(types, covers)
                else -> changesSince(token, types, covers)
            }
        }

    override suspend fun revoke() = guarded { client.permissionController.revokeAllPermissions() }

    /** Every reading Liora may see, and a token for what changes after. */
    private suspend fun everything(
        types: List<KClass<out Record>>,
        covers: String,
    ): HealthReadings {
        // The token first, so nothing written while reading slips through.
        val token = client.getChangesToken(ChangesTokenRequest(types.toSet()))
        val readings = types.flatMap { readAll(it) }
        return HealthReadings(readings, covers + CURSOR_SEPARATOR + token)
    }

    private suspend fun readAll(type: KClass<out Record>): List<BodyReading> {
        val readings = mutableListOf<BodyReading>()
        var page: String? = null
        do {
            val response =
                client.readRecords(
                    ReadRecordsRequest(type, TimeRangeFilter.after(Instant.EPOCH), pageToken = page),
                )
            readings += response.records.mapNotNull { it.toReading() }
            page = response.pageToken
        } while (page != null)
        return readings
    }

    private suspend fun changesSince(
        token: String,
        types: List<KClass<out Record>>,
        covers: String,
    ): HealthReadings {
        val readings = mutableListOf<BodyReading>()
        var next = token
        do {
            val response = client.getChanges(next)
            // Tokens run out after a month unused: start again from everything.
            if (response.changesTokenExpired) return everything(types, covers)
            readings += response.changes.filterIsInstance<UpsertionChange>().mapNotNull { it.record.toReading() }
            next = response.nextChangesToken
        } while (response.hasMore)
        return HealthReadings(readings, covers + CURSOR_SEPARATOR + next)
    }

    /** A weight or body fat reading from another app; Liora's own come back as nothing. */
    private fun Record.toReading(): BodyReading? =
        when {
            metadata.dataOrigin.packageName == context.packageName -> null
            this is WeightRecord -> BodyReading(MeasurementType.Bodyweight, time.toKotlin(), weight.inKilograms)
            this is BodyFatRecord -> BodyReading(MeasurementType.BodyFat, time.toKotlin(), percentage.value)
            else -> null
        }

    companion object {
        private val WRITE_SESSIONS = HealthPermission.getWritePermission(ExerciseSessionRecord::class)
        private val READ_WEIGHT = HealthPermission.getReadPermission(WeightRecord::class)
        private val READ_BODY_FAT = HealthPermission.getReadPermission(BodyFatRecord::class)
        private const val CHUNK = 500
        private const val CURSOR_SEPARATOR = "|"
        private const val PROVIDER = "com.google.android.apps.healthdata"

        /** What Liora asks Health Connect for (also declared in the app's manifest). */
        val permissions: Set<String> = setOf(WRITE_SESSIONS, READ_WEIGHT, READ_BODY_FAT)

        /** Health Connect's own permission screen. */
        fun permissionRequest(): ActivityResultContract<Set<String>, Set<String>> =
            PermissionController.createRequestPermissionResultContract()

        /** Health Connect's settings, where Liora's permissions are managed. */
        fun settingsIntent(): Intent = Intent(HealthConnectClient.ACTION_HEALTH_CONNECT_SETTINGS)

        /** The store page that installs or updates Health Connect. */
        fun installIntent(context: Context): Intent =
            Intent(Intent.ACTION_VIEW)
                .setPackage("com.android.vending")
                .setData(Uri.parse("market://details?id=$PROVIDER&url=healthconnect%3A%2F%2Fonboarding"))
                .putExtra("overlay", true)
                .putExtra("callerId", context.packageName)
    }
}

private fun HealthSession.toRecord(): ExerciseSessionRecord {
    val startTime = Instant.ofEpochMilli(start.toEpochMilliseconds())
    // Health Connect wants a session to last; a workout started and finished at once lasts a second.
    val endTime = Instant.ofEpochMilli(end.toEpochMilliseconds()).coerceAtLeast(startTime.plusSeconds(1))
    val zone = ZoneId.systemDefault().rules
    return ExerciseSessionRecord(
        startTime = startTime,
        startZoneOffset = zone.getOffset(startTime),
        endTime = endTime,
        endZoneOffset = zone.getOffset(endTime),
        metadata =
            Metadata.activelyRecorded(
                device = Device(type = Device.TYPE_PHONE),
                clientRecordId = id,
                clientRecordVersion = version,
            ),
        exerciseType = ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING,
        title = title,
        notes = notes,
    )
}

private fun Instant.toKotlin() = kotlin.time.Instant.fromEpochMilliseconds(toEpochMilli())

/** Health Connect's failures, as the one that says it can't be reached right now. */
private inline fun <T> guarded(block: () -> T): T =
    try {
        block()
    } catch (e: SecurityException) {
        throw HealthUnavailableException(e)
    } catch (e: RemoteException) {
        throw HealthUnavailableException(e)
    } catch (e: IllegalStateException) {
        throw HealthUnavailableException(e)
    } catch (e: IOException) {
        throw HealthUnavailableException(e)
    }
