package li.songe.gkd.ui.icon

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

private fun Animatable<Float, AnimationVector1D>.calc(start: Float, end: Float): Float {
    return start + (end - start) * value
}

@Composable
fun BackCloseIcon(
    backOrClose: Boolean,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current
) {
    // https://codepen.io/lisonge/pen/WbNEoPR
    val percent = remember { Animatable(if (backOrClose) 1f else 0f) }
    LaunchedEffect(backOrClose) {
        if (backOrClose && percent.value != 1f) {
            percent.animateTo(targetValue = 1f, animationSpec = tween())
        } else if (!backOrClose && percent.value != 0f) {
            percent.animateTo(targetValue = 0f, animationSpec = tween())
        }
    }
    Canvas(
        modifier = modifier
            .size(24.dp)
            .semantics {
                this.contentDescription = if (backOrClose) "back" else "close"
                this.role = Role.Image
            },
    ) {
        val x = size.width
        val halfX = x * 0.5f
        val closeOffset = 0.2375f * x
        val otherCloseOffset = x - closeOffset
        val closeStrokeWidth = x * 0.08202f
        val backStrokeWidth = x * 0.08316f
        val strokeWidth = percent.calc(closeStrokeWidth, backStrokeWidth)
        if (backOrClose) {
            drawLine(
                color = tint,
                start = Offset(
                    percent.calc(closeOffset, x * 0.208f),
                    percent.calc(closeOffset, halfX)
                ),
                end = Offset(
                    percent.calc(otherCloseOffset, x * 0.8335f),
                    percent.calc(otherCloseOffset, halfX)
                ),
                strokeWidth = strokeWidth,
            )
            drawLine(
                color = tint,
                start = Offset(
                    percent.calc(otherCloseOffset, x * 0.529675f),
                    percent.calc(closeOffset, x * 0.1861f)
                ),
                end = Offset(
                    percent.calc(halfX, x * 0.196f),
                    percent.calc(halfX, x * 0.5295f)
                ),
                strokeWidth = strokeWidth,
            )
            drawLine(
                color = tint,
                start = Offset(
                    percent.calc(closeOffset, x * 0.5295f),
                    percent.calc(otherCloseOffset, x * 0.804f)
                ),
                end = Offset(
                    percent.calc(halfX, x * 0.196f),
                    percent.calc(halfX, x * 0.4705f)
                ),
                strokeWidth = strokeWidth,
            )
        } else {
            drawLine(
                color = tint,
                start = Offset(
                    percent.calc(closeOffset, x * 0.208f),
                    percent.calc(otherCloseOffset, halfX)
                ),
                end = Offset(
                    percent.calc(otherCloseOffset, x * 0.8335f),
                    percent.calc(closeOffset, halfX)
                ),
                strokeWidth = strokeWidth,
            )
            drawLine(
                color = tint,
                start = Offset(
                    percent.calc(closeOffset, x * 0.529675f),
                    percent.calc(closeOffset, x * 0.1861f)
                ),
                end = Offset(
                    percent.calc(halfX, x * 0.196f),
                    percent.calc(halfX, x * 0.5295f)
                ),
                strokeWidth = strokeWidth,
            )
            drawLine(
                color = tint,
                start = Offset(
                    percent.calc(otherCloseOffset, x * 0.5295f),
                    percent.calc(otherCloseOffset, x * 0.804f)
                ),
                end = Offset(
                    percent.calc(halfX, x * 0.196f),
                    percent.calc(halfX, x * 0.4705f)
                ),
                strokeWidth = strokeWidth,
            )
        }
    }
}

@Preview(
    showBackground = true,
    heightDp = 48,
    widthDp = 48
)
@Composable
fun PreviewBackCloseIcon() {
    var backOrClose by remember { mutableStateOf(true) }
    LaunchedEffect(null) {
        delay(100)
        while (isActive) {
            backOrClose = !backOrClose
            delay(1000)
        }
    }
    BackCloseIcon(
        backOrClose = backOrClose,
        modifier = Modifier.fillMaxSize(),
    )
}
