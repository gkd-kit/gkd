package li.songe.gkd.ui.component

import androidx.annotation.DrawableRes
import androidx.compose.animation.graphics.res.animatedVectorResource
import androidx.compose.animation.graphics.res.rememberAnimatedVectorPainter
import androidx.compose.animation.graphics.vector.AnimatedImageVector
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import li.songe.gkd.util.SafeR

@Composable
private fun AnimatedIcon(
    modifier: Modifier,
    @DrawableRes id: Int,
    atEnd: Boolean,
    tint: Color,
    contentDescription: String?,
) {
    val animation = AnimatedImageVector.animatedVectorResource(id)
    val painter = rememberAnimatedVectorPainter(
        animation,
        atEnd,
    )
    Icon(
        modifier = modifier,
        painter = painter,
        contentDescription = contentDescription,
        tint = tint,
    )
}

@Composable
fun AnimatedIconButton(
    onClick: () -> Unit,
    @DrawableRes id: Int,
    modifier: Modifier = Modifier,
    atEnd: Boolean = false,
    tint: Color = LocalContentColor.current,
    contentDescription: String? = getIconDesc(id, atEnd),
) = TooltipIconButtonBox(
    contentDescription = contentDescription,
) {
    IconButton(
        onClick = onClick,
    ) {
        AnimatedIcon(
            id = id,
            atEnd = atEnd,
            modifier = modifier,
            tint = tint,
            contentDescription = contentDescription,
        )
    }
}

private fun getIconDesc(@DrawableRes id: Int, atEnd: Boolean): String? = when (id) {
    SafeR.ic_anim_search_close -> if (atEnd) "关闭搜索" else "打开搜索"
    else -> null
}
