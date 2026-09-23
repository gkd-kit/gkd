package li.gkd.app.ui.icon

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val FlashOn: ImageVector
    get() {
        if (_flashOn != null) {
            return _flashOn!!
        }
        _flashOn = ImageVector.Builder(
            name = "FlashOn", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 960f, viewportHeight = 960f,
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveToRelative(480f, 624f)
                lineToRelative(128f, -184f)
                lineTo(494f, 440f)
                lineToRelative(80f, -280f)
                lineTo(360f, 160f)
                verticalLineToRelative(320f)
                horizontalLineToRelative(120f)
                verticalLineToRelative(144f)
                close()
                moveTo(400f, 880f)
                verticalLineToRelative(-320f)
                lineTo(280f, 560f)
                verticalLineToRelative(-480f)
                horizontalLineToRelative(400f)
                lineToRelative(-80f, 280f)
                horizontalLineToRelative(160f)
                lineTo(400f, 880f)
                close()
                moveTo(480f, 480f)
                lineTo(360f, 480f)
                horizontalLineToRelative(120f)
                close()
            }
        }.build()
        return _flashOn!!
    }

private var _flashOn: ImageVector? = null
