package li.gkd.app.ui.icon

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Link
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.VectorPath
import androidx.compose.ui.unit.dp

val LinkDiagonal: ImageVector
    get() {
        if (_linkDiagonal != null) {
            return _linkDiagonal!!
        }
        val path = Icons.Outlined.Link.root[0] as VectorPath
        _linkDiagonal = ImageVector.Builder(
            name = "LinkDiagonal",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            addGroup(rotate = -45f, pivotX = 12f, pivotY = 12f)
            addPath(pathData = path.pathData, fill = path.fill)
            clearGroup()
        }.build()
        return _linkDiagonal!!
    }

private var _linkDiagonal: ImageVector? = null
