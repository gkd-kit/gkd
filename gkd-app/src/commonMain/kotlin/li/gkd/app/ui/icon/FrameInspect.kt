package li.gkd.app.ui.icon

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.vector.group
import androidx.compose.ui.unit.dp

// Google Material Symbols Outlined, converted from SVG to ImageVector (Apache-2.0).
// https://github.com/google/material-design-icons/blob/master/symbols/web/frame_inspect/materialsymbolsoutlined/frame_inspect_24px.svg
val FrameInspect: ImageVector by lazy {
    ImageVector.Builder(
            name = "FrameInspect",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 960f,
            viewportHeight = 960f,
        )
        .apply {
            group(translationY = 960f) {
                addPath(
                    pathData =
                        PathParser()
                            .parsePathString(
                                "M450-420q38 0 64-26t26-64q0-38-26-64t-64-26q-38 0-64 26t-26 64q0 38 26 64t64 26Z" +
                                    "m193 160L538-365q-20 13-42.5 19t-45.5 6q-71 0-120.5-49.5T280-510q0-71 49.5-120.5T450-680q71 0 120.5 49.5T620-510q0 23-6.5 45.5T594-422l106 106-57 56Z" +
                                    "M200-120q-33 0-56.5-23.5T120-200v-160h80v160h160v80H200Z" +
                                    "m400 0v-80h160v-160h80v160q0 33-23.5 56.5T760-120H600Z" +
                                    "M120-600v-160q0-33 23.5-56.5T200-840h160v80H200v160h-80Z" +
                                    "m640 0v-160H600v-80h160q33 0 56.5 23.5T840-760v160h-80Z",
                            )
                            .toNodes(),
                    fill = SolidColor(Color.Black),
                )
            }
        }
        .build()
}
