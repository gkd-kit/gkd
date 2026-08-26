package li.songe.gkd.ui.icon

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val DragPan: ImageVector
    get() {
        if (_IconName != null) {
            return _IconName!!
        }
        _IconName = ImageVector.Builder(
            name = "IconName",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 960f,
            viewportHeight = 960f
        ).apply {
            path(fill = SolidColor(Color(0xFF5F6368))) {
                moveTo(480f, 880f)
                lineTo(310f, 710f)
                lineToRelative(57f, -57f)
                lineToRelative(73f, 73f)
                verticalLineToRelative(-206f)
                lineTo(235f, 520f)
                lineToRelative(73f, 72f)
                lineToRelative(-58f, 58f)
                lineTo(80f, 480f)
                lineToRelative(169f, -169f)
                lineToRelative(57f, 57f)
                lineToRelative(-72f, 72f)
                horizontalLineToRelative(206f)
                verticalLineToRelative(-206f)
                lineToRelative(-73f, 73f)
                lineToRelative(-57f, -57f)
                lineToRelative(170f, -170f)
                lineToRelative(170f, 170f)
                lineToRelative(-57f, 57f)
                lineToRelative(-73f, -73f)
                verticalLineToRelative(206f)
                horizontalLineToRelative(205f)
                lineToRelative(-73f, -72f)
                lineToRelative(58f, -58f)
                lineToRelative(170f, 170f)
                lineToRelative(-170f, 170f)
                lineToRelative(-57f, -57f)
                lineToRelative(73f, -73f)
                lineTo(520f, 520f)
                verticalLineToRelative(205f)
                lineToRelative(72f, -73f)
                lineToRelative(58f, 58f)
                lineTo(480f, 880f)
                close()
            }
        }.build()

        return _IconName!!
    }

@Suppress("ObjectPropertyName")
private var _IconName: ImageVector? = null
