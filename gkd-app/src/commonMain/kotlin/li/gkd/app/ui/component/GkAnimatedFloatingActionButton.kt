package li.gkd.app.ui.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationConstants.DefaultDurationMillis
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.FloatingActionButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

@Composable
fun GkAnimatedFloatingActionButton(
    visible: Boolean,
    onClick: () -> Unit,
    imageVector: ImageVector,
    modifier: Modifier = Modifier,
    onClickLabel: String? = null,
    contentDescription: String? = getIconDefaultDesc(imageVector),
) {
    val density = LocalDensity.current
    val shadowSpace = with(density) { 24.dp.toPx() }
    var maxTranslationX by remember { mutableFloatStateOf(0f) }
    var innerVisible by remember { mutableStateOf(visible) }
    val percent = remember { Animatable(if (visible) 1f else 0f) }
    LaunchedEffect(visible) {
        if (visible) {
            innerVisible = true
        }
        percent.animateTo(
            targetValue = if (visible) 1f else 0f,
            animationSpec = tween(durationMillis = DefaultDurationMillis)
        )
        innerVisible = visible
    }
    if (innerVisible) {
        GkTooltipIconButtonBox(contentDescription) {
            // Measure an untransformed parent so animation frames cannot move the exit target.
            Box(Modifier.onGloballyPositioned { coordinates ->
                val distanceToEdge = coordinates.findRootCoordinates().size.width -
                        coordinates.positionInRoot().x
                maxTranslationX = distanceToEdge.coerceAtLeast(0f) + shadowSpace
            }) {
                FloatingActionButton(
                    modifier = modifier
                        .graphicsLayer {
                            alpha = percent.value
                            translationX = (1f - percent.value) * maxTranslationX
                            // Avoid an alpha offscreen buffer clipping the FAB's outer shadow.
                            compositingStrategy = CompositingStrategy.ModulateAlpha
                        }
                        .semantics {
                            if (contentDescription != null) {
                                this.contentDescription = contentDescription
                            }
                            if (onClickLabel != null) {
                                this.onClick(label = onClickLabel, action = null)
                            }
                        },
                    onClick = onClick,
                    content = {
                        GkIcon(imageVector = imageVector, contentDescription = null)
                    },
                )
            }
        }
    }
}
