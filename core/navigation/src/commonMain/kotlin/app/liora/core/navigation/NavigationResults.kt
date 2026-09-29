package app.liora.core.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.filterNotNull

/**
 * Values a screen hands back to the one that opened it, such as the exercises picked for a routine.
 * A result waits here until its requester collects it, because the requester usually isn't on screen
 * while the other screen is open.
 */
@Stable
class NavigationResults {
    private val pending = mutableStateMapOf<String, Any>()

    fun set(
        key: String,
        value: Any,
    ) {
        pending[key] = value
    }

    /** Hands every result for [key] to [onResult] exactly once, including one that arrived earlier. */
    suspend fun <T : Any> collect(
        key: String,
        onResult: (T) -> Unit,
    ) {
        snapshotFlow { pending[key] }.filterNotNull().collect { value ->
            pending.remove(key)
            @Suppress("UNCHECKED_CAST")
            onResult(value as T)
        }
    }
}

/** Receives results for [key] while the calling screen is shown. */
@Composable
fun <T : Any> NavigationResultEffect(
    results: NavigationResults,
    key: String,
    onResult: (T) -> Unit,
) {
    val currentOnResult by rememberUpdatedState(onResult)
    LaunchedEffect(results, key) { results.collect<T>(key) { currentOnResult(it) } }
}
