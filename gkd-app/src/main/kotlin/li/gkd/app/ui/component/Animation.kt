package li.gkd.app.ui.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntOffset

context(scope: LazyItemScope, )
fun Modifier.animateListItem(
    enabled: Boolean = true,
): Modifier {
    if (!enabled) {
        return this
    }
    return scope.run {
        animateItem(
            fadeInSpec = spring(stiffness = Spring.StiffnessMediumLow),
            placementSpec = spring(
                stiffness = Spring.StiffnessMediumLow,
                visibilityThreshold = IntOffset.VisibilityThreshold
            ),
            fadeOutSpec = spring(stiffness = Spring.StiffnessMediumLow)
        )
    }
}