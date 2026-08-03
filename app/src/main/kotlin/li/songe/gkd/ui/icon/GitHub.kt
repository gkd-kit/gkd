package li.songe.gkd.ui.icon

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val GitHub: ImageVector
    get() {
        if (_GitHub != null) {
            return _GitHub!!
        }
        _GitHub = ImageVector.Builder(
            name = "GitHub",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 32f,
            viewportHeight = 32f,
        ).apply {
            path(
                fill = SolidColor(Color.Black),
                pathFillType = PathFillType.EvenOdd,
            ) {
                moveTo(16f, 0f)
                curveTo(7.16f, 0f, 0f, 7.16f, 0f, 16f)
                curveTo(0f, 23.08f, 4.58f, 29.06f, 10.94f, 31.18f)
                curveTo(11.74f, 31.32f, 12.04f, 30.84f, 12.04f, 30.42f)
                curveTo(12.04f, 30.04f, 12.02f, 28.78f, 12.02f, 27.44f)
                curveTo(8f, 28.18f, 6.96f, 26.46f, 6.64f, 25.56f)
                curveTo(6.46f, 25.1f, 5.68f, 23.68f, 5f, 23.3f)
                curveTo(4.44f, 23f, 3.64f, 22.26f, 4.98f, 22.24f)
                curveTo(6.24f, 22.22f, 7.14f, 23.4f, 7.44f, 23.88f)
                curveTo(8.88f, 26.3f, 11.18f, 25.62f, 12.1f, 25.2f)
                curveTo(12.24f, 24.16f, 12.66f, 23.46f, 13.12f, 23.06f)
                curveTo(9.56f, 22.66f, 5.84f, 21.28f, 5.84f, 15.16f)
                curveTo(5.84f, 13.42f, 6.46f, 11.98f, 7.48f, 10.86f)
                curveTo(7.32f, 10.46f, 6.76f, 8.82f, 7.64f, 6.62f)
                curveTo(7.64f, 6.62f, 8.98f, 6.2f, 12.04f, 8.26f)
                curveTo(13.32f, 7.9f, 14.68f, 7.72f, 16.04f, 7.72f)
                curveTo(17.4f, 7.72f, 18.76f, 7.9f, 20.04f, 8.26f)
                curveTo(23.1f, 6.18f, 24.44f, 6.62f, 24.44f, 6.62f)
                curveTo(25.32f, 8.82f, 24.76f, 10.46f, 24.6f, 10.86f)
                curveTo(25.62f, 11.98f, 26.24f, 13.4f, 26.24f, 15.16f)
                curveTo(26.24f, 21.3f, 22.5f, 22.66f, 18.94f, 23.06f)
                curveTo(19.52f, 23.56f, 20.02f, 24.52f, 20.02f, 26.02f)
                curveTo(20.02f, 28.16f, 20f, 29.88f, 20f, 30.42f)
                curveTo(20f, 30.84f, 20.3f, 31.34f, 21.1f, 31.18f)
                curveTo(27.42f, 29.06f, 32f, 23.06f, 32f, 16f)
                curveTo(32f, 7.16f, 24.84f, 0f, 16f, 0f)
                close()
            }
        }.build()
        return _GitHub!!
    }

@Suppress("ObjectPropertyName")
private var _GitHub: ImageVector? = null
