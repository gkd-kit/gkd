package li.songe.gkd.icon

import androidx.compose.material.Icon
import androidx.compose.material.icons.materialIcon
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.tooling.preview.Preview

val ArrowIcon = materialIcon(name = "ArrowIcon") {
    addPath(
        pathData = addPathNodes("M6.23 20.23L8 22l10-10L8 2L6.23 3.77L14.46 12z"),
        fill = Brush.linearGradient(listOf(Color.Black, Color.Black))
    )
}

@Preview
@Composable
fun PreviewArrowIcon() {
    Icon(imageVector = ArrowIcon, contentDescription = null)
}