package li.gkd.app.ui.icon

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

// Material Symbols Outlined: collapse_content (Google, Apache-2.0).
val CollapseContent: ImageVector by lazy {
    ImageVector.Builder(
        name = "CollapseContent", defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 960f, viewportHeight = 960f,
    ).apply {
        path(fill = SolidColor(Color.Black)) {
            moveTo(440f, 520f)
            verticalLineTo(760f)
            horizontalLineTo(360f)
            verticalLineTo(600f)
            horizontalLineTo(200f)
            verticalLineTo(520f)
            close()
            moveTo(600f, 200f)
            verticalLineTo(360f)
            horizontalLineTo(760f)
            verticalLineTo(440f)
            horizontalLineTo(520f)
            verticalLineTo(200f)
            close()
        }
    }.build()
}
