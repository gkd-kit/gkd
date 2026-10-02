package li.gkd.app.ui.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay

object GkNavigationTransitions {
    val editor = NavDisplay.transitionSpec {
        (slideInVertically(tween(250)) { it / 8 } + fadeIn(tween(250))) togetherWith fadeOut(
            tween(
                150
            )
        )
    } + NavDisplay.popTransitionSpec {
        fadeIn(tween(150)) togetherWith (slideOutVertically(tween(250)) { it / 8 } + fadeOut(
            tween(
                250
            )
        ))
    } + NavDisplay.predictivePopTransitionSpec {
        fadeIn(tween(150)) togetherWith (slideOutVertically(tween(250)) { it / 8 } + fadeOut(
            tween(
                250
            )
        ))
    }
}

/** Both hosts use the same entry lifecycle, saved UI state and route transitions. */
@Composable
fun <T : Any> GkNavigation(
    backStack: List<T>,
    onBack: () -> Unit,
    entryProvider: (T) -> NavEntry<T>
) {
    NavDisplay(
        backStack = backStack,
        onBack = onBack,
        entryProvider = entryProvider,
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator(),
        ),
        transitionSpec = { slideInHorizontally { it } togetherWith slideOutHorizontally { -it } },
        popTransitionSpec = { slideInHorizontally { -it } togetherWith slideOutHorizontally { it } },
        predictivePopTransitionSpec = { slideInHorizontally { -it } togetherWith slideOutHorizontally { it } },
    )
}
