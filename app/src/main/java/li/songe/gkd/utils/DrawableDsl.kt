package li.songe.gkd.utils

import android.graphics.Path
import android.graphics.drawable.ShapeDrawable
import android.graphics.drawable.shapes.RectShape
import androidx.compose.material.icons.materialIcon
import androidx.compose.material.icons.materialPath
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

fun createDrawable(block: () -> Unit) {
    val s = materialIcon(name = "xx") {
        addPath(addPathNodes(""))
    }
}

fun createVectorDrawable(block: () -> Unit) {
    val path = Path().apply {
        addPathNodes("").forEach {
            it.isQuad
        }
    }
    val shapeDrawable = ShapeDrawable(RectShape())
    shapeDrawable.apply {

        addPathNodes("")[0].isCurve
    }
//    val r = Resources()
//    Drawable.createFromXml()
}


val x = createDrawable {
//    val p =
    vector {
        width = 24.dp
        height = 24.dp
        viewportWidth = 24F
        viewportHeight = 24F
        path {
            width
            fillColor = Color(0xFF000000)
            pathData = "M20,11H7.83l5.59,-5.59L12,4l-8,8l8,8l1.41,-1.41L7.83,13H20v-2z"
        }
    }
}

interface VectorType {
    var width: Dp
    var height: Dp
    var viewportWidth: Float
    var viewportHeight: Float
}

fun vector(block: VectorType.() -> Unit) {}

interface PathType {
    var fillColor: Color
    var pathData: String
}

fun path(block: PathType.() -> Unit) {}

fun testDrawable() {

    val s2 = vector {
        width = 24.dp
        height = 24.dp
        viewportWidth = 24F
        viewportHeight = 24F
        path {
            width
            fillColor = Color(0xFF000000)
            pathData = "M20,11H7.83l5.59,-5.59L12,4l-8,8l8,8l1.41,-1.41L7.83,13H20v-2z"
        }
    }
}