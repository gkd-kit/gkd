package li.songe.gkd.icon

import androidx.compose.material.Icon
import androidx.compose.material.icons.materialIcon
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.tooling.preview.Preview

val HomeIcon = materialIcon(name = "ArrowIcon") {
    addPath(
        pathData = addPathNodes("M16.612 2.214a1.01 1.01 0 0 0-1.242 0L1 13.419l1.243 1.572L4 13.621V26a2.004 2.004 0 0 0 2 2h20a2.004 2.004 0 0 0 2-2V13.63L29.757 15L31 13.428zM18 26h-4v-8h4zm2 0v-8a2.002 2.002 0 0 0-2-2h-4a2.002 2.002 0 0 0-2 2v8H6V12.062l10-7.79l10 7.8V26z"),
        fill = Brush.linearGradient(listOf(Color.Black, Color.Black))
    )
}

@Preview
@Composable
fun PreviewHomeIcon() {
    Icon(imageVector = HomeIcon, contentDescription = null)
}