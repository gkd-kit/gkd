package li.gkd.app.ui.icon

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val PageInfo: ImageVector
    get() {
        if (_pageInfo != null) {
            return _pageInfo!!
        }
        _pageInfo = ImageVector.Builder(
            name = "PageInfo", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 960f, viewportHeight = 960f,
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(710f, 810f)
                quadTo(647f, 810f, 603.5f, 766.5f)
                quadTo(560f, 723f, 560f, 660f)
                quadTo(560f, 597f, 603.5f, 553.5f)
                quadTo(647f, 510f, 710f, 510f)
                quadTo(773f, 510f, 816.5f, 553.5f)
                quadTo(860f, 597f, 860f, 660f)
                quadTo(860f, 723f, 816.5f, 766.5f)
                quadTo(773f, 810f, 710f, 810f)
                close()
                moveTo(710f, 730f)
                quadTo(739f, 730f, 759.5f, 709.5f)
                quadTo(780f, 689f, 780f, 660f)
                quadTo(780f, 631f, 759.5f, 610.5f)
                quadTo(739f, 590f, 710f, 590f)
                quadTo(681f, 590f, 660.5f, 610.5f)
                quadTo(640f, 631f, 640f, 660f)
                quadTo(640f, 689f, 660.5f, 709.5f)
                quadTo(681f, 730f, 710f, 730f)
                close()
                moveTo(160f, 700f)
                lineTo(160f, 620f)
                lineTo(480f, 620f)
                lineTo(480f, 700f)
                lineTo(160f, 700f)
                close()
                moveTo(250f, 450f)
                quadTo(187f, 450f, 143.5f, 406.5f)
                quadTo(100f, 363f, 100f, 300f)
                quadTo(100f, 237f, 143.5f, 193.5f)
                quadTo(187f, 150f, 250f, 150f)
                quadTo(313f, 150f, 356.5f, 193.5f)
                quadTo(400f, 237f, 400f, 300f)
                quadTo(400f, 363f, 356.5f, 406.5f)
                quadTo(313f, 450f, 250f, 450f)
                close()
                moveTo(250f, 370f)
                quadTo(279f, 370f, 299.5f, 349.5f)
                quadTo(320f, 329f, 320f, 300f)
                quadTo(320f, 271f, 299.5f, 250.5f)
                quadTo(279f, 230f, 250f, 230f)
                quadTo(221f, 230f, 200.5f, 250.5f)
                quadTo(180f, 271f, 180f, 300f)
                quadTo(180f, 329f, 200.5f, 349.5f)
                quadTo(221f, 370f, 250f, 370f)
                close()
                moveTo(480f, 340f)
                lineTo(480f, 260f)
                lineTo(800f, 260f)
                lineTo(800f, 340f)
                lineTo(480f, 340f)
                close()
                moveTo(710f, 660f)
                quadTo(710f, 660f, 710f, 660f)
                quadTo(710f, 660f, 710f, 660f)
                quadTo(710f, 660f, 710f, 660f)
                quadTo(710f, 660f, 710f, 660f)
                quadTo(710f, 660f, 710f, 660f)
                quadTo(710f, 660f, 710f, 660f)
                quadTo(710f, 660f, 710f, 660f)
                quadTo(710f, 660f, 710f, 660f)
                close()
                moveTo(250f, 300f)
                quadTo(250f, 300f, 250f, 300f)
                quadTo(250f, 300f, 250f, 300f)
                quadTo(250f, 300f, 250f, 300f)
                quadTo(250f, 300f, 250f, 300f)
                quadTo(250f, 300f, 250f, 300f)
                quadTo(250f, 300f, 250f, 300f)
                quadTo(250f, 300f, 250f, 300f)
                quadTo(250f, 300f, 250f, 300f)
                close()
            }
        }.build()
        return _pageInfo!!
    }

private var _pageInfo: ImageVector? = null
