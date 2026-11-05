package li.gkd.app.ui.icon

import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.RocketLaunch
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathNode
import androidx.compose.ui.graphics.vector.VectorPath
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos

// The original outlined icon has two flame contours, followed by the rocket body.
private val rocketParts: List<ImageVector>
    get() {
        if (_rocketParts != null) {
            return _rocketParts!!
        }
        val original = Icons.Outlined.RocketLaunch
        val path = original.root[0] as VectorPath
        val bodyStart = path.pathData.indices.filter { path.pathData[it] is PathNode.MoveTo }[2]
        _rocketParts =
            listOf(
                    path.pathData.take(bodyStart),
                    path.pathData.drop(bodyStart),
                )
                .mapIndexed { index, nodes ->
                    ImageVector.Builder(
                            name = "RocketPart$index",
                            defaultWidth = original.defaultWidth,
                            defaultHeight = original.defaultHeight,
                            viewportWidth = original.viewportWidth,
                            viewportHeight = original.viewportHeight,
                        )
                        .addPath(
                            pathData = nodes,
                            fill = SolidColor(Color.Black),
                            pathFillType = path.pathFillType,
                        )
                        .build()
                }
        return _rocketParts!!
    }

private var _rocketParts: List<ImageVector>? = null

@Composable
fun GkPrivilegeConnectionIcon(
    connected: Boolean,
    contentDescription: String,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
) {
    val transition = updateTransition(connected, label = "Privilege connection")
    val launchProgress =
        transition.animateFloat(
            transitionSpec = { tween(250) },
            label = "Rocket launch",
        ) {
            if (it) 1f else 0f
        }
    val drift =
        if (connected || launchProgress.value > 0f) {
            val flight = rememberInfiniteTransition(label = "Rocket flight")
            flight.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec =
                    infiniteRepeatable(
                        tween(1000, easing = Easing { ((1.0 - cos(PI * it)) / 2.0).toFloat() }),
                        repeatMode = RepeatMode.Reverse,
                    ),
                label = "Rocket drift",
            )
        } else {
            rememberUpdatedState(0f)
        }
    Box(
        modifier
            .size(24.dp)
            .semantics { this.contentDescription = contentDescription }
            .graphicsLayer {
                // RocketLaunch already points diagonally; rotate it upright while idle.
                val idle = 1f - launchProgress.value
                rotationZ = -45f * idle
                // The upright body bounds are approximately -1.66..18.14 in the 24dp viewport.
                translationY = 3.75.dp.toPx() * idle
            },
    ) {
        rocketParts.forEachIndexed { index, vector ->
            Icon(
                vector,
                contentDescription = null,
                tint = tint,
                modifier =
                    Modifier.size(24.dp).graphicsLayer {
                        val launch = launchProgress.value
                        alpha = if (index == 0) launch else 1f
                        val distance =
                            (if (index == 0) -0.6.dp else 0.4.dp).toPx() * drift.value * launch
                        translationX = distance
                        translationY = -distance
                    },
            )
        }
    }
}
