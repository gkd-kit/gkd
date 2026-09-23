package li.gkd.app.ui.icon

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val FlashOff: ImageVector
    get() {
        if (_flashOff != null) {
            return _flashOff!!
        }
        _flashOff = ImageVector.Builder(
            name = "FlashOff", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 960f, viewportHeight = 960f,
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(280f, 80f)
                horizontalLineToRelative(400f)
                lineToRelative(-80f, 280f)
                horizontalLineToRelative(160f)
                lineTo(643f, 529f)
                lineToRelative(-57f, -57f)
                lineToRelative(22f, -32f)
                horizontalLineToRelative(-54f)
                lineToRelative(-47f, -47f)
                lineToRelative(67f, -233f)
                lineTo(360f, 160f)
                verticalLineToRelative(86f)
                lineToRelative(-80f, -80f)
                verticalLineToRelative(-86f)
                close()
                moveTo(400f, 880f)
                verticalLineToRelative(-320f)
                lineTo(280f, 560f)
                verticalLineToRelative(-166f)
                lineTo(55f, 169f)
                lineToRelative(57f, -57f)
                lineToRelative(736f, 736f)
                lineToRelative(-57f, 57f)
                lineToRelative(-241f, -241f)
                lineTo(400f, 880f)
                close()
                moveTo(473f, 359f)
                close()
            }
        }.build()
        return _flashOff!!
    }

private var _flashOff: ImageVector? = null
