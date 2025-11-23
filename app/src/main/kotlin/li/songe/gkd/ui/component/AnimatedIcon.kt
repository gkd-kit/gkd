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
import li.songe.gkd.util.SafeR

@Composable
fun AnimatedIcon(
    modifier: Modifier = Modifier,
    @DrawableRes id: Int,
    atEnd: Boolean = false,
    tint: Color = LocalContentColor.current,
    contentDescription: String? = getIconDesc(id, atEnd),
) {
    val animation = AnimatedImageVector.animatedVectorResource(id)
    val painter = rememberAnimatedVectorPainter(
        animation,
        atEnd,
    )
    PerfTooltipBox(
        tooltipText = contentDescription,
    ) {
        Icon(
            modifier = modifier,
            painter = painter,
            contentDescription = contentDescription,
            tint = tint,
        )
    }
}

private fun getIconDesc(@DrawableRes id: Int, atEnd: Boolean): String? = when (id) {
    SafeR.ic_anim_search_close -> if (atEnd) "关闭搜索" else "打开搜索"
    else -> null
}
