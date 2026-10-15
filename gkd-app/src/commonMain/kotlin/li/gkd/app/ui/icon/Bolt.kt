package li.gkd.app.ui.icon

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val Bolt: ImageVector
    get() {
        if (_bolt != null) {
            return _bolt!!
        }
        _bolt = ImageVector.Builder(
            name = "Bolt", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f,
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(10.55f, 18.2f)
                lineTo(15.73f, 12f)
                horizontalLineToRelative(-4f)
                lineTo(12.45f, 6.32f)
                lineTo(7.83f, 13f)
                horizontalLineTo(11.3f)
                lineToRelative(-0.75f, 5.2f)
                close()
                moveTo(8f, 22f)
                lineTo(9f, 15f)
                horizontalLineTo(4f)
                lineTo(13f, 2f)
                horizontalLineToRelative(2f)
                lineToRelative(-1f, 8f)
                horizontalLineToRelative(6f)
                lineTo(10f, 22f)
                horizontalLineTo(8f)
                close()
            }
        }.build()
        return _bolt!!
    }

private var _bolt: ImageVector? = null
