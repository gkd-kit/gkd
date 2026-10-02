package li.gkd.app.ui.icon

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.vector.group
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp
import li.gkd.app.ui.component.GkIcons

val GkIcons.Logo: ImageVector by lazy {
    ImageVector.Builder(
        name = "GkdLogo",
        defaultWidth = 64.dp,
        defaultHeight = 64.dp,
        viewportWidth = 64f,
        viewportHeight = 64f,
    ).apply {
        path(fill = SolidColor(Color.White)) {
            moveTo(12f, 0f)
            lineTo(52f, 0f)
            curveTo(58.627f, 0f, 64f, 5.373f, 64f, 12f)
            lineTo(64f, 52f)
            curveTo(64f, 58.627f, 58.627f, 64f, 52f, 64f)
            lineTo(12f, 64f)
            curveTo(5.373f, 64f, 0f, 58.627f, 0f, 52f)
            lineTo(0f, 12f)
            curveTo(0f, 5.373f, 5.373f, 0f, 12f, 0f)
            close()
        }
        // Original Android launcher paths, without the adaptive icon's outer padding.
        group(translationX = -22f, translationY = -20f) {
            addPath(
                pathData = PathParser().parsePathString("M43.91,75C43.87,72.52 44.11,70.03 43.99,67.57C43.83,64.29 43.43,61.02 43.04,57.76C42.92,56.77 43.01,56.13 44.05,55.75C49.61,53.68 55.22,53.42 60.92,55.1C61.83,55.37 62.1,55.76 61.97,56.76C61.72,58.58 61.64,60.44 61.59,62.29C61.46,66.44 61.39,70.59 61.3,74.87C55.54,75 49.79,75 43.91,75Z")
                    .toNodes(), fill = SolidColor(Color.Black)
            )
            addPath(
                pathData = PathParser().parsePathString("M64,75C64.06,68.79 64.24,62.58 64.43,56C72.15,60.04 75.99,66.65 78,74.9C73.38,75 68.75,75 64,75Z")
                    .toNodes(), fill = SolidColor(Color.Black)
            )
            addPath(
                pathData = PathParser().parsePathString("M30,75C30.73,70.58 32.1,66.39 34.78,62.98C36.28,61.05 38.13,59.45 39.85,57.73C40.11,57.47 40.47,57.33 41,57C42.27,63 42.09,68.87 41.75,74.87C37.87,75 33.99,75 30,75Z")
                    .toNodes(), fill = SolidColor(Color.Black)
            )
            addPath(
                pathData = PathParser().parsePathString("M45.08,42l8.47,-8.5l8.47,8.5l-8.47,8.5z")
                    .toNodes(), fill = SolidColor(Color.Black)
            )
            addPath(
                pathData = PathParser().parsePathString("M41,37.49l8.49,-8.49l2.82,2.84l-8.49,8.49z")
                    .toNodes(), fill = SolidColor(Color.Black)
            )
            addPath(
                pathData = PathParser().parsePathString("M57.82,29l8.49,8.49l-2.82,2.84l-8.49,-8.49z")
                    .toNodes(), fill = SolidColor(Color.Black)
            )
        }
    }.build()
}
