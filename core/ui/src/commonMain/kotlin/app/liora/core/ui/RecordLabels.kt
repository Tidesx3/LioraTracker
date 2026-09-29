package app.liora.core.ui

import androidx.compose.runtime.Composable
import app.liora.core.domain.RecordKey
import app.liora.core.domain.RecordType
import app.liora.core.ui.resources.Res
import app.liora.core.ui.resources.record_best_pace
import app.liora.core.ui.resources.record_best_set_volume
import app.liora.core.ui.resources.record_estimated_1rm
import app.liora.core.ui.resources.record_heaviest_weight
import app.liora.core.ui.resources.record_least_assistance
import app.liora.core.ui.resources.record_longest_distance
import app.liora.core.ui.resources.record_longest_duration
import app.liora.core.ui.resources.record_most_reps
import app.liora.core.ui.resources.record_rep_max
import org.jetbrains.compose.resources.stringResource

/** "Heaviest weight", "5-rep max", … */
@Composable
fun recordLabel(key: RecordKey): String =
    when (key.type) {
        RecordType.HeaviestWeight -> stringResource(Res.string.record_heaviest_weight)
        RecordType.LeastAssistance -> stringResource(Res.string.record_least_assistance)
        RecordType.EstimatedOneRepMax -> stringResource(Res.string.record_estimated_1rm)
        RecordType.BestSetVolume -> stringResource(Res.string.record_best_set_volume)
        RecordType.RepMax -> stringResource(Res.string.record_rep_max, key.reps ?: 1)
        RecordType.MostReps -> stringResource(Res.string.record_most_reps)
        RecordType.LongestDuration -> stringResource(Res.string.record_longest_duration)
        RecordType.LongestDistance -> stringResource(Res.string.record_longest_distance)
        RecordType.BestPace -> stringResource(Res.string.record_best_pace)
    }

/**
 * The records worth naming out of [keys], most telling first. A heavier set breaks a whole row of
 * rep maxes at once; they're only named when nothing else was broken, and then just the best one.
 */
fun headlineRecords(keys: Collection<RecordKey>): List<RecordKey> {
    val main = keys.filter { it.type != RecordType.RepMax }.distinctBy { it.type }.sortedBy { it.type.ordinal }
    return main.ifEmpty { listOfNotNull(keys.maxByOrNull { it.reps ?: 0 }) }
}
