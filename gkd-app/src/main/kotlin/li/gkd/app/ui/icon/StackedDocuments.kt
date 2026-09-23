package li.gkd.app.ui.icon

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val StackedDocuments: ImageVector
    get() {
        if (_stackedDocuments != null) {
            return _stackedDocuments!!
        }
        _stackedDocuments = ImageVector.Builder(
            name = "StackedDocuments", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f,
        ).apply {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                // Back document, with its covered edges omitted.
                moveTo(5f, 17f)
                curveTo(3.9f, 17f, 3f, 16.1f, 3f, 15f)
                lineTo(3f, 5f)
                curveTo(3f, 3.9f, 3.9f, 3f, 5f, 3f)
                lineTo(15f, 3f)
                curveTo(16.1f, 3f, 17f, 3.9f, 17f, 5f)

                moveTo(9f, 7f)
                lineTo(19f, 7f)
                curveTo(20.1f, 7f, 21f, 7.9f, 21f, 9f)
                lineTo(21f, 19f)
                curveTo(21f, 20.1f, 20.1f, 21f, 19f, 21f)
                lineTo(9f, 21f)
                curveTo(7.9f, 21f, 7f, 20.1f, 7f, 19f)
                lineTo(7f, 9f)
                curveTo(7f, 7.9f, 7.9f, 7f, 9f, 7f)
                close()

                moveTo(11f, 12f)
                lineTo(16f, 12f)
                moveTo(11f, 16f)
                lineTo(14f, 16f)
            }
        }.build()
        return _stackedDocuments!!
    }

private var _stackedDocuments: ImageVector? = null
