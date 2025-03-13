package li.songe.gkd.ui.component

import androidx.annotation.DrawableRes
import androidx.compose.animation.graphics.res.animatedVectorResource
import androidx.compose.animation.graphics.res.rememberAnimatedVectorPainter
import androidx.compose.animation.graphics.vector.AnimatedImageVector
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

@Composable
fun AnimatedIcon(
    modifier: Modifier = Modifier,
    @DrawableRes id: Int,
    atEnd: Boolean = false,
    tint: Color = LocalContentColor.current,
) {
    val animation = AnimatedImageVector.animatedVectorResource(id)
    val painter = rememberAnimatedVectorPainter(
        animation,
        atEnd,
    )
    Icon(
        modifier = modifier,
        painter = painter,
        contentDescription = null,
        tint = tint,
    )
}