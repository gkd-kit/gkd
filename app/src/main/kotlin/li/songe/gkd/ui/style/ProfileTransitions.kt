package li.songe.gkd.ui.style

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.navigation.NavBackStackEntry
import com.ramcosta.composedestinations.spec.DestinationStyle

typealias EnterTransitionType = AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition?
typealias ExitTransitionType = AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition?

object ProfileTransitions : DestinationStyle.Animated() {
    override val enterTransition: EnterTransitionType = {
        slideInHorizontally(tween()) { it }
    }

    override val exitTransition: ExitTransitionType = {
        slideOutHorizontally(tween()) { -it } + fadeOut(tween())
    }

    override val popEnterTransition: EnterTransitionType = {
        slideInHorizontally(tween()) { -it }
    }

    override val popExitTransition: ExitTransitionType = {
        slideOutHorizontally(tween()) { it }
    }
}