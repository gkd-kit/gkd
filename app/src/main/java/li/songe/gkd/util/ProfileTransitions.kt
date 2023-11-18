package li.songe.gkd.util

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.navigation.NavBackStackEntry
import com.blankj.utilcode.util.ScreenUtils
import com.ramcosta.composedestinations.spec.DestinationStyle

object ProfileTransitions : DestinationStyle.Animated {
    private const val durationMillis = 400
    override fun AnimatedContentTransitionScope<NavBackStackEntry>.enterTransition(): EnterTransition? {
        return slideInHorizontally(
            initialOffsetX = { ScreenUtils.getScreenWidth() }, animationSpec = tween(durationMillis)
        )
    }

    override fun AnimatedContentTransitionScope<NavBackStackEntry>.exitTransition(): ExitTransition? {
        return slideOutHorizontally(
            targetOffsetX = { -ScreenUtils.getScreenWidth() / 2 },
            animationSpec = tween(durationMillis)
        )
    }

    override fun AnimatedContentTransitionScope<NavBackStackEntry>.popEnterTransition(): EnterTransition? {
        return slideInHorizontally(
            initialOffsetX = { -ScreenUtils.getScreenWidth() / 2 },
            animationSpec = tween(durationMillis)
        )
    }

    override fun AnimatedContentTransitionScope<NavBackStackEntry>.popExitTransition(): ExitTransition? {
        return slideOutHorizontally(
            targetOffsetX = { ScreenUtils.getScreenWidth() }, animationSpec = tween(durationMillis)
        )
    }
}