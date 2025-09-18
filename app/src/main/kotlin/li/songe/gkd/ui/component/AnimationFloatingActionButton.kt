package li.songe.gkd.ui.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationConstants.DefaultDurationMillis
import androidx.compose.animation.core.tween
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import li.songe.gkd.util.throttle

private const val elevationDurationMillis = 50

@Composable
fun AnimationFloatingActionButton(
    modifier: Modifier = Modifier,
    visible: Boolean,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val maxTranslationX = remember(density.density) { density.run { 24.dp.toPx() } }
    var innerVisible by remember { mutableStateOf(visible) }
    val percent = remember { Animatable(if (visible) 1f else 0f) }
    // https://stackoverflow.com/questions/75717579
    val defaultElevation = remember { Animatable(if (visible) 1f else 0f) }
    LaunchedEffect(visible) {
        if (visible != innerVisible) {
            if (visible) {
                innerVisible = true
                percent.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = DefaultDurationMillis - elevationDurationMillis)
                )
                defaultElevation.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = elevationDurationMillis)
                )
            } else {
                defaultElevation.animateTo(
                    targetValue = 0f,
                    animationSpec = tween(durationMillis = elevationDurationMillis)
                )
                percent.animateTo(
                    targetValue = 0f,
                    animationSpec = tween(durationMillis = DefaultDurationMillis - elevationDurationMillis)
                )
                innerVisible = false
            }
        }
    }
    if (innerVisible) {
        FloatingActionButton(
            modifier = modifier.graphicsLayer(
                alpha = percent.value,
                translationX = (1f - percent.value) * maxTranslationX
            ),
            elevation = FloatingActionButtonDefaults.elevation(defaultElevation = (defaultElevation.value * 6f).dp),
            onClick = throttle(onClick),
            content = content,
        )
    }
}
