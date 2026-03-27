package li.songe.gkd.ui.icon

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val ToggleMid: ImageVector
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
                moveTo(280f, 720f)
                quadToRelative(-100f, 0f, -170f, -70f)
                reflectiveQuadTo(40f, 480f)
                quadToRelative(0f, -100f, 70f, -170f)
                reflectiveQuadToRelative(170f, -70f)
                horizontalLineToRelative(400f)
                quadToRelative(100f, 0f, 170f, 70f)
                reflectiveQuadToRelative(70f, 170f)
                quadToRelative(0f, 100f, -70f, 170f)
                reflectiveQuadToRelative(-170f, 70f)
                lineTo(280f, 720f)
                close()
                moveTo(280f, 640f)
                horizontalLineToRelative(400f)
                quadToRelative(66f, 0f, 113f, -47f)
                reflectiveQuadToRelative(47f, -113f)
                quadToRelative(0f, -66f, -47f, -113f)
                reflectiveQuadToRelative(-113f, -47f)
                lineTo(280f, 320f)
                quadToRelative(-66f, 0f, -113f, 47f)
                reflectiveQuadToRelative(-47f, 113f)
                quadToRelative(0f, 66f, 47f, 113f)
                reflectiveQuadToRelative(113f, 47f)
                close()
                moveTo(565f, 565f)
                quadToRelative(35f, -35f, 35f, -85f)
                reflectiveQuadToRelative(-35f, -85f)
                quadToRelative(-35f, -35f, -85f, -35f)
                reflectiveQuadToRelative(-85f, 35f)
                quadToRelative(-35f, 35f, -35f, 85f)
                reflectiveQuadToRelative(35f, 85f)
                quadToRelative(35f, 35f, 85f, 35f)
                reflectiveQuadToRelative(85f, -35f)
                close()
            }
        }.build()

        return _IconName!!
    }

@Suppress("ObjectPropertyName")
private var _IconName: ImageVector? = null
