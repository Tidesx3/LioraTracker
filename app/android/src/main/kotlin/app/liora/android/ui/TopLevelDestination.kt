package app.liora.android.ui

import androidx.annotation.StringRes
import androidx.navigation3.runtime.NavKey
import app.liora.android.R
import app.liora.core.designsystem.icon.LioraIcons
import app.liora.core.navigation.ExercisesRoute
import app.liora.core.navigation.HistoryRoute
import app.liora.core.navigation.ProgressRoute
import app.liora.core.navigation.TrainRoute
import org.jetbrains.compose.resources.DrawableResource

/** The bottom-navigation tabs, in display order. */
enum class TopLevelDestination(
    val route: NavKey,
    @param:StringRes val label: Int,
    val icon: DrawableResource,
) {
    Train(TrainRoute, R.string.tab_train, LioraIcons.Train),
    History(HistoryRoute, R.string.tab_history, LioraIcons.History),
    Progress(ProgressRoute, R.string.tab_progress, LioraIcons.Progress),
    Exercises(ExercisesRoute, R.string.tab_exercises, LioraIcons.Exercises),
    ;

    companion object {
        val routes: Set<NavKey> = entries.map { it.route }.toSet()
    }
}
