package li.songe.gkd.ui.component

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

@Composable
fun usePercentAnimatable(
    visible: Boolean,
): Animatable<Float, AnimationVector1D> {
    val percent = remember { Animatable(if (visible) 1f else 0f) }
    LaunchedEffect(visible) {
        if (visible && percent.value != 1f) {
            percent.animateTo(targetValue = 1f, animationSpec = tween())
        } else if (!visible && percent.value != 0f) {
            percent.animateTo(targetValue = 0f, animationSpec = tween())
        }
    }
    return percent
}

fun Modifier.animateListItem(
    scope: LazyItemScope,
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