package li.gkd.app.ui.icon

import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.RocketLaunch
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
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
        _rocketParts = listOf(path.pathData.take(bodyStart), path.pathData.drop(bodyStart)).mapIndexed { index, nodes ->
            ImageVector.Builder(
                name = "RocketPart$index",
                defaultWidth = original.defaultWidth,
                defaultHeight = original.defaultHeight,
                viewportWidth = original.viewportWidth,
                viewportHeight = original.viewportHeight,
            ).addPath(pathData = nodes, fill = SolidColor(Color.Black), pathFillType = path.pathFillType).build()
        }
        return _rocketParts!!
    }

private var _rocketParts: List<ImageVector>? = null

@Composable
fun GkAnimatedRocketIcon(
    active: Boolean,
    contentDescription: String,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
) {
    Box(modifier.size(24.dp).semantics { this.contentDescription = contentDescription }) {
        if (active) {
            val transition = rememberInfiniteTransition(label = "Rocket")
            val progress = transition.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    tween(1000, easing = Easing { ((1.0 - cos(PI * it)) / 2.0).toFloat() }),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "Rocket drift",
            )
            rocketParts.forEachIndexed { index, vector ->
                Icon(vector, contentDescription = null, tint = tint,
                    modifier = Modifier.size(24.dp).graphicsLayer {
                        val distance = (if (index == 0) -0.6.dp else 0.4.dp).toPx() * progress.value
                        translationX = distance
                        translationY = -distance
                    })
            }
        } else {
            Icon(Icons.Outlined.RocketLaunch, contentDescription = null, tint = tint)
        }
    }
}
