package li.songe.gkd.icon

import androidx.compose.material.Icon
import androidx.compose.material.icons.materialIcon
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.tooling.preview.Preview

// @DslMarker
// https://github.com/JetBrains/kotlin-wrappers/blob/master/kotlin-react/src/jsMain/kotlin/react/ChildrenBuilder.kt
val AddIcon = materialIcon(name = "AddIcon") {
    addPath(
        pathData = addPathNodes("M18,13h-5v5c0,0.55 -0.45,1 -1,1s-1,-0.45 -1,-1v-5H6c-0.55,0 -1,-0.45 -1,-1s0.45,-1 1,-1h5V6c0,-0.55 0.45,-1 1,-1s1,0.45 1,1v5h5c0.55,0 1,0.45 1,1s-0.45,1 -1,1z"),
        fill = Brush.linearGradient(listOf(Color.Black, Color.Black))
    )
}

@Preview
@Composable
fun PreviewAddIcon() {
    Icon(imageVector = AddIcon, contentDescription = null)
}
