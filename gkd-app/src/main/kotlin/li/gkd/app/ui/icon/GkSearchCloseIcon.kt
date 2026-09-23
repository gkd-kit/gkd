package li.gkd.app.ui.icon

import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.group
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp
import li.gkd.app.text.UiStrings
import li.gkd.app.ui.component.GkIcons
import li.gkd.app.ui.component.GkTooltipIconButtonBox

@Composable
fun GkSearchCloseIconButton(
    onClick: () -> Unit,
    isSearchOpen: Boolean,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
    contentDescription: String = if (isSearchOpen) UiStrings.search_close else UiStrings.search_open,
) = GkTooltipIconButtonBox(contentDescription = contentDescription) {
    IconButton(onClick = onClick) {
        GkSearchCloseIcon(
            isSearchOpen = isSearchOpen,
            modifier = modifier,
            tint = tint,
            contentDescription = contentDescription,
        )
    }
}

@Composable
fun GkSearchCloseIcon(
    isSearchOpen: Boolean,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
    contentDescription: String = if (isSearchOpen) UiStrings.search_close else UiStrings.search_open,
) {
    val transition = updateTransition(isSearchOpen, label = "SearchClose")
    // Independent tracks preserve their current values when the target changes
    // mid-animation; switching direction never replaces the visible geometry.
    val handleProgress by transition.animateFloat(
        transitionSpec = { tween(if (targetState) 220 else 260) },
        label = "Handle",
    ) { if (it) 1f else 0f }
    val circleVisibility by transition.animateFloat(
        transitionSpec = {
            if (targetState) tween(120) else tween(220, delayMillis = 40)
        },
        label = "Circle",
    ) { if (it) 0f else 1f }
    val slashVisibility by transition.animateFloat(
        transitionSpec = {
            if (targetState) tween(160, delayMillis = 60) else tween(120)
        },
        label = "Slash",
    ) { if (it) 1f else 0f }
    val imageVector = remember(handleProgress, circleVisibility, slashVisibility) {
        when {
            handleProgress == 0f && circleVisibility == 1f && slashVisibility == 0f -> Icons.Filled.Search
            handleProgress == 1f && circleVisibility == 0f && slashVisibility == 1f -> GkIcons.Close
            else -> searchCloseVector(handleProgress, circleVisibility, slashVisibility)
        }
    }
    Icon(
        imageVector = imageVector,
        modifier = modifier.size(24.dp),
        contentDescription = contentDescription,
        tint = tint,
    )
}

// Filled contours retain AndroidX's original caps and handle junction.
// Coordinates originate from material-icons-core 1.7.8 (Apache-2.0).
private fun searchCloseVector(
    handleProgress: Float,
    circleVisibility: Float,
    slashVisibility: Float,
): ImageVector = ImageVector.Builder(
    name = "SearchClose",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
).apply {
    val circleScale = 0.85f + 0.15f * circleVisibility
    group(
        pivotX = 9.5f,
        pivotY = 9.5f,
        scaleX = circleScale,
        scaleY = circleScale,
    ) {
        path(fill = SolidColor(Color.Black), fillAlpha = circleVisibility) {
            // The original ring excluding the small wedge owned by the handle.
            moveTo(14.43f, 13.73f)
            curveTo(15.41f, 12.59f, 16f, 11.11f, 16f, 9.5f)
            curveTo(16f, 5.91f, 13.09f, 3f, 9.5f, 3f)
            curveTo(5.91f, 3f, 3f, 5.91f, 3f, 9.5f)
            curveTo(3f, 13.09f, 5.91f, 16f, 9.5f, 16f)
            curveTo(11.11f, 16f, 12.59f, 15.41f, 13.73f, 14.43f)
            lineTo(12.68375f, 12.68375f)
            curveTo(11.87f, 13.4975f, 10.745f, 14f, 9.5f, 14f)
            curveTo(7.01f, 14f, 5f, 11.99f, 5f, 9.5f)
            curveTo(5f, 7.01f, 7.01f, 5f, 9.5f, 5f)
            curveTo(11.99f, 5f, 14f, 7.01f, 14f, 9.5f)
            curveTo(14f, 10.745f, 13.4975f, 11.87f, 12.68375f, 12.68375f)
            close()
        }
    }
    path(fill = SolidColor(Color.Black)) {
        with(SearchCloseContour(this, handleProgress)) {
            // The handle stays opaque and becomes the complete backslash.
            move(14.43f, 13.73f, 6.41f, 5f)
            line(14.71f, 14f, 6.41f, 5f)
            line(15.5f, 14f, 6.41f, 5f)
            line(20.49f, 19f, 19f, 17.59f)
            line(19f, 20.49f, 17.59f, 19f)
            line(14f, 15.5f, 5f, 6.41f)
            line(14f, 14.71f, 5f, 6.41f)
            line(13.73f, 14.43f, 5f, 6.41f)
            line(12.68375f, 12.68375f, 5.705f, 5.705f)
            close()
        }
    }
    path(fill = SolidColor(Color.Black), fillAlpha = slashVisibility) {
        // Grow length symmetrically from the centre; keep the original width.
        val reach = 6.295f * slashVisibility
        moveTo(12f + reach - 0.705f, 12f - reach - 0.705f)
        lineTo(12f + reach + 0.705f, 12f - reach + 0.705f)
        lineTo(12f - reach + 0.705f, 12f + reach + 0.705f)
        lineTo(12f - reach - 0.705f, 12f + reach - 0.705f)
        close()
    }
}.build()

private class SearchCloseContour(
    private val path: PathBuilder,
    private val progress: Float,
) {
    private fun mix(from: Float, to: Float) = from + (to - from) * progress

    fun move(x: Float, y: Float, toX: Float, toY: Float) {
        path.moveTo(mix(x, toX), mix(y, toY))
    }

    fun line(x: Float, y: Float, toX: Float, toY: Float) {
        path.lineTo(mix(x, toX), mix(y, toY))
    }

    fun close() = path.close()
}
