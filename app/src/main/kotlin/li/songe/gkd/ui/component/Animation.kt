package li.songe.gkd.ui.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember

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