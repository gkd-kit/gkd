package li.gkd.app.ui.icon

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val AndroidHead: ImageVector
    get() {
        if (_androidHead != null) {
            return _androidHead!!
        }
        _androidHead = ImageVector.Builder(
            name = "AndroidHead", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f,
        ).apply {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(7f, 7f)
                lineTo(5f, 4f)
                moveTo(17f, 7f)
                lineTo(19f, 4f)
                moveTo(3f, 18f)
                lineTo(3f, 16f)
                arcTo(9f, 9f, 0f, false, true, 21f, 16f)
                lineTo(21f, 18f)
                close()
            }
            path(fill = SolidColor(Color.Black)) {
                moveTo(9f, 13f)
                arcTo(1f, 1f, 0f, true, true, 7f, 13f)
                arcTo(1f, 1f, 0f, true, true, 9f, 13f)
                close()
                moveTo(17f, 13f)
                arcTo(1f, 1f, 0f, true, true, 15f, 13f)
                arcTo(1f, 1f, 0f, true, true, 17f, 13f)
                close()
            }
        }.build()
        return _androidHead!!
    }

private var _androidHead: ImageVector? = null
