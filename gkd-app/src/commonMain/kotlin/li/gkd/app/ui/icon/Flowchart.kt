package li.gkd.app.ui.icon

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.vector.group
import androidx.compose.ui.unit.dp

// Google Material Symbols — Flowchart (Outlined), converted from SVG to ImageVector.
// Source: https://github.com/google/material-design-icons/blob/master/symbols/web/flowchart/materialsymbolsoutlined/flowchart_24px.svg
// Licensed under the Apache License, Version 2.0: https://www.apache.org/licenses/LICENSE-2.0
val Flowchart: ImageVector by lazy {
    ImageVector.Builder(
        name = "Flowchart", defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 960f, viewportHeight = 960f,
    ).apply {
        group(translationY = 960f) {
            addPath(
                pathData = PathParser().parsePathString(
                    "M600-160v-80H440v-200h-80v80H80v-240h280v80h80v-200h160v-80h280v240H600v-80h-80v320h80v-80h280v240H600Zm80-80h120v-80H680v80ZM160-440h120v-80H160v80Zm520-200h120v-80H680v80Zm0 400v-80 80ZM280-440v-80 80Zm400-200v-80 80Z"
                ).toNodes(),
                fill = SolidColor(Color.Black),
            )
        }
    }.build()
}
