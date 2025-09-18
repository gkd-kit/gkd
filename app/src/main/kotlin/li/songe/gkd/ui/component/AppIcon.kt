package li.songe.gkd.ui.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.accompanist.drawablepainter.rememberDrawablePainter
import li.songe.gkd.util.appIconMapFlow

@Composable
fun AppIcon(
    modifier: Modifier = Modifier,
    appId: String,
) {
    val icon = appIconMapFlow.collectAsState().value[appId]
    val iconModifier = modifier.size(32.dp)
    if (icon != null) {
        Image(
            painter = rememberDrawablePainter(icon),
            contentDescription = null,
            modifier = iconModifier
        )
    } else {
        PerfIcon(
            imageVector = PerfIcon.Android,
            modifier = iconModifier
        )
    }
}
