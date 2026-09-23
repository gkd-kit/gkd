package li.gkd.app.ui.icon

import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos

// Matches the original accelerate_decelerate interpolator.
private val LogoEasing = Easing { fraction ->
    ((1.0 - cos(PI * fraction)) / 2.0).toFloat()
}

@Composable
fun GkAnimatedLogoIcon(
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
) {
    val paths = remember { LogoPaths() }
    val transition = rememberInfiniteTransition(label = "Logo")
    val progress = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000, easing = LogoEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "Logo progress",
    )
    Canvas(modifier = modifier.size(64.dp)) {
        val fraction = progress.value
        val scale = size.minDimension / 64f
        withTransform({
            translate(
                left = (size.width - size.minDimension) / 2f,
                top = (size.height - size.minDimension) / 2f,
            )
            scale(scaleX = scale, scaleY = scale, pivot = Offset.Zero)
        }) {
            translate(left = -0.7071f * fraction, top = -0.7071f * fraction) {
                drawPath(paths.topLeft, tint)
            }
            translate(left = 0.7071f * fraction, top = -0.7071f * fraction) {
                drawPath(paths.topRight, tint)
            }
            translate(top = -1.13137f * fraction) {
                drawPath(paths.diamond, tint)
            }
            translate(left = 1.5f * fraction) {
                drawPath(paths.bottomLeft, tint)
            }
            drawPath(paths.bottomCenter, tint, alpha = 0.8f + 0.2f * fraction)
            translate(left = -0.7f - 1.5f * fraction) {
                drawPath(paths.bottomRight, tint)
            }
        }
    }
}

private class LogoPaths {
    val topLeft = Path().apply {
        moveTo(19f, 17.485f)
        relativeLineTo(8.485f, -8.485f)
        relativeLineTo(2.816f, 2.841f)
        relativeLineTo(-8.485f, 8.485f)
        close()
    }
    val topRight = Path().apply {
        moveTo(35.816f, 9f)
        relativeLineTo(8.485f, 8.485f)
        relativeLineTo(-2.816f, 2.841f)
        relativeLineTo(-8.485f, -8.485f)
        close()
    }
    val diamond = Path().apply {
        moveTo(23.08f, 21.999f)
        relativeLineTo(8.466f, -8.504f)
        relativeLineTo(8.466f, 8.504f)
        relativeLineTo(-8.466f, 8.504f)
        close()
    }
    val bottomLeft = Path().apply {
        moveTo(8f, 55f)
        cubicTo(8.726f, 50.581f, 10.104f, 46.395f, 12.775f, 42.977f)
        cubicTo(14.285f, 41.046f, 16.133f, 39.452f, 17.847f, 37.729f)
        cubicTo(18.107f, 37.468f, 18.465f, 37.332f, 19f, 37f)
        cubicTo(20.272f, 43.004f, 20.091f, 48.871f, 19.751f, 54.875f)
        cubicTo(15.872f, 55f, 11.994f, 55f, 8f, 55f)
        close()
    }
    val bottomCenter = Path().apply {
        moveTo(21.912f, 55f)
        cubicTo(21.867f, 52.521f, 22.113f, 50.034f, 21.993f, 47.565f)
        cubicTo(21.833f, 44.288f, 21.429f, 41.019f, 21.039f, 37.758f)
        cubicTo(20.922f, 36.775f, 21.013f, 36.135f, 22.054f, 35.748f)
        cubicTo(27.61f, 33.682f, 33.218f, 33.423f, 38.918f, 35.103f)
        cubicTo(39.826f, 35.37f, 40.103f, 35.763f, 39.967f, 36.755f)
        cubicTo(39.717f, 38.583f, 39.642f, 40.44f, 39.586f, 42.288f)
        cubicTo(39.461f, 46.44f, 39.392f, 50.593f, 39.297f, 54.873f)
        cubicTo(33.543f, 55f, 27.792f, 55f, 21.912f, 55f)
        close()
    }
    val bottomRight = Path().apply {
        moveTo(42f, 55f)
        cubicTo(42.06f, 48.791f, 42.24f, 42.582f, 42.432f, 36f)
        cubicTo(50.149f, 40.036f, 53.985f, 46.652f, 56f, 54.895f)
        cubicTo(51.379f, 55f, 46.75f, 55f, 42f, 55f)
        close()
    }
}
