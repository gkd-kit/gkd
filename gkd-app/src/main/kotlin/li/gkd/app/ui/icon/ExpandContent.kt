package li.gkd.app.ui.icon

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

// Material Symbols Outlined: expand_content (Google, Apache-2.0).
val ExpandContent: ImageVector by lazy {
    ImageVector.Builder(
        name = "ExpandContent", defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 960f, viewportHeight = 960f,
    ).apply {
        path(fill = SolidColor(Color.Black)) {
            moveTo(200f, 760f)
            verticalLineTo(520f)
            horizontalLineTo(280f)
            verticalLineTo(680f)
            horizontalLineTo(440f)
            verticalLineTo(760f)
            close()
            moveTo(680f, 440f)
            verticalLineTo(280f)
            horizontalLineTo(520f)
            verticalLineTo(200f)
            horizontalLineTo(760f)
            verticalLineTo(440f)
            close()
        }
    }.build()
}
