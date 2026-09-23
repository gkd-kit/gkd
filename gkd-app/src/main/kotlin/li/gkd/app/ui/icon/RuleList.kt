package li.gkd.app.ui.icon

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val RuleList: ImageVector
    get() {
        if (_ruleList != null) {
            return _ruleList!!
        }
        _ruleList = ImageVector.Builder(
            name = "RuleList", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f, autoMirror = true,
        ).apply {
            path(stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
                moveTo(3f, 6f)
                horizontalLineTo(16f)
                moveTo(3f, 12f)
                horizontalLineTo(9f)
                moveTo(3f, 18f)
                horizontalLineTo(16f)
                moveTo(13f, 12f)
                horizontalLineTo(21f)
                moveTo(18f, 9f)
                lineTo(21f, 12f)
                lineTo(18f, 15f)
            }
        }.build()
        return _ruleList!!
    }

private var _ruleList: ImageVector? = null
