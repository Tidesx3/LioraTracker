package app.liora.feature.logger

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import app.liora.core.domain.setAt

/**
 * Where the workout's list stands, shared by every layout, so turning or folding the phone keeps the
 * place. It opens at the set that's up next, or once everything is done at the one logged last, rather
 * than at the top: the exercise's card comes first, and the set brings itself on screen below it.
 */
@Stable
internal class WorkoutListPlace(
    val listState: LazyListState,
    openingSetId: String?,
) {
    /** The set to bring on screen once; null after, so scrolling away and back doesn't pull the list to it. */
    var openingSetId by mutableStateOf(openingSetId)
        private set

    fun opened() {
        openingSetId = null
    }
}

@Composable
internal fun rememberWorkoutListPlace(state: LoggerUiState.Active): WorkoutListPlace {
    val opening = state.opening
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = opening?.exerciseIndex ?: 0)
    return remember(listState) { WorkoutListPlace(listState, opening?.let { state.workout.setAt(it).id }) }
}
